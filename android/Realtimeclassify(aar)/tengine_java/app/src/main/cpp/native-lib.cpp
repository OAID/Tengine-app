#include <jni.h>
#include <string>
#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include <iostream>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fstream>
#include <opencv2/core/mat.hpp>
#include "opencv2/imgproc/imgproc.hpp"
#include "opencv2/highgui/highgui.hpp"
#define  LOG_TAG    "JNI_PART"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG, __VA_ARGS__)
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG, __VA_ARGS__)
#define LOGF(...)  __android_log_print(ANDROID_LOG_FATAL,LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_openailab_jni_tengine_1java_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_openailab_jni_tengine_1java_MainActivity_PrintTopLabels(
        JNIEnv* env,
        jobject,
        jstring labelfile, jlong outputDat){

    std::string label_file = (env->GetStringUTFChars(labelfile, NULL));
    std::vector<std::string> result;
    std::ifstream labels(label_file);
    std::string line;
    while (std::getline(labels, line))
        result.push_back(line);

    int true_id = 0;
    float true_score=0.f;

    float* data = (float*)outputDat;
    for(int i=0; i<1000; i++)
    {
        if(data[i]>true_score)
        {
            true_id = i;
            true_score = data[i];
        }
    }
    return env->NewStringUTF(result[true_id - 1].c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_openailab_jni_tengine_1java_MainActivity_getInputDataFromImage(
        JNIEnv* env,
        jobject  /* this */,
        jobject bitmap ,jfloatArray Dat){

    JNIEnv J = *env;
    if (bitmap == NULL) {
        LOGE("bitmap is null\n");
        return -1;
    }
    // Get bitmap info
    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(env, bitmap, &info);
    if (info.width <= 0 || info.height <= 0 ||(info.format != ANDROID_BITMAP_FORMAT_RGB_565 && info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)) {
        LOGE("invalid bitmap\n");
        return -1;
    }
    void * pixels = NULL;
    int res = AndroidBitmap_lockPixels(env, bitmap, &pixels);
    if (pixels == NULL) {
        LOGE("fail to lock bitmap: %d\n", res);
        return -1;
    }
    cv::Mat sample = cv::Mat(info.height,info.width,CV_8UC4,pixels);
    cv::Mat img;
    if (sample.channels() == 4)
    {
        cv::cvtColor(sample, img, cv::COLOR_BGRA2BGR);
    }
    else if (sample.channels() == 1)
    {
        cv::cvtColor(sample, img, cv::COLOR_GRAY2BGR);
    }
    else
    {
        img=sample;
    }
    int img_h =224;
    int img_w =224;
    cv::resize(img, img, cv::Size(img_h, img_w));
    img.convertTo(img, CV_32FC3);
    float *img_data = (float *)img.data;
    int hw = img_h * img_w;
    float mean[3]={104.f,117.f,123.f};
    jfloat* input = env->GetFloatArrayElements(Dat,NULL);
    for (int h = 0; h < img_h; h++)
    {
        for (int w = 0; w < img_w; w++)
        {
            for (int c = 0; c < 3; c++)
            {
                input[c * hw + h * img_w + w] = (*img_data - mean[c])*0.017;
                img_data++;
            }
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    env->ReleaseFloatArrayElements(Dat ,input,0);
    return res;
}