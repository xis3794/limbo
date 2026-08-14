#!/bin/bash
# build_libslirp.sh - Cross-compile libslirp 4.7.0 for Android arm64 using
# the NDK clang toolchain, then install the static lib into the NDK obj dir.
#
# Usage: build_libslirp.sh <jni_root> <ndk_root> [abi]
set -e

JNI=$(cd "$1" && pwd)
NDK=$2
ABI=${3:-arm64-v8a}
OBJ="$JNI/../obj/local/$ABI"

case "$ABI" in
  arm64-v8a)
    CLANG_TARGET=aarch64-linux-android21
    CPU_FAMILY=aarch64
    CPU=arm64
    ;;
  armeabi-v7a)
    CLANG_TARGET=armv7a-linux-androideabi21
    CPU_FAMILY=arm
    CPU=armv7
    ;;
  x86)
    CLANG_TARGET=i686-linux-android21
    CPU_FAMILY=x86
    CPU=i686
    ;;
  x86_64)
    CLANG_TARGET=x86_64-linux-android21
    CPU_FAMILY=x86_64
    CPU=x86_64
    ;;
  *)
    echo "Unsupported ABI: $ABI" >&2
    exit 1
    ;;
esac

CLANG_BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p "$OBJ"

cat > "$JNI/slirp-cross.txt" <<EOF
[binaries]
c = '$CLANG_BIN/$CLANG_TARGET-clang'
ar = '$CLANG_BIN/llvm-ar'
strip = '$CLANG_BIN/llvm-strip'
pkgconfig = 'pkg-config'

[host_machine]
system = 'linux'
cpu_family = '$CPU_FAMILY'
cpu = '$CPU'
endian = 'little'
EOF

export PKG_CONFIG_PATH="$JNI/pc"
cd "$JNI"
meson setup libslirp/build libslirp --cross-file slirp-cross.txt --default-library=static
ninja -C libslirp/build
cp -f libslirp/build/libslirp-version.h libslirp/src/
cp -f libslirp/build/libslirp.a "$OBJ/"
echo "[OK] libslirp built: $OBJ/libslirp.a"