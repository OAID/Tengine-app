package com.tengine.openailab.application.realtimedetection;

import android.bluetooth.BluetoothClass;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.Image;
import android.net.Uri;
import android.os.SystemClock;
import android.os.Trace;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.TypedArrayUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Button;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import android.view.KeyEvent;
import java.io.IOException;
import java.io.File;
import android.support.v4.content.FileProvider;

import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import android.os.Environment;

import org.apache.commons.lang3.ArrayUtils;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
    private SurfaceView mSurfaceView;
    private int                    mScreenWidth;
    private int                    mScreenHeight;
    private long                  processtime;
    private Camera.Parameters    mParameters;
    private Camera                mCamera;
    private Camera.CameraInfo mCameraInfo;
    private SurfaceView mDrawView;

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
            holder=mSurfaceView.getHolder();
            holder.addCallback(this);

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
                Log.d("camera", "surfaceview size: "+mSurfaceView.getWidth()+", "+mSurfaceView.getHeight() );
                mCamera.setParameters(mParameters);
            }

            mCamera.startPreview(); // 开始预览

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public Bitmap rotaingImageView(int angle , Bitmap bitmap) {         //旋转图片 动作
         Matrix matrix = new Matrix();
         matrix.setRotate(angle,bitmap.getWidth() / 2, bitmap.getHeight() / 2);       // 创建新的图片
         return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);     }

    private void getPreViewImage() { // 获取相机预览图

        mCamera.setPreviewCallback(new Camera.PreviewCallback(){

            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Camera.Size size = camera.getParameters().getPreviewSize();
                try{

                    YuvImage image = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);  // 图片转换加密

                    if(image!=null){

                        ByteArrayOutputStream stream = new ByteArrayOutputStream();

                        image.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, stream);

                        Bitmap bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());

                        Bitmap bmp = rotaingImageView(90,bitmap); // 图片旋转90度后导入tengine
//                        Log.d("onPreviewFrame", "bmp obtained.");

                        long startTime = SystemClock.uptimeMillis(); // 开始推理计时
                        Trace.beginSection("runInference");
                        if(RunMobilenet(bmp)>0){ // 模型导入
                            Log.e("onPreviewFrame", "run mobilenet error.");
                        }
                        else {
                            processtime = SystemClock.uptimeMillis()-startTime; // 结束推理计时
                            Trace.endSection();
                            showinference(processtime+"ms");
                            SurfaceHolder surfaceHolder = mDrawView.getHolder();
                            Bitmap rgba = bmp.copy(Bitmap.Config.ARGB_8888, true);
                            Log.e("converted bitmap size: ", ""+bmp.getWidth()+"*"+bmp.getHeight() );
                            // 画布配置
                            Canvas canvas = new Canvas(rgba);
                            //图像上画矩形
                            Paint paint = new Paint();

                            canvas=surfaceHolder.lockCanvas();
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); //实现透明化view和清理上个循环的框
                            Log.e("num of object detected", ""+Num() );
                            //  for (int i=1;i<=Num();i++){ // for MSSD.caffe model
                            for (int i = 0;i<Num();i++)  {  // for int8.tflite model

                                if (TengineWrapperGetprob(i)<0.3){ //过滤一些准确率过低的结果
                                    continue;
                                }
                                else  {
                                    paint.setColor(Color.RED);
                                    paint.setStyle(Paint.Style.STROKE);//不填充
                                    paint.setStrokeWidth(15); //线的宽度
                                    canvas.drawRect(TengineWrapperGetLeft(i) * canvas.getWidth(), Math.abs(TengineWrapperGetTop(i)) * canvas.getHeight(),
                                            TengineWrapperGetRight(i) * canvas.getWidth(), TengineWrapperGetbuttom(i) * canvas.getHeight(), paint);
//                                    Log.e("drawRect", "width: "+canvas.getWidth()+", "+"height: "+canvas.getHeight()+", "+"left: "+TengineWrapperGetLeft(i) * canvas.getWidth()+
//                                            ", top: "+Math.abs(TengineWrapperGetTop(i)) * canvas.getHeight() +", "+"right: "+TengineWrapperGetRight(i) * canvas.getWidth()+", buttom: "+TengineWrapperGetbuttom(i) * canvas.getHeight());
                                    paint.setColor(Color.YELLOW);
                                    paint.setStyle(Paint.Style.FILL);//不填充
                                    paint.setStrokeWidth(1); //线的宽度
                                    paint.setTextSize(33); // 字体大小
                                    paint.setFakeBoldText(true); //字体宽度
                                    canvas.drawText(TengineWrapperGetTop1(i) + "\n" + TengineWrapperGetprob(i),
                                            TengineWrapperGetLeft(i) * canvas.getWidth(), Math.abs(TengineWrapperGetTop(i)) * (canvas.getHeight()+66), paint);
//                                    Log.e("drawText", "width: "+canvas.getWidth()+", "+"height: "+canvas.getHeight()+", "+
//                                    "top: "+TengineWrapperGetTop(i) +", "+"prob: "+ TengineWrapperGetprob(i)+"width: "+bmp.getWidth()+", "+"height: "+bmp.getHeight());
                                }
                            }
                            if(canvas!=null){
                                surfaceHolder.unlockCanvasAndPost(canvas);
//                                Log.d("onPreviewFrame", "classify and draw successful.");
                            }
                        }
                        stream.close();
                    }
                }catch(Exception ex){
                    Log.e("Sys","Error:"+ex.getMessage());
                }
            }

        });

    }

    public void showinference(String inferenceTime){
        TextView time = findViewById(R.id.time);
//        time.setText("Time: ");
        time.setText("Time:              "+inferenceTime);
    } // 计算推理时间

    // Public Methods //////////////////////////////////////////////////////////
    public void show() {
//        .setVisibility(View.VISIBLE);
        mSurfaceView.setVisibility(View.VISIBLE); //
    }

    public void hideCamera() {
//        mView.setVisibility(View.GONE);
        mSurfaceView.setVisibility(View.GONE); //
    }


    public void initScreenSize(int nWidth, int nHeight) { // 设置屏幕的宽与高
        ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
        lp.width = nWidth;
        lp.height = nHeight;
        mSurfaceView.setLayoutParams(lp);

        mScreenWidth = nWidth;
        mScreenHeight = nHeight;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("tengine-app", "onCreate: started");
        setContentView(R.layout.activity_main);
        initDraw();
        initCameraView();
        initCameraEvent();

        if (TengineWrapperInit()>0){
            Log.e("init", "tengine init failed " );
        }
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
        public native int TengineWrapperInit();

//    public native int RunMobilenetFromFile(String file);

        public native int RunMobilenet(Bitmap bitmap);

        public native String TengineWrapperGetTop1(int num);

        public native float TengineWrapperGetTop(int num);

        public native float TengineWrapperGetprob(int num);

        public native float TengineWrapperGetRight(int num);

        public native float TengineWrapperGetLeft(int num);

        public native float TengineWrapperGetbuttom(int num);

        public native int Num();
}
