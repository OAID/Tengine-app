package com.openailab.jni.tengine_java;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.SystemClock;
import android.os.Trace;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    static final String TAG = "TEST_TENGINE_AAR";
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private SurfaceView mSurfaceView,mDrawView;
    private int                    mScreenWidth,mScreenHeight;;
    private long                  processtime;
    private Camera.Parameters    mParameters;
    private Camera                mCamera;
    private Camera.CameraInfo mCameraInfo;
    private YuvImage image;
    private ByteArrayOutputStream stream;
    private String result;
    private RectF rect;
    private byte [] dat;
    private byte[] inputDat = new byte[300*300*3];
    private Bitmap bmp,bitmap;
    private long _graph;
    String tfmodel ="/data/local/tmp/detect.tflite";
    String label = "/data/local/tmp/coco_labels_list.txt";
    String format ="tflite";
    private void initDraw(){
        mDrawView = findViewById(R.id.drawSurfaceView);
        mDrawView.setZOrderOnTop(true);
        mDrawView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
// 实现透明化view并把view设置在相机surfaceview上方
    }
    private void initCameraView() {
        mSurfaceView = findViewById(R.id.cameraSurfaceView); //初始化surface view

    }
    private void initCameraEvent() {

        mSurfaceView.getHolder().setKeepScreenOn(true);// 屏幕常亮
        mSurfaceView.getHolder().addCallback(new SurfaceCallback());// 为surfaceHolder添加回调
        mSurfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);
    }
    private final class SurfaceCallback implements SurfaceHolder.Callback { // 回调中包含三个重写方法，看方法名即可知道是干什么的

        @Override
        public void surfaceCreated(SurfaceHolder holder) { // 创建预览画面处理
            int nNum = Camera.getNumberOfCameras(); // 根据摄像头的id找前后摄像头
            if (nNum == 0) {
                // 没有摄像头
                return;
            }

            for (int i = 0; i < nNum; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo(); // camera information 对象
                Camera.getCameraInfo(i, info);// 获取information
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { // 后摄像头
                    startPreview(info, i, holder); // 设置preview的显示属性
                    return;
                }
            }

        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) { // 预览画面有变化时进行如下处理
            if (mCamera == null) {
                return;
            }

            mCamera.autoFocus(new Camera.AutoFocusCallback() { // 增加自动对焦
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    if (success) { // 如果自动对焦成功
                        mCamera.cancelAutoFocus(); // 关闭自动对焦，下次有变化时再重新打开自动对焦,这句不能少
                        Log.d("surfaceChange","autofocus success");
                    }
                }
            });
            getPreViewImage();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) { // surfaceView关闭处理以下方法
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release(); // 释放照相机 不能少
                mCamera = null;
                mCameraInfo = null;
            }
        }

    }

    private Camera.Size getOptimalSize(int nDisplayWidth, int nDisplayHeight,
                                       List<Camera.Size> sizes, double targetRatio) { // 这里是我的一个计算显示尺寸的方法，可以自己去设计
        final double ASPECT_TOLERANCE = 0.001;
        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = Math.min(nDisplayWidth, nDisplayHeight);
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
    public void startPreview(Camera.CameraInfo info, int cameraId, SurfaceHolder holder) { // 在回调中调用设置预览的属性
        try {

            mCameraInfo = info;

            mCamera = Camera.open(cameraId);

            mCamera.setPreviewDisplay(holder); // 设置用于显示拍照影像的SurfaceHolder对象
            mCamera.setDisplayOrientation(90); // 设置显示的方向，这里手机是竖直为正向90度，可以自己写个方法来根据屏幕旋转情况获取到相应的角度

            {
                mParameters = mCamera.getParameters();

                // PictureSize 获取支持显示的尺寸 因为支持的显示尺寸是和设备有关，所以需要获取设备支持的尺寸列表
                // 另外因为是预览画面是全屏显示，所以显示效果也和屏幕的分辨率也有关系，为了最好的适应屏幕，建议选取
                // 与屏幕最接近的宽高比的尺寸
                List<Camera.Size> listPictureSizes = mParameters
                        .getSupportedPictureSizes();

                Camera.Size sizeOptimalPicture = getOptimalSize(mScreenWidth,
                        mScreenHeight, listPictureSizes, (double) mScreenWidth
                                / mScreenHeight);
                mParameters.setPictureSize(sizeOptimalPicture.width,
                        sizeOptimalPicture.height);

                // PreviewSize
                List<Camera.Size> ListPreviewSizes = mParameters
                        .getSupportedPreviewSizes();

                Camera.Size sizeOptimalPreview = getOptimalSize(
                        sizeOptimalPicture.width, sizeOptimalPicture.height,
                        ListPreviewSizes, (double) sizeOptimalPicture.width
                                / sizeOptimalPicture.height);
                mParameters.setPreviewSize(sizeOptimalPreview.width,
                        sizeOptimalPreview.height);

                // 这里就是有的设备不支持Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE连续自动对焦这个字段，所以做个判断
                List<String> lstFocusModels = mParameters
                        .getSupportedFocusModes();
                for (String str : lstFocusModels) {
                    if (str.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        mParameters
                                .setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                        break;
                    }
                }
                mCamera.setParameters(mParameters);
            }
            mCamera.startPreview(); // 开始预览

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public Bitmap rotatingImageView(int angle , Bitmap bitmap) {         //旋转图片 动作
        Matrix matrix = new Matrix();
        matrix.setRotate(angle,bitmap.getWidth() / 2, bitmap.getHeight() / 2);       // 创建新的图片
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);     }
    private void getPreViewImage() { // 获取相机预览图
        Log.e(TAG, "prepare to get preview image " );
        mCamera.setPreviewCallback(new Camera.PreviewCallback(){
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Camera.Size size = camera.getParameters().getPreviewSize();
                image = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
                Log.e(TAG, "Yuv image obtained " );
                try {
                    stream = new ByteArrayOutputStream();
                    image.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, stream);
                    bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
                    bitmap = rotatingImageView(90,bmp);
                    Log.e(TAG, "rotate finished" );
                    inputDat = getImageData(bitmap,inputDat);
                    Log.e( TAG,com.openailab.jni.tengine.getTengineVersion() );
                    Log.e(TAG, "prepare to set tensor buffer" );
                    long _input_tensor = com.openailab.jni.tengine.getGraphInputTensor(_graph,0,0);
                    com.openailab.jni.tengine.setTensorBufferInt8(_input_tensor,inputDat,300*300*3);
//                    com.openailab.jni.tengine.setTensorBuffer(_input_tensor,inputDat,300*300*3);
                    long startTime = SystemClock.uptimeMillis(); // 开始推理计时
                    Trace.beginSection("runInference");
                    if( com.openailab.jni.tengine.runGraph(_graph,1) < 0 )
                    {
                        Log.e(TAG, "run graph failed" );
                    }
                    processtime = SystemClock.uptimeMillis()-startTime; // 结束推理计时
                    Trace.endSection();
                    Log.d("onPreviewFrame", "Classified successful.");
                    showinference(processtime+"ms");
                    long box_tensor = com.openailab.jni.tengine.getGraphOutputTensor(_graph,0,0);
                    long boxes= com.openailab.jni.tengine.getTensorBuffer(box_tensor);
//                    int dataSize = com.openailab.jni.tengine.getTensorBufferSize(_output_tensor) / 4;
                    long class_tensor = com.openailab.jni.tengine.getGraphOutputTensor(_graph,0,1);
                    long classes= com.openailab.jni.tengine.getTensorBuffer(class_tensor);
                    long score_tensor = com.openailab.jni.tengine.getGraphOutputTensor(_graph,0,2);
                    long scores= com.openailab.jni.tengine.getTensorBuffer(score_tensor);
                    long number_tensor = com.openailab.jni.tengine.getGraphOutputTensor(_graph,0,3);
                    long num= com.openailab.jni.tengine.getTensorBuffer(number_tensor);
                    SurfaceHolder surfaceHolder = mDrawView.getHolder();
                    Bitmap rgba = bmp.copy(Bitmap.Config.ARGB_8888, true);
                    Log.e("converted bitmap size: ", ""+bmp.getWidth()+"*"+bmp.getHeight() );
                    // 画布配置
                    Canvas canvas = new Canvas(rgba);
                    //图像上画矩形
                    Paint paint = new Paint();
                    canvas=surfaceHolder.lockCanvas();
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    int numobject = numOfObject(label,num);
                    Log.e("num of object detected", ""+numobject);
                    for (int i = 0;i<numobject;i++)  {  // for int8.tflite model

                        if (Getprob(scores,i)<0.1){ //过滤一些准确率过低的结果
                            continue;
                        }
                        else  {

                            paint.setColor(Color.RED);
                            paint.setStyle(Paint.Style.STROKE);//不填充
                            paint.setStrokeWidth(15); //线的宽度
                            canvas.drawRect(GetLeft(boxes,i) * canvas.getWidth(), Math.abs(GetTop(boxes,i)) * canvas.getHeight(),
                                    GetRight(boxes,i) * canvas.getWidth(), GetButtom(boxes,i) * canvas.getHeight(), paint);

                            paint.setColor(Color.YELLOW);
                            paint.setStyle(Paint.Style.FILL);//不填充
                            paint.setStrokeWidth(1); //线的宽度
                            paint.setTextSize(33); // 字体大小
                            paint.setFakeBoldText(true); //字体宽度
                            canvas.drawText(PrintTopLabels(label,classes,i) + "\n" + Getprob(scores,i),
                                    GetLeft(boxes,i) * canvas.getWidth(), Math.abs(GetTop(boxes,i)) * (canvas.getHeight()+66), paint);
                            Log.e("initdraw", "width: "+canvas.getWidth()+", "+"height: "+canvas.getHeight()+", "+
                                    "top: "+GetTop(boxes,i) +", "+"prob: "+ Getprob(scores,i)+"width: "+bmp.getWidth()+", "+"height: "+bmp.getHeight());
                        }

                    }
                    if(canvas!=null){
                        surfaceHolder.unlockCanvasAndPost(canvas);
 //                       Log.d("onPreviewFrame", "Classified successful.");
                    }

//                com.openailab.jni.tengine.releaseGraphTensor(_output_tensor);
//                com.openailab.jni.tengine.releaseGraphTensor(_input_tensor);
//                com.openailab.jni.tengine.postrunGraph(_graph);
//                com.openailab.jni.tengine.destoryGraph(_graph);
//                com.openailab.jni.tengine.releaseTengine();

                    stream.close();
                } catch (Exception ex) {
                    Log.e("Sys","Error:"+ex.getMessage());                }
            }

        });

    }
    public void showinference(String inferenceTime){
        TextView time = findViewById(R.id.time);
        time.setText("Time:              "+inferenceTime);
    } // 计算推理时间


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initDraw();
        initCameraView();
        // Example of a call to a native method
        com.openailab.jni.tengine.initTengine();
//                String mxJson = "/data/local/tmp/squeezenet1_1-symbol.json";
//                String mxParam="/data/local/tmp/squeezenet1_1-0000.params";
//        long _graph = com.openailab.jni.tengine.createGraph(0,"mxnet",mxJson,mxParam);
        _graph = com.openailab.jni.tengine.createGraph(0,format,tfmodel);
        if( _graph == 0 )
        {
            Log.e(TAG, "create graph failed" );
        }
        Log.e(TAG, "create graph finished" );
        int img_w = 300;
        int img_h = 300;
        int img_c = 3;
        long _input_tensor = com.openailab.jni.tengine.getGraphInputTensor(_graph,0,0);
        if( _input_tensor == 0 )
        {
            Log.e(TAG, "input tensor failed" );
        }

        int[] dims = {1,img_w, img_h,img_c};
        com.openailab.jni.tengine.setTensorShape(_input_tensor,dims,4);

        if( com.openailab.jni.tengine.prerunGraph(_graph) != 0 )
        {
            Log.e(TAG, "prerun failed" );
        }
        Log.e(TAG, "prerun finished " );
        Log.e(TAG, "init tengine finished" );
        initCameraEvent();
    }

    byte[] getImageData(Bitmap bitmap,byte[] inputDat)
    {
            dat = inputDat;
        if( getInputDataFromImage(bitmap,dat) != 0 )
        {
            Log.e(TAG,"can not load file ");
        }
        return dat;
    }


    //hold app at background
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
    public native int getInputDataFromImage(Bitmap bitmap, byte[] outDat);
    native String PrintTopLabels(String lablefile,long classes,int num);
    native int numOfObject(String labelfile,long number);
    native float Getprob(long scores, int num);
    native float GetLeft(long left, int num);
    native float GetTop(long top, int num);
    native float GetRight(long right, int num);
    native float GetButtom(long buttom, int num);
}
