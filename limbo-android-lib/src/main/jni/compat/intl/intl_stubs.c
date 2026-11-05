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
#include <pthread.h>
#include <time.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

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

/* copy_file_range / lchmod: bionic exports neither, but GLib's configure
 * probes them with link-only tests and the GTK stack is linked with
 * -Wl,-z,undefs (needed so the cross build tolerates bionic's ctype/locale
 * gaps). The probe therefore answers "YES", GLib compiles the calls, and the
 * undefined symbol survives into libgio-2.0.so - where the loader then
 * refuses the library with "cannot locate symbol", killing the display stack
 * before QEMU runs. Same failure shape as free_sized above.
 *
 * Both fail cleanly on the first call: GLib falls back to its portable copy
 * loop on ENOSYS, and lchmod only ever runs for the UNIX_MODE attribute of a
 * symlink, which is meaningless on Android.
 *
 * Signatures mirror bionic's (off64_t, not glibc's loff_t) so they do not
 * conflict with the declarations newer NDK sysroots already carry. */
#if __ANDROID_API__ < 28
/* copy_file_range only landed in bionic at API 28; below that the symbol is
 * genuinely absent and this definition is the only one. */
ssize_t copy_file_range(int fd_in, off64_t *off_in,
                        int fd_out, off64_t *off_out,
                        size_t len, unsigned int flags)
{
    (void)fd_in; (void)off_in; (void)fd_out; (void)off_out;
    (void)len; (void)flags;
    errno = ENOSYS;
    return -1;
}
#endif

#if __ANDROID_API__ < 30
/* lchmod is not in bionic at any level; the guard is against a future NDK
 * declaring it, which would make this a duplicate definition. */
int lchmod(const char *path, mode_t mode)
{
    (void)path; (void)mode;
    errno = ENOSYS;
    return -1;
}
#endif

/* ------------------------------------------------------------------ */
/* _rwlock_* internal aliases: NDK r23's libc version-binds
 * pthread_rwlock_* to the internal underscore names (_rwlock_trywrlock)
 * which Huawei's libc does NOT export. glib 2.76 linked against NDK r23
 * references _rwlock_trywrlock@LIBC -> provide the underscore-named
 * implementations here (on glib's DT_NEEDED chain), built on the standard
 * pthread API that every bionic exports.                              */
/* ------------------------------------------------------------------ */
int _rwlock_init(pthread_rwlock_t *lock) { return pthread_rwlock_init(lock, NULL); }
int _rwlock_destroy(pthread_rwlock_t *lock) { return pthread_rwlock_destroy(lock); }
int _rwlock_rdlock(pthread_rwlock_t *lock) { return pthread_rwlock_rdlock(lock); }
int _rwlock_wrlock(pthread_rwlock_t *lock) { return pthread_rwlock_wrlock(lock); }
int _rwlock_tryrdlock(pthread_rwlock_t *lock) { return pthread_rwlock_tryrdlock(lock); }
int _rwlock_trywrlock(pthread_rwlock_t *lock) { return pthread_rwlock_trywrlock(lock); }
int _rwlock_unlock(pthread_rwlock_t *lock) { return pthread_rwlock_unlock(lock); }
int _rwlock_timedrdlock(pthread_rwlock_t *lock, const struct timespec *t) { return pthread_rwlock_timedrdlock(lock, t); }
int _rwlock_timedwrlock(pthread_rwlock_t *lock, const struct timespec *t) { return pthread_rwlock_timedwrlock(lock, t); }

/* ------------------------------------------------------------------ */
/* g_libintl_* : glib 2.76 detects libintl via pkg-config and forwards
 * its i18n calls to these names (glib/glib/ggettext.c). The 8.0.5 musl
 * exports plain gettext/dgettext (which glib 2.56 used), but glib 2.76
 * wants the g_libintl_ prefixed aliases. Provide harmless stubs.      */
/* ------------------------------------------------------------------ */
const char *g_libintl_gettext(const char *msgid) { return msgid; }
const char *g_libintl_dgettext(const char *domain, const char *msgid) { (void)domain; return msgid; }
const char *g_libintl_dcgettext(const char *domain, const char *msgid, int category) { (void)domain; (void)category; return msgid; }
const char *g_libintl_ngettext(const char *msgid, const char *msgid_plural, unsigned long n) { return (n == 1) ? msgid : msgid_plural; }
const char *g_libintl_dngettext(const char *domain, const char *msgid, const char *msgid_plural, unsigned long n) { (void)domain; return (n == 1) ? msgid : msgid_plural; }
const char *g_libintl_textdomain(const char *domain) { return domain; }
const char *g_libintl_bindtextdomain(const char *domain, const char *dir) { (void)dir; return domain; }
const char *g_libintl_bind_textdomain_codeset(const char *domain, const char *codeset) { (void)domain; return codeset; }