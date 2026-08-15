#!/bin/bash
# build_glib276.sh - Cross-compile GLib 2.76.x for Android (QEMU 9+/11 needs
# glib >= 2.66; Limbo's 2.56.1 autotools flow is too old, glib >= 2.60 uses
# meson). Produces libglib-2.0.so + libgmodule-2.0.so + .pc files.
#
# Usage: build_glib276.sh <jni_root> <ndk_root> [abi]
set -e

JNI=$(cd "$1" && pwd)
NDK=$2
ABI=${3:-arm64-v8a}
OBJ="$JNI/../obj/local/$ABI"
GLIB_SRC="$JNI/glib276"
GLIB_BUILD="$JNI/glib276/build"

case "$ABI" in
  arm64-v8a)
    CLANG_TARGET=aarch64-linux-android21
    CPU_FAMILY=aarch64; CPU=arm64
    ;;
  armeabi-v7a)
    CLANG_TARGET=armv7a-linux-androideabi21
    CPU_FAMILY=arm; CPU=armv7
    ;;
  x86)
    CLANG_TARGET=i686-linux-android21
    CPU_FAMILY=x86; CPU=i686
    ;;
  x86_64)
    CLANG_TARGET=x86_64-linux-android21
    CPU_FAMILY=x86_64; CPU=x86_64
    ;;
  *) echo "Unsupported ABI: $ABI" >&2; exit 1 ;;
esac

CLANG_BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p "$OBJ"

cat > "$JNI/glib-cross.txt" <<EOF
[binaries]
c = '$CLANG_BIN/$CLANG_TARGET-clang'
cpp = '$CLANG_BIN/$CLANG_TARGET-clang++'
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
cd "$GLIB_SRC"
meson setup "$GLIB_BUILD" . --cross-file "$JNI/glib-cross.txt" \
  --default-library=shared -Dprefix=/usr \
  -Dc_args="-I$JNI/compat/musl/include -Wno-unknown-warning-option" \
  -Dlibmount=disabled -Dselinux=disabled -Ddtrace=false \
  -Dsystemtap=false -Dgtk_doc=false -Dman=false \
  -Dtests=false -Dinstalled_tests=false \
  -Dlibelf=disabled \
  -Dbsymbolic_functions=false -Dforce_posix_threads=true \
  -Dnls=disabled
ninja -C "$GLIB_BUILD"

# Install the built libs into Limbo's NDK obj dir
cp -f "$GLIB_BUILD"/glib/libglib-2.0.so.0.* "$OBJ/libglib-2.0.so" 2>/dev/null || \
  cp -f "$GLIB_BUILD"/glib/libglib-2.0.so "$OBJ/"
cp -f "$GLIB_BUILD"/gmodule/libgmodule-2.0.so.0.* "$OBJ/libgmodule-2.0.so" 2>/dev/null || \
  cp -f "$GLIB_BUILD"/gmodule/libgmodule-2.0.so "$OBJ/" 2>/dev/null || true
ls -la "$OBJ"/libglib* "$OBJ"/libgmodule* 2>/dev/null || true
echo "[OK] glib2.76 built: $OBJ/libglib-2.0.so"