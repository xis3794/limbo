#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
clear_verneed.py - Clear ELF versioning info from a shared library.

Android's linker requires versioned symbol references (@LIBC etc.) to be
resolvable by the target libc. NDK r23's libc binds glib2.76 to internal
symbols like _rwlock_trywrlock@LIBC that older Android libc does not export.
This script zeros DT_VERNEED/DT_VERNEEDNUM and the .gnu.version section so
all references become unversioned and resolve from any loaded library.

Usage: python3 clear_verneed.py <file.so> [more.so ...]
"""
import struct
import sys

def clear_version(path):
    f = open(path, 'r+b')
    data = bytearray(f.read())
    if len(data) < 0x40:
        f.close()
        print('[ERR] %s too small' % path)
        return False
    is64 = data[4] == 2

    def unpack(fmt, off):
        return struct.unpack_from(fmt, data, off)[0]

    if is64:
        e_phoff = unpack('<Q', 0x20)
        e_phentsize = unpack('<H', 0x36)
        e_phnum = unpack('<H', 0x38)
        e_shoff = unpack('<Q', 0x28)
        e_shentsize = unpack('<H', 0x3A)
        e_shnum = unpack('<H', 0x3C)
    else:
        e_phoff = unpack('<I', 0x1C)
        e_phentsize = unpack('<H', 0x2A)
        e_phnum = unpack('<H', 0x2C)
        e_shoff = unpack('<I', 0x20)
        e_shentsize = unpack('<H', 0x2E)
        e_shnum = unpack('<H', 0x30)

    # locate PT_DYNAMIC (p_type == 2)
    dyn_off = dyn_size = None
    for i in range(e_phnum):
        poff = e_phoff + i * e_phentsize
        if is64:
            p_type = unpack('<I', poff)
            p_offset = unpack('<Q', poff + 8)
            p_filesz = unpack('<Q', poff + 32)
        else:
            p_type = unpack('<I', poff)
            p_offset = unpack('<I', poff + 4)
            p_filesz = unpack('<I', poff + 16)
        if p_type == 2:
            dyn_off = p_offset
            dyn_size = p_filesz
            break
    if dyn_off is None:
        f.close()
        print('[WARN] %s: no PT_DYNAMIC' % path)
        return False

    # zero DT_VERNEED (0x6ffffffe) and DT_VERNEEDNUM (0x6fffffff)
    off = dyn_off
    cleared = 0
    while off < dyn_off + dyn_size:
        if is64:
            d_tag = unpack('<q', off)
        else:
            d_tag = unpack('<i', off)
        if d_tag == 0:
            break
        if d_tag in (0x6ffffffe, 0x6fffffff):
            # turn the whole entry into DT_NULL so the linker stops here
            # (VERNEED/VERNEEDNUM sit at the end of the dynamic table)
            if is64:
                struct.pack_into('<qQ', data, off, 0, 0)
            else:
                struct.pack_into('<iI', data, off, 0, 0)
            cleared += 1
        off += 16 if is64 else 8

    # zero .gnu.version section (SHT_GNU_versym = 0x6fffffff)
    for i in range(e_shnum):
        soff = e_shoff + i * e_shentsize
        if is64:
            sh_type = unpack('<I', soff + 4)
            sh_offset = unpack('<Q', soff + 24)
            sh_size = unpack('<Q', soff + 32)
        else:
            sh_type = unpack('<I', soff + 4)
            sh_offset = unpack('<I', soff + 16)
            sh_size = unpack('<I', soff + 20)
        if sh_type == 0x6fffffff:
            data[sh_offset:sh_offset + sh_size] = b'\x00' * sh_size
            cleared += 1

    f.seek(0)
    f.write(data)
    f.close()
    print('[OK] %s: cleared %d version entries' % (path, cleared))
    return True

if __name__ == '__main__':
    for p in sys.argv[1:]:
        clear_version(p)