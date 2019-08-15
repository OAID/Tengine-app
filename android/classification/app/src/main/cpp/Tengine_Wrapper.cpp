//
// Created by user on 10/26/2018.
//
#include <iostream>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fstream>
#include "Tengine_Wrapper.h"
#include "opencv2/imgcodecs.hpp"
#include <android/log.h>
#define  LOG_TAG    "TENGINE_WRAPPER"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG, __VA_ARGS__)
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG, __VA_ARGS__)
#define LOGF(...)  __android_log_print(ANDROID_LOG_FATAL,LOG_TAG, __VA_ARGS__)

void tengine_core_log_print_t(const char* log){
    LOGE("TENGINE CORE LOG:%s\n",log);
}
int TengineWrapper::InitTengine()
{
    set_log_level(LOG_DEBUG);
    set_log_output(tengine_core_log_print_t);
    int ret = init_tengine();
    LOGE("[%s][%d]ret:%d\n",__FUNCTION__,__LINE__,ret);
    if (request_tengine_version("0.1") < 0)
        return -1;
//    const char* mobilenet_tf_model = "/data/local/tmp/mobilenet_quant_tflite.tmfile";
    const char* mobilenet_caffe_proto = "/data/local/tmp/mobilenet_deploy.prototxt";
    const char* mobilenet_caffe_model = "/data/local/tmp/mobilenet.caffemodel";
    const char* format = "caffe";

    g_mobilenet = create_graph(nullptr,format,mobilenet_caffe_proto,mobilenet_caffe_model);
//    g_mobilenet =create_graph(nullptr,format,mobilenet_tf_model);
//    if(dump_graph(g_mobilenet);

    if(g_mobilenet == nullptr)
    {
        LOGE("[%s][%d] Create graph failed.\n",__FUNCTION__,__LINE__);
        return false;
    }

    const int img_h = 224;
    const int img_w = 224;

    int image_size = img_h * img_w * 3;
    int image_buffer_size = sizeof(float) * image_size;
    LOGE("[%s][%d] image_buffer_size:%d\n",__FUNCTION__,__LINE__,image_buffer_size);
    g_mobilenet_input = (float*) malloc(image_buffer_size);
    LOGE("[%s][%d]%p\n",__FUNCTION__,__LINE__,g_mobilenet_input);
    int dims[4] = {1,  3, img_h, img_w};

    tensor_t input_tensor = get_graph_input_tensor(g_mobilenet, 0, 0);
    if(input_tensor == nullptr)
        return 6;

    set_tensor_shape(input_tensor, dims, 4);

    if( prerun_graph(g_mobilenet)!=0 )
    {
        LOGE("[%s][%d]prerun_graph fail\n",__FUNCTION__,__LINE__);
        return 1;
    }
    set_tensor_buffer(input_tensor, g_mobilenet_input, image_buffer_size);

    return 0;
}


int TengineWrapper::ReleaseTengine()
{
    sleep(1);
    tensor_t input_tensor = get_graph_input_tensor(g_mobilenet, 0, 0);
    put_graph_tensor(input_tensor);
    free(g_mobilenet_input);
    postrun_graph(g_mobilenet);
    destroy_graph(g_mobilenet);
    return 0;
}

int TengineWrapper::get_input_data(const char* image, float* data, int img_h, int img_w)
{
    cv::Mat sample = cv::imread(image);
    if (sample.empty())
        return 1;
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
    cv::resize(img, img, cv::Size(img_h, img_w));
    img.convertTo(img, CV_32FC3);
    float *img_data = (float *)img.data;
    int hw = img_h * img_w;
    float mean[3]={104.f,117.f,123.f};
    //float mean[3]={127.5,127.5,127.5};
    for (int h = 0; h < img_h; h++)
    {
        for (int w = 0; w < img_w; w++)
        {
            for (int c = 0; c < 3; c++)
            {
                data[c * hw + h * img_w + w] = (*img_data - mean[c])*0.017;
                img_data++;
            }
        }
    }

/**/
    return 0;
}

int TengineWrapper::get_input_data(cv::Mat sample, float* data, int img_h, int img_w)
{
    LOGE("[%s][%d]\n",__FUNCTION__,__LINE__);
    if (sample.empty())
        return 1;
    LOGE("[%s][%d]\n",__FUNCTION__,__LINE__);
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
    LOGE("[%s][%d]\n",__FUNCTION__,__LINE__);
    cv::resize(img, img, cv::Size(img_h, img_w));
    LOGE("[%s][%d]\n",__FUNCTION__,__LINE__);
    img.convertTo(img, CV_32FC3);
    LOGE("[%s][%d]\n",__FUNCTION__,__LINE__);
    float *img_data = (float *)img.data;
    int hw = img_h * img_w;
    float mean[3]={104.f,117.f,123.f};
    //float mean[3]={127.5,127.5,127.5};
    int i=0;
    for (int h = 0; h < img_h; h++)
    {
        for (int w = 0; w < img_w; w++)
        {
            for (int c = 0; c < 3; c++)
            {
                data[c * hw + h * img_w + w] = (*img_data - mean[c])*0.017;
                img_data++;
            }
        }
    }
    LOGE("[%s][%d]\n",__FUNCTION__,__LINE__);

/**/
    return 0;
}

int TengineWrapper::RunTengine(const char* image)
{

    if( get_input_data(image, g_mobilenet_input, 224, 224) )
        return 7;
    if (!set_tensor_buffer(get_graph_input_tensor(g_mobilenet, 0, 0), g_mobilenet_input, 224 * 224 * 4 * 3))
        return 3;
    if( !run_graph(g_mobilenet,1))
        return 2;

    return 0;
}
int TengineWrapper::RunTengine(cv::Mat sample)
{

    if( get_input_data(sample, g_mobilenet_input, 224, 224) )
    {
        LOGE("[%s][%d]\n",__FUNCTION__,__LINE__);
        return 7;
    }
    if (set_tensor_buffer(get_graph_input_tensor(g_mobilenet, 0, 0), g_mobilenet_input, 224 * 224 * 4 *3)==-1)
    {
        LOGE("[%s][%d]\n",__FUNCTION__,__LINE__);
        return 3;
    }
    if(run_graph(g_mobilenet,1)<0)
    {
        LOGE("[%s][%d]\n",__FUNCTION__,__LINE__);
        return 2;
    }

    return 0;
}
std::string TengineWrapper::GetTop1()
{
    const char* label_file = "/data/local/tmp/synset_words.txt";
    std::vector<std::string> result;
    std::ifstream labels(label_file);

    std::string line;
    while (std::getline(labels, line))
        result.push_back(line);

    int true_id = 0;
    float true_score=0.f;

    tensor_t output_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
    float *data = (float *)get_tensor_buffer(output_tensor);

    for(int i=0; i<1000; i++)
    {
        if(data[i]>true_score)
        {
            true_id = i;
            true_score = data[i];
        }
    }

    return result[true_id - 1];
}
