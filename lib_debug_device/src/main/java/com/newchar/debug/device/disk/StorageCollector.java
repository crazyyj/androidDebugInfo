package com.newchar.debug.device.disk;

import android.os.Environment;
import android.os.StatFs;

/**
 * 采集设备内部与外部存储空间信息。
 */
public final class StorageCollector {

    private StorageCollector() {
    }

    public static StorageInfo collect() {
        StorageInfo info = StorageInfo.getInstance();
        info.setDevicesFreeSize(getDevicesFreeSize());
        info.setDevicesTotalSize(getDevicesTotalSize());
        info.setDevicesAvailableSize(getDevicesAvailableSize());
        info.setExternalFreeSize(getExternalFreeSize());
        info.setExternalTotalSize(getExternalTotalSize());
        info.setExternalAvailableSize(getExternalAvailableSize());
        return info;
    }

    private static boolean isExternalAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private static long getDevicesTotalSize() {
        return newDataStatFs().getTotalBytes();
    }

    private static long getDevicesAvailableSize() {
        return newDataStatFs().getAvailableBytes();
    }

    private static long getDevicesFreeSize() {
        return newDataStatFs().getFreeBytes();
    }

    private static long getExternalAvailableSize() {
        if (!isExternalAvailable()) {
            return -1L;
        }
        return newExternalStatFs().getAvailableBytes();
    }

    private static long getExternalFreeSize() {
        if (!isExternalAvailable()) {
            return -1L;
        }
        return newExternalStatFs().getFreeBytes();
    }

    private static long getExternalTotalSize() {
        if (!isExternalAvailable()) {
            return -1L;
        }
        return newExternalStatFs().getTotalBytes();
    }

    private static StatFs newDataStatFs() {
        return new StatFs(Environment.getDataDirectory().getPath());
    }

    private static StatFs newExternalStatFs() {
        return new StatFs(Environment.getExternalStorageDirectory().getPath());
    }

}
