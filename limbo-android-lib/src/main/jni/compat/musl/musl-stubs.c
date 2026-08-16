/*
 * musl-stubs.c
 * Extra compatibility stubs compiled into libcompat-musl.so so that
 * libglib-2.0.so (whose DT_NEEDED includes libcompat-musl.so) can resolve
 * symbols that some Android/HarmonyOS bionic variants do not export.
 *
 * NOTE: _rwlock_* stubs call the standard pthread_rwlock_* API directly.
 * Do NOT use dlsym() here: referencing dlsym adds a libdl.so verneed entry
 * that Huawei/HarmonyOS linkers fail to resolve ("cannot find text from
 * verneed[0]"). The standard pthread rwlock functions live in libc.so and
 * resolve fine (this matches how the working QEMU 8.0.5 libs are linked).
 */
#include <errno.h>
#include <stddef.h>
#include <stdlib.h>
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

/* NOTE: _rwlock_* stubs are NOT needed here. glib 2.76 references the
 * standard pthread_rwlock_trywrlock etc. (verified in the built .so's
 * undef table after clear_verneed), which libc.so exports on every
 * Android/HarmonyOS. Adding pthread_rwlock_* references here changed the
 * verneed layout and broke dlopen on Huawei ("cannot find ... from
 * verneed[0]"). Keep this library's symbol set identical to the working
 * QEMU 8.0.5 build. */