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

    # zero DT_VERNEED (0x6ffffffe), DT_VERNEEDNUM (0x6fffffff) and
    # DT_VERSYM (0x6ffffff0) - turn each whole entry into DT_NULL.
    # All three live at the END of the dynamic table (right before the
    # terminator NULL) in GNU toolchain output, so zeroing them cannot
    # truncate earlier entries (SYMTAB/STRTAB/NEEDED etc. stay intact).
    # Result: the library has NO version table at all (classic .so style),
    # which every linker - including vendor/Huawei ones - handles fine.
    off = dyn_off
    cleared = 0
    while off < dyn_off + dyn_size:
        if is64:
            d_tag = unpack('<q', off)
        else:
            d_tag = unpack('<i', off)
        if d_tag == 0:
            break
        if d_tag in (0x6ffffff0, 0x6ffffffe, 0x6fffffff):
            # VERSYM / VERNEED / VERNEEDNUM -> DT_NULL
            if is64:
                struct.pack_into('<qQ', data, off, 0, 0)
            else:
                struct.pack_into('<iI', data, off, 0, 0)
            cleared += 1
        off += 16 if is64 else 8

    # zero .gnu.version entries ONLY for UNDEFINED symbols (imports).
    # Exported symbols keep their version definition indexes (DT_VERDEF stays).
    # SHT_DYNSYM=11, SHT_GNU_versym=0x6fffffff
    dynsym_off = dynsym_entsize = dynsym_count = None
    for i in range(e_shnum):
        soff = e_shoff + i * e_shentsize
        if is64:
            sh_type = unpack('<I', soff + 4)
            sh_offset = unpack('<Q', soff + 24)
            sh_size = unpack('<Q', soff + 32)
            sh_link = unpack('<I', soff + 40)
        else:
            sh_type = unpack('<I', soff + 4)
            sh_offset = unpack('<I', soff + 16)
            sh_size = unpack('<I', soff + 20)
            sh_link = unpack('<I', soff + 24)
        if sh_type == 11:  # SHT_DYNSYM
            dynsym_off = sh_offset
            dynsym_entsize = unpack('<Q', soff + 56) if is64 else unpack('<I', soff + 44)
            dynsym_count = sh_size // dynsym_entsize
        if sh_type == 0x6fffffff:  # SHT_GNU_versym
            versym_off = sh_offset
            versym_size = sh_size

    if dynsym_off is not None and versym_off is not None:
        cleared_versym = 0
        # start at i=1: symbol 0 is the reserved null symbol and must keep
        # versym=0 (VER_NDX_LOCAL)
        for i in range(1, dynsym_count):
            eoff = dynsym_off + i * dynsym_entsize
            st_shndx = unpack('<H', eoff + 6) if is64 else unpack('<H', eoff + 6)
            if st_shndx == 0:  # SHN_UNDEF -> imported symbol
                vs_off = versym_off + i * 2
                # IMPORTANT: use VER_NDX_GLOBAL (1), NOT VER_NDX_LOCAL (0).
                # Some vendor (Huawei) linkers treat versym=0 as a LOCAL
                # symbol and skip the lookup -> "cannot locate symbol".
                # versym=1 means "unversioned global reference" -> the
                # linker resolves it by name in the global namespace.
                struct.pack_into('<H', data, vs_off, 1)
                cleared_versym += 1
        cleared += cleared_versym

    f.seek(0)
    f.write(data)
    f.close()
    print('[OK] %s: cleared %d version entries' % (path, cleared))
    return True

if __name__ == '__main__':
    for p in sys.argv[1:]:
        clear_version(p)