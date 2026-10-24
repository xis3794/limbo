package com.max2idea.android.limbo.qemu;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.max2idea.android.limbo.files.FileUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Installs and tracks downloadable QEMU builds. Each package is a tar.xz with a
 * manifest.json and the .so files; the active build is used the next time the app
 * process starts.
 */
public final class QemuVersionManager {
    private static final String TAG = "QemuVersionManager";
    private static final String PREFS = "limbo_qemu_versions";
    private static final String KEY_ACTIVE = "active_id";
    private static final String CATALOG_URL =
            "https://raw.githubusercontent.com/xis3794/limbo-qemu/main/catalog.json";
    private static final String DIR_NAME = "qemu";

    public interface Progress {
        void onProgress(long bytes);
    }

    public static final class CatalogEntry {
        public String id;
        public String version;
        public String series;
        public String displayName;
        public String description;
        public String url;
        public String sha256;
        public long size;
        public boolean published;
    }

    private QemuVersionManager() {
    }

    public static File root(Context context) {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static QemuVersion builtin(Context context) {
        String version = "11.1.0";
        try {
            String v = FileUtils.LoadFile(context, "QEMU_VERSION", false);
            if (v != null && v.trim().length() > 0) {
                version = v.trim();
            }
        } catch (Exception ignored) {
        }
        return new QemuVersion(QemuVersion.BUILTIN_ID, version, "11",
                "内置 QEMU " + version,
                "随应用打包的 QEMU 版本，无法删除。",
                "arm64-v8a", "libqemu-system-x86_64.so",
                new ArrayList<String>(), null, true);
    }

    public static List<QemuVersion> getInstalled(Context context) {
        List<QemuVersion> versions = new ArrayList<>();
        versions.add(builtin(context));
        File root = root(context);
        File[] dirs = root.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                if (!dir.isDirectory() || dir.getName().startsWith(".")) {
                    continue;
                }
                try {
                    QemuVersion version = readManifest(dir);
                    if (version != null) {
                        versions.add(version);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Broken QEMU package " + dir, e);
                }
            }
        }
        Collections.sort(versions, new Comparator<QemuVersion>() {
            @Override
            public int compare(QemuVersion a, QemuVersion b) {
                if (a.builtin != b.builtin) {
                    return a.builtin ? -1 : 1;
                }
                return b.version.compareToIgnoreCase(a.version);
            }
        });
        return versions;
    }

    private static QemuVersion readManifest(File dir) throws Exception {
        File manifest = new File(dir, "manifest.json");
        if (!manifest.isFile()) {
            return null;
        }
        String json = readText(manifest);
        JSONObject o = new JSONObject(json);
        String id = o.optString("id", dir.getName());
        String version = o.optString("version", id);
        String series = o.optString("series", "");
        String name = o.optString("display_name", "QEMU " + version);
        String description = o.optString("description", "");
        String abi = o.optString("abi", "arm64-v8a");
        String qemuLib = o.optString("qemu_lib", "libqemu-system-x86_64.so");
        ArrayList<String> loadOrder = new ArrayList<>();
        JSONArray order = o.optJSONArray("load_order");
        if (order != null) {
            for (int i = 0; i < order.length(); i++) {
                loadOrder.add(order.getString(i));
            }
        }
        if (!new File(new File(dir, "lib"), qemuLib).isFile()
                && !new File(dir, qemuLib).isFile()) {
            return null;
        }
        return new QemuVersion(id, version, series, name, description, abi,
                qemuLib, loadOrder, dir, false);
    }

    public static QemuVersion getActive(Context context) {
        String id = prefs(context).getString(KEY_ACTIVE, QemuVersion.BUILTIN_ID);
        for (QemuVersion v : getInstalled(context)) {
            if (v.id.equals(id)) {
                return v;
            }
        }
        setActive(context, QemuVersion.BUILTIN_ID);
        return builtin(context);
    }

    public static void setActive(Context context, String id) {
        prefs(context).edit().putString(KEY_ACTIVE, id == null ? QemuVersion.BUILTIN_ID : id).apply();
    }

    public static QemuVersion install(Context context, File archive, Progress progress) throws Exception {
        File root = root(context);
        File temp = new File(root, ".tmp-" + System.currentTimeMillis());
        if (!temp.mkdirs()) {
            throw new IOException("无法创建临时目录");
        }
        try {
            TarXzExtractor.extract(archive, temp, new TarXzExtractor.Progress() {
                @Override
                public void onBytes(long bytes) {
                    if (progress != null) {
                        progress.onProgress(bytes);
                    }
                }
            });
            File manifestDir = findManifestDir(temp);
            if (manifestDir == null) {
                throw new IOException("包内缺少 manifest.json");
            }
            QemuVersion parsed = readManifest(manifestDir);
            if (parsed == null) {
                throw new IOException("包内缺少 qemu 静态库或 manifest 无效");
            }
            String id = sanitize(parsed.id);
            if (id == null || id.length() == 0) {
                throw new IOException("包 id 无效");
            }
            File finalDir = new File(root, id);
            if (finalDir.exists()) {
                deleteRecursive(finalDir);
            }
            if (!manifestDir.renameTo(finalDir)) {
                copyRecursive(manifestDir, finalDir);
                deleteRecursive(manifestDir);
            }
            QemuVersion version = readManifest(finalDir);
            if (version == null) {
                throw new IOException("安装后校验失败");
            }
            return version;
        } finally {
            if (temp.exists()) {
                deleteRecursive(temp);
            }
        }
    }

    private static File findManifestDir(File dir) {
        if (new File(dir, "manifest.json").isFile()) {
            return dir;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && new File(child, "manifest.json").isFile()) {
                    return child;
                }
            }
        }
        return null;
    }

    public static boolean delete(Context context, String id) {
        if (id == null || QemuVersion.BUILTIN_ID.equals(id)) {
            return false;
        }
        String safe = sanitize(id);
        if (safe == null) {
            return false;
        }
        File dir = new File(root(context), safe);
        boolean deleted = dir.isDirectory() && deleteRecursive(dir);
        if (deleted && id.equals(prefs(context).getString(KEY_ACTIVE, ""))) {
            setActive(context, QemuVersion.BUILTIN_ID);
        }
        return deleted;
    }

    public static List<CatalogEntry> fetchCatalog() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(CATALOG_URL).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Limbo-Android");
            InputStream in = connection.getInputStream();
            String json = readText(in);
            JSONObject root = new JSONObject(json);
            JSONArray packages = root.optJSONArray("packages");
            List<CatalogEntry> entries = new ArrayList<>();
            if (packages == null) {
                return entries;
            }
            for (int i = 0; i < packages.length(); i++) {
                JSONObject o = packages.getJSONObject(i);
                CatalogEntry entry = new CatalogEntry();
                entry.id = o.optString("id");
                entry.version = o.optString("version");
                entry.series = o.optString("series");
                entry.displayName = o.optString("display_name", "QEMU " + entry.version);
                entry.description = o.optString("description");
                entry.url = o.optString("url");
                entry.sha256 = o.optString("sha256");
                entry.size = o.optLong("size", 0);
                entry.published = o.optBoolean("published", false);
                entries.add(entry);
            }
            return entries;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static File download(Context context, CatalogEntry entry, Progress progress) throws Exception {
        if (entry == null || entry.url == null || entry.url.length() == 0) {
            throw new IOException("下载地址为空");
        }
        String safeId = sanitize(entry.id);
        File target = new File(context.getCacheDir(),
                "qemu-" + (safeId != null ? safeId : "package") + ".tar.xz");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(entry.url).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Limbo-Android");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            long total = connection.getContentLength();
            try (InputStream in = new BufferedInputStream(connection.getInputStream(), 64 * 1024);
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    done += n;
                    if (progress != null) {
                        progress.onProgress(total > 0 ? (done * 100 / total) : done);
                    }
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        if (entry.sha256 != null && entry.sha256.length() > 0) {
            String actual = sha256(target);
            if (!entry.sha256.equalsIgnoreCase(actual)) {
                target.delete();
                throw new IOException("校验失败，请重试");
            }
        }
        return target;
    }

    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static long directorySize(File dir) {
        if (dir == null) {
            return 0;
        }
        if (dir.isFile()) {
            return dir.length();
        }
        long size = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                size += directorySize(child);
            }
        }
        return size;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String sanitize(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            return null;
        }
        return id;
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    private static void copyRecursive(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) {
                throw new IOException("无法创建 " + dst);
            }
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursive(child, new File(dst, child.getName()));
                }
            }
        } else {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dst)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
            }
        }
    }

    private static String readText(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readText(in);
        }
    }

    private static String readText(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            char[] buffer = new char[8192];
            int n;
            while ((n = reader.read(buffer)) > 0) {
                sb.append(buffer, 0, n);
            }
        }
        return sb.toString();
    }
}
