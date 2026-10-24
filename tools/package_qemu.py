#!/usr/bin/env python3
"""Package a built QEMU version into the format understood by the app.

The archive contains manifest.json and lib/*.so. The DT_SONAME of the QEMU
library and of the private glib/pcre2/intl copies is normalised to the file
name: the app maps these libraries from its private data directory, and the
Android linker resolves DT_NEEDED entries by soname.

Usage:
  package_qemu.py <libs-dir> <version> <out-file> [--series 11] [--patchelf patchelf]
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile

# name on disk -> soname it must advertise so DT_NEEDED lookups hit the mapped copy
SONAMES = {
    "libqemu-system-x86_64.so": "libqemu-system-x86_64.so",
    "libglib-2.0.so": "libglib-2.0.so",
    "libpcre2-8.so": "libpcre2-8.so",
    "libintl.so": "libintl.so",
}

# Load order: dependencies first, QEMU last.
LOAD_ORDER = [
    "libpcre2-8.so",
    "libintl.so",
    "libglib-2.0.so",
    "libqemu-system-x86_64.so",
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("libs")
    ap.add_argument("version")
    ap.add_argument("out")
    ap.add_argument("--series", default=None)
    ap.add_argument("--patchelf", default="patchelf")
    args = ap.parse_args()

    series = args.series or args.version.split(".")[0]
    pkg = os.path.join("/tmp", "qemu-pkg-" + args.version)
    if os.path.isdir(pkg):
        shutil.rmtree(pkg)
    libdir = os.path.join(pkg, "lib")
    os.makedirs(libdir)

    for name in LOAD_ORDER:
        src = os.path.join(args.libs, name)
        if not os.path.isfile(src):
            print("missing", src, file=sys.stderr)
            return 1
        dst = os.path.join(libdir, name)
        shutil.copy2(src, dst)
        soname = SONAMES.get(name)
        if soname:
            subprocess.check_call([args.patchelf, "--set-soname", soname, dst])

    manifest = {
        "id": "qemu-%s-arm64-v8a" % args.version,
        "version": args.version,
        "series": series,
        "display_name": "QEMU %s" % args.version,
        "description": "QEMU %s x86_64 for arm64 devices" % args.version,
        "abi": "arm64-v8a",
        "qemu_lib": "libqemu-system-x86_64.so",
        "load_order": LOAD_ORDER,
    }
    with open(os.path.join(pkg, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)

    with tarfile.open(args.out, "w:xz", format=tarfile.USTAR_FORMAT) as tar:
        for root, _, files in os.walk(pkg):
            for f in files:
                full = os.path.join(root, f)
                tar.add(full, arcname=os.path.relpath(full, pkg))

    data = open(args.out, "rb").read()
    print(json.dumps({
        "file": args.out,
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
