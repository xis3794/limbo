#### QEMU version-specific options (8.0.5)

# QEMU 8.x does not use a stub lib
USE_QEMUSTAB ?= false

# QEMU 8.x uses external libslirp (built separately in CI)
USE_SLIRP_LIB ?= false

# 8.x: capstone is a meson option; disable since we don't ship it
MISC += --disable-capstone

# 8.x: FDT needed by all softmmu targets; dtc/ submodule is downloaded in CI
FDT ?= --enable-fdt

# 8.x: zstd not available in NDK
MISC += --disable-zstd

# 8.x: libudev not available in NDK
MISC += --disable-libudev

# 8.x: vhost-user test links fail (memfd_create not in API 21)
MISC += --disable-vhost-user
MISC += --disable-vhost-crypto
MISC += --disable-vhost-vdpa
MISC += --disable-vhost-kernel
MISC += --disable-vhost-net
