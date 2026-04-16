package com.newchar.deviceview.bean;

/**
 * @author newChar
 * date 2022/7/24
 * @since 当前设备的存储信息
 * @since 迭代版本，（以及描述）
 */
public class StorageInfo {

    private long devicesFreeSize = 0;
    private long devicesTotalSize = 0;
    private long devicesAvailableSize = 0;

    private long externalFreeSize = 0;
    private long externalTotalSize = 0;
    private long externalAvailableSize = 0;

    private static volatile StorageInfo mStorageInfo;

    public static StorageInfo getInstance() {
        if (mStorageInfo == null) {
            synchronized (StorageInfo.class) {
                if (mStorageInfo == null) {
                    mStorageInfo = new StorageInfo();
                }
            }
        }
        return mStorageInfo;
    }

    public long getDevicesTotalSize() {
        return devicesTotalSize;
    }

    public void setDevicesTotalSize(long devicesTotalSize) {
        this.devicesTotalSize = devicesTotalSize;
    }

    public long getDevicesAvailableSize() {
        return devicesAvailableSize;
    }

    public void setDevicesAvailableSize(long devicesAvailableSize) {
        this.devicesAvailableSize = devicesAvailableSize;
    }

    public long getDevicesFreeSize() {
        return devicesFreeSize;
    }

    public void setDevicesFreeSize(long devicesFreeSize) {
        this.devicesFreeSize = devicesFreeSize;
    }

    public long getExternalFreeSize() {
        return externalFreeSize;
    }

    public void setExternalFreeSize(long externalFreeSize) {
        this.externalFreeSize = externalFreeSize;
    }

    public long getExternalTotalSize() {
        return externalTotalSize;
    }

    public void setExternalTotalSize(long externalTotalSize) {
        this.externalTotalSize = externalTotalSize;
    }

    public long getExternalAvailableSize() {
        return externalAvailableSize;
    }

    public void setExternalAvailableSize(long externalAvailableSize) {
        this.externalAvailableSize = externalAvailableSize;
    }


    //----

    /**
     * 构建数据
     */
    public void buildSelf() {

    }

}
