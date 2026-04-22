package com.newchar.debug.deviceview.disk;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DiskRepository {
    private static final long LARGE_FILE_THRESHOLD = 10L * DiskFormat.MB;

    private DiskRepository() {
    }

    static DiskSnapshot scan(Context context, List<DiskEvent> recentEvents, File logFile) {
        DiskSnapshot snapshot = new DiskSnapshot();
        snapshot.readExternalStorageGranted = hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= 33) {
            snapshot.readMediaImagesGranted = hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            snapshot.manageAllFilesGranted = Environment.isExternalStorageManager();
        }
        snapshot.externalStorageMounted = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        fillStorageStats(snapshot);
        if (logFile != null) {
            snapshot.monitorLogPath = logFile.getAbsolutePath();
        }
        List<DiskScope> scopes = buildScopes(context);
        for (DiskScope scope : scopes) {
            if (scope.dir == null) {
                continue;
            }
            snapshot.scopes.add(scope.name + " : " + scope.dir.getAbsolutePath());
            scanDir(scope.dir, snapshot, new HashSet<String>());
        }
        if (recentEvents != null) {
            for (DiskEvent event : recentEvents) {
                snapshot.recentEvents.add(formatEvent(event));
            }
        }
        return snapshot;
    }

    static List<DiskScope> buildScopes(Context context) {
        List<DiskScope> scopes = new ArrayList<>();
        if (context == null) {
            return scopes;
        }
        addScope(scopes, "内部 files", context.getFilesDir());
        addScope(scopes, "内部 cache", context.getCacheDir());
        addScope(scopes, "外部 files", context.getExternalFilesDir(null));
        addScope(scopes, "外部 cache", context.getExternalCacheDir());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            File[] mediaDirs = context.getExternalMediaDirs();
            if (mediaDirs != null) {
                for (int i = 0; i < mediaDirs.length; i++) {
                    addScope(scopes, "外部 media " + i, mediaDirs[i]);
                }
            }
        }
        return scopes;
    }

    private static void addScope(List<DiskScope> scopes, String name, File dir) {
        if (dir == null) {
            return;
        }
        try {
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Throwable ignored) {
        }
        if (dir.exists() && dir.isDirectory()) {
            scopes.add(new DiskScope(name, dir));
        }
    }

    private static void fillStorageStats(DiskSnapshot snapshot) {
        fillStat(Environment.getDataDirectory(), true, snapshot);
        fillStat(Environment.getExternalStorageDirectory(), false, snapshot);
    }

    private static void fillStat(File dir, boolean internal, DiskSnapshot snapshot) {
        if (dir == null) {
            return;
        }
        try {
            StatFs statFs = new StatFs(dir.getAbsolutePath());
            long total = statFs.getBlockCountLong() * statFs.getBlockSizeLong();
            long available = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
            if (internal) {
                snapshot.internalTotalBytes = total;
                snapshot.internalAvailableBytes = available;
            } else {
                snapshot.externalTotalBytes = total;
                snapshot.externalAvailableBytes = available;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void scanDir(File dir, DiskSnapshot snapshot, Set<String> visited) {
        if (dir == null || snapshot == null) {
            return;
        }
        String key;
        try {
            key = dir.getCanonicalPath();
        } catch (Throwable ignored) {
            key = dir.getAbsolutePath();
        }
        if (!visited.add(key)) {
            return;
        }
        File[] children;
        try {
            children = dir.listFiles();
        } catch (Throwable ignored) {
            return;
        }
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child == null) {
                continue;
            }
            if (child.isDirectory()) {
                snapshot.scopedDirCount++;
                scanDir(child, snapshot, visited);
                continue;
            }
            if (!child.isFile()) {
                continue;
            }
            long length = Math.max(0L, child.length());
            snapshot.scopedFileCount++;
            snapshot.scopedUsedBytes += length;
            String name = child.getName();
            if (isImage(name)) {
                snapshot.imageCount++;
            }
            if (isApk(name)) {
                snapshot.apkCount++;
            }
            if (length > LARGE_FILE_THRESHOLD) {
                snapshot.over10MCount++;
                snapshot.over10MBytes += length;
            }
        }
    }

    private static boolean isImage(String name) {
        String ext = extension(name);
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext)
                || "webp".equals(ext) || "gif".equals(ext) || "bmp".equals(ext)
                || "heic".equals(ext) || "heif".equals(ext);
    }

    private static boolean isApk(String name) {
        return "apk".equals(extension(name));
    }

    private static String extension(String name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        int index = name.lastIndexOf('.');
        if (index < 0 || index >= name.length() - 1) {
            return "";
        }
        return name.substring(index + 1).toLowerCase(Locale.US);
    }

    private static String formatEvent(DiskEvent event) {
        if (event == null) {
            return "";
        }
        return event.action + " : " + event.path;
    }

    private static boolean hasPermission(Context context, String permission) {
        if (context == null || TextUtils.isEmpty(permission)) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                PackageManager pm = context.getPackageManager();
                return pm != null && pm.checkPermission(permission, context.getPackageName())
                        == PackageManager.PERMISSION_GRANTED;
            }
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
