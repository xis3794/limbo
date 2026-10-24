package com.max2idea.android.limbo.qemu;

import java.io.File;
import java.util.ArrayList;
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

    public QemuVersion(String id, String version, String series, String displayName,
                       String description, String abi, String qemuLib,
                       List<String> loadOrder, File dir, boolean builtin) {
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
}
