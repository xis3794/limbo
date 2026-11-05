#!/usr/bin/env python3
"""Fail if a shipped library references a symbol nothing in the APK can provide.

Why this exists
---------------
check_shipped_needed.py catches a *library* that is missing from the APK.  This
script catches the next failure mode: the library is there, but a symbol it
needs is not.

That is how a release shipped with SDL, VNC **and** GTK all dead:

  * the GTK4 build stages every shared object from its own install prefix into
    obj/local, and that set included GLib's proxy libintl.so;
  * it landed on top of the libintl.so that ndk-build builds from
    compat/intl/intl_stubs.c, which is the file that carries the C23 sized-free
    stubs (free_sized / free_aligned_sized) and the _rwlock_* aliases;
  * libglib-2.0.so (2.89) has a real, non-weak DT_NEEDED on libintl.so and an
    undefined reference to free_sized, so dlopen() of the engine failed with
    "cannot locate symbol free_sized referenced by libglib-2.0.so".

The app then showed no screen for any backend and wrote nothing to the log,
because the failure happens before QEMU runs.

What counts as resolvable
-------------------------
A symbol is fine when it is defined by

  * any library shipped in the APK, or
  * one of the Android platform libraries, whose exports we read from the NDK
    sysroot stubs (the same files the linker resolves against, so the check
    matches what the device will do).

Undefined WEAK symbols are allowed: the loader binds them to 0 rather than
failing, which is why the sanitizer hooks (__lsan_*) never break anything.

Usage: check_resolvable_symbols.py <obj/local dir> <ndk>

The NDK root is needed to locate the platform stubs; the arch-specific stub
directory is derived from the ABI name of the obj/local directory.
"""
import glob
import os
import struct
import sys

SHN_UNDEF = 0
STB_GLOBAL = 1
STB_WEAK = 2

# Symbols the platform exports but the stub set does not always carry: the
# CRT ones come from crtbegin/crtend in every executable, and a few are
# provided by the dynamic linker itself rather than by a DT_NEEDED library.
ALWAYS_RESOLVED = {
    "__cxa_atexit", "__cxa_finalize", "__cxa_thread_atexit_impl",
    "__register_atfork", "__dso_handle", "__stack_chk_guard",
    "__stack_chk_fail", "__stack_chk_fail_local", "__errno",
    "_Unwind_Resume", "__gmon_start__", "_ITM_deregisterTMCloneTable",
    "_ITM_registerTMCloneTable", "_Jv_RegisterClasses",
}

# Platform libraries a normal Android app may link against. Their NDK stubs
# are parsed for exports; listing them here also covers the few that ship only
# as versioned files.
PLATFORM_LIBS = (
    "libc.so", "libm.so", "libdl.so", "liblog.so", "libz.so", "libandroid.so",
    "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so", "libEGL.so",
    "libvulkan.so", "libjnigraphics.so", "libnativewindow.so",
    "libaaudio.so", "libOpenSLES.so", "libmediandk.so", "libsync.so",
    "libcutils.so", "libstdc++.so", "libc++.so", "libc++_shared.so",
    "libnativehelper.so", "libcamera2ndk.so",
)

ABI_TO_TRIPLE = {
    "arm64-v8a": "aarch64-linux-android",
    "armeabi-v7a": "arm-linux-androideabi",
    "x86": "i686-linux-android",
    "x86_64": "x86_64-linux-android",
}


def read_elf(path):
    """Return (defined_symbols, undefined_global_symbols) of a shared object."""
    with open(path, "rb") as handle:
        data = handle.read()
    if data[:4] != b"\x7fELF" or data[4] != 2:
        return set(), set()

    (_, _, _, _, _phoff, shoff, _, _, _phentsize, _phnum,
     shentsize, shnum, _shstrndx) = struct.unpack("<HHIQQQIHHHHHH", data[16:64])

    sections = []
    for index in range(shnum):
        offset = shoff + index * shentsize
        (_, stype, _, _, sh_offset, sh_size,
         sh_link, _, _, sh_entsize) = struct.unpack(
            "<IIQQQQIIQQ", data[offset:offset + 64])
        sections.append((stype, sh_offset, sh_size, sh_link, sh_entsize))

    # SHT_DYNSYM == 11. Its sh_link points at the string table to use.
    dynsym = next((s for s in sections if s[0] == 11), None)
    if dynsym is None:
        return set(), set()
    _, sym_offset, sym_size, str_index, _ = dynsym
    str_offset = sections[str_index][1]

    defined = set()
    undefined = set()
    for index in range(sym_size // 24):
        offset = sym_offset + index * 24
        name_index, info, _other, shndx = struct.unpack(
            "<IBBH", data[offset:offset + 8])
        if name_index == 0:
            continue
        start = str_offset + name_index
        name = data[start:data.index(b"\0", start)].decode("utf-8", "replace")
        binding = info >> 4
        if binding == STB_WEAK:
            # Weak undefined binds to 0 instead of aborting the load.
            if shndx != SHN_UNDEF:
                defined.add(name)
            continue
        if binding != STB_GLOBAL:
            if shndx != SHN_UNDEF:
                defined.add(name)
            continue
        if shndx == SHN_UNDEF:
            undefined.add(name)
        else:
            defined.add(name)
    return defined, undefined


def platform_symbols(ndk, abi):
    """Collect the exports of every platform library stub for this ABI."""
    triple = ABI_TO_TRIPLE.get(abi)
    if not triple:
        return set()
    sysroot = os.path.join(ndk, "toolchains", "llvm", "prebuilt",
                           "linux-x86_64", "sysroot")
    symbols = set()
    searched = []
    # Union of every API level the NDK ships. This gate answers "does the
    # Android platform export this symbol at all?", not "is it new enough" -
    # API-level gating is a separate concern (runtime guards cover it, e.g.
    # AAudio is API 26 but only used behind a version check). Restricting to
    # the minSdkLevel stubs would flag those legitimate references.
    for api in (21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37):
        directory = os.path.join(sysroot, "usr", "lib", triple, str(api))
        if not os.path.isdir(directory):
            continue
        searched.append(directory)
        for name in PLATFORM_LIBS:
            path = os.path.join(directory, name)
            if os.path.isfile(path):
                defined, _ = read_elf(path)
                symbols |= defined
    if not symbols:
        print("WARNING: no NDK platform stubs found (%s); "
              "falling back to the always-resolved set" %
              ", ".join(searched) if searched else "sysroot missing")
    return symbols


def main():
    if len(sys.argv) < 3:
        print("usage: check_resolvable_symbols.py <obj/local dir> <ndk>",
              file=sys.stderr)
        return 2
    objdir, ndk = sys.argv[1], sys.argv[2]
    abi = os.path.basename(os.path.abspath(objdir))

    libraries = sorted(glob.glob(os.path.join(objdir, "*.so")))
    if not libraries:
        print("ERROR: no libraries in %s" % objdir, file=sys.stderr)
        return 2

    provided = set(ALWAYS_RESOLVED)
    definitions = {}
    for path in libraries:
        defined, _ = read_elf(path)
        definitions[os.path.basename(path)] = defined
        provided |= defined
    provided |= platform_symbols(ndk, abi)

    failures = 0
    for path in libraries:
        name = os.path.basename(path)
        _, undefined = read_elf(path)
        missing = sorted(undefined - provided)
        if not missing:
            continue
        failures += 1
        print("ERROR: %s references %d symbol(s) nothing provides:"
              % (name, len(missing)), file=sys.stderr)
        for symbol in missing[:20]:
            print("         %s" % symbol, file=sys.stderr)
        if len(missing) > 20:
            print("         ... and %d more" % (len(missing) - 20),
                  file=sys.stderr)

    if failures:
        print("", file=sys.stderr)
        print("A shipped library references a symbol that no shipped or "
              "platform library defines.", file=sys.stderr)
        print("dlopen() will fail with 'cannot locate symbol ...' and the app "
              "will show no screen.", file=sys.stderr)
        return 1

    print("OK: every undefined symbol of the %d shipped libraries resolves "
          "against the APK or the platform" % len(libraries))
    return 0


if __name__ == "__main__":
    sys.exit(main())
