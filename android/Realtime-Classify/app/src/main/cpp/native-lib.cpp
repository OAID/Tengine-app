#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include "Tengine_Wrapper.h"

#define  LOG_TAG    "JNI_PART"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG, __VA_ARGS__)
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG, __VA_ARGS__)
#define LOGF(...)  __android_log_print(ANDROID_LOG_FATAL,LOG_TAG, __VA_ARGS__)

extern "C" {

TengineWrapper *tengine_wrapper;

JNIEXPORT jstring JNICALL
Java_com_tengine_openailab_application_classification_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

JNIEXPORT jint JNICALL
Java_com_tengine_openailab_application_classification_MainActivity_TengineWrapperInit(
        JNIEnv *env,
        jobject /* this */) {
    tengine_wrapper = new TengineWrapper();
    int ret = tengine_wrapper->InitTengine();
    if( ret!= 0) {
        return ret;
    }
    return 0;
}
JNIEXPORT jint JNICALL
Java_com_tengine_openailab_application_classification_MainActivity_TengineWrapperInitint8(
        JNIEnv *env,
        jobject /* this */) {
    tengine_wrapper = new TengineWrapper();
    int ret = tengine_wrapper->InitTengineint8();
    if( ret!= 0) {
        return ret;
    }
    return 0;
}
JNIEXPORT jint JNICALL
Java_com_tengine_openailab_application_classification_MainActivity_TengineWrapperRelease(
        JNIEnv *env,
        jobject /* this */) {
    tengine_wrapper->ReleaseTengine();
    delete tengine_wrapper;
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_tengine_openailab_application_classification_MainActivity_RunMobilenetFromFile(
        JNIEnv *env,
        jobject /* this */, jstring file) {
    const char* image_file = env->GetStringUTFChars(file,0);
    env->ReleaseStringUTFChars(file, image_file);
    if( tengine_wrapper->RunTengine(image_file) > 0 )
        return 1;
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_tengine_openailab_application_classification_MainActivity_RunMobilenet(
        JNIEnv *env,
        jobject /* this */,  jobject bitmap) {
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

    if( tengine_wrapper->RunTengine(sample) > 0 ){
        AndroidBitmap_unlockPixels(env, bitmap);
        return -1;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_tengine_openailab_application_classification_MainActivity_TengineWrapperGetTop1(
        JNIEnv *env,
        jobject /* this */) {

    std::string top1 = tengine_wrapper->GetTop1();
    return env->NewStringUTF(top1.c_str());
}

JNIEXPORT jint JNICALL
Java_com_tengine_openailab_application_classification_MainActivity_deviceChange(
        JNIEnv *env,
        jobject /* this */, jstring dev_name) {
    const char *dev = env->GetStringUTFChars(dev_name, 0);
//    if(tengine_wrapper->changeDevice(dev)){
        return 0;
//    } else{
//        return -1;
//    }
}



}

