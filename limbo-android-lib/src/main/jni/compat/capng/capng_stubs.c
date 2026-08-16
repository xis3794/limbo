/*
 * capng_stubs.c
 *
 * Standalone libcap-ng stub for Android (bionic has no libcap-ng).
 * QEMU virtfs/9p requires it at link time and runtime.
 *
 * Built as its own libcap-ng.so so that libcompat-limbo.so stays
 * byte-for-byte equivalent to the QEMU 8.0.5 build (which loads fine
 * on Huawei/HarmonyOS devices).
 *
 * Linux capabilities are a no-op on Android -> return harmless values.
 */
#include <stdarg.h>
#include <stddef.h>
#include <stdlib.h>
#include "cap-ng.h"

const char *capng_capability_to_name(unsigned int capability)
{
    (void)capability;
    return "cap";
}

int capng_getpid(void)
{
    /* Do NOT call getpid(): it adds a getpid@LIBC versioned reference
     * that some Huawei/HarmonyOS libc do not export. QEMU does not
     * depend on the value returned here. */
    return 0;
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
