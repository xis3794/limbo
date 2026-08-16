LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := intl_stubs.c

LOCAL_MODULE := intl

LOCAL_ARM_MODE := $(ARM_MODE)
include $(BUILD_SHARED_LIBRARY)