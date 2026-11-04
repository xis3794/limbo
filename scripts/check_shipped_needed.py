#!/usr/bin/env python3
"""Fail if any shipped library needs a library we do not ship.

Why this exists
---------------
Limbo dlopen()s libqemu-system-<guest>.so from the APK's lib directory, and
Android's linker resolves every DT_NEEDED entry against that same flat
directory by exact file name. If a single entry cannot be resolved, loading
the engine fails *before QEMU runs*: the app then shows no screen at all for
every backend (SDL, VNC and GTK alike) and writes nothing to the log.

That is exactly how GLib 2.89 broke a release: it builds gobject and gio as
separate shared objects (2.76 had both linked into libglib-2.0.so), nothing
staged them into obj/local, and libqemu declared NEEDED libgio-2.0.so.0 and
libgobject-2.0.so.0 that were absent from the APK.

Usage: check_shipped_needed.py <obj/local dir>
"""
import glob
import os
import struct
import sys

# Android platform libraries come from the device, never from the APK. The
# NDK's libstdc++.so is a linker alias for the platform's libc++.
SYSTEM_PREFIXES = (
    "libc.so", "libm.so", "libdl.so", "liblog.so", "libz.so", "libandroid.so",
    "libGLESv", "libEGL.so", "libvulkan", "libstdc++.so", "libc++",
    "libjnigraphics", "libnativewindow", "libaaudio.so", "libOpenSLES",
    "libmediandk", "libsync.so",
)


def needed_libraries(path):
    """Return the DT_NEEDED entries of an ELF64 shared object."""
    with open(path, "rb") as handle:
        data = handle.read()
    if data[:4] != b"\x7fELF" or data[4] != 2:
        return []
    (_, _, _, _, phoff, shoff, _, _, phentsize, phnum,
     shentsize, shnum, _) = struct.unpack("<HHIQQQIHHHHHH", data[16:64])

    # Section headers are enough here: .dynamic and its string table are both
    # reachable from them, so the program headers are not needed.
    sections = []
    for index in range(shnum):
        offset = shoff + index * shentsize
        (_, stype, _, _, sh_offset, sh_size,
         sh_link, _, _, _) = struct.unpack("<IIQQQQIIQQ", data[offset:offset + 64])
        sections.append((stype, sh_offset, sh_size, sh_link))

    dynamic = [section for section in sections if section[0] == 6]
    if not dynamic:
        return []
    _, dyn_offset, dyn_size, dyn_link = dynamic[0]
    strtab_offset = sections[dyn_link][1]

    def read_string(string_index):
        start = strtab_offset + string_index
        return data[start:data.index(b"\0", start)].decode("utf-8", "replace")

    entries = []
    offset = dyn_offset
    while offset < dyn_offset + dyn_size:
        tag, value = struct.unpack("<qQ", data[offset:offset + 16])
        offset += 16
        if tag == 0:
            break
        if tag == 1:
            entries.append(read_string(value))
    return entries


def main():
    if len(sys.argv) != 2:
        print("usage: check_shipped_needed.py <obj/local dir>", file=sys.stderr)
        return 2
    obj_dir = sys.argv[1]
    shipped = {os.path.basename(path) for path in glob.glob(os.path.join(obj_dir, "*.so"))}
    if not shipped:
        print(f"::error::no shared libraries under {obj_dir}", file=sys.stderr)
        return 1

    problems = 0
    for path in sorted(glob.glob(os.path.join(obj_dir, "*.so"))):
        base = os.path.basename(path)
        for dependency in needed_libraries(path):
            if dependency in shipped or dependency.startswith(SYSTEM_PREFIXES):
                continue
            print(f"::error::{base} needs {dependency}, which is not among the shipped libraries")
            problems += 1

    if problems:
        print("::error::unresolved DT_NEEDED entries; the engine would fail to load "
              "(no screen for any backend, and no log line)")
        return 1
    print(f"OK: every DT_NEEDED entry resolves against the {len(shipped)} shipped libraries")
    return 0


if __name__ == "__main__":
    sys.exit(main())
