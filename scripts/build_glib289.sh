#!/bin/bash
# build_glib289.sh - Cross-compile GLib 2.89 for the GTK4 display stack.
#
# Why a second GLib build:
#   QEMU 9+/11 links GLib 2.76.6 (build_glib276.sh) and that must not change -
#   it is the ABI the engine was developed and shipped against. GTK 4.19+
#   (gtk4android master) however declares
#       glib_req = '>= 2.84'
#   in its meson.build, so meson rejects 2.76.6 and falls back to the glib
#   wrap, which needs a subprojects/packagefiles/glib/hack.patch that the
#   repo does not ship. Building GLib 2.89.3 into its own prefix solves both:
#   GTK gets a new enough GLib through pkg-config, and QEMU keeps 2.76.6.
#
# The two GLibs never meet in one process: QEMU loads libgtk-4.so, which links
# the 2.89 copy, so the host process would end up with two GLibs after all -
# which is why the GTK prefix is only used for GTK's own build, while QEMU's
# link line keeps pointing at the 2.76 prefix. GTK and QEMU exchange only C
# structs and callbacks across the display interface, never GLib objects.
#
# Produces: <glib289_install>/lib/libglib-2.0.so... and .pc files.
#
# Usage: build_glib289.sh <jni_root> <ndk_root> [abi]

set -e

JNI=$(cd "$1" && pwd)
NDK=$2
ABI=${3:-arm64-v8a}
OBJ="$JNI/../obj/local/$ABI"
SRC="$JNI/glib289"
BUILD="$JNI/glib289/build-android"
INSTALL="$JNI/glib289/install-android"
GLIB_VERSION=2.89.3

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

if [ ! -f "$SRC/meson.build" ]; then
  echo "ERROR: glib $GLIB_VERSION sources missing at $SRC" >&2
  echo "       CI fetches them into limbo-android-lib/src/main/jni/glib289" >&2
  exit 1
fi

# The Android adaptation (trace probes removed, g_set_user_dirs exported) is
# shared with the QEMU-side GLib; see patches/glib-2.89.3-android.patch.
if [ -f "$JNI/patches/glib-2.89.3-android.patch" ]; then
  if patch -p1 -d "$SRC" --dry-run -R -f < "$JNI/patches/glib-2.89.3-android.patch" >/dev/null 2>&1; then
    echo ">> glib-2.89.3-android.patch already applied"
  elif patch -p1 -d "$SRC" --dry-run -f < "$JNI/patches/glib-2.89.3-android.patch" >/dev/null 2>&1; then
    patch -p1 -d "$SRC" --no-backup-if-mismatch -f < "$JNI/patches/glib-2.89.3-android.patch"
    echo ">> applied glib-2.89.3-android.patch"
  else
    echo "ERROR: glib-2.89.3-android.patch does not apply" >&2
    exit 1
  fi
fi

mkdir -p "$OBJ" "$INSTALL"

# GObject type instances need libffi. The QEMU-side libffi is a *static*
# archive (--enable-static --disable-shared, see the Makefile's config-ffi),
# so it publishes no .pc file and cannot satisfy dependency('libffi'). Build
# a shared copy into the GTK prefix instead; both copies are ABI compatible
# and never link into the same module.
FFI_SRC="$JNI/libffi"
FFI_BUILD="$JNI/libffi/build-gtk289"
if [ -f "$FFI_SRC/configure" ] && [ ! -f "$INSTALL/lib/libffi.so" ]; then
  echo ">> building shared libffi for the GTK stack"
  rm -rf "$FFI_BUILD"
  mkdir -p "$FFI_BUILD"
  ( cd "$FFI_BUILD" && \
    "$FFI_SRC/configure" --host="$CLANG_TARGET" \
      --prefix="$INSTALL" --enable-shared --disable-static \
      CC="$CLANG_BIN/$CLANG_TARGET-clang" \
      AR="$CLANG_BIN/llvm-ar" \
      CFLAGS="-I$JNI/compat/musl/include -Wno-unknown-warning-option" \
      LDFLAGS="-L$OBJ -lcompat-musl -llog -Wl,-z,undefs" \
      >/dev/null && \
    make -j "$(nproc)" >/dev/null && make install >/dev/null )
  echo ">> shared libffi installed into $INSTALL"
fi

# bionic has no libiconv; Limbo's compat-musl carries the symbols. meson's
# iconv probe only looks for a library literally named libiconv.
ln -sf libcompat-musl.so "$OBJ/libiconv.so" 2>/dev/null || \
  cp -f "$OBJ/libcompat-musl.so" "$OBJ/libiconv.so" 2>/dev/null || true

cat > "$JNI/glib289-cross.txt" <<EOF
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

# Only Limbo's own prefixes: the runner's host glib/pcre2 must stay out or
# meson would happily link the build machine's libraries into Android code.
# $INSTALL first: it carries the shared libffi built above. $JNI/pc is still
# needed for pixman (Limbo's own pc file) - keeping the searched set small is
# what stops the runner's host GLib/pcre2 from leaking into the cross build.
export PKG_CONFIG_PATH="$INSTALL/lib/pkgconfig:$JNI/pc"
export PKG_CONFIG_LIBDIR="$INSTALL/lib/pkgconfig:$JNI/pc"

rm -rf "$BUILD"
cd "$SRC"
# bionic has no sys/event.h but does export kqueue()/kevent() stubs, so the
# link probe passes while the header is missing and meson would pick the
# kqueue file monitor. Its inotify backend works on Android (sys/inotify.h and
# inotify_init1 are both available since API 21), and -- unlike the sed patch
# the 2.76 build needed -- this is a supported meson option, so it is stated
# explicitly rather than by editing the tree.
#
# iconv stays optional: bionic has none and the symbols come from
# libcompat-musl.so at runtime.
sed -i "s|^  libiconv = dependency('iconv')|  libiconv = dependency('iconv', required: false)|" meson.build 2>/dev/null || true

meson setup "$BUILD" . \
  --cross-file "$JNI/glib289-cross.txt" \
  --prefix "$INSTALL" \
  --default-library=shared \
  -Dc_args="-I$JNI/compat/musl/include -Wno-unknown-warning-option -Wno-error=implicit-function-declaration" \
  -Dc_link_args="-L$OBJ -lcompat-musl -llog -Wl,-z,undefs" \
  -Dfile_monitor_backend=inotify \
  -Dlibmount=disabled -Dselinux=disabled -Ddtrace=false \
  -Dsystemtap=false -Dgtk_doc=false -Dman=false \
  -Dtests=false -Dinstalled_tests=false \
  -Dlibelf=disabled \
  -Dbsymbolic_functions=false -Dforce_posix_threads=true \
  -Dnls=disabled

ninja -C "$BUILD" -j "$(nproc)"
ninja -C "$BUILD" install

# QEMU dlopens libqemu-system-*.so from the APK's lib dir, and Android's
# linker resolves every DT_NEEDED entry against that flat directory by exact
# file name. GLib 2.89 builds gobject and gio as separate shared libraries
# (the 2.76 build linked both into libglib-2.0.so, which is why nothing had to
# be copied before), so all four have to be staged in obj/local or the engine
# fails to load with no GTK/SDL/VNC screen at all.
# libgobject-2.0.so links the shared libffi we built above, and the loader
# needs that file in the same flat directory, so it is staged with the rest.
GLIB_SOS="libglib-2.0 libgmodule-2.0 libgobject-2.0 libgio-2.0 libffi"
for base in $GLIB_SOS; do
  src=""
  for cand in "$INSTALL/lib/$base.so" "$INSTALL/lib/$base.so."*; do
    [ -f "$cand" ] || continue
    # prefer the real ELF over the linker script / bare symlink
    head -c4 "$cand" 2>/dev/null | grep -q ELF && { src=$cand; break; }
  done
  if [ -z "$src" ]; then
    echo "ERROR: $base.so missing from $INSTALL/lib" >&2
    exit 1
  fi
  # -L dereferences: the candidates are usually SONAME symlinks, and copying a
  # link into obj/local would leave a dangling reference inside the APK.
  cp -fL "$src" "$OBJ/$base.so"
  echo ">> $base.so -> obj/local ($(stat -c%s "$OBJ/$base.so") bytes)"
done

# Rewrite the versioned DT_NEEDED spellings in place (fix_needed.py overwrites
# the strings without relocating .dynstr, which the Huawei linker requires).
# Missing a rewrite means "library not found" at load time, i.e. no display.
NEEDED_MAP="libglib-2.0.so.0=libglib-2.0.so libgmodule-2.0.so.0=libgmodule-2.0.so libgobject-2.0.so.0=libgobject-2.0.so libgio-2.0.so.0=libgio-2.0.so libintl.so.8=libintl.so"
for f in $GLIB_SOS; do
  python3 "$JNI/../../../../scripts/fix_needed.py" "$OBJ/$f.so" $NEEDED_MAP || true
done

# Every other library we ship (GTK, pango, cairo, gdk-pixbuf, harfbuzz, ...)
# carries the same versioned names, so rewrite them too; otherwise the loader
# gets past libqemu and fails on the next dependency instead.
for f in "$OBJ"/*.so; do
  [ -f "$f" ] || continue
  python3 "$JNI/../../../../scripts/fix_needed.py" "$f" $NEEDED_MAP || true
done
echo "[OK] GLib DT_NEEDED rewritten to unversioned names across obj/local"

echo "[OK] glib $GLIB_VERSION built: $INSTALL"
ls -la "$INSTALL"/lib/libglib-2.0.so* 2>/dev/null | head -3 || true
