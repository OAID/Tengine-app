#/bin/bash
if [ $# -eq 0 ];
then
	echo "must run with path of android_v7"
	exit -1
fi

if [ -d $1 ];
then
	cp $1/*.so tengine/src/main/jniLibs/armeabi-v7a/ -f 
else
	echo "path : $1 is not existed!!!"
fi

