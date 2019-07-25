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
#include <opencv2/tracking.hpp>
#include "opencv2/opencv.hpp"
#include "opencv2/tracking/tracker.hpp"
#include "vector"
using namespace cv;
using namespace std;
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
        jstring labelfile, jlong out_classes, jint num){
    float* classes = (float*)out_classes;
    std::string label_file = (env->GetStringUTFChars(labelfile, NULL));
    std::vector<std::string> result;
    std::ifstream labels(label_file);
    std::string line;
    while (std::getline(labels, line))
        result.push_back(line);

    int class_idx = classes[num];
    return env->NewStringUTF(result[class_idx].c_str());
}
extern "C" JNIEXPORT jint JNICALL
Java_com_openailab_jni_tengine_1java_MainActivity_numOfObject(
        JNIEnv* env,
        jobject,
        jstring labelfile, jlong number){
    float* num = (float*)number;
    std::string label_file = (env->GetStringUTFChars(labelfile, NULL));
    std::vector<std::string> result;
    std::ifstream labels(label_file);
    std::string line;
    while (std::getline(labels, line))
        result.push_back(line);
    int max_num = num[0];
    return max_num;
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_openailab_jni_tengine_1java_MainActivity_Getprob(
        JNIEnv* env,
        jobject,
        jlong scores, jint num){
    float* score = (float*)scores;

    return score[num];
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_openailab_jni_tengine_1java_MainActivity_GetLeft(
        JNIEnv* env,
        jobject,
        jlong left_box, jint num){
    float* boxes= (float*)left_box;
    return boxes[num*4];
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_openailab_jni_tengine_1java_MainActivity_GetTop(
        JNIEnv* env,
        jobject,
        jlong top_box, jint num){
    float* boxes = (float*)top_box;

    return boxes[num*4+1];
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_openailab_jni_tengine_1java_MainActivity_GetRight(
        JNIEnv* env,
        jobject,
        jlong right_box, jint num){
    float* boxes = (float*)right_box;

    return boxes[num*4+2];
}
extern "C" JNIEXPORT jfloat JNICALL
Java_com_openailab_jni_tengine_1java_MainActivity_GetButtom(
        JNIEnv* env,
        jobject,
        jlong but_box, jint num){
    float* boxes = (float*)but_box;

    return boxes[num*4+3];
}
extern "C" JNIEXPORT jint JNICALL
Java_com_openailab_jni_tengine_1java_MainActivity_getInputDataFromImage(
        JNIEnv* env,
        jobject  /* this */,
        jobject bitmap ,jbyteArray Dat){

    JNIEnv J = *env;

    if (bitmap == NULL) {
        LOGE("bitmap is null\n");
        return -1;
    }
    // Get bitmap info
    LOGE("Bitmap obtained");
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
    int img_h =300;
    int img_w =300;
    cv::cvtColor(img,img,cv::COLOR_RGBA2BGR);
    cv::resize(img, img, cv::Size(img_h, img_w));
    img.convertTo(img, CV_32FC3);
    LOGE("convert finished");
    float *img_data = (float *)img.data;
    jbyte* input = env->GetByteArrayElements(Dat,NULL);
    //float mean[3]={127.5,127.5,127.5};
    for (int h = 0; h < img_h; h++)
    {
        for (int w = 0; w < img_w; w++)
        {
            for (int c = 0; c < 3; c++)
            {
                *input = img_data[2 - c];
                input++;
            }
            img_data+=3;
        }
    }
//    LOGE("prepare to unlock pixels");
    AndroidBitmap_unlockPixels(env, bitmap);
//    LOGE("unlock success");
//    env->ReleaseByteArrayElements(Dat,input,0);
//    LOGE("release byte array success");
    return res;
}

