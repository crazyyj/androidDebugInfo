package com.newchar.deviceview.bean;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;

/**
 * @author newChar
 * date 2022/8/5
 * @since 内存信息
 * @since 迭代版本，（以及描述）
 */
public class MemoryInfo {

    private static MemoryInfo mInstance;

    private double mDevicesAvailMemory;
    private double mDevicesTotalMem;
    private double mBgThreshold;

    private double mAppMaxMemory;
    private double mAppFreeMemory;
    private double mAppTotalMemory;

    public static MemoryInfo getInstance() {
        if (mInstance == null) {
            synchronized (MemoryInfo.class) {
                if (mInstance == null) {
                    mInstance = new MemoryInfo();
                }
            }
        }
        return mInstance;
    }

    public MemoryInfo getMemoryInfo(Context context) {
//        Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
//        Debug.getMemoryInfo(memoryInfo);
//        double nativePss = memoryInfo.nativePss;
//        double totalPss = memoryInfo.getTotalPss();
//        double dalvikPss = memoryInfo.dalvikPss;

        if (context != null) {
            ActivityManager activityManager = (ActivityManager)
                    context.getSystemService(Application.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo easyMemoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(easyMemoryInfo);

            setDevicesAvailMemory(easyMemoryInfo.availMem);
            setDevicesTotalMem(easyMemoryInfo.totalMem);
        }

        setAppMaxMemory(Runtime.getRuntime().maxMemory());
        setAppFreeMemory(Runtime.getRuntime().freeMemory());
        setAppTotalMemory(Runtime.getRuntime().totalMemory());
//        Log.e("ARRARAR", "\n手机可用内存 " + Utils.two( mDevicesAvailMemory / DebugUtils.GB)
//                + "G\n 手机总内存 " + mDevicesTotalMem / DebugUtils.GB
//                + "\n mMaxMemory " + mMaxMemory / DebugUtils.MB
//                + " m\n mFreeMemory " + mFreeMemory / DebugUtils.MB
//                + " m\n mTotalMemory  " + mTotalMemory / DebugUtils.MB
//                + " m\n 正在用的  " + (mTotalMemory  - mFreeMemory) / DebugUtils.MB
//                + " m\n nativePss  " + nativePss / DebugUtils.KB
//                + " m\n dalvikPss  " + dalvikPss / DebugUtils.KB
//                + " m\n totalPss " + totalPss / DebugUtils.KB );
        return this;
    }

    public void release(){
        mInstance = null;
    }

    public double getDevicesAvailMemory() {
        return mDevicesAvailMemory;
    }

    public void setDevicesAvailMemory(double mDevicesAvailMemory) {
        this.mDevicesAvailMemory = mDevicesAvailMemory;
    }

    public double getDevicesTotalMem() {
        return mDevicesTotalMem;
    }

    public void setDevicesTotalMem(double mDevicesTotalMem) {
        this.mDevicesTotalMem = mDevicesTotalMem;
    }

    public double getBgThreshold() {
        return mBgThreshold;
    }

    public void setBgThreshold(double mBgThreshold) {
        this.mBgThreshold = mBgThreshold;
    }

    public double getAppMaxMemory() {
        return mAppMaxMemory;
    }

    public void setAppMaxMemory(double mAppMaxMemory) {
        this.mAppMaxMemory = mAppMaxMemory;
    }

    public double getAppFreeMemory() {
        return mAppFreeMemory;
    }

    public void setAppFreeMemory(double mAppFreeMemory) {
        this.mAppFreeMemory = mAppFreeMemory;
    }

    public double getAppTotalMemory() {
        return mAppTotalMemory;
    }

    public void setAppTotalMemory(double mAppTotalMemory) {
        this.mAppTotalMemory = mAppTotalMemory;
    }

}
