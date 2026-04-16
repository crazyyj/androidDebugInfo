package com.newchar.deviceview.devices;

import java.util.HashMap;
import java.util.Map;

/**
 * @author newChar
 * date 2023/7/30
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class DevicesInfoFactory {

    private static final Map<String, ICPUProvider> factory = new HashMap<>(2);

    public static ICPUProvider getCpuInfo(String clazz) {
        ICPUProvider provider = factory.get(clazz);
        if (provider == null) {
            provider = new CPUProviderImpl();
            factory.put(clazz, provider);
        }
        return provider;
    }

}
