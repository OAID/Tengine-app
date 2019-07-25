package com.tengine.openailab.application.detection;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.media.Image;
import android.net.Uri;
import android.os.SystemClock;
import android.os.Trace;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import android.view.KeyEvent;
import java.io.IOException;
import java.io.File;
import android.support.v4.content.FileProvider;
import java.util.Date;
import java.text.SimpleDateFormat;
import android.os.Environment;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
    TextView tv = null;
    private long                  processtime;
    int mutiplier;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.e("app", "onCreate: start" );
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            finish();
            return;
        }


        tv = findViewById(R.id.sample_text);
        tv.setText("Wait for choosing a Picture");
        Log.e("app", "tv found" );
        //try init cash reopen finally click


        if (TengineWrapperInit()>0) {
            tv.setText("Exit,try again" + TengineWrapperInit());
            Log.e("init", "init failed");
        }
        Log.e("app", "init finished" );
        Button choose = findViewById(R.id.choose);
        choose.setOnClickListener(new View.OnClickListener() {

            //choose Image
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, 1);

            }
//            private void selectPic(){
//                Intent intent=new Intent();
//                intent.setType("image/*");
//                intent.setAction(Intent.ACTION_GET_CONTENT);
//                startActivityForResult(intent, 1);
//            }


        });

    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode==RESULT_OK) {
            Uri uri = data.getData();
            Log.e("uri", uri.toString());

            ContentResolver cr = getContentResolver();
            try {

//                //Uri originalUri=data.getData();
                Bitmap bitmap = BitmapFactory.decodeStream(cr.openInputStream(uri)); // 获取图像bitmap
                Bitmap rgba = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                // 画布配置
                Canvas canvas = new Canvas(rgba);
                //图像上画矩形
                Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Paint.Style.STROKE);//不填充
                paint.setStrokeWidth(5); //线的宽度
                long startTime = SystemClock.uptimeMillis(); // 开始推理计时
                Trace.beginSection("runInference");
                Log.d("bitmap", "created bitmap");
                if(RunMobilenet(bitmap)>0){ // 模型导入
                            tv.setText("run App error !!!!!!!"+RunMobilenet(bitmap));
                    Log.e("onPreviewFrame", "Failed to Classify.");}

                else {
                    Log.d("painting", "painting");
//                    for (int i=1;i<=Num();i++){ // for MSSD.caffe model
                    for (int i = 0;i<Num();i++)  {  // for int8.tflite model
                        if (Math.round(rgba.getWidth()/500)==0){ // 解决label字体大小问题，避免在大图检测完label字体过小或小图时字体过大
                            mutiplier =1;
                        }
                        else
                            mutiplier=Math.round(rgba.getWidth()/500);
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);//不填充
                        paint.setStrokeWidth(5*mutiplier); //线的宽度
                        canvas.drawRect(TengineWrapperGetLeft(i) * rgba.getWidth(), TengineWrapperGetTop(i) * rgba.getHeight(),
                            TengineWrapperGetRight(i) * rgba.getWidth(), TengineWrapperGetbuttom(i) * rgba.getHeight(), paint);
                        paint.setColor(Color.YELLOW);

                        Log.e("bitmap size", " "+rgba.getWidth()+"* "+rgba.getHeight() );
                        Log.e("box coordinates", ""+TengineWrapperGetLeft(i)+", "+TengineWrapperGetTop(i)+", "+ TengineWrapperGetRight(i) +", "+TengineWrapperGetbuttom(i)  );
                        paint.setStyle(Paint.Style.FILL);//不填充

                        paint.setStrokeWidth(mutiplier); //线的宽度

                        paint.setTextSize(12*mutiplier);
                        paint.setFakeBoldText(true);
                        canvas.drawText(TengineWrapperGetTop1(i) + "\n" + TengineWrapperGetprob(i),
                            TengineWrapperGetLeft(i) * rgba.getWidth(), (TengineWrapperGetTop(i) * rgba.getHeight()), paint);


//                    tv.setText(""+Num());
                    ImageView imageView =  findViewById(R.id.image);
                    imageView.setImageBitmap(rgba);
                    }
                    processtime = SystemClock.uptimeMillis()-startTime; // 结束推理计时
                    Trace.endSection();
                                Log.d("Leyang", "imagesize: "+rgba.getWidth()+"*"+rgba.getHeight());
//                    tv.setText(""+TengineWrappertest());
                    showinference(processtime+"ms");
                }
            } catch (FileNotFoundException e) {
                Log.e("Exception", e.getMessage(), e);
            }
        }else {
            Log.i("MainActivity", "error");
        }
        MainActivity.super.onActivityResult(requestCode,requestCode,data);


    }
    public void showinference(String inferenceTime){
        TextView time = findViewById(R.id.text1);
        time.setText("Time: ");
        tv.setText(inferenceTime);
    } // 计算推理时间
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

//    public native int RunMobilenetFromFile(String file);

    public native int RunMobilenet(Bitmap bitmap);

    public native int TengineWrapperRelease();

    public native String TengineWrapperGetTop1(int num);

    public native float TengineWrapperGetTop(int num);

    public native float TengineWrapperGetprob(int num);

    public native float TengineWrapperGetRight(int num);

    public native float TengineWrapperGetLeft(int num);

    public native float TengineWrapperGetbuttom(int num);


//    public native float TengineWrappertest();

    public native int Num();

}
