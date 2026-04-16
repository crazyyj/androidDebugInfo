package com.newchar.deviceview.devices;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * @author newChar
 * date 2023/2/12
 * @since GPU 信息获取的类
 * @since 迭代版本，（以及描述）
 */
public class GPUProviderImpl implements IGPUProvider {


    @Override
    public String getMaxFreq() {
        final String maxFreqFile = "/sys/class/devfreq/gpufreq/max_freq";
        try (BufferedReader br = new BufferedReader(
                new FileReader(maxFreqFile))) {
            return br.readLine();
        } catch (Exception ignored) {
        }
        return "";
    }

    @Override
    public String getCurrFreq() {

        return null;
    }

}
