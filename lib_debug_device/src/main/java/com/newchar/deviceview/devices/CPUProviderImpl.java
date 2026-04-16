package com.newchar.deviceview.devices;

import android.os.SystemClock;

import com.newchar.deviceview.bean.CPUInfo;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author newChar
 * date 2023/2/12
 * @since 看接口层
 * @since 迭代版本，（以及描述）
 */
public class CPUProviderImpl implements ICPUProvider {

    {
        buildBasicCpuInfo();
    }

    @Override
    public List<Integer> getAllCPUOnlineStatus() {
        final String cpuOnlineStatus = "/sys/devices/system/cpu/online";
        final List<Integer> onlineStatus = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new FileReader(cpuOnlineStatus))) {
            String text;
            if ((text = br.readLine()) != null && !"".equals(text)) {
                String[] onlineResult = text.split("-");
                if (onlineResult.length == 1) {
                    onlineStatus.add(Integer.parseInt(onlineResult[0]));
                } else if (onlineResult.length > 1) {
                    // 默认认为序号是连续的
                    int cpu_index_max = Integer.parseInt(onlineResult[1]);
                    int cpu_index_min = Integer.parseInt(onlineResult[0]);
                    // 经验：大的值会在后边，索引差值 + 1 为cpu在线数 0-7 为8核心
                    int cpu_index_gap = Math.abs(cpu_index_max - cpu_index_min) + 1;
                    for (int i = 0; i < cpu_index_gap; i++) {
                        onlineStatus.add(cpu_index_min + i);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return onlineStatus;
    }

    @Override
    public List<Integer> getDevicesSupportCPUCoreNum() {
        final String devicesSupportCPUCore = "/sys/devices/system/cpu/present";
        final List<Integer> supportCPUCore = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new FileReader(devicesSupportCPUCore))) {
            String text;
            if ((text = br.readLine()) != null && !"".equals(text)) {
                String[] supportResult = text.split("-");
                if (supportResult.length == 1) {
                    supportCPUCore.add(Integer.parseInt(supportResult[0]));
                } else if (supportResult.length > 1) {
                    // 默认认为序号是连续的
                    int cpu_index_max = Integer.parseInt(supportResult[1]);
                    int cpu_index_min = Integer.parseInt(supportResult[0]);
                    // 经验：大的值会在后边，索引差值 + 1 为cpu在线数 0-7 为8核心
                    int cpu_index_gap = Math.abs(cpu_index_max - cpu_index_min) + 1;
                    for (int i = 0; i < cpu_index_gap; i++) {
                        supportCPUCore.add(cpu_index_min + i);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return supportCPUCore;
    }

//      看接口层记录
//    @Override
//    public boolean setCPUOnlineStatus(int index, int status) {
//        final String cpuOnlineFile = String.format(Locale.getDefault(),
//                "/sys/devices/system/cpu/cpu%1$d/online", index);
//        try (Writer writer = new BufferedWriter(new FileWriter(cpuOnlineFile))) {
//            writer.write(status);
//            writer.flush();
//        } catch (Exception ignored) {
//            ignored.printStackTrace();
//        }
//        try (BufferedReader br = new BufferedReader(
//                new FileReader(cpuOnlineFile))) {
//            String text = br.readLine();
//            return String.valueOf(status).equals(text);
//        } catch (Exception ignored) {
//        }
//        return false;
//    }

    @Override
    public String getCPUMaxRate(int index) {
        final String cpuMaxFreqPath = "/sys/devices/system/cpu/cpu%1$d/cpufreq/cpuinfo_max_freq";
        try (BufferedReader br = new BufferedReader(
                new FileReader(String.format(Locale.getDefault(), cpuMaxFreqPath, index)))) {
            String text;
            if ((text = br.readLine()) != null && !"".equals(text)) {
                return text.trim();
            }
        } catch (Exception ignored) {
        }
        return "-1";
    }

    @Override
    public String getCPUMinRate(int index) {
        final String cpuMaxFreqPath = "/sys/devices/system/cpu/cpu%1$d/cpufreq/cpuinfo_min_freq";
        try (BufferedReader br = new BufferedReader(
                new FileReader(String.format(Locale.getDefault(), cpuMaxFreqPath, index)))) {
            String text;
            if ((text = br.readLine()) != null && !"".equals(text)) {
                return text.trim();
            }
        } catch (Exception ignored) {
        }
        return "-1";
    }

    @Override
    public String getCPUCurRate(int index) {
        final String cpuMaxFreqPath = "/sys/devices/system/cpu/cpu%1$d/cpufreq/scaling_cur_freq";
        try (BufferedReader br = new BufferedReader(
                new FileReader(String.format(Locale.getDefault(), cpuMaxFreqPath, index)))) {
            String text;
            if ((text = br.readLine()) != null && !"".equals(text)) {
                return text.trim();
            }
        } catch (Exception ignored) {
        }
        return "-1";
    }

    /**
     * cpu温度
     *
     * @return 温度
     */
    public int getCPUTemp() {
        String a = "/sys/class/thermal/thermal_zone9/temp";
        return 0;
    }

    @Override
    public String getCPUUsage(int pid) {
        final String cpuUsageFile = "/proc/%1$d/stat";
        try (BufferedReader br = new BufferedReader(new FileReader(
                String.format(Locale.getDefault(), cpuUsageFile, pid)))) {
            try (StringWriter result = new StringWriter()) {
                String text;
                while ((text = br.readLine()) != null && !"".equals(text)) {
                    result.append(text);
                }
                result.flush();
                // 在这里进行数据处理。
                return result.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


    // Linux 默认时钟频率为 100 HZ
    private int CLK_TCK = 100;
    // 上次的CPU使用时间
    private long lastUsedCPUTime = 0L;
    // 上次的CPU记录时间
    private long lastRecordCPUTime = SystemClock.uptimeMillis();

    /**
     * 计算方法 ∆(uTime + sTime) / ∆upTimeMillis / 1000 * CLK_TCK / CORE_COUNT
     * 计算方法 进程运行时间片数 / (已过去时间 * 时钟频率 * 可用核心数)
     *
     * @return 当前CPU使用率%(from 0 to 100)
     */
    public float getCpuUsage(int pid) {
        final String cpu = "/proc/%1$d/stat";
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(new FileInputStream(String.format(Locale.getDefault(), cpu, pid))))) {
            String line = bufferedReader.readLine();
            String[] res = line.split(" ");
            // 进程在用户态运行的时间
            long uTime = Long.parseLong(res[13]);
            // 进程在内核态运行的时间
            long sTime = Long.parseLong(res[14]);
            // 本次运行总时间
            long usedTime = (uTime + sTime) - lastUsedCPUTime;

            long currentTime = SystemClock.uptimeMillis();
            // 获得已过去时间 ms ==> s ==> Clock Tick
            float elapsedTime = (currentTime - lastRecordCPUTime) / 1000.0F * CLK_TCK;

            lastUsedCPUTime = uTime + sTime;
            lastRecordCPUTime = currentTime;
            if (elapsedTime == 0) {
                return -1F;
            }

            float usage = usedTime / elapsedTime * 100;
            return usage / Runtime.getRuntime().availableProcessors();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0F;
    }

    private void buildCurCpuFreq() {
        CPUInfo cpuInfo = CPUInfo.getInstance();
        final List<CPUInfo.CpuLogicInfo> logicCPUInfo = cpuInfo.getLogicInfo();
        for (CPUInfo.CpuLogicInfo cpuLogicInfo : logicCPUInfo) {
            String cpuCurFreq = getCPUCurRate(cpuLogicInfo.getIndex());
            cpuLogicInfo.setCurFreq(cpuCurFreq);
        }
    }

    /**
     * 该类为获取信息类，不应该构建数据，该方法会重新提炼。
     */
    public void buildBasicCpuInfo() {
        CPUInfo info = CPUInfo.getInstance();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String text;
            CPUInfo.CpuLogicInfo logicInfo = null;
            while ((text = br.readLine()) != null) {
                text = text.trim();
                String[] itemInfo = text.split("\\s*:\\s*", 2);
                if (CPUInfo.KEY_CPU_LOGIC_CORE_NUMBER.equals(itemInfo[0])) {
                    if (logicInfo != null) {
                        info.addLogicInfo(logicInfo);
                    }
                    logicInfo = new CPUInfo.CpuLogicInfo();
                    int index = Integer.parseInt(itemInfo[1]);
                    logicInfo.setIndex(index);
                    logicInfo.setCPU_min_freq(getCPUMinRate(index));
                    logicInfo.setCPU_max_freq(getCPUMaxRate(index));
                } else if (text.startsWith(CPUInfo.KEY_CPU_LOGIC_CORE_BOGOMIPS)) {
                    if (logicInfo == null) {
                        logicInfo = new CPUInfo.CpuLogicInfo();
                    }
                    logicInfo.setBogoMIPS(itemInfo[1]);
                } else if (text.startsWith(CPUInfo.KEY_CPU_LOGIC_CORE_FEATURES)) {
                    if (logicInfo == null) {
                        logicInfo = new CPUInfo.CpuLogicInfo();
                    }
                    logicInfo.setFeatures(itemInfo[1]);
                } else if (text.startsWith(CPUInfo.KEY_CPU_LOGIC_CORE_CPU_IMPLEMENTER)) {
                    if (logicInfo == null) {
                        logicInfo = new CPUInfo.CpuLogicInfo();
                    }
                    logicInfo.setCPU_implementer(itemInfo[1]);
                } else if (text.startsWith(CPUInfo.KEY_CPU_LOGIC_CORE_CPU_ARCHITECTURE)) {
                    if (logicInfo == null) {
                        logicInfo = new CPUInfo.CpuLogicInfo();
                    }
                    logicInfo.setCPU_architecture(itemInfo[1]);
                } else if (text.startsWith(CPUInfo.KEY_CPU_LOGIC_CORE_CPU_VARIANT)) {
                    if (logicInfo == null) {
                        logicInfo = new CPUInfo.CpuLogicInfo();
                    }
                    logicInfo.setCPU_variant(itemInfo[1]);
                } else if (text.startsWith(CPUInfo.KEY_CPU_LOGIC_CORE_CPU_PART)) {
                    if (logicInfo == null) {
                        logicInfo = new CPUInfo.CpuLogicInfo();
                    }
                    logicInfo.setCPU_part(itemInfo[1]);
                } else if (text.startsWith(CPUInfo.KEY_CPU_LOGIC_CORE_CPU_REVISION)) {
                    if (logicInfo == null) {
                        logicInfo = new CPUInfo.CpuLogicInfo();
                    }
                    logicInfo.setCPU_revision(itemInfo[1]);
                } else if (text.startsWith(CPUInfo.KEY_CPU_HARDWARE)) {
                    info.setHardware(itemInfo[1]);
                } else if (text.startsWith(CPUInfo.KEY_CPU_NAME)) {
                    info.setCpuName(itemInfo[1]);
                }
            }
            if (logicInfo != null) {
                info.addLogicInfo(logicInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
