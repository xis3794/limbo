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
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <android/log.h>
#include "limbo_compat_filesystem.h"

/* NOTE: _rwlock_* stubs live in compat/musl/musl-stubs.c (they are on
 * libglib's DT_NEEDED chain). They must NOT be defined here - direct calls
 * to pthread_rwlock_* would add versioned @LIBC references to this lib. */

/* NOTE: capng_* stubs now live in compat/capng/capng_stubs.c (built as
 * the standalone libcap-ng.so). free_sized/free_aligned_sized live in
 * compat/musl/musl-stubs.c (on libglib's DT_NEEDED chain). Keeping this
 * library byte-for-byte equivalent to the working QEMU 8.0.5 build. */

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

/* ------------------------------------------------------------------ */
/* POSIX shared memory: bionic's shm support is limited and some       */
/* Huawei/HarmonyOS libc do not export shm_open/shm_unlink at all.     */
/* QEMU uses them for vhost-user / ivshmem; stub them (no-op failure). */
/* ------------------------------------------------------------------ */
int shm_open(const char *name, int oflag, mode_t mode)
{
    (void)name;
    (void)oflag;
    (void)mode;
    errno = ENOSYS;
    return -1;
}

int shm_unlink(const char *name)
{
    (void)name;
    errno = ENOSYS;
    return -1;
}

/* ------------------------------------------------------------------ */
/* ld --wrap entry point for exit()                                    */
/*                                                                     */
/* QEMU terminates the process with exit(1) on fatal initialisation    */
/* errors (e.g. "unsupported machine type: pc-q35-5.0" or an option it */
/* does not know any more, such as the standalone -tb-size of QEMU<8). */
/* A normal                                                           */
/* exit() runs __cxa_finalize, i.e. the static destructors of every    */
/* library in the process, and those destructors call                  */
/* pthread_mutex_destroy() on globals of libhwui.so /                  */
/* libgrallocutils.so. The app's UI and render threads are still using */
/* those libraries, so the process instantly dies with                 */
/*   "FORTIFY: pthread_mutex_lock called on a destroyed mutex"         */
/* which hides QEMU's real error message and looks like a random       */
/* crash. Terminating with _exit() skips the destructors: the process  */
/* still goes away but nothing is torn down under the UI threads' feet.*/
/*                                                                     */
/* Because _exit() also skips the normal log flushing, the reason for  */
/* the exit would otherwise be invisible (fd 2 was redirected to a     */
/* file before QEMU was dlopen'ed - see vm-executor-jni.c). Read that  */
/* file back and push it to logcat first, so a fatal QEMU error shows  */
/* up immediately instead of only after the next app start.            */
/* ------------------------------------------------------------------ */
static void limbo_log_captured_stderr(void)
{
    char buf[2048];
    ssize_t n;

    /* make sure everything QEMU wrote is on disk before reading it back */
    fsync(2);

    /* fd 2 is the capture file, opened O_RDWR by the JNI layer. If the
     * redirect failed (or fd 2 is a tty/pipe) this fails and we skip. */
    if (lseek(2, 0, SEEK_SET) == (off_t)-1) {
        return;
    }
    while ((n = read(2, buf, sizeof(buf) - 1)) > 0) {
        buf[n] = '\0';
        __android_log_write(ANDROID_LOG_ERROR, "LimboQemuFatal", buf);
    }
    __android_log_write(ANDROID_LOG_ERROR, "LimboQemuFatal",
                        "[limbo] QEMU called exit(): see the lines above");
}

void __wrap_exit(int status)
{
    limbo_log_captured_stderr();
    _exit(status);
}
