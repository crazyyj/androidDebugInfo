package com.newchar.debug.utils;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * 已经测试通过的Emulator包括：google自身的模拟器、BlueStacks、Droid4X、Genymotion、AMIDuos
 * 逍遥安卓、天天模拟器、夜神模拟器以及一些手游助手
 */
public class EmulatorChecker {
    private Context mContext;
    private TelephonyManager mTelephonyManager;
    private static final String DRIVER_PATH = "/proc/tty/drivers";
    private static final String CPU_INFO_PATH = "/proc/cpuinfo";
    private static final String DISKSTATS_PATH = "/proc/diskstats";
    //基于QUME的模拟器所特有的keyword
    private static final String EMULATOR_KEYWORD = "goldfish";
    //真机中才包含的分区
    private static final String DISKSTATS_KEYWORD = "mmcblk0p";
    private static final String[] DEFAULT_DEVICE_ID = {"000000000000000"};
    private static final String DEFAULT_IMSI = "310260000000000";

    private static final String[] KNOWN_QEMU_FILES = new String[]{"/dev/socket/qemud", "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so", "/sys/qemu_trace", "/system/bin/qemu-props"};
    private static final String[] KNOWN_NUMBERS = new String[]{"15555215554", "15555215556", "15555215558",
            "15555215560", "15555215562", "15555215564", "15555215566", "15555215568",
            "15555215570", "15555215572", "15555215574", "15555215576", "15555215578",
            "15555215580", "15555215582", "15555215584"};

    private EmulatorChecker() {
    }

    public EmulatorChecker(Context mContext) {
        this.mContext = mContext;
        this.mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

    /**
     * 判断当前设备是否为模拟器
     * @return 如果是模拟器，则返回true;否则，返回false。
     */
    public boolean isEmulator() {
        /**
         * 为了提高效率，这里优先检查模拟器内核中是否存在某些特定的文件。
         * 基本上第一个判断就可以检查是否为Emulator，这里结合不同的检测结果是为了防止遗漏。
         */
        boolean flag = !checkEmulatorFiles(DISKSTATS_PATH, DISKSTATS_KEYWORD)
                || checkEmulatorFiles(DRIVER_PATH, EMULATOR_KEYWORD)
                || checkEmulatorFiles(CPU_INFO_PATH, EMULATOR_KEYWORD)
                || checkQemuFiles()
                || checkPhoneNumber(mContext)
                || checkIDs(mContext)
                || checkEmulatorBuild(mContext)
                || checkOperatorName(mContext);

        mContext = null;
        return flag;
    }

    /**
     * 检测运行APP的设备是否为基于QUME的模拟器
     * @return 如果是QUME Emulator，则返回true;否则，返回false.
     */
    private boolean checkQemuFiles() {
        for (int i = 0; i < KNOWN_QEMU_FILES.length; i++) {
            String knownFile = KNOWN_QEMU_FILES[i];
            File qemuFile = new File(knownFile);
            if (qemuFile.exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据path所对应的文件中是否包含keyword来判断设备是否为模拟器
     * @param path 模拟器所包含的特有文件的路径
     * @param keyword 特有文件中所包含的关键字
     * @return 如果是Emulator，则返回true;否则，返回false.
     */
    private boolean checkEmulatorFiles(String path, String keyword) {
        FileReader fr = null;
        BufferedReader br = null;
        File driverFile = new File(path);

        if (driverFile.exists() && driverFile.canRead()) {
            try {
                fr = new FileReader(driverFile);
                br = new BufferedReader(fr);
                String text = null;
                while ((text = br.readLine()) != null) {
                    if (text.toLowerCase().contains(keyword)) {

                        return true;
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (null != br) {
                        br.close();
                    }
                    if (null != fr) {
                        fr.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
        return false;
    }

    /**
     * 根据设备的默认电话号码来判断其是否为Emulator
     * @param context 应用环境上下文
     * @return 如果是Emulator，则返回true;否则，返回false.
     */
    private boolean checkPhoneNumber(Context context) {
//        String phonenumber = this.mTelephonyManager.getLine1Number();
//
//        for (String number : KNOWN_NUMBERS) {
//            if (number.equalsIgnoreCase(phonenumber)) {
//
//                return true;
//            }
//        }

        return false;
    }

    /**
     * 根据当前设备的Deviceid、IMSI与模拟器的默认值相等与否来判断设备是否为Emulator
     * @param context 应用环境上下文
     * @return 如果是Emulator，则返回true;否则，返回false.
     */
    private boolean checkIDs(Context context) {
        return checkDeviceIDS(context) || checkIMSI(context);
    }

    /**
     * 通过比较当前设备的Deviceid与模拟器的默认Deviceid来判断设备是否为Emulator
     * @param context 应用环境上下文
     * @return 如果是Emulator，则返回true;否则，返回false.
     */
    private boolean checkDeviceIDS(Context context) {
//        String deviceId = mTelephonyManager.getDeviceId();
//
//        for (String id : DEFAULT_DEVICE_ID) {
//            if (id.equalsIgnoreCase(deviceId)) {
//
//                return true;
//            }
//        }

        return false;
    }

    /**
     * 通过比较当前设备的IMSI与模拟器的默认IMSI来判断设备是否为Emulator
     * @param context 应用环境上下文
     * @return 如果是Emulator，则返回true;否则，返回false.
     */
    private boolean checkIMSI(Context context){
//        String imsi = mTelephonyManager.getSubscriberId();
//        if (imsi != null && imsi.equalsIgnoreCase(DEFAULT_IMSI)) {
//
//            return true;
//        }

        return false;
    }

    /**
     * 通过比较当前设备的Build信息与模拟器的默认Build信息来判断设备是否为Emulator
     * @param context 应用环境上下文
     * @return 如果是Emulator，则返回true;否则，返回false.
     */
    private boolean checkEmulatorBuild(Context context) {
        String serial = Build.SERIAL;
        String board = Build.BOARD;
        String bootloader = Build.BOOTLOADER;
        String brand = Build.BRAND;
        String device = Build.DEVICE;
        String hardware = Build.HARDWARE;
        String model = Build.MODEL;
        String product = Build.PRODUCT;

        if (board == "unknown" || bootloader == "unknown" || "unknown" == serial
                || brand == "generic" || device == "generic" || model == "sdk"
                || product == "sdk" || hardware == "goldfish") {

            return true;
        }

        return false;
    }

    /**
     * 通过比较当前设备的NetworkOperator与模拟器的默认NetworkOperator来判断设备是否为Emulator
     * @param context 应用环境上下文
     * @return 如果是Emulator，则返回true;否则，返回false.
     */
    private boolean checkOperatorName(Context context) {
        String operatorName = this.mTelephonyManager.getNetworkOperatorName();

        if (operatorName != null && operatorName.equalsIgnoreCase("android")) {

            return true;
        }

        return false;
    }

}