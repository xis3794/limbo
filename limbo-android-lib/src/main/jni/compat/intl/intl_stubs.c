/*
 * intl_stubs.c - standalone libintl.so for the QEMU 11 / glib 2.76 stack.
 *
 * glib 2.76 needs free_sized/free_aligned_sized (C23 sized-free) and
 * close_range, which older bionic variants do not export. These stubs are
 * compiled into their OWN library (libintl.so) which is on glib's DT_NEEDED
 * chain, so glib resolves them at load time.
 *
 * libcompat-musl.so stays byte-for-byte identical to the working QEMU 8.0.5
 * build (no extra symbols) - adding anything to it changed its verneed and
 * broke dlopen on Huawei/HarmonyOS linkers.
 */
#include <errno.h>
#include <stddef.h>
#include <stdlib.h>

/* close_range: only in bionic API 30+; some Huawei/HarmonyOS lack it */
int close_range(unsigned int first, unsigned int last, int flags)
{
    (void)first;
    (void)last;
    (void)flags;
    errno = ENOSYS;
    return -1;
}

/* C23 sized-free helpers (bionic lacks free_sized/free_aligned_sized) */
void free_sized(void *ptr, size_t size)
{
    (void)size;
    free(ptr);
}

void free_aligned_sized(void *ptr, size_t alignment, size_t size)
{
    (void)alignment;
    (void)size;
    free(ptr);
}