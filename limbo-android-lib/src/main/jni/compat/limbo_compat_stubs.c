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
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include "limbo_compat_filesystem.h"
#include "cap-ng.h"

/* ------------------------------------------------------------------ */
/* libcap-ng stub: bionic has no libcap-ng; QEMU virtfs/9p needs it.  */
/* Linux capabilities are a no-op on Android -> return harmless values */
/* ------------------------------------------------------------------ */
const char *capng_capability_to_name(unsigned int capability)
{
    (void)capability;
    return "cap";
}

int capng_getpid(void)
{
    return getpid();
}

void capng_setpid(int pid)
{
    (void)pid;
}

void capng_clear(int set)
{
    (void)set;
}

void capng_fill(int set)
{
    (void)set;
}

int capng_update(int action, int type, unsigned int capability)
{
    (void)action;
    (void)type;
    (void)capability;
    return CAPNG_OK;
}

int capng_updatev(int action, int type, unsigned int capability, ...)
{
    (void)action;
    (void)type;
    (void)capability;
    return CAPNG_OK;
}

int capng_apply(int set)
{
    (void)set;
    return CAPNG_OK;
}

int capng_change_id(int uid, int gid, capng_flags_t flag)
{
    (void)uid;
    (void)gid;
    (void)flag;
    return CAPNG_FAIL;
}

int capng_have_capability(int set, unsigned int capability)
{
    (void)set;
    (void)capability;
    return 0;
}

int capng_have_permitted_capability(unsigned int capability)
{
    (void)capability;
    return 0;
}

int capng_lock(void)
{
    return CAPNG_OK;
}

char *capng_print_caps_text(int set)
{
    (void)set;
    return NULL;
}

char *capng_print_caps_numeric(int set)
{
    (void)set;
    return NULL;
}

int capng_name_to_capability(const char *name)
{
    (void)name;
    return -1;
}

capng_results_t capng_get_caps_process(void)
{
    return CAPNG_OK;
}

int capng_set_caps_process(void)
{
    return CAPNG_OK;
}

/* ------------------------------------------------------------------ */
/* C23 sized-free helpers (bionic lacks free_sized/free_aligned_sized)*/
/* glib 2.76 references them on C23-aware compilers.                   */
/* ------------------------------------------------------------------ */
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
