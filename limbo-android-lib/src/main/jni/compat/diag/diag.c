/*
 * diag.c - native crash catcher for Limbo QEMU 11 debugging.
 *
 * Installed FIRST (before any other native lib). Any SIGSEGV/SIGABRT/SIGBUS
 * during dlopen of compat-limbo/glib/qemu is recorded to the app-private
 * dir, then forwarded to Downloads by Java on next launch.
 *
 * Only async-signal-safe calls are used inside the handler.
 */
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>

#define CRASH_PATH "/data/data/com.limbo.emu.main/files/native_crash.txt"

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

static void crash_handler(int sig, siginfo_t *info, void *ctx)
{
    (void)ctx;
    int fd = open(CRASH_PATH, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd >= 0) {
        write(fd, "NATIVE CRASH sig=", 17);
        write_num(fd, (unsigned long)sig);
        write(fd, " addr=", 6);
        write_num(fd, (unsigned long)info->si_addr);
        write(fd, "\n", 1);
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
    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGABRT, &sa, NULL);
    sigaction(SIGBUS, &sa, NULL);
}