#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
fix_qemu11.py - Adapt QEMU 11.1.0 source tree for Limbo/Android build.

Derived from fix_qemu805.py; QEMU11 differences:
  * executable() block format changed (no link_language, no exe.get)
  * zlib dependency pattern changed (no static_kwargs)
  * audio_legacy.c removed
  * configure: glib/pkg-config probes moved to meson, and configure
    passes through -D options directly (no configure patching needed)

Usage: python3 scripts/fix_qemu11.py <jni_root>
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
    with open(os.path.join(QEMU, rel), 'r', encoding='utf-8', errors='replace') as f:
        return f.read()

def write(rel, content):
    with open(os.path.join(QEMU, rel), 'w', encoding='utf-8') as f:
        f.write(content)

def sub(rel, old, new, count=1, required=True):
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

def force_config_false(meson_content, name):
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
               dependencies: arch_deps + exe['dependencies'],
               objects: lib.extract_all_objects(recursive: true),
               link_depends: [block_syms, qemu_syms],
               link_args: link_args,
               win_subsystem: exe['win_subsystem'])"""
new_exe = """    emulator = shared_library(exe_name, exe['sources'],
               name_prefix: '',
               install: true,
               c_args: c_args,
               dependencies: arch_deps + exe['dependencies'],
               objects: lib.extract_all_objects(recursive: true),
               link_depends: [block_syms, qemu_syms],
               link_args: link_args + ['-Wl,-z,undefs', '-Wl,--no-undefined-version',
               '-Wl,--wrap=open', '-Wl,--wrap=fopen', '-Wl,--wrap=close',
               '-Wl,--wrap=stat', '-Wl,--wrap=mkstemp'])"""
if old_exe in meson:
    meson = meson.replace(old_exe, new_exe, 1)
    log('[OK] meson.build: executable -> shared_library')
else:
    log('[FAIL] meson.build: executable block not found')

# Disable tests subdir (unit test executables fail linking on Android)
if "subdir('tests')" in meson:
    meson = meson.replace("subdir('tests')", "# Limbo: disabled tests\n#subdir('tests')", 1)
    log('[OK] meson.build: tests subdir disabled')
else:
    log('[SKIP] meson.build: tests subdir not found')

# zlib: bionic provides libz -> find_library
old_z = "zlib = dependency('zlib', required: true)"
new_z = "zlib = cc.find_library('z', required: true)"
if old_z in meson:
    meson = meson.replace(old_z, new_z, 1)
    log('[OK] meson.build: zlib -> find_library(z)')
else:
    log('[SKIP] meson.build: zlib pattern differs')

# Force-disable Android-incompatible syscalls (same policy as Limbo)
for name in ['CONFIG_SIGNALFD', 'CONFIG_MEMFD', 'CONFIG_GETRANDOM',
             'HAVE_STRCHRNUL', 'HAVE_COPY_FILE_RANGE', 'CONFIG_PREADV']:
    meson, n = force_config_false(meson, name)
    log('[OK] meson.build: %s = false (x%d)' % (name, n) if n else
        '[WARN] meson.build: %s not found' % name)

# bionic already provides struct iovec; QEMU11 sets CONFIG_IOVEC=true by
# default - only patch if a has_type() detection exists (QEMU8 style)
import re as _re
_iovec_pat = _re.compile(r"config_host_data\.set\('CONFIG_IOVEC',\s*cc\.has_type\([^)]+\)\)", _re.S)
meson, _n = _iovec_pat.subn("config_host_data.set('CONFIG_IOVEC', true)", meson)
if _n > 0:
    log('[OK] meson.build: CONFIG_IOVEC = true (bionic provides iovec)')
elif "config_host_data.set('CONFIG_IOVEC', true)" in meson:
    log('[OK] meson.build: CONFIG_IOVEC already true (QEMU11 default)')
else:
    log('[WARN] meson.build: CONFIG_IOVEC pattern not found')
write('meson.build', meson)

# QEMU11 checks cc.sizeof('void *') < 8 - the probe fails on NDK clang
# cross builds because of -Werror + GCC-only warning options, returning -1.
# NDK aarch64/x86_64 targets are always 64-bit: skip the probe.
old_64 = "if (cc.sizeof('void *') < 8) and (have_system or have_user)"
new_64 = "if false # Limbo: NDK aarch64 host is 64-bit; skip sizeof probe"
if old_64 in meson:
    meson = meson.replace(old_64, new_64, 1)
    log('[OK] meson.build: 64-bit sizeof probe skipped')
else:
    log('[SKIP] meson.build: 64-bit probe pattern differs')
write('meson.build', meson)

# QEMU11 compiler version check uses compiler.compiles() which runs with
# -Werror + our GCC-style warning flags; fails on clang -> error. NDK r23
# clang (12.x) satisfies the requirement, so just skip the check.
old_ver = "    error('You either need GCC v10.4 or Clang v10.0 (or XCode Clang v15.0) to compile QEMU')"
new_ver = "    message('Limbo: compiler version check skipped (NDK clang 12.x is fine)')"
if old_ver in meson:
    meson = meson.replace(old_ver, new_ver, 1)
    log('[OK] meson.build: compiler version check skipped')
else:
    log('[SKIP] meson.build: compiler version check pattern differs')
write('meson.build', meson)

# ---------------------------------------------------------------------------
# 2. audio: QEMU11 already defaults to 22050 (QEMU8 needed a patch)
# ---------------------------------------------------------------------------
sub('audio/audio.c',
    '        pdo->frequency = 44100;',
    '        pdo->frequency = 22050;',
    required=False)

# ---------------------------------------------------------------------------
# 3. GUI refresh interval: make configurable at runtime (Limbo UI tweaks)
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
# 4. SDL2 backend: force hardware rendering, NULL-safety, Android tweaks
# ---------------------------------------------------------------------------
sdl2 = read('ui/sdl2.c')
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
# 7b. 9p/virtfs: bionic sys/stat.h defines st_*_nsec as macros that clash
#     with the 9p protocol's own st_atime_nsec etc. members -> undef them.
# ---------------------------------------------------------------------------
_undef = ("/* Limbo: undef bionic stat macros that clash with 9p protocol */\n"
          "#undef st_atime\n#undef st_atime_nsec\n"
          "#undef st_mtime\n#undef st_mtime_nsec\n"
          "#undef st_ctime\n#undef st_ctime_nsec\n")
for _f in ['fsdev/9p-marshal.h', 'fsdev/9p-iov-marshal.c', 'fsdev/9p-marshal.c']:
    try:
        _c = read(_f)
    except FileNotFoundError:
        log('[SKIP] %s absent' % _f)
        continue
    if '#undef st_atime_nsec' not in _c:
        _c = _undef + _c
        write(_f, _c)
        log('[OK] %s: stat macros undef' % _f)
    else:
        log('[SKIP] %s already patched' % _f)

# ---------------------------------------------------------------------------
# 6. Limbo UI hook symbols (5.1-era) kept for vm-executor-jni.c dlsym()
# ---------------------------------------------------------------------------
hook = """/*
 * limbo-hooks.c
 * Limbo UI hook variables (5.1-era) kept for vm-executor-jni.c dlsym().
 * QEMU 11.x does not use these knobs; they exist so the JNI layer can
 * resolve them without errors. UI features degrade gracefully.
 */
int limbo_ignore_breakpoint_invalidate = 0;
int limbo_vga_full_update = 0;
int vnc_refresh_interval_inc = 30;
int vnc_refresh_interval_base = 30;
"""
write('util/limbo-hooks.c', hook)
log('[OK] util/limbo-hooks.c created')

umeson = read('util/meson.build')
old_util = "util_ss.add(files('osdep.c', 'cutils.c', 'unicode.c', 'qemu-timer-common.c'))"
new_util = "util_ss.add(files('osdep.c', 'cutils.c', 'unicode.c', 'qemu-timer-common.c', 'limbo-hooks.c'))"
if old_util in umeson:
    umeson = umeson.replace(old_util, new_util, 1)
    write('util/meson.build', umeson)
    log('[OK] util/meson.build: limbo-hooks.c registered')
else:
    log('[FAIL] util/meson.build: pattern not found')

# ---------------------------------------------------------------------------
print('=' * 60)
print('fix_qemu11.py done. Summary:')
for l in LOGS:
    print('  ' + l)
ok = sum(1 for l in LOGS if l.startswith('[OK]'))
fail = sum(1 for l in LOGS if l.startswith('[FAIL]'))
print('OK=%d FAIL=%d SKIP=%d' % (ok, fail, len(LOGS) - ok - fail))
sys.exit(0 if fail == 0 else 1)