package com.tengine.openailab.application;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.media.Image;
import android.net.Uri;
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            finish();
            return;
        }


        tv = (TextView) findViewById(R.id.sample_text);
        tv.setText("Wait for choosing a Picture");
        ImageView imageView = (ImageView) findViewById(R.id.image);
        //try init cash reopen finally click


        if (TengineWrapperInit()>0)
            tv.setText("Exit,try again");



        Button choose = (Button) findViewById(R.id.choose);
        choose.setOnClickListener(new View.OnClickListener() {

            //choose Image
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, 1);

            }
            private void selectPic(){
                Intent intent=new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, 1);
            }


        });

    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode==RESULT_OK) {
            Uri uri = data.getData();
            Log.e("uri", uri.toString());

            ContentResolver cr = getContentResolver();
            try {

                //Uri originalUri=data.getData();
                Bitmap bitmap = BitmapFactory.decodeStream(cr.openInputStream(uri));


                    tv.setText("run App...");
                    if(RunMobilenet(bitmap)>0)
                        tv.setText("run App error !!!!!!!");
                    else {
                        tv.setText(TengineWrapperGetTop1());

                    }


                ImageView imageView = (ImageView) findViewById(R.id.image);
                imageView.setImageBitmap(bitmap);

            } catch (FileNotFoundException e) {
                Log.e("Exception", e.getMessage(), e);
            }
        }else {
            Log.i("MainActivity", "error");
        }
        MainActivity.super.onActivityResult(requestCode,requestCode,data);


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

    public native int RunMobilenetFromFile(String file);

    public native int RunMobilenet(Bitmap bitmap);

    public native String TengineWrapperGetTop1();
}
