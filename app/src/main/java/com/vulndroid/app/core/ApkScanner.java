package com.vulndroid.app.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Small, dependency-free APK string scanner used by the hardcoded-secrets lab.
 * This is the same idea as running `strings` over the DEX files in the APK, just in-app.
 */
public final class ApkScanner {

    public static final class Hit {
        public final String entryName;
        public final long offset;
        public final int occurrences;

        Hit(String entryName, long offset, int occurrences) {
            this.entryName = entryName;
            this.offset = offset;
            this.occurrences = occurrences;
        }
    }

    private static final long MAX_ENTRY_BYTES = 48L * 1024L * 1024L;

    private ApkScanner() {
    }

    public static Hit findString(File apk, String needle) throws IOException {
        if (apk == null || !apk.isFile() || needle == null || needle.isEmpty()) {
            return null;
        }
        byte[] target = needle.getBytes("UTF-8");
        try (ZipInputStream zip = new ZipInputStream(new java.io.BufferedInputStream(
                new java.io.FileInputStream(apk)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] data = readLimited(zip, MAX_ENTRY_BYTES);
                if (data == null) {
                    continue;
                }
                int offset = indexOf(data, target);
                if (offset >= 0) {
                    int count = 1;
                    int cursor = offset + 1;
                    while (cursor < data.length) {
                        int next = indexOf(data, target, cursor);
                        if (next < 0) {
                            break;
                        }
                        count++;
                        cursor = next + 1;
                    }
                    return new Hit(entry.getName(), offset, count);
                }
            }
        }
        return null;
    }

    private static byte[] readLimited(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        return indexOf(haystack, needle, 0);
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        if (needle.length == 0) {
            return from;
        }
        outer:
        for (int i = Math.max(0, from); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
