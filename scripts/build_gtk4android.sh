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

# meson resolves a dependency's helper programs (glib_compile_resources,
# glib_mkenums, ...) from the pkg-config variables of that dependency. The
# build-machine gio-2.0 lookup was still answering with the 2.89 prefix because
# PKG_CONFIG_PATH below is process-wide, so meson took the aarch64 tool paths
# straight out of the target .pc file. A wrapper for the native file hides our
# prefixes from build-machine lookups without touching the cross build.
cat > "$JNI/host-pkg-config.sh" <<'WRAPPER'
#!/bin/sh
# Only the build machine's own pkg-config entries; the Limbo prefixes are
# deliberately absent so build-machine dependencies resolve to host packages.
PKG_CONFIG_PATH=/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/lib/pkgconfig:/usr/share/pkgconfig
PKG_CONFIG_LIBDIR=
export PKG_CONFIG_PATH PKG_CONFIG_LIBDIR
exec /usr/bin/pkg-config "$@"
WRAPPER
chmod +x "$JNI/host-pkg-config.sh"

cat > "$JNI/gtk4-native.txt" <<EOF
[binaries]
c = '/usr/bin/cc'
cpp = '/usr/bin/c++'
ar = '/usr/bin/ar'
strip = '/usr/bin/strip'
pkgconfig = '$JNI/host-pkg-config.sh'

[host_machine]
system = 'linux'
cpu_family = 'x86_64'
cpu = 'x86_64'
endian = 'little'
EOF

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

# GTK needs GLib >= 2.84 (glib_req in its meson.build) while QEMU is built
# against Limbo's 2.76.6, so the GTK stack gets its own 2.89.3 prefix built by
# scripts/build_glib289.sh. libffi and pixman stay the QEMU ones - they are
# ABI-stable and GLib 2.89 is built against them as well. libepoxy is our
# standalone EGL-only build, not the wrap, so GPU acceleration does not
# depend on GTK compiling.
#
# Restricting PKG_CONFIG_LIBDIR to those prefixes also keeps the runner's
# host pcre2/zlib/gtk out of the build.
GLIB289_PC="$JNI/glib289/install-android/lib/pkgconfig"
if [ ! -d "$GLIB289_PC" ]; then
  echo "ERROR: GTK needs GLib >= 2.84, but $GLIB289_PC is missing." >&2
  echo "       The Makefile builds it first (config-glib289 / glib289)." >&2
  exit 1
fi
PC_DIRS="$GLIB289_PC:$JNI/libepoxy/install/lib/pkgconfig"
export PKG_CONFIG_PATH="$PC_DIRS"
export PKG_CONFIG_LIBDIR="$PC_DIRS"

# GTK's build needs GLib *helper programs* (glib-compile-resources,
# glib-mkenums, glib-genmarshal). They run on the BUILD machine, so they have to
# be host binaries. Putting the 2.89 prefix on PATH supplied the Android
# aarch64 builds instead and resource compilation died with
#     /bin/sh: 1: .../glib-compile-resources: Exec format error
# A native_file (further down) makes meson resolve build-machine programs
# against the host's own pkg-config, while everything the target links still
# comes from the 2.89 prefix.
if ! command -v glib-compile-resources >/dev/null 2>&1; then
  echo "ERROR: host glib-compile-resources missing (apt-get install libglib2.0-dev-bin)" >&2
  exit 1
fi
echo ">> host glib-compile-resources: $(command -v glib-compile-resources)"

cd "$SRC"
meson setup "$BUILD" . \
  --cross-file "$JNI/gtk4-cross.txt" \
  --native-file "$JNI/gtk4-native.txt" \
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
#
# But NOT over the QEMU-side libraries that already live in obj/local.  This
# glob used to copy everything, and the GTK prefix contains its own
# proxy-libintl (pulled in as a meson wrap), which landed on top of the
# libintl.so that ndk-build compiles from compat/intl/intl_stubs.c.  That stub
# is the only file in the tree that defines the C23 sized-free helpers, and
# libglib-2.0.so has a real global reference to free_sized, so the next APK
# died on load with
#     cannot locate symbol "free_sized" referenced by libglib-2.0.so
# before QEMU ran: SDL, VNC and GTK all dead, nothing in the log.
#
# The list below are the libraries the QEMU side owns.  Stems are compared
# before the first ".so", so a versioned sibling (libintl.so.8) is protected
# along with the plain name.
PROTECTED_STEMS="
libintl
libiconv
libcompat-musl
libcompat-limbo
libcompat-SDL2-addons
libcompat-SDL2-ext
libdiag
liblimbo
libpixman-1
libpcre2-8
libffi
libglib-2.0
libgmodule-2.0
libgobject-2.0
libgio-2.0
"

is_protected() {
  stem=$(basename "$1" | sed 's/\.so.*$//')
  for protected in $PROTECTED_STEMS; do
    [ "$stem" = "$protected" ] && return 0
  done
  return 1
}

count=0
skipped=""
for so in "$INSTALL"/lib/lib*.so*; do
  [ -f "$so" ] || continue
  if is_protected "$so"; then
    skipped="$skipped $(basename "$so")"
    continue
  fi
  cp -Lf "$so" "$OBJ/"
  count=$((count + 1))
done
[ -n "$skipped" ] && echo ">> kept the QEMU-side copies of:$skipped"

# Hard stop if the sized-free stubs are missing again: this is the exact
# defect that shipped a build where no display backend could start, and it is
# invisible until the APK is installed on a device.
STUB_INTL="$OBJ/libintl.so"
if [ ! -f "$STUB_INTL" ]; then
  echo "ERROR: $STUB_INTL is missing - libglib-2.0.so needs free_sized from it" >&2
  exit 1
fi
if ! python3 - "$STUB_INTL" <<'PY'
import sys
data = open(sys.argv[1], "rb").read()
missing = [s for s in (b"free_sized\0", b"free_aligned_sized\0") if s not in data]
if missing:
    print("missing: " + ", ".join(m.decode().strip("\0") for m in missing))
    raise SystemExit(1)
PY
then
  echo "ERROR: $STUB_INTL no longer defines free_sized/free_aligned_sized." >&2
  echo "       libglib-2.0.so references them; dlopen would fail and no" >&2
  echo "       display backend would start. Refusing to continue." >&2
  exit 1
fi
echo "[OK] libintl.so keeps the sized-free stubs (free_sized present)"

if [ ! -f "$INSTALL/lib/pkgconfig/gtk4.pc" ]; then
  echo "ERROR: gtk4.pc was not installed - QEMU's meson will not find GTK" >&2
  exit 1
fi

echo "[OK] GTK4 built: $count shared libraries into $OBJ"
echo "[OK] gtk4.pc at $INSTALL/lib/pkgconfig/gtk4.pc"
