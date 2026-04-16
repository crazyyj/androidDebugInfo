package com.newchar.deviceview.bean;

import java.util.ArrayList;
import java.util.List;

/**
 * @author newChar
 * date 2022/7/24
 * @since cpu 信息
 * @since 迭代版本，（以及描述）
 */
public final class CPUInfo {

    private static volatile CPUInfo mInstance;

    public static final String KEY_CPU_NAME = "Processor";
    /**
     * 逻辑核心数
     */
    public static final String KEY_CPU_LOGIC_CORE_NUMBER = "processor";

    public static final String KEY_CPU_LOGIC_CORE_BOGOMIPS = "BogoMIPS";

    public static final String KEY_CPU_LOGIC_CORE_FEATURES = "Features";

    public static final String KEY_CPU_LOGIC_CORE_CPU_IMPLEMENTER = "CPU implementer";

    public static final String KEY_CPU_LOGIC_CORE_CPU_ARCHITECTURE = "CPU architecture";

    public static final String KEY_CPU_LOGIC_CORE_CPU_VARIANT = "CPU variant";

    public static final String KEY_CPU_LOGIC_CORE_CPU_PART = "CPU part";

    public static final String KEY_CPU_LOGIC_CORE_CPU_REVISION = "CPU revision";

    /**
     * CPU型号
     */
    public static final String KEY_CPU_HARDWARE = "Hardware";


    private List<CpuLogicInfo> mLogicInfo = new ArrayList<>();

    private String name;
    /**
     * 型号
     */
    private String hardware;

    private CPUInfo() {
    }

    public static CPUInfo getInstance() {
        if (mInstance == null) {
            synchronized (CPUInfo.class) {
                if (mInstance == null) {
                    mInstance = new CPUInfo();
                }
            }
        }
        return mInstance;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CpuLogicInfo> getLogicInfo() {
        return mLogicInfo;
    }

    public void setLogicInfo(List<CpuLogicInfo> mLogicInfo) {
        this.mLogicInfo = mLogicInfo;
    }

    public void addLogicInfo(CpuLogicInfo logicInfo) {
        this.mLogicInfo.add(logicInfo);
    }

    public String getCpuName() {
        return name;
    }

    public void setCpuName(String cpuName) {
        this.name = cpuName;
    }

    public String getHardware() {
        return hardware;
    }

    public void setHardware(String hardware) {
        this.hardware = hardware;
    }

    public static final class CpuLogicInfo {
        /**
         * 逻辑核心序号
         */
        private int index;

        /**
         * cpu启动粗估时间，不做设置记录
         */
        private String bogoMIPS;

        private String features;

        private String CPU_implementer;

        private String CPU_architecture;

        private String CPU_variant;

        private String CPU_part;

        private String CPU_revision;
        private String mMaxFreq;
        private String mMinFreq;
        private String mCurFreq;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getFeatures() {
            return features;
        }

        public void setFeatures(String features) {
            this.features = features;
        }

        public String getCPU_max_freq() {
            return mMaxFreq;
        }

        public void setCPU_max_freq(String CPU_max_freq) {
            this.mMaxFreq = CPU_max_freq;
        }

        public String getCPU_min_freq() {
            return mMinFreq;
        }

        public void setCPU_min_freq(String CPU_min_freq) {
            this.mMinFreq = CPU_min_freq;
        }

        public String getCurFreq() {
            return mCurFreq;
        }

        public void setCurFreq(String mCurFreq) {
            this.mCurFreq = mCurFreq;
        }

        public String getBogoMIPS() {
            return bogoMIPS;
        }

        public void setBogoMIPS(String bogoMIPS) {
            this.bogoMIPS = bogoMIPS;
        }

        public String getCPU_implementer() {
            return CPU_implementer;
        }

        public void setCPU_implementer(String CPU_implementer) {
            this.CPU_implementer = CPU_implementer;
        }

        public String getCPU_architecture() {
            return CPU_architecture;
        }

        public void setCPU_architecture(String CPU_architecture) {
            this.CPU_architecture = CPU_architecture;
        }

        public String getCPU_variant() {
            return CPU_variant;
        }

        public void setCPU_variant(String CPU_variant) {
            this.CPU_variant = CPU_variant;
        }

        public String getCPU_part() {
            return CPU_part;
        }

        public void setCPU_part(String CPU_part) {
            this.CPU_part = CPU_part;
        }

        public String getCPU_revision() {
            return CPU_revision;
        }

        public void setCPU_revision(String CPU_revision) {
            this.CPU_revision = CPU_revision;
        }
    }

}