LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

OPENCV_INSTALL_MODULES:=on
OPENCV_CAMERA_MODULES:=off
OPENCV_LIB_TYPE:=STATIC
include ../../NVPACK/OpenCV-2.4.2-Tegra-sdk/sdk/native/jni/OpenCV-tegra3.mk

LOCAL_MODULE    := orb_feature_detector
LOCAL_SRC_FILES := jni_part.cpp
LOCAL_LDLIBS +=  -llog -ldl

include $(BUILD_SHARED_LIBRARY)
