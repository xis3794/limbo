#### QEMU version-specific options (8.0.5)

# QEMU 8.x does not use a stub lib
USE_QEMUSTAB ?= false

# QEMU 8.x uses external libslirp (built separately in CI)
USE_SLIRP_LIB ?= false

# 8.x: capstone is a meson option; disable since we don't ship it
MISC += --disable-capstone
