#!/bin/bash
# build_libepoxy.sh - Cross-compile libepoxy for Android (arm64).
#
# libepoxy is the GL/EGL function loader that QEMU's CONFIG_OPENGL and
# virglrenderer's EGL platform both link against.  It is built SEPARATELY from
# the GTK4 stack on purpose: GPU acceleration must not depend on whether
# gtk4android built successfully.  Android has no GLX/gbm, so only the EGL
# backend is enabled.
#
# Produces:
#   <obj>/libepoxy.so
#   <epoxy_install>/lib/pkgconfig/epoxy.pc  (so QEMU's meson sees it)
#
# Usage: build_libepoxy.sh <jni_root> <ndk_root> [abi]
set -e

JNI=$(cd "$1" && pwd)
NDK=$2
ABI=${3:-arm64-v8a}
OBJ="$JNI/../obj/local/$ABI"
SRC="$JNI/libepoxy"
BUILD="$JNI/libepoxy/build"
INSTALL="$JNI/libepoxy/install"

case "$ABI" in
  arm64-v8a)
    CLANG_TARGET=aarch64-linux-android21
    CPU_FAMILY=aarch64; CPU=arm64 ;;
  armeabi-v7a)
    CLANG_TARGET=armv7a-linux-androideabi21
    CPU_FAMILY=arm; CPU=armv7 ;;
  x86)
    CLANG_TARGET=i686-linux-android21
    CPU_FAMILY=x86; CPU=i686 ;;
  x86_64)
    CLANG_TARGET=x86_64-linux-android21
    CPU_FAMILY=x86_64; CPU=x86_64 ;;
  *) echo "Unsupported ABI: $ABI" >&2; exit 1 ;;
esac

CLANG_BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
SYSROOT="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
mkdir -p "$OBJ" "$INSTALL"

if [ ! -d "$SRC" ]; then
  echo "ERROR: libepoxy sources missing at $SRC" >&2
  exit 1
fi

cat > "$JNI/epoxy-cross.txt" <<EOF
[binaries]
c = '$CLANG_BIN/$CLANG_TARGET-clang'
cpp = '$CLANG_BIN/$CLANG_TARGET-clang++'
ar = '$CLANG_BIN/llvm-ar'
strip = '$CLANG_BIN/llvm-strip'
pkgconfig = 'pkg-config'

[properties]
needs_exe_wrapper = true

[host_machine]
system = 'android'
cpu_family = '$CPU_FAMILY'
cpu = '$CPU'
endian = 'little'
EOF

# Android ships libEGL/libGLESv2 in the NDK sysroot but publishes no
# pkg-config files for them; epoxy looks them up with dependency().
mkdir -p "$INSTALL/lib/pkgconfig"
cat > "$INSTALL/lib/pkgconfig/egl.pc" <<EOF
Name: EGL
Description: Android EGL (NDK sysroot)
Version: 1.5
Cflags: -I$SYSROOT/usr/include
Libs: -lEGL
EOF
cat > "$INSTALL/lib/pkgconfig/glesv2.pc" <<EOF
Name: GLESv2
Description: Android OpenGL ES (NDK sysroot)
Version: 3.2
Cflags: -I$SYSROOT/usr/include
Libs: -lGLESv2
EOF

export PKG_CONFIG_PATH="$INSTALL/lib/pkgconfig"
export PKG_CONFIG_LIBDIR="$INSTALL/lib/pkgconfig"

rm -rf "$BUILD"
# Only EGL: Android has no X11 and no GLX.  x11=false also stops epoxy from
# probing (and failing on) missing X11 headers, and tests/docs are not shipped.
meson setup "$BUILD" "$SRC" \
  --cross-file "$JNI/epoxy-cross.txt" \
  --prefix "$INSTALL" \
  --default-library=shared \
  -Degl=yes \
  -Dglx=no \
  -Dx11=false \
  -Dtests=false \
  -Ddocs=false

ninja -C "$BUILD" -j "$(nproc)"
ninja -C "$BUILD" install

# Deploy into the obj dir next to the other native libs so the APK packer picks
# it up, and keep a resolvable unversioned name for QEMU's DT_NEEDED rewrite.
for so in "$INSTALL"/lib/libepoxy.so*; do
  [ -f "$so" ] || continue
  cp -Lf "$so" "$OBJ/$(basename "$so")"
done
cp -Lf "$INSTALL/lib/libepoxy.so" "$OBJ/libepoxy.so"

echo "[OK] libepoxy built: $OBJ/libepoxy.so"
echo "[OK] epoxy.pc at $INSTALL/lib/pkgconfig/epoxy.pc"
