package com.newchar.debug.deviceview.disk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DiskSnapshot {
    boolean readExternalStorageGranted;
    boolean readMediaImagesGranted;
    boolean manageAllFilesGranted;
    boolean externalStorageMounted;
    long internalTotalBytes;
    long internalAvailableBytes;
    long externalTotalBytes;
    long externalAvailableBytes;
    long scopedUsedBytes;
    int scopedDirCount;
    int scopedFileCount;
    int imageCount;
    int apkCount;
    int over10MCount;
    long over10MBytes;
    String monitorLogPath;
    final List<String> scopes = new ArrayList<>();
    final List<String> recentEvents = new ArrayList<>();

    public boolean isReadExternalStorageGranted() {
        return readExternalStorageGranted;
    }

    public boolean isReadMediaImagesGranted() {
        return readMediaImagesGranted;
    }

    public boolean isManageAllFilesGranted() {
        return manageAllFilesGranted;
    }

    public boolean isExternalStorageMounted() {
        return externalStorageMounted;
    }

    public long getScopedUsedBytes() {
        return scopedUsedBytes;
    }

    public int getImageCount() {
        return imageCount;
    }

    public int getApkCount() {
        return apkCount;
    }

    public int getOver10MCount() {
        return over10MCount;
    }

    public List<String> getScopes() {
        return Collections.unmodifiableList(scopes);
    }

    public List<String> getRecentEvents() {
        return Collections.unmodifiableList(recentEvents);
    }

    public String toDisplayText() {
        StringBuilder builder = new StringBuilder();
        builder.append("磁盘监控\n");
        builder.append("外置存储挂载 : ").append(externalStorageMounted).append('\n');
        builder.append("READ_EXTERNAL_STORAGE : ").append(readExternalStorageGranted).append('\n');
        builder.append("READ_MEDIA_IMAGES : ").append(readMediaImagesGranted).append('\n');
        builder.append("MANAGE_EXTERNAL_STORAGE : ").append(manageAllFilesGranted).append('\n');
        builder.append("内部总量 : ").append(DiskFormat.bytes(internalTotalBytes)).append('\n');
        builder.append("内部可用 : ").append(DiskFormat.bytes(internalAvailableBytes)).append('\n');
        builder.append("外部总量 : ").append(DiskFormat.bytes(externalTotalBytes)).append('\n');
        builder.append("外部可用 : ").append(DiskFormat.bytes(externalAvailableBytes)).append('\n');
        builder.append("可管辖目录占用 : ").append(DiskFormat.bytes(scopedUsedBytes)).append('\n');
        builder.append("目录数 : ").append(scopedDirCount).append('\n');
        builder.append("文件数 : ").append(scopedFileCount).append('\n');
        builder.append("图片数量 : ").append(imageCount).append('\n');
        builder.append("APK 数量 : ").append(apkCount).append('\n');
        builder.append("超过 10M 文件 : ").append(over10MCount)
                .append(" / ").append(DiskFormat.bytes(over10MBytes)).append('\n');
        builder.append("事件日志 : ").append(monitorLogPath == null ? "暂无" : monitorLogPath).append('\n');
        builder.append("监控目录:");
        for (String scope : scopes) {
            builder.append('\n').append("- ").append(scope);
        }
        if (!recentEvents.isEmpty()) {
            builder.append("\n最近目录事件:");
            for (String event : recentEvents) {
                builder.append('\n').append("- ").append(event);
            }
        }
        return builder.toString();
    }
}
