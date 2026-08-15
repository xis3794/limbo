/*
 * limbo_compat_stubs.c
 *
 * Stub implementations for symbols referenced by QEMU 8.x that are not
 * available in the Android NDK API level (21) used by Limbo.
 *
 * These keep the dynamic linker happy at load time: without them,
 * libqemu-system-*.so would fail to load and the app would crash on
 * startup. The stubbed features degrade gracefully (return ENOSYS/NULL).
 */
#include <errno.h>
#include <stdarg.h>
#include <stddef.h>
#include <sys/types.h>
#include <sys/stat.h>
#include "limbo_compat_filesystem.h"

/* ------------------------------------------------------------------ */
/* ld --wrap entry points: redirect QEMU's file IO to Limbo's          */
/* SAF-aware compat layer (android_open/android_fopen/...).            */
/* These are referenced by -Wl,--wrap=open etc. in the .so link step.  */
/* ------------------------------------------------------------------ */
int __wrap_open(const char *path, int flags, ...)
{
    va_list ap;
    mode_t mode = 0;
    va_start(ap, flags);
    mode = (mode_t)va_arg(ap, int);
    va_end(ap);
    return android_open(path, flags, mode);
}

FILE *__wrap_fopen(const char *path, const char *mode)
{
    return android_fopen(path, mode);
}

int __wrap_close(int fd)
{
    return android_close(fd);
}

int __wrap_stat(const char *path, struct stat *buf)
{
    return android_stat(path, buf);
}

int __wrap_mkstemp(char *t)
{
    return android_mkstemp(t);
}

/* ------------------------------------------------------------------ */
/* Network interface list (bionic only provides these since API 24)   */
/* ------------------------------------------------------------------ */
struct ifaddrs;

int getifaddrs(struct ifaddrs **ifap)
{
    (void)ifap;
    errno = ENOSYS;
    return -1;
}

void freeifaddrs(struct ifaddrs *ifa)
{
    (void)ifa;
}

/* ------------------------------------------------------------------ */
/* Device number helpers (glibc: <sys/sysmacros.h>)                   */
/* ------------------------------------------------------------------ */
unsigned int major(unsigned long long dev)
{
    return (unsigned int)((dev >> 8) & 0xfff);
}

unsigned int minor(unsigned long long dev)
{
    return (unsigned int)(dev & 0xff) | (unsigned int)((dev >> 12) & 0xfff00);
}

/* ------------------------------------------------------------------ */
/* GLib gmodule (Limbo does not build the gmodule sub-library)        */
/* ------------------------------------------------------------------ */
typedef void *GModule;
typedef void (*GModuleFunc)(void);

GModule g_module_open(const char *file, int flags)
{
    (void)file;
    (void)flags;
    return NULL;
}

int g_module_symbol(GModule module, const char *symbol_name, void **symbol)
{
    (void)module;
    (void)symbol_name;
    if (symbol) {
        *symbol = NULL;
    }
    return 0;
}

const char *g_module_error(void)
{
    return "gmodule is not supported on Android (Limbo stub)";
}

int g_module_close(GModule module)
{
    (void)module;
    return 0;
}

/* ------------------------------------------------------------------ */
/* pty helpers (bionic lacks openpty on API 21)                       */
/* ------------------------------------------------------------------ */
int openpty(int *amaster, int *aslave, char *name,
            const void *termp, const void *winp)
{
    (void)amaster;
    (void)aslave;
    (void)name;
    (void)termp;
    (void)winp;
    errno = ENOSYS;
    return -1;
}

/* ------------------------------------------------------------------ */
/* close_range (bionic only provides this since API 30)               */
/* ------------------------------------------------------------------ */
int close_range(unsigned int first, unsigned int last, int flags)
{
    (void)first;
    (void)last;
    (void)flags;
    errno = ENOSYS;
    return -1;
}
