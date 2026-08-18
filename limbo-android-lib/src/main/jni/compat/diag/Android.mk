LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := diag.c

LOCAL_MODULE := diag

LOCAL_ARM_MODE := $(ARM_MODE)
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)