
#### QEMU version-specific options

# QEMU version 5.x is not using a stab lib
USE_QEMUSTAB ?= false

# For QEMU 5.0.0 uses slirp as a static lib so set to true
USE_SLIRP_LIB ?= true

# For QEMU 5.0.0 set the explicit sdlabi to false
USE_SDL_ABI ?= false

# For QEMU 2.11.0 and above (3.x, 4.x) disable these features
MISC += --disable-capstone
MISC += --disable-malloc-trim

# 5.1-only options (removed from QEMU 8.x, kept here for 5.1.0 builds)
MISC += --disable-zlib-test
MISC += --disable-blobs
MISC += --disable-sparse
MISC += --disable-guest-agent
MISC += --disable-vnc-sasl
MISC += --disable-vhost-net --disable-vhost-scsi
MISC += --disable-xfsctl

