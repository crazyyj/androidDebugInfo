package com.newchar.deviceview.devices;

/**
 * @author newChar
 * date 2023/2/12
 * @since GPU相关信息获取
 * @since 迭代版本，（以及描述）
 * <p>
 * mali gpu可以从/sys/devices/platform/gpusysfs里面去取，最小最大频率，当前频率，gpu使用率都有。
 * adreno gpu可以从/sys/class/devfreq/目录下带kgsl的文件夹中去找
 */
interface IGPUProvider {

    /**
     * 获取最大频率
     *
     * @return gpu的最大频率
     */
    String getMaxFreq();


    /**
     * 获取当前频率
     *
     * @return gpu的当前频率
     */
    String getCurrFreq();


}
