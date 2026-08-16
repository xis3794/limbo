LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := capng_stubs.c

LOCAL_MODULE := cap-ng

LOCAL_C_INCLUDES :=			\
	$(LOCAL_PATH)/..

LOCAL_ARM_MODE := $(ARM_MODE)
include $(BUILD_SHARED_LIBRARY)
