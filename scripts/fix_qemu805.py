#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
fix_qemu805.py - Adapt QEMU 8.0.5 source tree for Limbo/Android build.

Run inside the jni directory AFTER extracting qemu-8.0.5 as ./qemu
and BEFORE running `make limbo`.

Usage: python3 scripts/fix_qemu805.py <jni_root>
"""
import os
import re
import sys

JNI = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else '.')
QEMU = os.path.join(JNI, 'qemu')
LOGS = []

def log(msg):
    LOGS.append(msg)
    print(msg)

def read(rel):
    p = os.path.join(QEMU, rel)
    with open(p, 'r', encoding='utf-8', errors='replace') as f:
        return f.read()

def write(rel, content):
    p = os.path.join(QEMU, rel)
    with open(p, 'w', encoding='utf-8') as f:
        f.write(content)

def sub(rel, old, new, count=1, required=True):
    """Simple string replacement in a QEMU source file."""
    content = read(rel)
    if old not in content:
        if required:
            log('[FAIL] pattern not found in %s' % rel)
            return False
        log('[SKIP] pattern absent in %s' % rel)
        return False
    content = content.replace(old, new, count)
    write(rel, content)
    log('[OK] %s' % rel)
    return True

def regex_sub(rel, pattern, new, required=True):
    """Regex replacement (multiline) in a QEMU source file."""
    content = read(rel)
    new_content, n = re.subn(pattern, new, content, flags=re.S)
    if n == 0:
        if required:
            log('[FAIL] regex not found in %s: %s' % (rel, pattern[:60]))
            return False
        log('[SKIP] regex absent in %s' % rel)
        return False
    write(rel, new_content)
    log('[OK] %s (x%d)' % (rel, n))
    return True

def force_config_false(meson_content, name):
    """Force a config_host_data.set('NAME', ...) to false (multi-line safe)."""
    pat = re.compile(r"config_host_data\.set\('" + name + r"',.*?\)\)", re.S)
    new, n = pat.subn("config_host_data.set('" + name + "', false)", meson_content)
    return new, n

# ---------------------------------------------------------------------------
# 1. meson.build: link emulators as shared libraries (so Android can dlopen)
# ---------------------------------------------------------------------------
meson = read('meson.build')
old_exe = """    emulator = executable(exe_name, exe['sources'],
               install: true,
               c_args: c_args,
               dependencies: arch_deps + deps + exe['dependencies'],
               objects: lib.extract_all_objects(recursive: true),
               link_language: link_language,
               link_depends: [block_syms, qemu_syms] + exe.get('link_depends', []),
               link_args: link_args,
               win_subsystem: exe['win_subsystem'])"""
new_exe = """    emulator = shared_library(exe_name, exe['sources'],
               name_prefix: '',
               install: true,
               c_args: c_args,
               dependencies: arch_deps + deps + exe['dependencies'],
               objects: lib.extract_all_objects(recursive: true),
               link_language: link_language,
               link_depends: [block_syms, qemu_syms] + exe.get('link_depends', []),
               link_args: link_args + ['-Wl,-z,undefs', '-Wl,--no-undefined-version',
               '-Wl,--wrap=open', '-Wl,--wrap=fopen', '-Wl,--wrap=close',
               '-Wl,--wrap=stat', '-Wl,--wrap=mkstemp'])"""
if old_exe in meson:
    meson = meson.replace(old_exe, new_exe, 1)
    log('[OK] meson.build: executable -> shared_library')
else:
    log('[FAIL] meson.build: executable block not found')

# Disable tests build: unit tests are executables that fail linking on Android
# (major/minor macros, getifaddrs etc. not available in bionic API 21)
if "subdir('tests')" in meson:
    meson = meson.replace("subdir('tests')", "# Limbo: disabled tests\n#subdir('tests')", 1)
    log('[OK] meson.build: tests subdir disabled')
else:
    log('[SKIP] meson.build: tests subdir not found')

# zlib: bionic provides libz, no pkg-config .pc needed -> use find_library
old_z = "zlib = dependency('zlib', required: true, kwargs: static_kwargs)"
new_z = "zlib = cc.find_library('z', required: true)"
if old_z in meson:
    meson = meson.replace(old_z, new_z, 1)
    log('[OK] meson.build: zlib -> find_library(z)')
else:
    log('[SKIP] meson.build: zlib pattern differs')

# Force-disable Android-incompatible syscalls (same policy as Limbo 5.1 patch)
# Also force CONFIG_IOVEC so QEMU doesn't redefine struct iovec (bionic has it)
for name in ['CONFIG_SIGNALFD', 'CONFIG_MEMFD', 'CONFIG_GETRANDOM',
             'HAVE_STRCHRNUL', 'HAVE_COPY_FILE_RANGE', 'CONFIG_PREADV']:
    meson, n = force_config_false(meson, name)
    log('[OK] meson.build: %s = false (x%d)' % (name, n) if n else
        '[WARN] meson.build: %s not found' % name)

# bionic already provides struct iovec; tell QEMU not to redefine it
# Pattern: config_host_data.set('CONFIG_IOVEC',\n  cc.has_type('struct iovec', ...))
import re as _re
_iovec_pat = _re.compile(r"config_host_data\.set\('CONFIG_IOVEC',\s*cc\.has_type\([^)]+\)\)", _re.S)
meson, _n = _iovec_pat.subn("config_host_data.set('CONFIG_IOVEC', true)", meson)
if _n > 0:
    log('[OK] meson.build: CONFIG_IOVEC = true (bionic provides iovec)')
else:
    log('[WARN] meson.build: CONFIG_IOVEC pattern not found')
write('meson.build', meson)

# FDT: QEMU 8.x requires libfdt for most softmmu targets including x86_64.
# The release tarball does NOT include the dtc submodule, so we download it
# separately in the CI workflow. Meson will detect it as "internal" if the
# dtc/libfdt directory exists. No meson.build patch needed for this — just
# ensure the dtc/ directory is present before configure runs.
# (See workflow step "Download QEMU and dependency sources" for dtc download.)

# ---------------------------------------------------------------------------
# 2. configure: tolerate missing pkg-config detection of our hand-built libs
# ---------------------------------------------------------------------------
conf = read('configure')
# pkg-config probe: cross_prefix (aarch64-linux-android-) has no pkg-config in NDK
old_pkg_err = """if ! has "$pkg_config_exe"; then
  error_exit "pkg-config binary '$pkg_config_exe' not found"
fi"""
new_pkg_err = """if ! has "$pkg_config_exe"; then
  echo "Limbo: ignoring missing pkg-config binary '$pkg_config_exe'"
fi"""
if old_pkg_err in conf:
    conf = conf.replace(old_pkg_err, new_pkg_err, 1)
    log('[OK] configure: pkg-config binary check bypassed')
else:
    log('[SKIP] configure: pkg-config binary check pattern differs')
# glib probe: if pkg-config fails, do not hard-fail; QEMU will get flags via
# --extra-cflags/--extra-ldflags and the generated .pc files.
old_glib_err = """        error_exit "glib-$glib_req_ver $i is required to compile QEMU"
"""
new_glib_err = """        echo "Limbo: glib probe bypassed for $i"
        glib_cflags="$glib_cflags"
        glib_libs="$glib_libs"
"""
if old_glib_err in conf:
    conf = conf.replace(old_glib_err, new_glib_err, 1)
    log('[OK] configure: glib error_exit bypassed')
else:
    log('[SKIP] configure: glib error_exit pattern differs')
# sizeof(size_t) compile test: skip if we are cross compiling for Android
old_sizeof = """if ! compile_prog "$glib_cflags" "$glib_libs" ; then
    error_exit "sizeof(size_t) doesn't match GLIB_SIZEOF_SIZE_T."\\
               "You probably need to set PKG_CONFIG_LIBDIR"\\
	       "to point to the right pkg-config files for your"\\
	       "build target"
fi"""
new_sizeof = """# Limbo: skip sizeof(size_t) glib test on Android cross builds
# if ! compile_prog "$glib_cflags" "$glib_libs" ; then
#     error_exit "sizeof(size_t) doesn't match GLIB_SIZEOF_SIZE_T."\\
#                "You probably need to set PKG_CONFIG_LIBDIR"\\
# 	       "to point to the right pkg-config files for your"\\
# 	       "build target"
# fi"""
if old_sizeof in conf:
    conf = conf.replace(old_sizeof, new_sizeof, 1)
    log('[OK] configure: sizeof(size_t) test skipped')
else:
    log('[SKIP] configure: sizeof(size_t) pattern differs')

# Force PIC for all static libraries: needed to link qemu-system-*.so
# (meson otherwise builds non-PIC static libs for executables)
old_pic = 'test "$werror" = yes && meson_option_add -Dwerror=true'
new_pic = 'test "$werror" = yes && meson_option_add -Dwerror=true\n  meson_option_add -Db_staticpic=true'
if old_pic in conf:
    conf = conf.replace(old_pic, new_pic, 1)
    log('[OK] configure: forced -Db_staticpic=true')
else:
    log('[SKIP] configure: werror/meson_option pattern differs')
write('configure', conf)

# ---------------------------------------------------------------------------
# 3. audio: default sample rate 22050 (Android audio pipeline friendly)
# ---------------------------------------------------------------------------
sub('audio/audio.c',
    '        pdo->frequency = 44100;',
    '        pdo->frequency = 22050;')

alc = read('audio/audio_legacy.c')
if '44100' in alc:
    alc = alc.replace('44100', '22050')
    write('audio/audio_legacy.c', alc)
    log('[OK] audio/audio_legacy.c: 44100 -> 22050')
else:
    log('[SKIP] audio/audio_legacy.c: no 44100')

# ---------------------------------------------------------------------------
# 4. GUI refresh interval: make configurable at runtime (Limbo UI tweaks)
# ---------------------------------------------------------------------------
sub('include/ui/console.h',
    """/* in ms */
#define GUI_REFRESH_INTERVAL_DEFAULT    30
#define GUI_REFRESH_INTERVAL_IDLE     3000""",
    """/* in ms */
#ifdef __LIMBO__
extern int gui_refresh_interval_default;
extern int gui_refresh_interval_idle;
#define GUI_REFRESH_INTERVAL_DEFAULT gui_refresh_interval_default
#define GUI_REFRESH_INTERVAL_IDLE gui_refresh_interval_idle
#else
#define GUI_REFRESH_INTERVAL_DEFAULT    30
#define GUI_REFRESH_INTERVAL_IDLE     3000
#endif //__LIMBO__""")

ccon = read('ui/console.c')
if 'gui_refresh_interval_default' not in ccon:
    anchor = '#include "qemu/osdep.h"'
    if anchor in ccon:
        ccon = ccon.replace(anchor, anchor + """

#ifdef __LIMBO__
int gui_refresh_interval_default = 30;
int gui_refresh_interval_idle = 300;
#endif //__LIMBO__""", 1)
        write('ui/console.c', ccon)
        log('[OK] ui/console.c: refresh interval vars defined')
    else:
        log('[SKIP] ui/console.c: anchor not found')
else:
    log('[SKIP] ui/console.c: already patched')

# ---------------------------------------------------------------------------
# 5. SDL2 backend: force hardware rendering, NULL-safety, Android tweaks
# ---------------------------------------------------------------------------
sdl2 = read('ui/sdl2.c')
# CreateRenderer: force accelerated renderer on Android (Limbo config flag)
old_renderer = "scon->real_renderer = SDL_CreateRenderer(scon->real_window, -1, 0);"
new_renderer = """
#if defined(__LIMBO_SDL_FORCE_SOFTWARE_RENDERING__)
    scon->real_renderer = SDL_CreateRenderer(scon->real_window, -1, SDL_RENDERER_SOFTWARE);
#elif defined(__LIMBO_SDL_FORCE_HARDWARE_RENDERING__)
    scon->real_renderer = SDL_CreateRenderer(scon->real_window, -1, SDL_RENDERER_ACCELERATED);
#else
    scon->real_renderer = SDL_CreateRenderer(scon->real_window, -1, 0);
#endif"""
if old_renderer in sdl2:
    sdl2 = sdl2.replace(old_renderer, new_renderer, 1)
    log('[OK] ui/sdl2.c: renderer flags')
else:
    log('[SKIP] ui/sdl2.c: renderer pattern differs')
# NULL safety for scon in mouse events (5.1 patch carried over)
if 'scon==NULL' not in sdl2:
    old_mouse = """    if (prev_state != state) {"""
    new_mouse = """#ifdef __LIMBO__
    if(scon==NULL)
        return;
#endif
    if (prev_state != state) {"""
    if old_mouse in sdl2:
        sdl2 = sdl2.replace(old_mouse, new_mouse, 1)
        log('[OK] ui/sdl2.c: scon NULL guard')
    else:
        log('[SKIP] ui/sdl2.c: mouse handler pattern differs')
# PollEvent NULL guard
if 'if(!ev)' not in sdl2:
    old_poll = """    while (SDL_PollEvent(ev)) {"""
    new_poll = """    while (SDL_PollEvent(ev)) {
#ifdef __LIMBO__
        if(!ev)
            continue;
#endif"""
    if old_poll in sdl2:
        sdl2 = sdl2.replace(old_poll, new_poll, 1)
        log('[OK] ui/sdl2.c: PollEvent NULL guard')
    else:
        log('[SKIP] ui/sdl2.c: PollEvent pattern differs')
# Skip SDL_VIDEODRIVER=x11 env on Android
if 'limbo_sdl_scale_hint' not in sdl2:
    old_video = """    g_setenv("SDL_VIDEODRIVER", "x11", 0);"""
    new_video = """#ifndef __ANDROID__
    g_setenv("SDL_VIDEODRIVER", "x11", 0);
#else
    if(limbo_sdl_scale_hint == 1) {
        SDL_bool res = SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY, "1");
        LOGI("Setting SDL_HINT_RENDER_SCALE_QUALITY to %d, code = %d", limbo_sdl_scale_hint, res);
    }
#endif"""
    if old_video in sdl2:
        sdl2 = sdl2.replace(old_video, new_video, 1)
        log('[OK] ui/sdl2.c: Android SDL hint')
    else:
        log('[SKIP] ui/sdl2.c: SDL_VIDEODRIVER pattern differs')
    # define limbo_sdl_scale_hint
    anchor = "static void sdl2_display_init(DisplayState *ds, DisplayOptions *o)"
    if anchor in sdl2:
        sdl2 = sdl2.replace(anchor, """
#ifdef __LIMBO__
int limbo_sdl_scale_hint = -1;
#endif

""" + anchor, 1)
        log('[OK] ui/sdl2.c: limbo_sdl_scale_hint defined')
    else:
        log('[SKIP] ui/sdl2.c: display_init anchor not found')
write('ui/sdl2.c', sdl2)

# ---------------------------------------------------------------------------
# 6. VNC: configurable refresh rate (Limbo perf tweak)
# ---------------------------------------------------------------------------
vnc = read('ui/vnc.c')
if 'vnc_refresh_interval_base' not in vnc:
    old_vnc = """#define VNC_REFRESH_INTERVAL_BASE GUI_REFRESH_INTERVAL_DEFAULT
#define VNC_REFRESH_INTERVAL_INC  50"""
    new_vnc = """#ifdef __LIMBO__
int vnc_refresh_interval_base = 30;
#define VNC_REFRESH_INTERVAL_BASE vnc_refresh_interval_base

int vnc_refresh_interval_inc = 30;
#define VNC_REFRESH_INTERVAL_INC vnc_refresh_interval_inc
#else
#define VNC_REFRESH_INTERVAL_BASE GUI_REFRESH_INTERVAL_DEFAULT
#define VNC_REFRESH_INTERVAL_INC  50
#endif //__LIMBO__"""
    if old_vnc in vnc:
        vnc = vnc.replace(old_vnc, new_vnc, 1)
        log('[OK] ui/vnc.c: refresh interval')
    else:
        log('[SKIP] ui/vnc.c: refresh interval pattern differs')
    write('ui/vnc.c', vnc)
else:
    log('[SKIP] ui/vnc.c: already patched')

# ---------------------------------------------------------------------------
# 7. osdep.h: Android needs linux/mman.h for some MAP_* macros
# ---------------------------------------------------------------------------
osdep = read('include/qemu/osdep.h')
if '#include "qemu/compiler.h"' in osdep and '__ANDROID__' not in osdep.split('qemu/compiler.h')[1][:200]:
    osdep = osdep.replace('#include "qemu/compiler.h"',
                          '#include "qemu/compiler.h"\n\n#ifdef __ANDROID__\n#include <linux/mman.h>\n#endif', 1)
    write('include/qemu/osdep.h', osdep)
    log('[OK] include/qemu/osdep.h: linux/mman.h for Android')
else:
    log('[SKIP] include/qemu/osdep.h: already patched or anchor differs')

# ---------------------------------------------------------------------------
print('=' * 60)
print('fix_qemu805.py done. Summary:')
for l in LOGS:
    print('  ' + l)
ok = sum(1 for l in LOGS if l.startswith('[OK]'))
fail = sum(1 for l in LOGS if l.startswith('[FAIL]'))
print('OK=%d FAIL=%d SKIP=%d' % (ok, fail, len(LOGS) - ok - fail))
sys.exit(0 if fail == 0 else 1)
