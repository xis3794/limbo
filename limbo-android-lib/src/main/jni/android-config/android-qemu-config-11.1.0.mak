#### QEMU version-specific options (11.1.0)

# QEMU 11.x does not use a stub lib
USE_QEMUSTAB ?= false

# QEMU 11.x uses external libslirp (built separately in CI)
USE_SLIRP_LIB ?= false

# 11.x: capstone is a meson option; disable since we don't ship it
MISC += --disable-capstone

# 11.x: FDT needed by all softmmu targets; dtc/ submodule is downloaded in CI
FDT ?= --enable-fdt

# 11.x: zstd/libudev not available in NDK
MISC += --disable-zstd
MISC += --disable-libudev

# 11.x: vhost-* link tests fail on Android (memfd_create not in API 21)
MISC += --disable-vhost-user --disable-vhost-crypto
MISC += --disable-vhost-vdpa --disable-vhost-kernel --disable-vhost-net

# 11.x: tools and guest-agent executables fail linking on Android
MISC += --disable-tools
MISC += --disable-guest-agent

# 11.x: configure passes -D options straight to meson;
#       force PIC for static libs (needed to link .so)
MISC += -Db_staticpic=true

# 11.x: allow meson wrap downloads; fdt must use internal dtc sources
# (wrap_mode defaults to nodownload which forces system libfdt)
MISC += -Dwrap_mode=default
FDT ?= --enable-fdt=internal

# 11.x: MonitorClass has printf/fprintf members; skip logutils printf macros
ARCH_CFLAGS += -D__LIMBO_QEMU11__

# 11.x: TCG plugins not needed (contrib/plugins fail linking on Android)
MISC += --disable-plugins