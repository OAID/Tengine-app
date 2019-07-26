//
// Created by user on 07/05/2019.
//

#ifndef MOBILENET_TENGINE_WRAPPER_H
#define MOBILENET_TENGINE_WRAPPER_H

#include <opencv2/core/mat.hpp>
#include "tengine_c_api.h"
#include "opencv2/imgproc/imgproc.hpp"
#include "opencv2/highgui/highgui.hpp"

class TengineWrapper{
private:
    graph_t g_mobilenet;
    float* g_mobilenet_input;
    uint8_t* g_mobilenet_inputint8;
    int get_input_data(const char* image, float* data, int img_h, int img_w);
    int get_input_data(cv::Mat sample, float* data, int img_h, int img_w);
    int get_input_dataint8(cv::Mat sample, uint8_t* data, int img_h, int img_w);
public:
    TengineWrapper(){};
    ~TengineWrapper(){};
    int InitMobilenet();
    int InitTengine();
    int InitTengineint8();
    int RunMobilenet(const char* image);
    int RunTengine(const char* image);
    int RunMobilenet(cv::Mat sample);
    int RunTengine(cv::Mat sample);
    int RunTengineint8(cv::Mat sample);
    int ReleaseMobilenet();
    int ReleaseTengine();
    std::string GetTop1();

};

#endif //MOBILENET_TENGINE_WRAPPER_H
