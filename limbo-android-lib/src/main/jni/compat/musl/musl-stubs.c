/*
 * musl-stubs.c
 * Extra compatibility stubs compiled into libcompat-musl.so so that
 * libglib-2.0.so (whose DT_NEEDED includes libcompat-musl.so) can resolve
 * symbols that some Android/HarmonyOS bionic variants do not export.
 *
 * NOTE: we must NOT call pthread_rwlock_* directly here - ndk-build links
 * would bind to pthread_rwlock_trywrlock@LIBC (versioned) which some
 * Huawei/HarmonyOS libc do not export. Use dlsym() for lazy resolution.
 */
#include <errno.h>
#include <stddef.h>
#include <stdlib.h>
#include <pthread.h>
#include <time.h>
#include <dlfcn.h>

/* close_range: only in bionic API 30+; some Huawei/HarmonyOS lack it */
int close_range(unsigned int first, unsigned int last, int flags)
{
    (void)first;
    (void)last;
    (void)flags;
    errno = ENOSYS;
    return -1;
}

/* C23 sized-free helpers (bionic lacks free_sized/free_aligned_sized).
 * glib 2.76 references them on C23-aware compilers; provide them on
 * libglib's DT_NEEDED chain (libcompat-musl). */
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

static int rwlock_call(const char *sym, pthread_rwlock_t *lock,
                       const struct timespec *t)
{
    void *h = dlsym(RTLD_DEFAULT, sym);
    if (h) {
        if (t) {
            int (*fn)(pthread_rwlock_t *, const struct timespec *) = (int (*)(pthread_rwlock_t *, const struct timespec *))h;
            return fn(lock, t);
        } else {
            int (*fn)(pthread_rwlock_t *) = (int (*)(pthread_rwlock_t *))h;
            return fn(lock);
        }
    }
    errno = ENOSYS;
    return -1;
}

/* glibc-style internal rwlock aliases; Huawei bionic may not export them. */
int _rwlock_init(pthread_rwlock_t *lock) { return rwlock_call("pthread_rwlock_init", lock, NULL); }
int _rwlock_destroy(pthread_rwlock_t *lock) { return rwlock_call("pthread_rwlock_destroy", lock, NULL); }
int _rwlock_rdlock(pthread_rwlock_t *lock) { return rwlock_call("pthread_rwlock_rdlock", lock, NULL); }
int _rwlock_wrlock(pthread_rwlock_t *lock) { return rwlock_call("pthread_rwlock_wrlock", lock, NULL); }
int _rwlock_tryrdlock(pthread_rwlock_t *lock) { return rwlock_call("pthread_rwlock_tryrdlock", lock, NULL); }
int _rwlock_trywrlock(pthread_rwlock_t *lock) { return rwlock_call("pthread_rwlock_trywrlock", lock, NULL); }
int _rwlock_unlock(pthread_rwlock_t *lock) { return rwlock_call("pthread_rwlock_unlock", lock, NULL); }
int _rwlock_timedrdlock(pthread_rwlock_t *lock, const struct timespec *t) { return rwlock_call("pthread_rwlock_timedrdlock", lock, t); }
int _rwlock_timedwrlock(pthread_rwlock_t *lock, const struct timespec *t) { return rwlock_call("pthread_rwlock_timedwrlock", lock, t); }