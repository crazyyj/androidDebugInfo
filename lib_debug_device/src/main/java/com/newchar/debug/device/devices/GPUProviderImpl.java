package com.newchar.debug.device.devices;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * @author newChar
 * date 2023/2/12
 * @since GPU 信息获取的实现类
 * <p>
 * 支持常见 Adreno 与 Mali GPU 的频率读取：
 * - Adreno：/sys/class/kgsl/kgsl-3d0/... 及 /sys/class/devfreq/ 下带 kgsl 的节点
 * - Mali：/sys/devices/platform/gpusysfs/... 及 /sys/class/misc/mali0/...
 */
public final class GPUProviderImpl implements IGPUProvider {

    private static final String DEVFREQ_DIR = "/sys/class/devfreq/";

    @Override
    public String getMaxFreq() {
        String value;

        // Adreno
        value = readFirstLine("/sys/class/kgsl/kgsl-3d0/max_gpuclk");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }
        value = readFirstLine("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }

        // Mali
        value = readFirstLine("/sys/devices/platform/gpusysfs/gpu_max_clock");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }
        value = readFirstLine("/sys/class/misc/mali0/device/clock");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }

        // 通用 gpufreq
        value = readFirstLine("/sys/class/devfreq/gpufreq/max_freq");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }

        // 动态扫描 devfreq 目录
        value = scanDevFreq("max_freq");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }

        return "";
    }

    @Override
    public String getCurrFreq() {
        String value;

        // Adreno
        value = readFirstLine("/sys/class/kgsl/kgsl-3d0/gpuclk");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }
        value = readFirstLine("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }

        // Mali
        value = readFirstLine("/sys/devices/platform/gpusysfs/gpu_clock");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }
        value = readFirstLine("/sys/class/misc/mali0/device/clock");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }

        // 通用 gpufreq
        value = readFirstLine("/sys/class/devfreq/gpufreq/cur_freq");
        if (!TextUtils.isEmpty(value)) {
            return value;
        }

        // 动态扫描 devfreq 目录
        value = scanDevFreq("cur_freq");
        return !TextUtils.isEmpty(value) ? value : "";
    }

    private static String readFirstLine(String path) {
        if (path == null) {
            return null;
        }
        File file = new File(path);
        if (!file.exists() || !file.canRead()) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            return line != null ? line.trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String scanDevFreq(String fileName) {
        File devfreq = new File(DEVFREQ_DIR);
        if (!devfreq.exists() || !devfreq.isDirectory()) {
            return null;
        }
        File[] dirs = devfreq.listFiles();
        if (dirs == null) {
            return null;
        }
        for (File dir : dirs) {
            if (!dir.isDirectory()) {
                continue;
            }
            String name = dir.getName().toLowerCase(Locale.getDefault());
            if (name.contains("kgsl") || name.contains("gpu") || name.contains("mali")) {
                String value = readFirstLine(new File(dir, fileName).getAbsolutePath());
                if (!TextUtils.isEmpty(value)) {
                    return value;
                }
            }
        }
        return null;
    }

}
