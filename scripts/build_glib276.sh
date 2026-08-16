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
# meson's iconv "system" probe looks for a library named libiconv;
# Limbo provides iconv symbols inside libcompat-musl.so
ln -sf libcompat-musl.so "$OBJ/libiconv.so" 2>/dev/null || \
  cp -f "$OBJ/libcompat-musl.so" "$OBJ/libiconv.so"

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

# Only search OUR pkg-config dir: the host Ubuntu pcre2/glib etc. would be
# picked up otherwise and break cross builds (host headers not in sysroot)
export PKG_CONFIG_PATH="$JNI/pc"
export PKG_CONFIG_LIBDIR="$JNI/pc"
cd "$GLIB_SRC"
# glib requires iconv on non-Windows; bionic has none and meson's iconv probe
# cannot use our pkg-config entries -> relax it to optional (iconv.h comes
# from c_args, symbols resolve at runtime from libcompat-musl.so)
sed -i "s|^  libiconv = dependency('iconv')|  libiconv = dependency('iconv', required: false)|" "$GLIB_SRC/meson.build"
# bionic libc exports kqueue/kevent stub symbols (link probe passes) but has
# no sys/event.h -> disable kqueue file monitor
sed -i 's|^if have_func_kqueue and have_func_kevent$|if false # Limbo: no kqueue on Android|' "$GLIB_SRC/gio/meson.build"
meson setup "$GLIB_BUILD" . --cross-file "$JNI/glib-cross.txt" \
  --default-library=shared -Dprefix=/usr \
  -Dc_args="-I$JNI/compat/musl/include -Wno-unknown-warning-option -Wno-error=implicit-function-declaration" \
  -Dc_link_args="-L$OBJ -lcompat-musl -llog -Wl,-z,undefs" \
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

# libglib-2.0.so has DT_NEEDED on libpcre2-8.so.0 (meson wrap build) and
# libintl.so.8 (NDK stub). AGP only packages standard "lib*.so" names, so we
# rename them and patch libglib's DT_NEEDED to the unversioned names.
# pcre2's real .so is ~700KB; the 0-byte placeholder files must be skipped
PCRE2_SO=$(find "$GLIB_BUILD/subprojects" -type f -name 'libpcre2-8.so*' -size +100k 2>/dev/null | head -1)
if [ -n "$PCRE2_SO" ]; then
  cp -f "$PCRE2_SO" "$OBJ/libpcre2-8.so"
  # sanity: must be a real ELF
  if head -c4 "$OBJ/libpcre2-8.so" 2>/dev/null | grep -q ELF; then
    echo "[OK] libpcre2-8.so copied to $OBJ ($(stat -c%s "$OBJ/libpcre2-8.so") bytes)"
  else
    echo "[WARN] libpcre2-8.so is not valid ELF!"
  fi
else
  echo "[WARN] pcre2 shared lib not found in subprojects"
fi
# intl: NDK provides a stub libintl.so; use musl's real implementation
# (already inside libcompat-musl.so) as libintl.so
cp -f "$OBJ/libcompat-musl.so" "$OBJ/libintl.so"
echo "[OK] libintl.so created from libcompat-musl.so"

# Patch DT_NEEDED entries so Android finds the unversioned names
if command -v patchelf >/dev/null 2>&1; then
  patchelf --replace-needed libintl.so.8 libintl.so "$OBJ/libglib-2.0.so" 2>/dev/null || true
  patchelf --replace-needed libpcre2-8.so.0 libpcre2-8.so "$OBJ/libglib-2.0.so" 2>/dev/null || true
  echo "[OK] libglib DT_NEEDED patched to unversioned names"
else
  echo "[WARN] patchelf not available; DT_NEEDED not patched"
fi
ls -la "$OBJ"/libglib* "$OBJ"/libgmodule* "$OBJ"/libpcre2* "$OBJ"/libintl* 2>/dev/null || true
echo "[OK] glib2.76 built: $OBJ/libglib-2.0.so"