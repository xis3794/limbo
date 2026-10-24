package com.max2idea.android.limbo.qemu;

import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Minimal tar.xz extractor for the QEMU version packages. Only regular files and
 * directories are extracted; entries are validated so a crafted archive cannot
 * escape the destination directory.
 */
public final class TarXzExtractor {

    public interface Progress {
        void onBytes(long bytes);
    }

    private static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024; // 512 MB safety cap

    private TarXzExtractor() {
    }

    public static void extract(File archive, File destination, Progress progress) throws IOException {
        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Cannot create " + destination);
        }
        String destCanonical = destination.getCanonicalPath();
        long total = 0;
        try (InputStream raw = new BufferedInputStream(new FileInputStream(archive), 64 * 1024);
             XZInputStream xz = new XZInputStream(raw)) {
            byte[] header = new byte[512];
            byte[] longName = null;
            while (true) {
                int read = readFully(xz, header, 512);
                if (read == 0) {
                    break;
                }
                if (read < 512) {
                    throw new IOException("Truncated tar header");
                }
                if (allZero(header)) {
                    break;
                }
                String name = parseName(header);
                if (longName != null) {
                    name = new String(longName, "UTF-8");
                    longName = null;
                }
                long size = parseSize(header);
                char type = (char) (header[156] & 0xff);
                if (type == 'L') {
                    longName = readEntryData(xz, size);
                    continue;
                }
                if (type == 'x' || type == 'g') {
                    skipFully(xz, size);
                    continue;
                }
                total += size;
                if (total > MAX_TOTAL_BYTES) {
                    throw new IOException("Archive too large");
                }
                File out = new File(destination, name);
                String canonical = out.getCanonicalPath();
                if (!canonical.equals(destCanonical) && !canonical.startsWith(destCanonical + File.separator)) {
                    throw new IOException("Unsafe path in archive: " + name);
                }
                if (type == '5' || name.endsWith("/")) {
                    if (!out.isDirectory() && !out.mkdirs() && !out.isDirectory()) {
                        throw new IOException("Cannot create " + out);
                    }
                    continue;
                }
                if (type != '0' && type != '\0' && type != '7') {
                    // symlinks/devices are not used by version packages
                    skipFully(xz, size);
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Cannot create " + parent);
                }
                try (OutputStream os = new FileOutputStream(out)) {
                    copyFully(xz, os, size, progress);
                }
            }
        }
    }

    private static String parseName(byte[] h) {
        String name = cString(h, 0, 100);
        String prefix = cString(h, 345, 155);
        if (prefix.length() > 0) {
            return prefix + "/" + name;
        }
        return name;
    }

    private static long parseSize(byte[] h) {
        long v = parseOctal(h, 124, 12);
        if (v < 0) {
            // GNU base-256 encoding
            int first = h[124] & 0xff;
            if ((first & 0x80) != 0) {
                v = first & 0x7f;
                for (int i = 125; i < 136; i++) {
                    v = (v << 8) | (h[i] & 0xff);
                }
            } else {
                v = 0;
            }
        }
        return v;
    }

    private static long parseOctal(byte[] buf, int off, int len) {
        long value = 0;
        int i = off;
        int end = off + len;
        while (i < end && (buf[i] == ' ' || buf[i] == 0)) {
            i++;
        }
        boolean any = false;
        while (i < end) {
            int b = buf[i] & 0xff;
            if (b == 0 || b == ' ') {
                break;
            }
            if (b < '0' || b > '7') {
                return -1;
            }
            any = true;
            value = (value << 3) + (b - '0');
            i++;
        }
        return any ? value : 0;
    }

    private static String cString(byte[] buf, int off, int len) {
        int end = off;
        while (end < off + len && buf[end] != 0) {
            end++;
        }
        try {
            return new String(buf, off, end - off, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean allZero(byte[] buf) {
        for (byte b : buf) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static int readFully(InputStream in, byte[] buf, int len) throws IOException {
        int read = 0;
        while (read < len) {
            int n = in.read(buf, read, len - read);
            if (n < 0) {
                break;
            }
            read += n;
        }
        return read;
    }

    private static void copyFully(InputStream in, OutputStream out, long len, Progress progress)
            throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long left = len;
        while (left > 0) {
            int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (n < 0) {
                throw new IOException("Truncated tar entry");
            }
            out.write(buffer, 0, n);
            left -= n;
            if (progress != null) {
                progress.onBytes(n);
            }
        }
        skipFully(in, (512 - (len % 512)) % 512);
    }

    private static void skipFully(InputStream in, long len) throws IOException {
        long left = len;
        while (left > 0) {
            long n = in.skip(left);
            if (n <= 0) {
                if (in.read() < 0) {
                    throw new IOException("Truncated tar entry");
                }
                n = 1;
            }
            left -= n;
        }
    }

    private static byte[] readEntryData(InputStream in, long size) throws IOException {
        if (size <= 0 || size > 64 * 1024) {
            skipFully(in, size + (512 - (size % 512)) % 512);
            return null;
        }
        byte[] data = new byte[(int) size];
        int read = readFully(in, data, data.length);
        if (read < data.length) {
            throw new IOException("Truncated long name entry");
        }
        skipFully(in, (512 - (size % 512)) % 512);
        return data;
    }
}
