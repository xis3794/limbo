#!/usr/bin/env python3
"""Package a built QEMU version into the format understood by the app.

The archive contains manifest.json and lib/*.so.

QEMU is linked against a set of "private" libraries that are built together with
it (glib/gmodule/pcre2/intl).  Those are shipped inside the package so that a
version installed from GitHub does not depend on the copies that happen to be
bundled in the APK.  Their DT_SONAME and the DT_NEEDED entries that reference
them are normalised to the unversioned file name because Android's linker
resolves DT_NEEDED by soname and the app maps the files from its private data
directory.

Android/APK infrastructure libraries (SDL2, compat-*, liblimbo, libdiag,
pixman) are intentionally NOT packaged: the running APK already provides them
and duplicating them would create two incompatible copies in one process.

Usage:
  package_qemu.py <libs-dir> <version> <out-file>
                  [--series 11] [--abi arm64-v8a]
                  [--qemu-lib libqemu-system-x86_64.so]
                  [--patchelf patchelf]

Prints a JSON object with file/size/sha256 so CI can update the catalog.
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile

# Optional private dependencies, in load order (dependencies first).
DEPENDENCIES = [
    "libpcre2-8.so",
    "libpcre.so",
    "libffi.so",
    "libintl.so",
    "libglib-2.0.so",
    "libgmodule-2.0.so",
    "libgobject-2.0.so",
    "libgthread-2.0.so",
]

# Some builds keep the versioned soname on disk; accept both spellings.
ALTERNATE_NAMES = {
    "libpcre2-8.so": ["libpcre2-8.so.0"],
    "libpcre.so": ["libpcre.so.1"],
    "libffi.so": ["libffi.so.7", "libffi.so.6"],
    "libintl.so": ["libintl.so.8"],
    "libglib-2.0.so": ["libglib-2.0.so.0"],
    "libgmodule-2.0.so": ["libgmodule-2.0.so.0"],
    "libgobject-2.0.so": ["libgobject-2.0.so.0"],
    "libgthread-2.0.so": ["libgthread-2.0.so.0"],
}


def run(patchelf, *args):
    subprocess.check_call([patchelf] + list(args))


def find_lib(libs, name):
    path = os.path.join(libs, name)
    if os.path.isfile(path) and os.path.getsize(path) > 4096:
        return path
    for alt in ALTERNATE_NAMES.get(name, []):
        alt_path = os.path.join(libs, alt)
        if os.path.isfile(alt_path) and os.path.getsize(alt_path) > 4096:
            return alt_path
    return None


def needed(patchelf, path):
    try:
        out = subprocess.check_output([patchelf, "--print-needed", path])
        return out.decode("utf-8", "replace").split()
    except subprocess.CalledProcessError:
        return []


def load_capabilities(version, table_path):
    """Capability block for this build, read from tools/qemu_capabilities.json.

    The app filters the machine-type / CPU pickers with this block so that a
    downloaded build only offers the models it was actually compiled with
    (QEMU 8 has no "pc-q35-11.1", QEMU 11 dropped most old versioned aliases).
    Returns None when the version is not in the table - old packages simply
    keep the static APK lists.
    """
    if not os.path.isfile(table_path):
        return None
    with open(table_path) as f:
        table = json.load(f)
    series = table.get("series", {})
    entry = series.get(version)
    if entry is not None:
        return entry
    # Patch releases share the machine/CPU list of their minor series.
    prefix = version.split(".")[0]
    for key, value in series.items():
        if key == prefix or key.startswith(prefix + "."):
            return value
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("libs")
    ap.add_argument("version")
    ap.add_argument("out")
    ap.add_argument("--series", default=None)
    ap.add_argument("--abi", default="arm64-v8a")
    ap.add_argument("--qemu-lib", default="libqemu-system-x86_64.so")
    ap.add_argument("--patchelf", default="patchelf")
    ap.add_argument("--id", default=None)
    ap.add_argument("--description", default=None)
    ap.add_argument("--capabilities", default=None,
                    help="qemu_capabilities.json (default: next to this script)")
    args = ap.parse_args()

    series = args.series or args.version.split(".")[0]
    version_id = args.id or ("qemu-%s-%s" % (args.version, args.abi))
    pkg = os.path.join("/tmp", "qemu-pkg-" + version_id)
    if os.path.isdir(pkg):
        shutil.rmtree(pkg)
    libdir = os.path.join(pkg, "lib")
    os.makedirs(libdir)

    qemu_src = os.path.join(args.libs, args.qemu_lib)
    if not os.path.isfile(qemu_src):
        print("missing QEMU library: %s" % qemu_src, file=sys.stderr)
        return 1

    copied = []
    for name in DEPENDENCIES:
        src = find_lib(args.libs, name)
        if not src:
            continue
        dst = os.path.join(libdir, name)
        shutil.copy2(src, dst)
        # normalise soname -> file name used by the app
        run(args.patchelf, "--set-soname", name, dst)
        copied.append(name)

    qemu_dst = os.path.join(libdir, args.qemu_lib)
    shutil.copy2(qemu_src, qemu_dst)

    # Make every DT_NEEDED reference use the packaged unversioned names.  Doing
    # this on the qemu library and on the packaged dependencies keeps the whole
    # graph self-consistent.
    packaged = [qemu_dst] + [os.path.join(libdir, n) for n in copied]
    replacements = {}
    for name in copied:
        replacements[name] = [name] + ALTERNATE_NAMES.get(name, [])
    for path in packaged:
        for dep in needed(args.patchelf, path):
            for name, aliases in replacements.items():
                if dep in aliases and dep != name:
                    run(args.patchelf, "--replace-needed", dep, name, path)

    load_order = copied + [args.qemu_lib]
    manifest = {
        "id": version_id,
        "version": args.version,
        "series": series,
        "display_name": "QEMU %s" % args.version,
        "description": args.description
        or "QEMU %s x86_64 emulator core for %s devices" % (args.version, args.abi),
        "abi": args.abi,
        "qemu_lib": args.qemu_lib,
        "load_order": load_order,
    }
    caps_path = args.capabilities or os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "qemu_capabilities.json")
    capabilities = load_capabilities(args.version, caps_path)
    if capabilities:
        manifest["capabilities"] = capabilities
    else:
        print("WARNING: no capabilities entry for QEMU %s in %s; the app will fall "
              "back to its static machine-type / CPU lists." % (args.version, caps_path),
              file=sys.stderr)
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
        "id": version_id,
        "version": args.version,
        "series": series,
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "load_order": load_order,
    }, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
