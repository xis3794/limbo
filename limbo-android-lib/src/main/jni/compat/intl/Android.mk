LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := intl_stubs.c

LOCAL_MODULE := intl

LOCAL_ARM_MODE := $(ARM_MODE)
# version-script: export _rwlock_* etc. under the LIBC version name so
# glib's _rwlock_trywrlock@LIBC references resolve (Huawei linker requires
# versioned-ref -> versioned-def matching)
LOCAL_LDFLAGS += -Wl,--version-script=$(LOCAL_PATH)/intl.map
include $(BUILD_SHARED_LIBRARY)