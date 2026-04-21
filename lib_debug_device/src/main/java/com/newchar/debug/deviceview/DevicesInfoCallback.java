package com.newchar.debug.deviceview;

import com.newchar.debug.deviceview.bean.CPUInfo;
import com.newchar.debug.deviceview.bean.DevicesInfo;
import com.newchar.debug.deviceview.bean.MemoryInfo;
import com.newchar.debug.deviceview.bean.StorageInfo;
import com.newchar.debug.deviceview.traffic.TrafficInfo;

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
