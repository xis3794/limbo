include android-limbo-build.mak

ifeq ($(NDK_ENV), windows)
	NDK_BUILD = ndk-build.cmd
else ifeq ($(NDK_ENV), windows-x86_64)
	NDK_BUILD = ndk-build.cmd
else
	NDK_BUILD = ndk-build
endif

# V=1 so we can see the full compile command and verify which diag.c is used
ndkbuild=cd $(NDK_PROJECT_PATH) && $(NDK_BUILD) -j $(BUILD_THREADS) V=1
ndkclean=cd $(NDK_PROJECT_PATH) && $(NDK_BUILD) clean
ndkcleanobj=rm -rf $(NDK_PROJECT_PATH)/obj/

ifeq ($(NDK_ENV), windows)
	ndkgdb=cd $(NDK_PROJECT_PATH) && ndk-gdb.cmd
else ifeq ($(NDK_ENV), windows-x86_64)
	ndkgdb=cd $(NDK_PROJECT_PATH) && ndk-gdb.cmd
else
	ndkgdb=cd $(NDK_PROJECT_PATH) && ndk-gdb
endif

CREATE_OBJDIR=mkdir -p $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/
CREATE_LIBSDIR=mkdir -p $(NDK_PROJECT_PATH)/libs/$(APP_ABI_DIR)/
DEL_LIBSDIR=rm -rf $(NDK_PROJECT_PATH)/libs/* \
	$(NDK_PROJECT_PATH)/jniLibs/* \
	$(LIMBO_APP_X86)/jniLibs/* \
	$(LIMBO_APP_ARM)/jniLibs/* \
	$(LIMBO_APP_PPC)/jniLibs/* \
	$(LIMBO_APP_SPARC)/jniLibs/*


INSTALL_GLIB_LIBS=cp -f glib/glib/.libs/*.so \
    glib/gmodule/.libs/*.so \
    $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/

INSTALL_FFI_LIBS=cp -f libffi/$(GNU_HOST)/.libs/*.a \
    $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/

INSTALL_PIXMAN_LIBS=cp -f pixman/pixman/.libs/*.so \
    $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/

MOD_QEMU_LIBS=$(OBJCOPY) --redefine-sym open=android_open  \
    qemu/x86_64-softmmu/qemu-system-x86_64

INSTALL_LIBS=cp -f $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/*.so \
	../libs/$(APP_ABI_DIR)/

REMOVE_GDBSETUP=rm -f ../libs/$(APP_ABI_DIR)/gdb.setup
REMOVE_GDBSERVER=rm -f ../libs/$(APP_ABI_DIR)/gdbserver

SYNC_LIBS=rsync -av --exclude libqemu-system-* ../libs/* ../jniLibs/

LIMBO_PRJ_ROOT=../../../..
LIMBO_APP_X86=$(LIMBO_PRJ_ROOT)/limbo-android-x86/src/main
LIMBO_APP_ARM=$(LIMBO_PRJ_ROOT)/limbo-android-arm/src/main
LIMBO_APP_PPC=$(LIMBO_PRJ_ROOT)/limbo-android-ppc/src/main
LIMBO_APP_SPARC=$(LIMBO_PRJ_ROOT)/limbo-android-sparc/src/main

# short guest name, e.g. x86_64-softmmu -> x86_64
GUEST_SHORT=$(subst -softmmu,,$(BUILD_GUEST))

SYNC_LIBS_X86=rsync -av --include='libqemu-system-x86*.so' \
    --include='gdbserver' --include='gdb.setup' \
    --include='*/' --exclude='*' \
    ../libs/ $(LIMBO_APP_X86)/jniLibs/
SYNC_LIBS_ARM=rsync -av --include='libqemu-system-arm*.so' \
    --include='libqemu-system-aarch64*.so' \
    --include='gdbserver' --include='gdb.setup' \
    --include='*/' --exclude='*' \
    ../libs/ $(LIMBO_APP_ARM)/jniLibs/
SYNC_LIBS_PPC=rsync -av --include='libqemu-system-ppc*.so' \
    --include='gdbserver' --include='gdb.setup' \
    --include='*/' --exclude='*' \
    ../libs/ $(LIMBO_APP_PPC)/jniLibs/
SYNC_LIBS_SPARC=rsync -av --include='libqemu-system-sparc*.so' \
    --include='gdbserver' --include='gdb.setup' \
    --include='*/' --exclude='*' \
    ../libs/ $(LIMBO_APP_SPARC)/jniLibs/

# NOTE: the destination (../assets/roms/) MUST be the last argument of the
# SAME cp command. A previous `2>/dev/null || true` placed in the middle of
# the argument list split the command, so cp ran with no destination and
# NOTHING was copied -> APK shipped without bios.bin/vgabios*.bin and QEMU
# failed instantly ("Could not load PC BIOS 'bios.bin'").
COPY_ROMS=-mkdir -p ../assets/roms && cp -rf \
./qemu/pc-bios/*.rom \
./qemu/pc-bios/*.bin \
./qemu/pc-bios/*.img \
./qemu/pc-bios/*.bmp \
./qemu/pc-bios/*.ico \
./qemu/pc-bios/*.svg \
./qemu/pc-bios/*.rsrc \
./qemu/pc-bios/openbios-* \
./qemu/pc-bios/*.fd \
./qemu/pc-bios/README \
../assets/roms/ 2>/dev/null || true

COPY_KEYMAPS=cp -rf ./qemu/pc-bios/keymaps ../assets/roms/
COPY_QEMU_VERSION=cp -f ./qemu/VERSION ../assets/QEMU_VERSION
# Per-QEMU-version machine-type / CPU tables. The app reads this asset to know
# what the active core supports; tools/package_qemu.py embeds the same block in
# every downloadable package's manifest.json.
COPY_CAPABILITIES=-cp -f $(LIMBO_PRJ_ROOT)/tools/qemu_capabilities.json ../assets/qemu_capabilities.json
COPY_RESOLV_CONF=mkdir -p ../assets/roms/etc/ && printf "nameserver 8.8.8.8\n\n" > ../assets/roms/etc/resolv.conf

COPY_ASSETS_X86=mkdir -p ../../../../limbo-android-x86/src/main/assets/ && \
    cp -rf ../assets/* ../../../../limbo-android-x86/src/main/assets/
COPY_ASSETS_ARM=mkdir -p ../../../../limbo-android-arm/src/main/assets/ && \
    cp -rf ../assets/* ../../../../limbo-android-arm/src/main/assets/
COPY_ASSETS_PPC=mkdir -p ../../../../limbo-android-ppc/src/main/assets/ && \
    cp -rf ../assets/* ../../../../limbo-android-ppc/src/main/assets/
COPY_ASSETS_SPARC=mkdir -p ../../../../limbo-android-sparc/src/main/assets/ && \
    cp -rf ../assets/* ../../../../limbo-android-sparc/src/main/assets/

#RENAME_TUNCTL=mv -f $(NDK_PROJECT_PATH)/libs/$(APP_ABI)/tunctl \
	$(NDK_PROJECT_PATH)/libs/$(APP_ABI)/libtunctl.so
#RENAME_PARPROUTED=mv -f $(NDK_PROJECT_PATH)/libs/$(APP_ABI)/parprouted \
	$(NDK_PROJECT_PATH)/libs/$(APP_ABI)/libparprouted.so
	
#GDB needs the gdb.setup under ./libs
COPY_GDB_SETUP_LIBS=-cp -f $(NDK_PROJECT_PATH)/libs/$(APP_ABI)/gdb.setup \
	$(NDK_PROJECT_PATH)/libs/gdb.setup
	
ifneq ($(NDK_DEBUG),1)
	# strip errors on exotic .so names must not fail the build
	STRIP_LIBS=$(STRIP) $(NDK_PROJECT_PATH)/libs/$(APP_ABI_DIR)/*.so || true
endif

# we disable for now some options so armv7 host can compile with gcc
PIXMAN_DISABLE_OPTIONS=--disable-mmx --disable-arm-iwmmxt --disable-arm-iwmmxt2  \
	--disable-openmp  --disable-loongson-mmi  --disable-mmx    --disable-sse2   --disable-ssse3 \
	--disable-vmx --disable-arm-simd   --disable-arm-neon   --disable-arm-iwmmxt   \
	--disable-arm-iwmmxt2  --disable-mips-dspr2  --disable-gcc-inline-asm

DEBUG =
ifeq ($(NDK_DEBUG), 1)
	LIBFFI_DEBUG = --enable-debug
	LIBGLIB_DEBUG = --enable-debug
	LIBPIXMAN_DEBUG =
else
	LIBFFI_DEBUG = --disable-debug
    LIBGLIB_DEBUG = --disable-debug
    LIBPIXMAN_DEBUG =
endif

# QEMU 8.x/11.x (meson): make our hand-built pkg-config files visible
# Also export NDK tool names so QEMU configure/meson finds them
# (configure uses ${NM-${cross_prefix}nm} pattern: env var wins if set)
ifneq ($(filter 10.% 11.%,$(USE_QEMU_VERSION)),)
GLIB_DIR := glib276
else
GLIB_DIR := glib
endif
ifneq ($(filter 8.0.5 10.% 11.%,$(USE_QEMU_VERSION)),)
export PKG_CONFIG := pkg-config
# The optional stacks (libepoxy / GTK4 / virglrenderer) live in prefixes of
# their own; QEMU's meson finds them through this search path. Restricting the
# dirs to what we build keeps the runner's host gtk/virgl out of the cross build.
ifneq ($(filter true,$(USE_GTK) $(USE_VIRGL)),)
export PKG_CONFIG_PATH := $(OPT_PC_DIRS)
export PKG_CONFIG_LIBDIR := $(OPT_PC_DIRS)
else
export PKG_CONFIG_PATH := $(LIMBO_JNI_ROOT)/pc
endif
export NM
export AR
export STRIP
export OBJ_COPY
endif

export

.PHONY: limbo clean distclean clean-ndk createdirs clean-ffi clean-glib clean-glib289 clean-qemu clean-pixman config-libslirp libslirp glib289 config-glib289 clean-gtk gtk clean-virgl virgl clean-epoxy epoxy

default: limbo
	
createdirs:
	$(CREATE_OBJDIR)
	$(DEL_LIBSDIR)
	$(CREATE_LIBSDIR)

ndk:
	$(ndkbuild)

clean-ndk:
	$(ndkclean)
	$(ndkcleanobj)

clean-ffi:
	-cd $(LIMBO_JNI_ROOT)/libffi && $(MAKE) distclean
	-cd $(LIMBO_JNI_ROOT)/libffi && $(MAKE) clean

config-ffi:
	cd $(LIMBO_JNI_ROOT)/libffi && ./configure --host=$(GNU_HOST) $(LIBFFI_DEBUG) --enable-static --disable-shared \
	CFLAGS="$(ARCH_CFLAGS) -I$(NDK_INCLUDE) --sysroot=$(NDK_SYSROOT) $(NDK_SYSROOT_ARCH_INC) " \
	LDFLAGS="$(ARCH_LD_FLAGS) $(SYS_ROOT) -nostdlib -fno-unwind-tables "

ffi:
	cd $(LIMBO_JNI_ROOT)/libffi && $(MAKE) -j $(BUILD_THREADS) \
	CFLAGS="$(ARCH_CFLAGS) -I$(NDK_INCLUDE) --sysroot=$(NDK_SYSROOT) $(NDK_SYSROOT_ARCH_INC) " \
	LDFLAGS="$(ARCH_LD_FLAGS) $(SYS_ROOT) -nostdlib -fno-unwind-tables "
	$(INSTALL_FFI_LIBS)

ifneq ($(filter 10.% 11.%,$(USE_QEMU_VERSION)),)
config-glib: ndk
	@echo "QEMU11: glib2.76 via meson (config skipped)"
	GLIB_DIR=glib276 GLIB_VERSION=2.76.6 bash $(LIMBO_PRJ_ROOT)/scripts/gen_pc_files.sh $(LIMBO_JNI_ROOT) $(APP_ABI)

else
config-glib: ndk
	cd $(LIMBO_JNI_ROOT)/glib && NOCONFIGURE=1 ./autogen.sh
	cd $(LIMBO_JNI_ROOT)/glib && cp -rf $(LIMBO_JNI_ROOT)/patches/android.cache . \
	&& chmod a-w android.cache && ./configure --host=$(GNU_HOST) --cache-file=android.cache \
	--disable-libmount  --disable-dtrace  --disable-systemtap --with-pcre=internal \
	--with-libiconv=no \
	--with-sysroot=$(SYSROOT) $(LIBGLIB_DEBUG) \
	CFLAGS="$(ARCH_CFLAGS) -I$(NDK_INCLUDE) \
	$(NDK_SYSROOT_INC) \
	$(NDK_SYSROOT_ARCH_INC) \
	-I${SYSROOT}/usr/include $(SYS_ROOT) \
	-I${LIMBO_JNI_ROOT}/compat \
	-I${LIMBO_JNI_ROOT}/compat/musl -I${LIMBO_JNI_ROOT}/compat/musl/include " \
	LDFLAGS=" $(ARCH_LD_CLANG_FLAGS) $(ARCH_LD_FLAGS) $(SYS_ROOT) \
	-L${LIMBO_JNI_ROOT}/../obj/local/$(APP_ABI) -lc -lcompat-musl -llog -lcompat-limbo " \
	LIBFFI_CFLAGS="-I${LIMBO_JNI_ROOT}/libffi/$(GNU_HOST)/include " \
	LIBFFI_LIBS="${LIMBO_JNI_ROOT}/../obj/local/$(APP_ABI)/libffi.a"
	bash $(LIMBO_PRJ_ROOT)/scripts/gen_pc_files.sh $(LIMBO_JNI_ROOT) $(APP_ABI)
endif

ifneq ($(filter 10.% 11.%,$(USE_QEMU_VERSION)),)
glib: ndk
	bash $(LIMBO_PRJ_ROOT)/scripts/build_glib276.sh $(LIMBO_JNI_ROOT) $(NDK_ROOT) $(APP_ABI)

else
glib: ndk
	cd $(LIMBO_JNI_ROOT)/glib/glib && $(MAKE) -j $(BUILD_THREADS) CFLAGS="$(ARCH_CFLAGS) \
	$(NDK_SYSROOT_INC) \
	$(NDK_SYSROOT_ARCH_INC) \
	-I$(NDK_INCLUDE)  -I${SYSROOT}/usr/include $(SYS_ROOT) \
	-I${NDK_ROOT}/sources/android/support/include -I${LIMBO_JNI_ROOT}/compat \
	-include ${LIMBO_JNI_ROOT}/compat/musl/compat_iconv.h -I${LIMBO_JNI_ROOT}/compat/musl -I${LIMBO_JNI_ROOT}/compat/musl/include " \
	LDFLAGS=" $(ARCH_LD_CLANG_FLAGS) $(ARCH_LD_FLAGS) $(SYS_ROOT) -L${LIMBO_JNI_ROOT}/../obj/local/$(APP_ABI) \
	-lc -lcompat-musl -llog -lcompat-limbo " \
	-lgcc \
	LIBFFI_CFLAGS="-I${LIMBO_JNI_ROOT}/libffi/$(GNU_HOST)/include " \
	LIBFFI_LIBS="${LIMBO_JNI_ROOT}/libffi/$(GNU_HOST)/.libs/libffi.a "
	cd $(LIMBO_JNI_ROOT)/glib/gmodule && $(MAKE) -j $(BUILD_THREADS) CFLAGS="$(ARCH_CFLAGS) \
	$(NDK_SYSROOT_INC) \
	$(NDK_SYSROOT_ARCH_INC) \
	-I$(NDK_INCLUDE)  -I${SYSROOT}/usr/include $(SYS_ROOT) \
	-I${NDK_ROOT}/sources/android/support/include -I${LIMBO_JNI_ROOT}/compat \
	-include ${LIMBO_JNI_ROOT}/compat/musl/compat_iconv.h -I${LIMBO_JNI_ROOT}/compat/musl -I${LIMBO_JNI_ROOT}/compat/musl/include " \
	LDFLAGS=" $(ARCH_LD_CLANG_FLAGS) $(ARCH_LD_FLAGS) $(SYS_ROOT) -L${LIMBO_JNI_ROOT}/../obj/local/$(APP_ABI) \
	-lc -lcompat-musl -llog -lcompat-limbo " \
	-lgcc \
	LIBFFI_CFLAGS="-I${LIMBO_JNI_ROOT}/libffi/$(GNU_HOST)/include " \
	LIBFFI_LIBS="${LIMBO_JNI_ROOT}/libffi/$(GNU_HOST)/.libs/libffi.a "
	$(INSTALL_GLIB_LIBS)
endif

config-libslirp:
	@if [ "$(USE_QEMU_VERSION)" = "8.0.5" ] || echo "$(USE_QEMU_VERSION)" | grep -qE "^(10|11)\."; then \
		echo "Configuring libslirp for QEMU $(USE_QEMU_VERSION)"; \
	fi
	@if [ "$(USE_QEMU_VERSION)" = "11.1.0" ]; then \
		echo "libcap-ng.so stub is built by ndk-build (compat/capng module)"; \
	fi

libslirp:
	@if [ "$(USE_QEMU_VERSION)" = "8.0.5" ] || echo "$(USE_QEMU_VERSION)" | grep -qE "^(10|11)\."; then \
		bash $(LIMBO_PRJ_ROOT)/scripts/build_libslirp.sh $(LIMBO_JNI_ROOT) $(NDK_ROOT) $(APP_ABI); \
	else \
		echo "libslirp not needed for QEMU $(USE_QEMU_VERSION)"; \
	fi

clean-glib:
	-find $(LIMBO_JNI_ROOT)/glib/ -name "*.lo" -exec rm -rf {} \;
	-cd $(LIMBO_JNI_ROOT)/glib && $(MAKE) distclean
	-cd $(LIMBO_JNI_ROOT)/glib && $(MAKE) clean
	-rm -rf $(LIMBO_JNI_ROOT)/glib/android.cache


config-pixman: ndk
	cd $(LIMBO_JNI_ROOT)/pixman && ./configure --host=$(GNU_HOST) --with-sysroot=$(SYSROOT) \
	--disable-libtool-lock \
	$(PIXMAN_DISABLE_OPTIONS) \
	$(LIBPIXMAN_DEBUG) \
	CFLAGS="$(ARCH_CFLAGS) -I$(NDK_INCLUDE) \
	$(NDK_SYSROOT_INC) \
	$(NDK_SYSROOT_ARCH_INC) \
	-I${SYSROOT}/usr/include $(SYS_ROOT) -I${NDK_ROOT}/sources/android/support/include \
	-I${NDK_ROOT}/sources/android/cpufeatures -I${LIMBO_JNI_ROOT}/compat " \
	LDFLAGS="$(ARCH_LD_FLAGS) $(SYS_ROOT) \
	-L${LIMBO_JNI_ROOT}/../obj/local/$(APP_ABI) -lc -llog \
	-lcompat-limbo "

pixman: ndk
	cd $(LIMBO_JNI_ROOT)/pixman/pixman && $(MAKE) -j $(BUILD_THREADS) \
	CFLAGS="$(ARCH_CFLAGS) -I$(NDK_INCLUDE)  -I${SYSROOT}/usr/include \
	$(SYS_ROOT) -I${NDK_ROOT}/sources/android/support/include \
	$(NDK_SYSROOT_INC) \
	$(NDK_SYSROOT_ARCH_INC) \
	-I${NDK_ROOT}/sources/android/cpufeatures -I${LIMBO_JNI_ROOT}/compat " \
	LDFLAGS="$(ARCH_LD_FLAGS) $(SYS_ROOT) \
	-L${LIMBO_JNI_ROOT}/../obj/local/$(APP_ABI) -lc -llog \
	-lcompat-limbo "
	$(INSTALL_PIXMAN_LIBS)

clean-pixman:
	-find $(LIMBO_JNI_ROOT)/pixman -name "*.lo" -exec rm -rf {} \;
	-cd $(LIMBO_JNI_ROOT)/pixman && $(MAKE) distclean
	-cd $(LIMBO_JNI_ROOT)/pixman && $(MAKE) clean

# ---------------------------------------------------------------------------
# Optional display stacks (USE_GTK / USE_VIRGL, both default false)
#
# Order matters and is encoded in the dependencies:
#   libepoxy     -> GL/EGL loader, needs nothing but the NDK sysroot
#   GTK4         -> needs glib/pixman/libffi (from `deps`) + libepoxy
#   virglrenderer-> needs libepoxy (EGL platform) + glib; it does NOT need GTK
#
# They are built through the scripts/ helpers so the same recipe works from a
# local shell and from CI, and so the meson cross files stay out of the Makefile.
# ---------------------------------------------------------------------------

config-epoxy: ndk
	@if [ ! -d "$(LIMBO_JNI_ROOT)/libepoxy" ]; then \
		echo "ERROR: libepoxy sources missing (CI step 'Download GTK4 / libepoxy /"\n\
		      "virglrenderer sources' fetches them)"; exit 1; \
	fi

epoxy: ndk config-epoxy
	bash $(LIMBO_PRJ_ROOT)/scripts/build_libepoxy.sh $(LIMBO_JNI_ROOT) $(NDK_ROOT) $(APP_ABI)

clean-epoxy:
	-rm -rf "$(LIMBO_JNI_ROOT)/libepoxy/build" "$(EPOXY_INSTALL_DIR)"
	-rm -f "$(LIMBO_JNI_ROOT)/epoxy-cross.txt"

# GTK 4.19+ requires GLib >= 2.84, but QEMU is built against 2.76.6 and that
# ABI must not move. Building 2.89.3 into its own prefix lets meson resolve
# GTK's glib-2.0 dependency through pkg-config while QEMU keeps its GLib.
# See scripts/build_glib289.sh for why the two copies never meet in one link.
config-glib289: ndk
	@if [ ! -f "$(LIMBO_JNI_ROOT)/glib289/meson.build" ]; then \
		echo "ERROR: glib 2.89.3 sources missing (CI fetches them into glib289)"; exit 1; \
	fi

glib289: ndk
	bash $(LIMBO_PRJ_ROOT)/scripts/build_glib289.sh $(LIMBO_JNI_ROOT) $(NDK_ROOT) $(APP_ABI)

clean-glib289:
	-rm -rf "$(LIMBO_JNI_ROOT)/glib289/build-android" "$(LIMBO_JNI_ROOT)/glib289/install-android"
	-rm -f "$(LIMBO_JNI_ROOT)/glib289-cross.txt"

config-gtk: ndk epoxy glib289
	@if [ "$(APP_ABI)" != "arm64-v8a" ]; then \
		echo "ERROR: the GDK android backend only builds for arm64-v8a (got $(APP_ABI))"; \
		exit 1; \
	fi
	@if [ ! -d "$(LIMBO_JNI_ROOT)/gtk4android" ]; then \
		echo "ERROR: gtk4android sources missing (CI step 'Download GTK4 /"\n\
		      "libepoxy / virglrenderer sources' fetches them)"; exit 1; \
	fi

gtk: ndk epoxy
	bash $(LIMBO_PRJ_ROOT)/scripts/build_gtk4android.sh $(LIMBO_JNI_ROOT) $(NDK_ROOT) $(APP_ABI)
	# GTK ships a dozen inter-dependent .so files; the script puts them in
	# obj/local so the normal INSTALL_LIBS -> jniLibs chain packs them into the
	# APK next to libqemu-system-*.so (QEMU dlopens libgtk-4.so from there).

clean-gtk:
	-rm -rf "$(LIMBO_JNI_ROOT)/gtk4android/build-android" "$(LIMBO_JNI_ROOT)/gtk4android/install-android"
	-rm -rf "$(LIMBO_JNI_ROOT)/gtk4android/build-android.subprojects-save"
	-rm -f "$(LIMBO_JNI_ROOT)/gtk4-cross.txt"

config-virgl: ndk epoxy
	@if [ ! -d "$(LIMBO_JNI_ROOT)/virglrenderer" ]; then \
		echo "ERROR: virglrenderer sources missing (CI step 'Download GTK4 /"\n\
		      "libepoxy / virglrenderer sources' fetches them)"; exit 1; \
	fi

virgl: ndk epoxy
	USE_VIRGL_VENUS=$(USE_VIRGL_VENUS) \
		bash $(LIMBO_PRJ_ROOT)/scripts/build_virglrenderer.sh $(LIMBO_JNI_ROOT) $(NDK_ROOT) $(APP_ABI)
	# Keep the renderer next to the qemu lib in obj/local as well: the DT_NEEDED
	# rewrite below and the APK both work off that directory.
	cp -Lf $(VIRGL_INSTALL_DIR)/lib/libvirglrenderer.so* $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/ || true

clean-virgl:
	-rm -rf "$(LIMBO_JNI_ROOT)/virglrenderer/build-android" "$(LIMBO_JNI_ROOT)/virglrenderer/install-android"
	-rm -f "$(LIMBO_JNI_ROOT)/virgl-cross.txt"

_qemu: ndk
	cd $(LIMBO_JNI_ROOT)/qemu && $(MAKE) all -j $(BUILD_THREADS) BUILD_GUEST=$(BUILD_GUEST) CFLAGS="$(ARCH_CFLAGS)"

config-qemu: ndk
	$(MAKE) -f $(LIMBO_JNI_ROOT)/android-config/android-qemu-config.mak

clean-qemu:
	-cd $(LIMBO_JNI_ROOT)/qemu && $(MAKE) distclean
	-cd $(LIMBO_JNI_ROOT)/qemu && $(MAKE) clean
	-rm $(NDK_PROJECT_PATH)/obj/local/arm64-v8a/libqemu-system-x86_64.a
	-rm -rf ../assets/roms/* 
	-rm -rf $(LIMBO_APP_X86)/assets/roms/*
	-rm -rf $(LIMBO_APP_ARM)/assets/roms/*
	-rm -rf $(LIMBO_APP_PPC)/assets/roms/*
	-rm -rf $(LIMBO_APP_SPARC)/assets/roms/*

config-deps: ndk config-ffi config-glib config-pixman
	@if [ "$(USE_GTK)" = "true" ] || [ "$(USE_VIRGL)" = "true" ]; then \
		$(MAKE) config-epoxy; \
	fi

deps: ndk ffi glib pixman
	# GTK resolves its glib-2.0 through pkg-config against the 2.89 prefix, so
	# it has to be built before gtk; QEMU's own glib stays 2.76.6.
	@if [ "$(USE_GTK)" = "true" ]; then $(MAKE) glib289; fi
	@if [ "$(USE_VIRGL)" = "true" ]; then $(MAKE) virgl; fi
	@if [ "$(USE_GTK)" = "true" ]; then $(MAKE) gtk; fi

_build-qemu: ndk 
	make _qemu
	# Optional stacks: Android's linker resolves a DT_NEEDED entry by the exact
	# file name, and meson/ld default to the SONAME-versioned spelling
	# (libepoxy.so.0, libgtk-4.so.1, ...) while the APK only ships the
	# unversioned lib<name>.so. Rewrite the dependency entries in place; all
	# the replacements shorten the string, so dynstr never moves (see
	# scripts/fix_needed.py for why that matters on Huawei devices).
	@if [ "$(USE_GTK)" = "true" ] || [ "$(USE_VIRGL)" = "true" ]; then \
		for so in $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/*.so; do \
			python3 $(LIMBO_PRJ_ROOT)/scripts/fix_needed.py "$$so" \
				libepoxy.so.0=libepoxy.so \
				libgtk-4.so.1=libgtk-4.so \
				libgdk_pixbuf-2.0.so.0=libgdk_pixbuf-2.0.so \
				libcairo.so.2=libcairo.so \
				libcairo-gobject.so.2=libcairo-gobject.so \
				libpango-1.0.so.0=libpango-1.0.so \
				libpangocairo-1.0.so.0=libpangocairo-1.0.so \
				libpangoft2-1.0.so.0=libpangoft2-1.0.so \
				libharfbuzz.so.0=libharfbuzz.so \
				libfreetype.so.6=libfreetype.so \
				libfontconfig.so.1=libfontconfig.so \
				libfribidi.so.0=libfribidi.so \
				libgraphene-1.0.so.0=libgraphene-1.0.so \
				libgdk-4.so.1=libgdk-4.so \
				libgsk-4.so.1=libgsk-4.so \
				libvirglrenderer.so.1=libvirglrenderer.so \
				libglib-2.0.so.0=libglib-2.0.so \
				libgmodule-2.0.so.0=libgmodule-2.0.so \
				libgobject-2.0.so.0=libgobject-2.0.so \
				libgio-2.0.so.0=libgio-2.0.so \
				libintl.so.8=libintl.so \
				libffi.so.7=libffi.so \
				libffi.so.8=libffi.so \
				2>/dev/null || true; \
		done; \
		echo "[OK] DT_NEEDED rewritten to unversioned GTK/GL library names"; \
	fi
	# QEMU 8.x: meson/ninja outputs the emulator as a shared lib into qemu/build/
	# copy it into the NDK obj dir (file IO redirect is done via -Wl,--wrap in
	# fix_qemu805.py, no objcopy --redefine-sym needed for .so)
	@if [ -f qemu/build/qemu-system-$(GUEST_SHORT).so ]; then \
		cp -f qemu/build/qemu-system-$(GUEST_SHORT).so $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/libqemu-system-$(GUEST_SHORT).so; \
		echo "[OK] QEMU8.x .so copied"; \
		echo "[OK] DT_NEEDED glib -> unversioned name (in-place, no dynstr move)"; \
		python3 $(LIMBO_PRJ_ROOT)/scripts/fix_needed.py $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/libqemu-system-$(GUEST_SHORT).so libglib-2.0.so.0=libglib-2.0.so || true; \
		echo "[OK] DT_NEEDED gmodule -> unversioned glib name"; \
		python3 $(LIMBO_PRJ_ROOT)/scripts/fix_needed.py $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/libgmodule-2.0.so libglib-2.0.so.0=libglib-2.0.so || true; \
		echo "[OK] using prebuilt 8.0.5 libcompat-musl.so (Huawei linker cannot load any freshly-built musl: verneed[0] failure)"; \
		cp -f $(LIMBO_PRJ_ROOT)/tools/prebuilt/libcompat-musl-805.so $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/libcompat-musl.so; \
		echo "[OK] objcopy classic layout on all libs (sh_addr==sh_offset for Huawei linker)"; \
		for so in $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/*.so; do \
			objcopy --strip-debug "$$so" 2>/dev/null || true; \
		done; \
	fi
	# Final DT_NEEDED pass, unconditionally and immediately before the
	# libraries are copied out of obj/local.
	#
	# The pass above runs after `make _qemu`, but QEMU 11 emits
	# libqemu-system-*.so from its own make rules, which can finish after that
	# point - so the engine binary kept its versioned spellings
	# (libgio-2.0.so.0, libgobject-2.0.so.0, libintl.so.8) while the APK only
	# ships the unversioned names. That is precisely the "no screen for any
	# backend, no log line" failure. Rewriting here covers every library in
	# obj/local no matter which target produced it.
	@for so in $(NDK_PROJECT_PATH)/obj/local/$(APP_ABI)/*.so; do \
		[ -f "$$so" ] || continue; \
		python3 $(LIMBO_PRJ_ROOT)/scripts/fix_needed.py "$$so" \
			libglib-2.0.so.0=libglib-2.0.so \
			libgmodule-2.0.so.0=libgmodule-2.0.so \
			libgobject-2.0.so.0=libgobject-2.0.so \
			libgio-2.0.so.0=libgio-2.0.so \
			libintl.so.8=libintl.so \
			libffi.so.7=libffi.so \
			libffi.so.8=libffi.so \
			libepoxy.so.0=libepoxy.so \
			libgtk-4.so.1=libgtk-4.so \
			libgdk-4.so.1=libgdk-4.so \
			libgsk-4.so.1=libgsk-4.so \
			libgdk_pixbuf-2.0.so.0=libgdk_pixbuf-2.0.so \
			libcairo.so.2=libcairo.so \
			libcairo-gobject.so.2=libcairo-gobject.so \
			libpango-1.0.so.0=libpango-1.0.so \
			libpangocairo-1.0.so.0=libpangocairo-1.0.so \
			libpangoft2-1.0.so.0=libpangoft2-1.0.so \
			libharfbuzz.so.0=libharfbuzz.so \
			libfreetype.so.6=libfreetype.so \
			libfontconfig.so.1=libfontconfig.so \
			libfribidi.so.0=libfribidi.so \
			libgraphene-1.0.so.0=libgraphene-1.0.so \
			libvirglrenderer.so.1=libvirglrenderer.so \
			>/dev/null 2>&1 || true; \
	done; \
	echo "[OK] final DT_NEEDED pass over obj/local"

	$(INSTALL_LIBS)
	$(RENAME_TUNCTL)
	$(RENAME_PARPROUTED)
	$(COPY_ROMS)
	$(COPY_KEYMAPS)
	$(COPY_QEMU_VERSION)
	$(COPY_CAPABILITIES)
	$(COPY_RESOLV_CONF)

qemu: ndk
	make _build-qemu
	make install

install-common:
	$(CREATE_LIBSDIR)
	$(INSTALL_LIBS)
	$(STRIP_LIBS)
	$(SYNC_LIBS)

install:
	make install-common
	$(SYNC_LIBS_X86)
	$(COPY_ASSETS_X86)
	$(SYNC_LIBS_ARM)
	$(COPY_ASSETS_ARM)
	$(SYNC_LIBS_PPC)
	$(COPY_ASSETS_PPC)
	$(SYNC_LIBS_SPARC)
	$(COPY_ASSETS_SPARC)

limbo:
	make ndk
	make config-deps
	make deps
	make config-libslirp
	make libslirp
	make config-qemu
	make qemu
	make install

EXPORT_GUEST_ALL=export BUILD_GUEST=x86_64-softmmu,aarch64-softmmu,ppc64-softmmu,sparc-softmmu

#most common combinations
all:
	make distclean && $(EXPORT_GUEST_ALL) && export BUILD_HOST=arm64-v8a && make limbo
	make distclean && $(EXPORT_GUEST_ALL) && export BUILD_HOST=armeabi-v7a && make limbo
	make distclean && $(EXPORT_GUEST_ALL) && export BUILD_HOST=x86 && make limbo
	make distclean && $(EXPORT_GUEST_ALL) && export BUILD_HOST=x86_64 && make limbo

ndk-gdb:
	@echo APP_ABI=$(APP_ABI)
	$(COPY_GDB_SETUP_LIBS)
	$(ndkgdb) --attach $(PKG_NAME) --verbose -t

distclean: clean-qemu clean-ffi clean-glib clean-pixman

clean: 
	make clean-qemu 
	make clean-ffi 
	make clean-glib 
	make clean-pixman
	make clean-ndk
	-find . -name "*.d" -exec rm -rf {} \;
	-find . -name "*.a" -exec rm -rf {} \;
	-find . -name "*.o" -exec rm -rf {} \;
	-find . -name "*.so" -exec rm -rf {} \;
	$(MAKE) createdirs
	
help:
	@echo " Usage: export BUILD_GUEST=<emu-arch>-softmmu && export BUILD_HOST=<eabi> && make limbo"
	@echo "  emu-arch (emulator): x86_64, aarch64, ppc64, sparc"
	@echo "  eabi (device): armeabi-v7a, arm64-v8a, x86, x86_64"
	@echo "  optional: export NDK_DEBUG=1 for debuggable binaries"
	@echo " example: export BUILD_GUEST=x86_64-softmmu && export BUILD_HOST=armeabi-v7a && make limbo"
