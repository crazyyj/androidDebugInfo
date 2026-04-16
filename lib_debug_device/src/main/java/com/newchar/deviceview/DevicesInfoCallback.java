package com.newchar.deviceview;

import com.newchar.deviceview.bean.CPUInfo;
import com.newchar.deviceview.bean.DevicesInfo;
import com.newchar.deviceview.bean.MemoryInfo;
import com.newchar.deviceview.bean.StorageInfo;
import com.newchar.deviceview.traffic.TrafficInfo;

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
