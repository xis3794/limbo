/*
 * diag.c - native crash catcher for Limbo QEMU 11 debugging.
 *
 * Installed FIRST (before any other native lib). Any SIGSEGV/SIGABRT/SIGBUS
 * during dlopen of compat-limbo/glib/qemu is recorded to the app-private
 * dir, then forwarded to Downloads by Java on next launch.
 *
 * Only async-signal-safe calls are used inside the handler.
 */
#define _GNU_SOURCE
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <stdint.h>
#include <unwind.h>
#include <android/log.h>
#include <link.h>
#include <stdio.h>

/* Library base addresses captured at load time (dl_iterate_phdr).
 * Written into the crash log so host-side addr2line can resolve PCs. */
struct libinfo {
    uintptr_t base;
    char name[64];
};
static struct libinfo g_libs[64];
static int g_lib_count = 0;

static int collect_libs(struct dl_phdr_info *info, size_t size, void *data)
{
    (void)size;
    (void)data;
    if (g_lib_count < 64 && info->dlpi_name && info->dlpi_name[0]) {
        g_libs[g_lib_count].base = (uintptr_t)info->dlpi_addr;
        size_t n = strlen(info->dlpi_name);
        if (n > 63) n = 63;
        memcpy(g_libs[g_lib_count].name, info->dlpi_name, n);
        g_libs[g_lib_count].name[n] = '\0';
        g_lib_count++;
    }
    return 0;
}

/* Huawei/HarmonyOS may not expose /data/data symlink; try several paths.
 * The Java side (LimboApplication.installNativeDiag) reads the FIRST file
 * from getFilesDir() and forwards it to Downloads as limbo_native.txt.
 * We also write DIRECTLY to /sdcard/Download so the user can see the crash
 * log without any forwarding step, AND to the app cache dir which the user
 * can browse on-device (/Android/data/com.limbo.emu.main/cache/). */
#define CRASH_PATH_1 "/sdcard/Download/limbo_native.txt"
#define CRASH_PATH_2 "/storage/emulated/0/Download/limbo_native.txt"
#define CRASH_PATH_3 "/data/user/0/com.limbo.emu.main/files/native_crash.txt"
#define CRASH_PATH_4 "/data/data/com.limbo.emu.main/files/native_crash.txt"
#define CRASH_PATH_5 "/data/user/0/com.limbo.emu.main/cache/native_crash.txt"
#define CRASH_PATH_6 "/data/data/com.limbo.emu.main/cache/native_crash.txt"
#define CRASH_PATH_7 "/data/cache/limbo/native_crash.txt"

static int open_crash_file(void)
{
    int fd = open(CRASH_PATH_1, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd < 0) {
        fd = open(CRASH_PATH_2, O_WRONLY | O_CREAT | O_APPEND, 0644);
    }
    if (fd < 0) {
        fd = open(CRASH_PATH_3, O_WRONLY | O_CREAT | O_APPEND, 0644);
    }
    if (fd < 0) {
        fd = open(CRASH_PATH_4, O_WRONLY | O_CREAT | O_APPEND, 0644);
    }
    if (fd < 0) {
        fd = open(CRASH_PATH_5, O_WRONLY | O_CREAT | O_APPEND, 0644);
    }
    if (fd < 0) {
        fd = open(CRASH_PATH_6, O_WRONLY | O_CREAT | O_APPEND, 0644);
    }
    if (fd < 0) {
        fd = open(CRASH_PATH_7, O_WRONLY | O_CREAT | O_APPEND, 0644);
    }
    return fd;
}

static void write_num(int fd, unsigned long v)
{
    char buf[24];
    int i = 0;
    if (v == 0) {
        write(fd, "0", 1);
        return;
    }
    while (v > 0 && i < 23) {
        buf[i++] = (char)('0' + (v % 10));
        v /= 10;
    }
    while (i > 0) {
        write(fd, &buf[--i], 1);
    }
}

static void write_hex(int fd, uintptr_t v)
{
    static const char hex[] = "0123456789abcdef";
    char buf[20];
    int i = 0;
    if (v == 0) {
        write(fd, "0", 1);
        return;
    }
    while (v > 0 && i < 18) {
        buf[i++] = hex[v & 0xf];
        v >>= 4;
    }
    while (i > 0) {
        write(fd, &buf[--i], 1);
    }
}

/* backtrace via unwind.h (works inside signal handlers on arm64) */
struct trace_arg {
    uintptr_t *addrs;
    int count;
    int max;
};

static _Unwind_Reason_Code trace_fn(struct _Unwind_Context *ctx, void *arg)
{
    struct trace_arg *ta = (struct trace_arg *)arg;
    if (ta->count < ta->max) {
        ta->addrs[ta->count++] = (uintptr_t)_Unwind_GetIP(ctx);
        return _URC_NO_REASON;
    }
    return _URC_END_OF_STACK;
}

/* async-signal-safe buffer builders (NO snprintf/__android_log in handler) */
static void b_append(char *buf, int *off, int size, const char *s)
{
    while (*s && *off < size - 1) {
        buf[(*off)++] = *s++;
    }
}

static void b_append_hex(char *buf, int *off, int size, uintptr_t v)
{
    static const char hex[] = "0123456789abcdef";
    char tmp[20];
    int i = 0;
    if (v == 0) {
        tmp[i++] = '0';
    }
    while (v > 0 && i < 18) {
        tmp[i++] = hex[v & 0xf];
        v >>= 4;
    }
    while (i > 0 && *off < size - 1) {
        buf[(*off)++] = tmp[--i];
    }
}

static void b_append_num(char *buf, int *off, int size, unsigned long v)
{
    char tmp[24];
    int i = 0;
    if (v == 0) {
        tmp[i++] = '0';
    }
    while (v > 0 && i < 23) {
        tmp[i++] = (char)('0' + (v % 10));
        v /= 10;
    }
    while (i > 0 && *off < size - 1) {
        buf[(*off)++] = tmp[--i];
    }
}

static void crash_handler(int sig, siginfo_t *info, void *ctx)
{
    (void)ctx;
    char buf[16384];
    int off = 0;
    b_append(buf, &off, sizeof(buf), "\n===== NATIVE CRASH sig=");
    b_append_num(buf, &off, sizeof(buf), (unsigned long)sig);
    b_append(buf, &off, sizeof(buf), " addr=0x");
    b_append_hex(buf, &off, sizeof(buf), (uintptr_t)info->si_addr);
    b_append(buf, &off, sizeof(buf), " =====\n");

    /* Dump /proc/self/maps COMPLETELY (loop until EOF or buffer full).
     * Host side parses it to get each lib's base address, then resolves
     * the PCs below with addr2line. */
    int mfd = open("/proc/self/maps", O_RDONLY);
    if (mfd >= 0) {
        b_append(buf, &off, sizeof(buf), "[maps]\n");
        char mbuf[2048];
        ssize_t n;
        while (off < (int)sizeof(buf) - 1024 &&
               (n = read(mfd, mbuf, sizeof(mbuf))) > 0) {
            for (ssize_t i = 0; i < n && off < (int)sizeof(buf) - 1024; i++) {
                buf[off++] = mbuf[i];
            }
        }
        close(mfd);
        b_append(buf, &off, sizeof(buf), "\n");
    }

    b_append(buf, &off, sizeof(buf), "[bt]\n");
    uintptr_t addrs[40];
    struct trace_arg ta = { addrs, 0, 40 };
    _Unwind_Backtrace(trace_fn, &ta);
    for (int i = 0; i < ta.count && off < (int)sizeof(buf) - 128; i++) {
        b_append(buf, &off, sizeof(buf), "  #");
        b_append_num(buf, &off, sizeof(buf), (unsigned long)i);
        b_append(buf, &off, sizeof(buf), " pc=0x");
        b_append_hex(buf, &off, sizeof(buf), addrs[i]);
        b_append(buf, &off, sizeof(buf), "\n");
    }
    /* single atomic write */
    int fd = open_crash_file();
    if (fd >= 0) {
        (void)write(fd, buf, (size_t)off);
        close(fd);
    }
    /* restore default handler and re-raise so the system still
     * generates the normal tombstone */
    signal(sig, SIG_DFL);
    raise(sig);
}

void diag_install(void)
{
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = crash_handler;
    sa.sa_flags = SA_SIGINFO;
    /* cover all likely crash signals: segv/abrt/bus/ill/trap/fpe/sys */
    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGABRT, &sa, NULL);
    sigaction(SIGBUS, &sa, NULL);
    sigaction(SIGILL, &sa, NULL);
    sigaction(SIGTRAP, &sa, NULL);
    sigaction(SIGFPE, &sa, NULL);
    sigaction(SIGSYS, &sa, NULL);
}

/* Auto-install as soon as the library is dlopen'd (Java only calls
 * System.loadLibrary("diag") - it never invokes diag_install() explicitly).
 * Without this the crash handler was never registered and limbo_native.txt
 * was never written. */
__attribute__((constructor))
static void diag_auto_install(void)
{
    dl_iterate_phdr(collect_libs, NULL);
    diag_install();
    __android_log_print(ANDROID_LOG_INFO, "LimboDiag",
                        "diag crash handler INSTALLED (sigaction registered)");
}