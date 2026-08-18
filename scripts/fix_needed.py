#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
fix_needed.py - in-place DT_NEEDED string replacement.

patchelf --replace-needed rewrites/reallocates .dynstr, moving it to the end
of the file. Huawei/HarmonyOS linkers read section data using the FILE
offset while the .so is mapped by virtual address; once sh_addr != sh_offset
the dynstr/verneed parsing breaks with garbled version/symbol names.

All our replacements shorten the string (e.g. libintl.so.8 -> libintl.so),
so we can overwrite in place inside .dynstr without moving anything.

Usage: fix_needed.py <file.so> old1=new1 old2=new2 ...
"""
import struct
import sys


def find_dynstr(data, is64):
    e_shoff = struct.unpack_from('<Q', data, 0x28)[0] if is64 else struct.unpack_from('<I', data, 0x20)[0]
    e_shentsize = struct.unpack_from('<H', data, 0x3A)[0] if is64 else struct.unpack_from('<H', data, 0x2E)[0]
    e_shnum = struct.unpack_from('<H', data, 0x3C)[0] if is64 else struct.unpack_from('<H', data, 0x30)[0]
    for i in range(e_shnum):
        soff = e_shoff + i * e_shentsize
        sh_name = struct.unpack_from('<I', data, soff)[0]
        sh_type = struct.unpack_from('<I', data, soff + 4)[0]
        if sh_type == 3:  # SHT_STRTAB
            sh_offset = struct.unpack_from('<Q', data, soff + 24)[0] if is64 else struct.unpack_from('<I', data, soff + 16)[0]
            sh_size = struct.unpack_from('<Q', data, soff + 32)[0] if is64 else struct.unpack_from('<I', data, soff + 20)[0]
            # check name (only .dynstr is what we want; sh_name indexes .shstrtab)
            # .dynstr is the FIRST STRTAB that is NOT .shstrtab in practice;
            # verify by reading the string name if possible
            if sh_name != 0:
                return sh_offset, sh_size
    return None, None


def main():
    if len(sys.argv) < 3:
        print('usage: fix_needed.py <file.so> old=new [old2=new2 ...]')
        sys.exit(1)
    path = sys.argv[1]
    pairs = []
    for a in sys.argv[2:]:
        if '=' in a:
            old, new = a.split('=', 1)
            pairs.append((old.encode(), new.encode()))
    f = open(path, 'rb')
    data = bytearray(f.read())
    f.close()
    if len(data) < 0x40:
        print('[ERR] too small')
        sys.exit(1)
    is64 = data[4] == 2
    dynstr_off, dynstr_size = find_dynstr(data, is64)
    if dynstr_off is None:
        print('[ERR] .dynstr not found')
        sys.exit(1)
    total = 0
    for old, new in pairs:
        if len(new) > len(old):
            print('[WARN] %s -> %s longer, skip' % (old, new))
            continue
        start = dynstr_off
        end = dynstr_off + dynstr_size
        idx = data.find(old, start, end)
        while idx != -1:
            data[idx:idx + len(old)] = new + b'\x00' * (len(old) - len(new))
            total += 1
            idx = data.find(old, idx + len(old), end)
    f = open(path, 'wb')
    f.write(data)
    f.close()
    print('[OK] %s: replaced %d NEEDED strings in place (dynstr NOT moved)' % (path, total))


if __name__ == '__main__':
    main()
