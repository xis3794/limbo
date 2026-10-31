#!/bin/bash
# build_virglrenderer.sh - Cross-compile virglrenderer for Android.
#
# virglrenderer is the host-side renderer behind QEMU's virtio-gpu-gl-pci
# device: the guest's Mesa submits Gallium command streams, QEMU forwards them
# over virtio-gpu, and virglrenderer turns them into real GPU calls through
# EGL/GLES on the phone.
#
# Android specifics (see patches/virglrenderer-android-egl.patch):
#   * there is no libdrm and no gbm, so the DRM renderers stay off and libdrm
#     must not be required;
#   * EGL must be enabled without gbm - QEMU hands virglrenderer its own
#     EGLDisplay through the get_egl_display callback;
#   * the venus (Vulkan) backend needs AHardwareBuffer instead of dma-buf
#     export, since Android vendor drivers cannot export host memory.
#
# Produces:
#   <obj>/libvirglrenderer.so
#   <virgl_install>/lib/pkgconfig/virglrenderer.pc
#
# Usage: build_virglrenderer.sh <jni_root> <ndk_root> [abi]
#   USE_VIRGL_VENUS=true enables the venus (Vulkan) backend.
set -e

JNI=$(cd "$1" && pwd)
NDK=$2
ABI=${3:-arm64-v8a}
OBJ="$JNI/../obj/local/$ABI"
SRC="$JNI/virglrenderer"
BUILD="$JNI/virglrenderer/build-android"
INSTALL="$JNI/virglrenderer/install-android"
VENUS=${USE_VIRGL_VENUS:-false}

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

if [ ! -d "$SRC" ]; then
  echo "ERROR: virglrenderer sources missing at $SRC" >&2
  exit 1
fi

# The patches live in the repo; apply them when the tree is still pristine.
for p in "$JNI/patches"/virglrenderer-*.patch; do
  [ -f "$p" ] || continue
  if patch -p1 -d "$SRC" --dry-run -R -f <"$p" >/dev/null 2>&1; then
    echo ">> $(basename "$p") already applied"
  elif patch -p1 -d "$SRC" --dry-run -f <"$p" >/dev/null 2>&1; then
    patch -p1 -d "$SRC" --no-backup-if-mismatch -f <"$p"
    echo ">> applied $(basename "$p")"
  else
    echo "ERROR: $(basename "$p") does not apply to the virglrenderer tree" >&2
    exit 1
  fi
done

mkdir -p "$OBJ" "$INSTALL"

cat > "$JNI/virgl-cross.txt" <<EOF
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

# Android ships libEGL/libGLESv2/libvulkan in the NDK sysroot but publishes no
# pkg-config files for them; virglrenderer looks them up with dependency().
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
# venus calls Vulkan 1.1 entry points that only exist from the API 28 stub on.
cat > "$INSTALL/lib/pkgconfig/vulkan.pc" <<EOF
Name: Vulkan
Description: Android Vulkan loader (NDK sysroot, API 28 stub)
Version: 1.3.0
Cflags: -I$SYSROOT/usr/include
Libs: -L$SYSROOT/usr/lib/aarch64-linux-android/28 -lvulkan
EOF

PC_DIRS="$INSTALL/lib/pkgconfig:$JNI/libepoxy/install/lib/pkgconfig:$JNI/pc"
export PKG_CONFIG_PATH="$PC_DIRS"
export PKG_CONFIG_LIBDIR="$PC_DIRS"

# Collect only the meson options this tree actually declares: option names and
# types differ between releases (venus vs venus-experimental, drm as feature or
# boolean), and a wrong *value* is a hard meson error even though an unknown
# option is only a warning.
opts="-Dplatforms=egl"
if [ "$VENUS" = "true" ]; then venus_on=1; else venus_on=0; fi
for opt in venus venus-experimental venus-validate drm drm-msm drm-msm-experimental minigbm_allocation tests; do
  [ "$(grep -cE "option\('$opt'|^[[:space:]]*'$opt'[[:space:]]*," "$SRC/meson_options.txt" 2>/dev/null || echo 0)" = "0" ] && continue
  opt_type=$(grep -A4 -E "option\('$opt'|^[[:space:]]*'$opt'[[:space:]]*," "$SRC/meson_options.txt" \
    | sed -n "s/.*type *: *'\([a-z]*\)'.*/\1/p" | head -n1)
  opt_on=0
  if [ "$venus_on" = "1" ]; then
    case "$opt" in venus | venus-experimental) opt_on=1 ;; esac
  fi
  case "$opt_type" in
    feature) if [ "$opt_on" = "1" ]; then opts="$opts -D$opt=enabled"; else opts="$opts -D$opt=disabled"; fi ;;
    boolean) if [ "$opt_on" = "1" ]; then opts="$opts -D$opt=true"; else opts="$opts -D$opt=false"; fi ;;
  esac
done
# With venus, the default 'process' render server forks/execs
# virgl_render_server, which does not exist inside the app sandbox.
if [ "$venus_on" = "1" ] && grep -qE "option\('render-server-worker'|^[[:space:]]*'render-server-worker'[[:space:]]*," "$SRC/meson_options.txt" 2>/dev/null; then
  opts="$opts -Drender-server-worker=thread"
fi

echo ">> virglrenderer meson options: $opts"

rm -rf "$BUILD"
cd "$SRC"
meson setup "$BUILD" . \
  --cross-file "$JNI/virgl-cross.txt" \
  --prefix "$INSTALL" \
  --default-library=shared \
  $opts

ninja -C "$BUILD" -j "$(nproc)"
ninja -C "$BUILD" install

for so in "$INSTALL"/lib/libvirglrenderer.so*; do
  [ -f "$so" ] || continue
  cp -Lf "$so" "$OBJ/$(basename "$so")"
done

if [ ! -f "$OBJ/libvirglrenderer.so" ]; then
  echo "ERROR: libvirglrenderer.so was not produced" >&2
  exit 1
fi

echo "[OK] virglrenderer built: $OBJ/libvirglrenderer.so (venus=$VENUS)"
