package com.max2idea.android.limbo.qemu;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One installable QEMU build (or the version bundled with the APK). */
public class QemuVersion {
    public static final String BUILTIN_ID = "builtin";

    public final String id;
    public final String version;
    public final String series;
    public final String displayName;
    public final String description;
    public final String abi;
    public final String qemuLib;
    public final List<String> loadOrder;
    public final File dir;
    public final boolean builtin;

    /**
     * What the packaged QEMU core can actually do. Optional: packages made with
     * older tools have no "capabilities" block, in which case this is null and
     * the UI falls back to the static lists shipped in the APK.
     */
    public final Capabilities capabilities;

    /**
     * Capability block of manifest.json. A downloaded QEMU 8 cannot run the
     * machine models QEMU 11 added (and vice versa), so the settings screen has
     * to know which values the active build really accepts; otherwise picking
     * "pc-q35-11.1" would make QEMU print "unsupported machine type" and
     * exit(1) before the VM ever starts.
     */
    public static final class Capabilities {
        public final int versionCode;
        public final List<String> machineTypes;
        public final List<String> cpus;
        public final List<String> vga;
        public final List<String> soundcards;
        public final List<String> nicCards;
        public final List<String> displays;
        /**
         * Whether {@code -vnc ...,password=on} can work on this build.
         *
         * QEMU validates the VNC password with
         * {@code qcrypto_cipher_supports(QCRYPTO_CIPHER_ALG_DES, ECB)} and
         * aborts with "Cipher backend does not support DES algorithm" when the
         * build has no crypto backend. 5.1.0 still ships the built-in
         * crypto/desrfb.c, 8.0.5's cipher-builtin only knows AES, and 10.x/11.x
         * use cipher-stub (supports() == false), so enabling the password on
         * those builds would stop the VM from starting at all.
         */
        public final boolean vncPassword;

        public Capabilities(int versionCode, List<String> machineTypes, List<String> cpus,
                            List<String> vga, List<String> soundcards,
                            List<String> nicCards, List<String> displays) {
            this(versionCode, machineTypes, cpus, vga, soundcards, nicCards, displays, true);
        }

        public Capabilities(int versionCode, List<String> machineTypes, List<String> cpus,
                            List<String> vga, List<String> soundcards,
                            List<String> nicCards, List<String> displays,
                            boolean vncPassword) {
            this.versionCode = versionCode;
            this.machineTypes = safe(machineTypes);
            this.cpus = safe(cpus);
            this.vga = safe(vga);
            this.soundcards = safe(soundcards);
            this.nicCards = safe(nicCards);
            this.displays = safe(displays);
            this.vncPassword = vncPassword;
        }

        private static List<String> safe(List<String> in) {
            return in != null ? Collections.unmodifiableList(new ArrayList<String>(in))
                    : Collections.<String>emptyList();
        }

        public boolean hasMachineTypes() {
            return !machineTypes.isEmpty();
        }

        public boolean hasCpus() {
            return !cpus.isEmpty();
        }

        /** Machine names this build accepts (case-insensitive). */
        public boolean supportsMachineType(String machineType) {
            return contains(machineTypes, machineType);
        }

        /** x86 CPU model names this build accepts (case-insensitive). */
        public boolean supportsCpu(String cpu) {
            return contains(cpus, cpu);
        }

        public boolean supportsVga(String name) {
            return contains(vga, name);
        }

        public boolean supportsSoundcard(String name) {
            return contains(soundcards, name);
        }

        public boolean supportsNic(String name) {
            return contains(nicCards, name);
        }

        /** True when {@code -vnc ...,password=on} is usable on this build. */
        public boolean supportsVncPassword() {
            return vncPassword;
        }

        private static boolean contains(List<String> list, String value) {
            if (value == null) {
                return false;
            }
            for (String item : list) {
                if (item.equalsIgnoreCase(value)) {
                    return true;
                }
            }
            return false;
        }
    }

    public QemuVersion(String id, String version, String series, String displayName,
                       String description, String abi, String qemuLib,
                       List<String> loadOrder, File dir, boolean builtin) {
        this(id, version, series, displayName, description, abi, qemuLib,
                loadOrder, dir, builtin, null);
    }

    public QemuVersion(String id, String version, String series, String displayName,
                       String description, String abi, String qemuLib,
                       List<String> loadOrder, File dir, boolean builtin,
                       Capabilities capabilities) {
        this.id = id;
        this.version = version;
        this.series = series;
        this.displayName = displayName;
        this.description = description;
        this.abi = abi;
        this.qemuLib = qemuLib;
        this.loadOrder = loadOrder != null ? loadOrder : new ArrayList<String>();
        this.dir = dir;
        this.builtin = builtin;
        this.capabilities = capabilities;
    }

    public File libDir() {
        if (builtin || dir == null) {
            return null;
        }
        File lib = new File(dir, "lib");
        return lib.isDirectory() ? lib : dir;
    }

    public String getSummary() {
        return version + (builtin ? " · built-in" : "");
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public int machineTypeCount() {
        return capabilities == null ? 0 : capabilities.machineTypes.size();
    }

    public int cpuCount() {
        return capabilities == null ? 0 : capabilities.cpus.size();
    }
}
