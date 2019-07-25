package com.tengine.openailab.application.classification;

import android.bluetooth.BluetoothClass;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PixelFormat;
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
    TextView tv = null;
    TextView tc = null;
    private SurfaceView mSurfaceView;
    private int                    mScreenWidth;
    private int                    mScreenHeight;
    private long                  processtime;
    private Camera.Parameters    mParameters;
    private Camera                mCamera;
    private Camera.CameraInfo mCameraInfo;
    private ImageView plusImageView, minusImageView;
    private Spinner modelSpinner;
    private TextView threadsTextView;
    private int numThreads = -1;
    private CheckBox cpu0;
    private CheckBox cpu1;
    private CheckBox cpu2;
    private CheckBox cpu3;
    private String cpu_list= "0,1,2,3";
    private String []cpuArr=cpu_list.split(","); // cpu字符串和数组间互相转化，方便之后删除/增加
    List<String> list = new ArrayList<>();
    List<String> list2 = new ArrayList<>();
    Map<String, Integer> map = new HashMap<>();
    private String result;
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
                    YuvImage image = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
                    if(image!=null){
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        image.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, stream);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
                        Bitmap bmp = rotaingImageView(90,bitmap);
//                        Log.d("onPreviewFrame", "bmp obtained.");
                        tv.setText("Starting up...");
                        if(RunMobilenet(bmp)>0){ // 模型导入
                            tv.setText("run App error !!!!!!!"+RunMobilenet(bmp));
                            Log.d("onPreviewFrame", "Failed to Classify.");}
                        else {
                            long startTime = SystemClock.uptimeMillis(); // 开始推理计时
                            Trace.beginSection("runInference");
                            if (list.size()<5){
                                result = TengineWrapperGetTop1();
                                tv.setText(result); // 调用Tengine 推理，并输出推理结果
                                processtime = SystemClock.uptimeMillis()-startTime; // 结束推理计时
                                Trace.endSection();
//                                Log.d("onPreviewFrame", "Classified successful.");
                                showinference(processtime+"ms");
                                list.add(result); // 程序开始的几个result加到list
                            }
                            else {
                                for(int i=0;i<6;i++){
                                    list.set(i,TengineWrapperGetTop1()); // 循环更新替换list里的字符串
                                    getMaxResult(list); // 得出list中重复最多的字符串
                                    processtime = SystemClock.uptimeMillis()-startTime; // 结束推理计时
                                    Trace.endSection();
//                                    Log.d("onPreviewFrame", "Classified successful.");
                                    showinference(processtime+"ms");
                                }
                                // 以上方法把结果取时域平均，以达到精确结果和减少错误判断的目的
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
    public void showinference(String inferenceTime){
        tc.setText(inferenceTime);
    } // 计算推理时间
    public void getMaxResult(List<String> list){ // 得出list中重复最多的字符串
        int count = 1;

        //重复的加入list2集合
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(i).equals(list.get(j))) {
                    list2.add(list.get(i));
                    break;
                }
            }
        }
//        Log.e("compare", "getMaxResult: "+list2 );
        for (String obj : list2) {
            if (map.containsKey(obj)) {
                count++;
                map.put(obj, map.get(obj) + 1);
            } else {
                map.put(obj, 1);
            }
        }

        list2.clear(); // 每次用完list2,清空内容
//        Log.e("map", "getMaxResult: "+map );
        Iterator<Map.Entry<String, Integer>> it = map.entrySet().iterator(); // 迭代器查找count数最多的字符串
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            if (entry.getValue() == count) {
                list2.add(entry.getKey()); // 把结果再导入空list2，循环使用节省空间
            }
        }
        map.clear(); // 每次用完map,清空内容
//        Log.e("getMaxResult", "getMaxResult:max obtained "+list+"\n"+list2);
        tv.setText(list2.get(0));
        list2.clear();} // 每次用完list2,清空内容
//    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("tengine-app", "onCreate: started");
        setContentView(R.layout.activity_main);
        initCameraView();
        initCameraEvent();
        if (TengineWrapperInit()>0)
            tv.setText("Exit,try again"+TengineWrapperInit());


        tv = findViewById(R.id.sample_text);
        tc = findViewById(R.id.infer_time);
        tv.setText("Wait for a Picture");
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

    public native int TengineWrapperInit();

    public native int TengineWrapperInitint8();

    public native int RunMobilenetFromFile(String file);

    public native int TengineWrapperRelease();

    public native int RunMobilenet(Bitmap bitmap);

    public native String TengineWrapperGetTop1();

    public native int SetThread(String cpu_list);

    public native int deviceChange(String device);

    public native int modeChange(int mode);
}
