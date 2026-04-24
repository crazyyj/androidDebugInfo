package com.newchar.debug.device;

import com.newchar.debug.device.bean.CPUInfo;
import com.newchar.debug.device.bean.DevicesInfo;
import com.newchar.debug.device.bean.MemoryInfo;
import com.newchar.debug.device.disk.StorageInfo;
import com.newchar.debug.device.traffic.TrafficInfo;

/**
 * @author newChar
 * date 2022/7/24
 * @since 通用设备信息
 * @since 迭代版本，（以及描述）
 */
public interface DevicesInfoCallback {

    void onMemoryCallback(MemoryInfo memoryInfo);

    void onCPUInfoCallback(CPUInfo info);

    void onStorageCallback(StorageInfo storageInfo);

    void onDevicesInfoCallback(DevicesInfo devicesInfo);

    void onFrameRateUpdate(float frameInterval_ms, float frameRate);

    void onTrafficUpdate(TrafficInfo trafficInfo);

}
