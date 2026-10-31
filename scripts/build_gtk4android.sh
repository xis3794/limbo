#!/bin/bash
# build_gtk4android.sh - Cross-compile GTK4 (gtk4android fork) for Android.
#
# QEMU's ui/gtk.c talks to GTK4 once patches/qemu-*‑gtk4-android.patch is
# applied (upstream QEMU still asks for gtk+-3.0; the patch rewrites that
# dependency to 'gtk4').  gtk4android is the GTK tree that carries the
# GDK "android" backend, which is what lets a desktop GTK window render into
# an Android SurfaceView.
#
# The GTK stack is large: cairo, pango, harfbuzz, freetype, fontconfig,
# fribidi, gdk-pixbuf, graphene, libpng/jpeg/tiff and libsass are pulled in as
# meson subproject wraps.  Only the shared libraries are kept; demos, tests,
# introspection and every non-Android GDK backend are disabled.
#
# Produces:
#   <obj>/libgtk-4.so + its shared dependencies
#   <gtk_install>/lib/pkgconfig/gtk4.pc
#
# Usage: build_gtk4android.sh <jni_root> <ndk_root> [abi]
set -e

JNI=$(cd "$1" && pwd)
NDK=$2
ABI=${3:-arm64-v8a}
OBJ="$JNI/../obj/local/$ABI"
SRC="$JNI/gtk4android"
BUILD="$JNI/gtk4android/build-android"
INSTALL="$JNI/gtk4android/install-android"

# gtk4android's GDK android backend is compiled against API 31 (it needs
# AMotionEvent_fromJava/AKeyEvent_fromJava, exported by libandroid.so only
# from API 31 on).  The rest of the native stack stays on the lower API.
GTK_ANDROID_API=${GTK_ANDROID_API:-31}

case "$ABI" in
  arm64-v8a)
    CPU_FAMILY=aarch64; CPU=arm64 ;;
  armeabi-v7a)
    CPU_FAMILY=arm; CPU=armv7 ;;
  x86)
    CPU_FAMILY=x86; CPU=i686 ;;
  x86_64)
    CPU_FAMILY=x86_64; CPU=x86_64 ;;
  *) echo "Unsupported ABI: $ABI" >&2; exit 1 ;;
esac

# Only aarch64 is exercised by gtk4android; refuse rather than produce a
# GTK build that was never tested.
if [ "$CPU_FAMILY" != "aarch64" ]; then
  echo "ERROR: gtk4android only supports arm64-v8a (requested $ABI)" >&2
  exit 1
fi

CLANG_BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
SYSROOT="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot"

if [ ! -d "$SRC" ]; then
  echo "ERROR: gtk4android sources missing at $SRC" >&2
  exit 1
fi

mkdir -p "$OBJ" "$INSTALL"

cat > "$JNI/gtk4-cross.txt" <<EOF
[binaries]
c = '$CLANG_BIN/aarch64-linux-android$GTK_ANDROID_API-clang'
cpp = '$CLANG_BIN/aarch64-linux-android$GTK_ANDROID_API-clang++'
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

# Keep the downloaded wrap sources across reconfigures so CI does not refetch
# 30 tarballs on every run.
SAVE="$BUILD.subprojects-save"
if [ -d "$BUILD/subprojects" ]; then
  rm -rf "$SAVE"; mv "$BUILD/subprojects" "$SAVE"
fi
rm -rf "$BUILD"
if [ -d "$SAVE" ]; then
  mkdir -p "$BUILD"; cp -a "$SAVE" "$BUILD/subprojects"; rm -rf "$SAVE"
fi

# glib/libffi/pixman/epoxy come from the jni builds, not from the wraps: they
# are the very same libraries QEMU links against, so GTK must share them or
# the process ends up with two GLibs.  Restricting PKG_CONFIG_LIBDIR to those
# prefixes also keeps the runner's host pcre2/zlib/gtk out of the build.
PC_DIRS="$JNI/pc:$JNI/libepoxy/install/lib/pkgconfig"
export PKG_CONFIG_PATH="$PC_DIRS"
export PKG_CONFIG_LIBDIR="$PC_DIRS"

cd "$SRC"
meson setup "$BUILD" . \
  --cross-file "$JNI/gtk4-cross.txt" \
  --prefix "$INSTALL" \
  --default-library=shared \
  --wrap-mode=default \
  -Dandroid-backend=true \
  -Dx11-backend=false \
  -Dwayland-backend=false \
  -Dbroadway-backend=false \
  -Dwin32-backend=false \
  -Dmacos-backend=false \
  -Dmedia-gstreamer=disabled \
  -Dprint-cups=disabled \
  -Dprint-cpdb=disabled \
  -Dcloudproviders=disabled \
  -Dcolord=disabled \
  -Dtracker=disabled \
  -Daccesskit=disabled \
  -Dsysprof=disabled \
  -Dintrospection=disabled \
  -Dbuild-demos=false \
  -Dbuild-examples=false \
  -Dbuild-tests=false \
  -Dbuild-testsuite=false \
  -Dvulkan=disabled

ninja -C "$BUILD" -j "$(nproc)"
ninja -C "$BUILD" install

# Ship every shared library the GTK stack produced.  QEMU dlopens libgtk-4.so
# and it in turn needs the cairo/pango/gdk-pixbuf/... siblings from the same
# install prefix, so the whole lib dir goes into the APK.
count=0
for so in "$INSTALL"/lib/lib*.so*; do
  [ -f "$so" ] || continue
  cp -Lf "$so" "$OBJ/"
  count=$((count + 1))
done

if [ ! -f "$INSTALL/lib/pkgconfig/gtk4.pc" ]; then
  echo "ERROR: gtk4.pc was not installed - QEMU's meson will not find GTK" >&2
  exit 1
fi

echo "[OK] GTK4 built: $count shared libraries into $OBJ"
echo "[OK] gtk4.pc at $INSTALL/lib/pkgconfig/gtk4.pc"
