package com.newchar.debug.device.disk;

import java.util.Locale;

final class DiskFormat {

    static final long KB = 1024L;
    static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;

    private DiskFormat() {
    }

    static String bytes(long bytes) {
        if (bytes < 0) {
            return "不可用";
        }
        if (bytes >= GB) {
            return String.format(Locale.US, "%.2f GB", bytes * 1f / GB);
        }
        if (bytes >= MB) {
            return String.format(Locale.US, "%.2f MB", bytes * 1f / MB);
        }
        if (bytes >= KB) {
            return String.format(Locale.US, "%.2f KB", bytes * 1f / KB);
        }
        return bytes + " B";
    }
}
