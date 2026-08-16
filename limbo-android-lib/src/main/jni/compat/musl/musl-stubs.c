/*
 * musl-stubs.c
 * Extra compatibility stubs compiled into libcompat-musl.so so that
 * libglib-2.0.so (whose DT_NEEDED includes libcompat-musl.so) can resolve
 * symbols that older Android bionic does not export.
 */
#include <errno.h>
#include <stddef.h>

/* close_range: only in bionic API 30+; older devices need a stub */
int close_range(unsigned int first, unsigned int last, int flags)
{
    (void)first;
    (void)last;
    (void)flags;
    errno = ENOSYS;
    return -1;
}