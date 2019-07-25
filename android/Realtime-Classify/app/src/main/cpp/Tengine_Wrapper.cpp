//
// Created by user on 07/05/2019.
//
#include <iostream>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fstream>
#include "Tengine_Wrapper.h"
#include "opencv2/imgcodecs.hpp"
#include <android/log.h>
#define  LOG_TAG    "JNI_PART"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG, __VA_ARGS__)
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG, __VA_ARGS__)
#define LOGF(...)  __android_log_print(ANDROID_LOG_FATAL,LOG_TAG, __VA_ARGS__)
int TengineWrapper::InitTengine()
{
    init_tengine();
    if (request_tengine_version("0.1") < 0)
        return -1;

    const char* mobilenet_caffe_proto = "/data/local/tmp/mobilenet_deploy.prototxt";
    const char* mobilenet_caffe_model = "/data/local/tmp/mobilenet.caffemodel";
    const char* format = "caffe";

    g_mobilenet = create_graph(nullptr,format,mobilenet_caffe_proto,mobilenet_caffe_model);
//    g_mobilenet =create_graph(nullptr,format,mobilenet_tf_model);
//    if(dump_graph(g_mobilenet);

    if(g_mobilenet == nullptr)
    {
        std::cerr << "Create graph failed.\n";
        std::cerr << "errno: " << get_tengine_errno() << "\n";
        return false;
    }

    const int img_h = 224;
    const int img_w = 224;

    int image_size = img_h * img_w * 3;
    g_mobilenet_input = (float*) malloc(sizeof(float) * image_size);

    int dims[] = {1, 3, img_h, img_w};

    tensor_t input_tensor = get_graph_input_tensor(g_mobilenet, 0, 0);
    if(input_tensor == nullptr)
    {
        std::cerr << "Get input tensor failed\n";
        return false;
    }

    set_tensor_shape(input_tensor, dims, 4);
    set_tensor_buffer(input_tensor, g_mobilenet_input, image_size * 4);
//
    if( prerun_graph(g_mobilenet)!=0 )
        return 1;

    return 0;
}

int TengineWrapper::InitTengineint8()
{
    init_tengine();
    if (request_tengine_version("0.1") < 0)
        return -1;

    const char* mobilenet_tf_model = "/data/local/tmp/mobilenet_quant_v1_224.tflite";
//    const char* mobilenet_caffe_proto = "/data/local/tmp/mobilenet_deploy.prototxt";
//    const char* mobilenet_caffe_model = "/data/local/tmp/mobilenet.caffemodel";
    const char* format = "tflite";

//    g_mobilenet = create_graph(nullptr,format,mobilenet_caffe_proto,mobilenet_caffe_model);
    g_mobilenet =create_graph(nullptr,format,mobilenet_tf_model);
//    if(dump_graph(g_mobilenet);

    if(g_mobilenet == nullptr)
    {
        LOGE( "Create graph failed.\n");
        std::cerr << "errno: " << get_tengine_errno() << "\n";
        return 1;
    }

    const int img_h = 224;
    const int img_w = 224;

    int image_size = img_h * img_w * 3;
    g_mobilenet_inputint8 = (uint8_t*) malloc(sizeof(uint8_t*) * image_size);

    int dims[] = {1, img_h, img_w,3};

    tensor_t input_tensor = get_graph_input_tensor(g_mobilenet, 0, 0);
    if(input_tensor == nullptr)
    {
        LOGE( "Get input tensor failed\n");
        return 2;
    }

    set_tensor_shape(input_tensor, dims, 4);
//
    if( prerun_graph(g_mobilenet)!=0 ) {
        LOGE("prerun failed");
        return 3;
    }
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
//    remove_model("mobilenet");

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
int TengineWrapper::get_input_dataint8(cv::Mat img, uint8_t* data, int img_h, int img_w)
{
    if (img.empty())
        return 1;
    cv::cvtColor(img,img,cv::COLOR_RGBA2BGR);
    cv::resize(img, img, cv::Size(img_h, img_w));
    img.convertTo(img, CV_32FC3);
    float* img_data = ( float* )img.data;
    uint8_t* ptr = data;

    for(int h = 0; h < img_h; h++)
    {
        for(int w = 0; w < img_w; w++)
        {
            for(int c = 0; c < 3; c++)
            {
                *ptr = img_data[2 - c];
                ptr++;
            }
            img_data += 3;
        }
    }
/**/LOGE("get input data finished");
    return 0;
}
int TengineWrapper::RunTengine(const char* image)
{

    if( get_input_data(image, g_mobilenet_input, 224, 224) )
        return 7;
    if (!set_tensor_buffer(get_graph_input_tensor(g_mobilenet, 0, 0), g_mobilenet_input, 224 * 224 * 4))
        return 3;
    if( !run_graph(g_mobilenet,1))
        return 2;

    return 0;
}
int TengineWrapper::RunTengine(cv::Mat sample)
{
    if( get_input_data(sample, g_mobilenet_input, 224, 224) )
        return 7;
    tensor_t input_tensor = get_graph_input_tensor(g_mobilenet, 0, 0);
    if(input_tensor == nullptr)
    {
        std::cerr << "Get input tensor failed\n";
        return 5;
    }

//    if(!set_tensor_shape(input_tensor, dims, 4))
//        return get_tengine_errno();

    if( set_tensor_buffer(input_tensor, g_mobilenet_input, 224 * 224 *3 * 4)==-1)
       return 4 ;
    if( run_graph(g_mobilenet,1)<0)
        return 2;

    return 0;
}
int TengineWrapper::RunTengineint8(cv::Mat sample)
{
    if( get_input_dataint8(sample, g_mobilenet_inputint8, 224, 224) )
        return 7;
    tensor_t input_tensor = get_graph_input_tensor(g_mobilenet, 0, 0);
    if(input_tensor == nullptr)
    {
        std::cerr << "Get input tensor failed\n";
        return 5;
    }
//    LOGE("detect input data: %d,%d,%d\n",g_mobilenet_input[0],g_mobilenet_input[1],g_mobilenet_input[2]);
    if( set_tensor_buffer(input_tensor, g_mobilenet_inputint8, 224 * 224 *3)==-1)
        return 4 ;
    if( run_graph(g_mobilenet,1)<0)
    {
        LOGE("Run graph failed");
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
    return result[true_id ];
}

