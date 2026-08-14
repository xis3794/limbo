#!/bin/bash
# gen_pc_files.sh - Generate pkg-config .pc files pointing at Limbo's
# hand-built libraries so QEMU 8.x (meson) can find them.
#
# Usage: gen_pc_files.sh <jni_root> [app_abi]
#   jni_root: limbo-android-lib/src/main/jni (absolute or relative)
#   app_abi : arm64-v8a (default) | armeabi-v7a | x86 | x86_64
set -e

JNI=$(cd "$1" && pwd)
ABI=${2:-arm64-v8a}
OBJ="$JNI/../obj/local/$ABI"
PC="$JNI/pc"
mkdir -p "$PC"

cat > "$PC/glib-2.0.pc" <<EOF
prefix=$JNI/glib
exec_prefix=\${prefix}
libdir=$OBJ
includedir=\${prefix}

Name: glib-2.0
Description: GLib (Limbo self-built)
Version: 2.56.1
Libs: -L\${libdir} -lglib-2.0 -L$OBJ -lcompat-musl -llog -lcompat-limbo -lcompat-iconv
Cflags: -I\${includedir} -I\${includedir}/glib -I\${includedir}/glib/glib -I\${includedir}/gmodule -I\${includedir}/io -I\${includedir}/android -I$JNI/compat -I$JNI/compat/musl -I$JNI/compat/musl/include
EOF

cat > "$PC/gthread-2.0.pc" <<EOF
prefix=$JNI/glib
exec_prefix=\${prefix}
libdir=$OBJ
includedir=\${prefix}

Name: gthread-2.0
Description: Thread support for GLib (Limbo self-built)
Version: 2.56.1
Libs: -L\${libdir} -lglib-2.0 -L$OBJ -lcompat-musl -llog -lcompat-limbo
Cflags: -I\${includedir} -I\${includedir}/glib -I\${includedir}/glib/glib -I\${includedir}/gmodule -I$JNI/compat -I$JNI/compat/musl -I$JNI/compat/musl/include
EOF

cat > "$PC/pixman-1.pc" <<EOF
prefix=$JNI/pixman
exec_prefix=\${prefix}
libdir=$OBJ
includedir=\${prefix}

Name: pixman-1
Description: pixel manipulation library (Limbo self-built)
Version: 0.40.0
Libs: -L\${libdir} -lpixman-1
Cflags: -I\${includedir} -I\${includedir}/pixman
EOF

cat > "$PC/sdl2.pc" <<EOF
prefix=$JNI/SDL2
exec_prefix=\${prefix}
libdir=$OBJ
includedir=\${prefix}/include

Name: sdl2
Description: Simple DirectMedia Layer 2 (Limbo ndk-build)
Version: 2.0.8
Libs: -L\${libdir} -lSDL2
Libs.private: -ldl -llog
Cflags: -I\${includedir} -D_REENTRANT
EOF

cat > "$PC/slirp.pc" <<EOF
prefix=$JNI/libslirp
exec_prefix=\${prefix}
libdir=$OBJ
includedir=\${prefix}/src
buildincludedir=\${prefix}/build

Name: slirp
Description: libslirp (Limbo CI-built)
Version: 4.7.0
Libs: -L\${libdir} -lslirp
Cflags: -I\${includedir} -I\${buildincludedir}
EOF

echo "[OK] pkg-config files generated in $PC:"
ls -la "$PC"