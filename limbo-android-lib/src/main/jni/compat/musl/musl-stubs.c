/*
 * musl-stubs.c
 * Extra compatibility stubs compiled into libcompat-musl.so so that
 * libglib-2.0.so (whose DT_NEEDED includes libcompat-musl.so) can resolve
 * symbols that some Android/HarmonyOS bionic variants do not export.
 */
#include <errno.h>
#include <stddef.h>
#include <pthread.h>
#include <time.h>

/* close_range: only in bionic API 30+; some Huawei/HarmonyOS lack it */
int close_range(unsigned int first, unsigned int last, int flags)
{
    (void)first;
    (void)last;
    (void)flags;
    errno = ENOSYS;
    return -1;
}

/* glibc-style internal rwlock aliases; Huawei bionic may not export them.
 * Forward to the standard pthread_rwlock_* API (present on all Android). */
int _rwlock_init(pthread_rwlock_t *lock) { return pthread_rwlock_init(lock, NULL); }
int _rwlock_destroy(pthread_rwlock_t *lock) { return pthread_rwlock_destroy(lock); }
int _rwlock_rdlock(pthread_rwlock_t *lock) { return pthread_rwlock_rdlock(lock); }
int _rwlock_wrlock(pthread_rwlock_t *lock) { return pthread_rwlock_wrlock(lock); }
int _rwlock_tryrdlock(pthread_rwlock_t *lock) { return pthread_rwlock_tryrdlock(lock); }
int _rwlock_trywrlock(pthread_rwlock_t *lock) { return pthread_rwlock_trywrlock(lock); }
int _rwlock_unlock(pthread_rwlock_t *lock) { return pthread_rwlock_unlock(lock); }
int _rwlock_timedrdlock(pthread_rwlock_t *lock, const struct timespec *t) { return pthread_rwlock_timedrdlock(lock, t); }
int _rwlock_timedwrlock(pthread_rwlock_t *lock, const struct timespec *t) { return pthread_rwlock_timedwrlock(lock, t); }