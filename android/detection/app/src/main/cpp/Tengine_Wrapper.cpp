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

void tengine_log_func(const char* log){
    LOGE("TENGINE_LOG: %s\n",log);
};
const char* label_file ="/data/local/tmp/coco_labels_list.txt";
int TengineWrapper::InitTengine()
{

    set_log_level(LOG_DEBUG);
    set_log_output(tengine_log_func);
    if (init_tengine()!=0) {
        return 1;
    }
//    if (request_tengine_version("0.1") < 0)
//        return 1;
    LOGE("%s",get_tengine_version());
    const char* mobilenet_tf_model = "/data/local/tmp/detect.tflite";
//    const char* mobilenet_tf_model = "/data/local/tmp/mobilenet_v2_1.0_224.tflite";

//    const char* mobilenet_caffe_proto = "/data/local/tmp/MobileNetSSD_deploy.prototxt";
//    const char* mobilenet_caffe_model = "/data/local/tmp/MobileNetSSD_deploy.caffemodel";
    const char* format = "tflite";

//    const char* mobilenet_quant_model ="/data/local/tmp/mobilenet_int8.tmfile";
//    const char* format = "tengine";
    context_t context;
//    g_mobilenet = create_graph(nullptr,format,mobilenet_caffe_proto,mobilenet_caffe_model);
    g_mobilenet =create_graph(nullptr,format,mobilenet_tf_model);
    if(g_mobilenet == nullptr)
    {
        LOGE("create fail");
        return 1;
    }
    LOGE("create_graph finished");

    const int img_h = 300;
    const int img_w = 300;

    int image_size = img_h * img_w * 3;
    g_mobilenet_input = (uint8_t* )malloc(sizeof(uint8_t*) * image_size);

    int dims[] = {1,img_h, img_w,3 };

    tensor_t input_tensor = get_graph_input_tensor(g_mobilenet, 0, 0);
    LOGE("get input tensor finished");
    if(input_tensor == nullptr)
    {
        LOGE("Get input tensor failed\n");
        return 1;
    }

    set_tensor_shape(input_tensor, dims, 4);
    if( prerun_graph(g_mobilenet)<0 ) {
        LOGE("prerun failed");
        return 1;
    }
//    dump_graph(g_mobilenet);
    LOGE("prerun finished");


    LOGE("tensor shape and buffer set");

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
int TengineWrapper::get_input_data(cv::Mat img, uint8_t* data, int img_h, int img_w)
{
    if (img.empty())
        return 1;
//    cv::Mat img;
//    if (sample.channels() == 4)
//    {
//        cv::cvtColor(sample, img, cv::COLOR_BGRA2BGR);
//    }
//    else if (sample.channels() == 1)
//    {
//        cv::cvtColor(sample, img, cv::COLOR_GRAY2BGR);
//    }
//    else
//    {
//        img=sample;
//    }
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
int TengineWrapper::RunTengine(cv::Mat sample)
{
//    int dims[] = {1, 300, 300, 3};
    if( get_input_data(sample, g_mobilenet_input, 300, 300) )
        return 7;
    tensor_t input_tensor = get_graph_input_tensor(g_mobilenet, 0, 0);
    if(input_tensor == nullptr)
    {
        std::cerr << "Get input tensor failed\n";
        return 5;
    }

//    if(!set_tensor_shape(input_tensor, dims, 4))
//        return get_tengine_errno();

    LOGE("detect input data: %d,%d,%d\n",g_mobilenet_input[0],g_mobilenet_input[1],g_mobilenet_input[2]);
    if( set_tensor_buffer(input_tensor, g_mobilenet_input, 300 * 300 *3)==-1)
        return 4 ;
    if( run_graph(g_mobilenet,1)<0)
    {
        LOGE("Run graph failed");
        return 2;
    }
    return 0;
}
/** for caffe**/
//std::string TengineWrapper::GetTop1(int num)
//{
////    const char* label_file = "/data/local/tmp/coco_labels_list.txt";
//    std::vector<std::string> result;
//    std::ifstream labels(label_file);
//
//    std::string line;
//    while (std::getline(labels, line))
//        result.push_back(line);
//
//    int true_id = 0;
//    float true_score=0.f;
//
//    tensor_t output_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
//    float *data = (float *)get_tensor_buffer(output_tensor);
//    if(data == NULL)
//    {
//        LOGE("get output data data == NULL");
//        return nullptr;
//    }
//    return result[data[0+(num-1)*6]];
//
//}
//
//float TengineWrapper::Getprob(int num)
//{
////    const char* label_file = "/data/local/tmp/coco_labels_list.txt";
//    std::vector<std::string> result;
//    std::ifstream labels(label_file);
//
//    std::string line;
//    while (std::getline(labels, line))
//        result.push_back(line);
//
//    int true_id = 0;
//    float true_score=0.f;
//
//    tensor_t output_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
//    float *data = (float *)get_tensor_buffer(output_tensor);
//
//
//    return data[1+(num-1)*6];
//}
//
//float TengineWrapper::GetLeft(int num)
//{
////    const char* label_file = "/data/local/tmp/coco_labels_list.txt";
//    std::vector<std::string> result;
//    std::ifstream labels(label_file);
//
//    std::string line;
//    while (std::getline(labels, line))
//        result.push_back(line);
//
//    int true_id = 0;
//    float true_score=0.f;
//
//    tensor_t output_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
//    float *data = (float *)get_tensor_buffer(output_tensor);
//
//    return data[2+(num-1)*6];
//}
//float TengineWrapper::GetTop(int num)
//{
////   const char* label_file = "/data/local/tmp/coco_labels_list.txt";
//    std::vector<std::string> result;
//    std::ifstream labels(label_file);
//
//    std::string line;
//    while (std::getline(labels, line))
//        result.push_back(line);
//
//    int true_id = 0;
//    float true_score=0.f;
//
//    tensor_t output_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
//    float *data = (float *)get_tensor_buffer(output_tensor);
//
//
//    return data[3+(num-1)*6];
//}
//float TengineWrapper::GetRight(int num)
//{
////    const char* label_file = "/data/local/tmp/coco_labels_list.txt";
//    std::vector<std::string> result;
//    std::ifstream labels(label_file);
//
//    std::string line;
//    while (std::getline(labels, line))
//        result.push_back(line);
//
//    int true_id = 0;
//    float true_score=0.f;
//
//    tensor_t output_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
//    float *data = (float *)get_tensor_buffer(output_tensor);
//
//    return data[4+(num-1)*6];
//}
//float TengineWrapper::Getbuttom(int num)
//{
////    const char* label_file = "/data/local/tmp/coco_labels_list.txt";
//    std::vector<std::string> result;
//    std::ifstream labels(label_file);
//
//    std::string line;
//    while (std::getline(labels, line))
//        result.push_back(line);
//
//    int true_id = 0;
//    float true_score=0.f;
//
//    tensor_t output_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
//    float *data = (float *)get_tensor_buffer(output_tensor);
//
//    return data[5+(num-1)*6];
//}
//int TengineWrapper::ObjectNum()
//{
////    const char* label_file = "/data/local/tmp/coco_labels_list.txt";
//    std::vector<std::string> result;
//    std::ifstream labels(label_file);
//
//    std::string line;
//    while (std::getline(labels, line))
//        result.push_back(line);
//
//    int true_id = 0;
//    float true_score=0.f;
//
//    tensor_t output_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
//    float *data = (float *)get_tensor_buffer(output_tensor);
//    int out_dim[4];
//    int ret = get_tensor_shape(output_tensor,out_dim,4);
//    if(ret <= 0)
//    {
//        std::cout << "get tensor shape failed, errno: " << get_tengine_errno() << "\n";
//        return 1;
//    }
//    int num = out_dim[1];
//    return num;
//}
/**int8.tflite**/
std::string TengineWrapper::GetTop1(int num)
{

    tensor_t class_tensor = get_graph_output_tensor(g_mobilenet, 0, 1);

    float *classes = (float *) get_tensor_buffer(class_tensor);


    std::vector<std::string> result;
    std::ifstream labels(label_file);

    std::string line;
    while (std::getline(labels, line))
        result.push_back(line);

    int class_idx=classes[num];
    return result[class_idx];
}

float TengineWrapper::Getprob(int num)
{
    tensor_t score_tensor = get_graph_output_tensor(g_mobilenet, 0, 2);
    float *scores = (float *) get_tensor_buffer(score_tensor);
    LOGE("PROB: %f",scores[num]);
    return scores[num];
}

float TengineWrapper::GetLeft(int num)
{
    tensor_t box_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
    float *boxes = (float *) get_tensor_buffer(box_tensor);
    LOGE("left: %f",boxes[num*4]);
    return boxes[num*4];

}
float TengineWrapper::GetTop(int num)
{
    tensor_t box_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
    float *boxes = (float *) get_tensor_buffer(box_tensor);
    LOGE("top: %f",boxes[num*4+1]);
    return boxes[num*4+1];
}
float TengineWrapper::GetRight(int num)
{
    tensor_t box_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
    float *boxes = (float *) get_tensor_buffer(box_tensor);
    LOGE("right: %f",boxes[num*4+2]);
    return boxes[num*4+2];
}
float TengineWrapper::Getbuttom(int num)
{
    tensor_t box_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
    float *boxes = (float *) get_tensor_buffer(box_tensor);

    LOGE("buttom: %f",boxes[num*4+3]);
    return boxes[num*4+3];
}
int TengineWrapper::ObjectNum()
{
    tensor_t number_tensor = get_graph_output_tensor(g_mobilenet, 0, 3);
    float *num = (float *) get_tensor_buffer(number_tensor);

    std::vector<std::string> result;
    std::ifstream labels(label_file);

    std::string line;
    while (std::getline(labels, line))
        result.push_back(line);

    int max_num = num[0];

    return max_num;

}
//float TengineWrapper::test() {
//
//
//    tensor_t box_tensor = get_graph_output_tensor(g_mobilenet, 0, 0);
//    tensor_t class_tensor = get_graph_output_tensor(g_mobilenet, 0, 1);
//    tensor_t score_tensor = get_graph_output_tensor(g_mobilenet, 0, 2);
//    tensor_t number_tensor = get_graph_output_tensor(g_mobilenet, 0, 3);
//    float *boxes = (float *) get_tensor_buffer(box_tensor);
//    float *classes = (float *) get_tensor_buffer(class_tensor);
//    float *scores = (float *) get_tensor_buffer(score_tensor);
//    float *num = (float *) get_tensor_buffer(number_tensor);
//
//    std::vector<std::string> result;
//    std::ifstream labels(label_file);
//
//    std::string line;
//    while (std::getline(labels, line))
//        result.push_back(line);
//
//    int max_num = num[0];
//    printf("detect num : %d\n", max_num);
//    LOGE("detect num: %d\n",max_num);
//    for(int i = 0; i < max_num; i++)
//    {
//        if(scores[i] > 0.6)
//        {
//            int class_idx = classes[i];
//            std::cout << "score: " << scores[i] << " class:" << class_idx << "-- " << result[class_idx];
//            printf("\t%f,%f,%f,%f\n", boxes[i * 4] * 768, boxes[i * 4 + 1] * 576, boxes[i * 4 + 2] * 768,
//                   boxes[i * 4 + 3] * 576);
//        }
//
//    }
//    return max_num;
//    };
//    LOGE("DIM0: %f",out_dim[0]);
//    LOGE("DIM1: %f",out_dim[1]);
//    LOGE("DIM2: %f",out_dim[2]);
//    LOGE("DIM3: %f",out_dim[3]);
//    return out_dim_size;
//}

//    LOGE("data0: %f\n",data[0]);
//    LOGE("data1: %f\n",data[1]);
//    LOGE("data2: %f\n",data[2]);
//    LOGE("data3: %f\n",data[3]);
//    LOGE("data4: %f\n",data[4]);
//    LOGE("data5: %f\n",data[5]);
//    LOGE("data6: %f\n",data[6]);
//    LOGE("DIM0: %i",out_dim[0][0][1]);
//    LOGE("DIM1: %i",out_dim[0][0][0]);
//    LOGE("DIM2: %i",out_dim[0][0][3]);
//    LOGE("DIM3: %i",out_dim[0][0][2]);
//    float location=out_dim[2];
//    LOGE("DIM: %i",out_dim[2]);
//    return data[0];

