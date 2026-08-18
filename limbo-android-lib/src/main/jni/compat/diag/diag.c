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

/* Huawei/HarmonyOS may not expose /data/data symlink; try several paths.
 * The Java side (LimboApplication.installNativeDiag) reads the FIRST file
 * from getFilesDir() and forwards it to Downloads as limbo_native.txt.
 * We also write DIRECTLY to /sdcard/Download so the user can see the crash
 * log without any forwarding step. */
#define CRASH_PATH_1 "/sdcard/Download/limbo_native.txt"
#define CRASH_PATH_2 "/storage/emulated/0/Download/limbo_native.txt"
#define CRASH_PATH_3 "/data/user/0/com.limbo.emu.main/files/native_crash.txt"
#define CRASH_PATH_4 "/data/data/com.limbo.emu.main/files/native_crash.txt"

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

static void crash_handler(int sig, siginfo_t *info, void *ctx)
{
    (void)ctx;
    /* ALWAYS log to logcat - Huawei logd works even if file writes fail */
    __android_log_print(ANDROID_LOG_FATAL, "LimboDiag",
                        "===== NATIVE CRASH sig=%d addr=%p =====", sig, info->si_addr);
    int fd = open_crash_file();
    if (fd >= 0) {
        write(fd, "\n===== NATIVE CRASH sig=", 24);
        write_num(fd, (unsigned long)sig);
        write(fd, " addr=", 6);
        write_hex(fd, (uintptr_t)info->si_addr);
        write(fd, " =====", 6);
        write(fd, "\n", 1);
        /* capture backtrace */
        uintptr_t addrs[40];
        struct trace_arg ta = { addrs, 0, 40 };
        _Unwind_Backtrace(trace_fn, &ta);
        for (int i = 0; i < ta.count; i++) {
            write(fd, "  #", 3);
            write_num(fd, (unsigned long)i);
            write(fd, " pc=0x", 6);
            write_hex(fd, addrs[i]);
            write(fd, "\n", 1);
            __android_log_print(ANDROID_LOG_FATAL, "LimboDiag", "  #%d pc=0x%lx", i,
                                (unsigned long)addrs[i]);
        }
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
    diag_install();
    __android_log_print(ANDROID_LOG_INFO, "LimboDiag",
                        "diag crash handler INSTALLED (sigaction registered)");
}