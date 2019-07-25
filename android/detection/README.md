# Tengine - Object Detection

## Preface
- This app is built based on [Tengine](https://github.com/OAID), developed by OPEN AI LAB, which is a lite, high-performance, and modular inference engine for embedded device.  The app is the product of machine learning and deep neural networks, and includes the content to help users define the object in front of them. Thus, it is general enough to be applicable in wide variety for future expansion.

- In this project, we use Tengine to run the pretrained model to predict image.

- This App supports for choosing a picture from your phone for object detection. 

- The App processes the real time image camera preview and gives the predict results.


## Installation and Instruction
 

- Install the Android Studio, go to [Download](https://developer.android.google.cn/studio/). During installation, install the recommend tools and package as much as possible except the cloud ones. 

- You need to install adb in your library. After installation. You can always use the following command to see the list of your phone or emulator available for test. 

- Make sure your devices are based on Armv8.
- You need to push your model by using adb. You should find it if you already installed Android Studio. It is located under Android\Sdk\platform-tools (Linux) and \AppData\Local\Android\Sdk\platform-tools(Windows). If you have not installed yet, use

For Linux:
```
sudo apt-get install android-tools-adb
```
For Windows:
- If you already have Android SDK, it is under directory \sdk\platform-tools. Or download SDK here: http://developer.android.com/sdk/index.html 
- Find the adb and add it to your system environment variable. Then you can use adb command in terminal.

- Use adb –version to make sure it is installed correctly.

If you already have libtengine.so, place the library and other relative libraries into app/src/main/JniLibs/arm64-v8a. If you have not complied the library yet, please follow [Android build](https://github.com/OAID/Tengine/blob/master/doc/build_android.md). 

Use adb to push your model and label file in your file. Please [find them here](https://pan.baidu.com/s/1LXZ8vOdyOo50IXS0CUPp8g#list/path=%2F) (psw:57vb). For this Demo, you will need "detect.tflite" and "coco_labels_list.txt" under path Tengine_models/tflite

Use adb to push your model and label file in your file.
```
adb push your-model-name /data/local/tmp
```
In my case,
```
adb push detect.tflite coco_labels_list.txt /data/local/tmp
```
- Go to Android Studio, sync and build/run the project. 
<img src ="https://github.com/OAID/Tengine-app/blob/master/android/classification/app/src/asset/Sync.png">

- Also, make sure the App is given permission of using camera and SD card from your phone.

### Optional
- You can see all the supported models here: [Tengine model zoo](https://pan.baidu.com/s/1LXZ8vOdyOo50IXS0CUPp8g#list/path=%2F) (psw:57vb)
You may need to edit the model and its label based on your preference.
 
 

- You need to change the corresponding model input and output. 
- e. g: In the following case, the input is 300 * 300 for int8 TFLite model. And dimension is {1, 300, 300, 3}. If you want to change model to MobileNet SSD Caffe model, the size then needs to be 224*224. And dimension will be changed to {1, 3, 224, 224}. (Hint: Make sure you change all the code mentioning size and dimension in Tengine-Wrapper. The following snapshot is only for reference)
 

## Build your own library 
- If for some reason, you want to build your own Tengine library, it is ok. Please follow [Android build](https://github.com/OAID/Tengine/blob/master/doc/build_android.md). You can change the configure setting in CMakelist. 

- If you compile tengine yourself, make sure your protobuf version is exactly 3.0.0 otherwise you will see errors when you make file.

