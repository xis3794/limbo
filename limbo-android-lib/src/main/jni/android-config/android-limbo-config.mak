############### Limbo Configuration ##############
# if the  makefile doesn't recognize the project path you can override it here:
#LIMBO_JNI_ROOT := /home/dev/limbo/workspace_limbo/limbo-android-lib/src/main/jni

# Last version with gcc support is 14b 
#NDK_ROOT = /home/dev/tools/ndk/android-ndk-r14b
#USE_GCC?=true
# Or use r23 with clang 
NDK_ROOT ?= /home/dev/tools/ndk/android-ndk-r23b
USE_GCC?=false

### the ndk api should be the same as the minSdkVersion in your AndroidManifest.xml 
NDK_PLATFORM_API=21

# Set to true if you use platform-21 or above
USE_NDK_PLATFORM21 ?= true

# Set to true if you use platform-26 or above
USE_NDK_PLATFORM26 ?= false

# Optimization, generally it is better set to false when debugging
USE_OPTIMIZATION ?= true

# Hardening: it produces slower runtimes but helps preventing buffer overflow attacks
# Set to false for maximum performance (matches performance-focused builds)
USE_SECURITY ?= false

# Uncomment to enable debugging
# If you enable debugging you should turn off optimization as well
#NDK_DEBUG=1

# Uncomment if you use Linux x86, Linux 64bit, or macosx PC to compile
# Compiling on Windows is no longer supported
#NDK_ENV ?= linux-x86
NDK_ENV ?= linux-x86_64
#NDK_ENV ?= darwin-x86

# Build threads (make -j ?) makes building faster
BUILD_THREADS ?= 3

############## QEMU Host and Guest

# Android device type (host arch)
# values: armeabi-v7a, arm64-v8a, x86, x86_64
BUILD_HOST?=arm64-v8a

# GUEST_ARCH is the Emulator type
# values: x86_64-softmmu,aarch64-softmmu,sparc64-softmmu,ppc64-softmmu
BUILD_GUEST?=x86_64-softmmu

# QEMU Version
# values: 2.9.1, 5.1.0, 8.0.5, 11.1.0
USE_QEMU_VERSION ?= 11.1.0

# If you want to use SDL interface
USE_SDL ?= true

# If you want to use SDL Audio with Android AudioTrack
USE_SDL_AUDIO ?= true

# if you want to use Android AAudio, it needs version platform API 26
USE_AAUDIO ?= true

# Enable KVM
# Note: KVM headers are available only for android-21 platform and above
USE_KVM ?= true

# ---------------------------------------------------------------------------
# Optional display stacks.  Both are off by default so a plain build keeps
# exactly the libraries it always had; CI (and a local `make limbo`) turns
# them on with `export USE_GTK=true` / `export USE_VIRGL=true`.
#
#   USE_GTK     GTK4 display backend (gtk4android): `-display gtk`.
#               Requires QEMU 11.0.0, the only tree the GTK4 port applies to,
#               and an arm64-v8a host (the GDK android backend targets aarch64).
#   USE_VIRGL   OpenGL/virglrenderer: `-Dopengl=enabled`,
#               `-Dvirglrenderer=enabled`, virtio-gpu-gl-pci and ,gl=on.
#               Needs USE_GTK too: both link the libepoxy that gtk4android
#               installs, and a GL-only build would be an untested combination.
#
# USE_VIRGL_VENUS additionally enables the Vulkan (venus) backend.
# ---------------------------------------------------------------------------
USE_GTK ?= false
USE_VIRGL ?= false
USE_VIRGL_VENUS ?= false

# The GDK android backend needs the API 31 NDK (AMotionEvent_fromJava and
# AKeyEvent_fromJava are only exported by libandroid.so from API 31 on).
GTK_ANDROID_API ?= 31

# virglrenderer / gtk4android install prefixes (kept next to their sources so
# `make clean-*` can wipe them).
VIRGL_URL ?= https://gitlab.freedesktop.org/virgl/virglrenderer.git
VIRGL_TAG ?= 1.1.0
EPOXY_URL ?= https://github.com/anholt/libepoxy/archive/refs/tags/1.5.10.tar.gz
GTK4_URL ?= https://github.com/SlugAir928437/gtk4android.git
GTK4_BRANCH ?= master

EPOXY_INSTALL_DIR := $(LIMBO_JNI_ROOT)/libepoxy/install
GTK_INSTALL_DIR := $(LIMBO_JNI_ROOT)/gtk4android/install-android
VIRGL_INSTALL_DIR := $(LIMBO_JNI_ROOT)/virglrenderer/install-android

# pkg-config search path for the optional stacks: the libs we build ourselves
# must be found, and nothing from the host may leak in.
# Order matters: GTK's own dependencies (gio-2.0, gobject-2.0, gmodule-2.0,
# glib-2.0) must resolve to the GLib 2.89 prefix that GTK was built against,
# otherwise pkg-config walks gtk4.pc's Requires line and finds QEMU's 2.76.6
# from $(LIMBO_JNI_ROOT)/pc first:
#   Package dependency requirement 'glib-2.0 >= 2.84' could not be satisfied.
#   Package 'gio-2.0', required by 'gtk4', not found
# $(LIMBO_JNI_ROOT)/pc therefore comes last, and only supplies what the 2.89
# prefix does not have (pixman, iconv shims).
GLIB289_INSTALL_DIR := $(LIMBO_JNI_ROOT)/glib289/install-android
OPT_PC_DIRS := $(GLIB289_INSTALL_DIR)/lib/pkgconfig:$(EPOXY_INSTALL_DIR)/lib/pkgconfig:$(GTK_INSTALL_DIR)/lib/pkgconfig:$(VIRGL_INSTALL_DIR)/lib/pkgconfig:$(LIMBO_JNI_ROOT)/pc

$(info USE_GTK = $(USE_GTK))
$(info USE_VIRGL = $(USE_VIRGL))
$(info USE_VIRGL_VENUS = $(USE_VIRGL_VENUS))
