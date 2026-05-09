package com.newchar.debug.device.bean;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class MemoryInfo {

    private static final String TAG = "MemoryInfo";
    private static final String PROC_MEMINFO_PATH = "/proc/meminfo";
    private static final long PROC_READ_TIMEOUT_MS = 900L;

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
        boolean procSuccess = false;
        try {
            procSuccess = collectMemoryFromProc();
        } catch (Throwable e) {
            Log.w(TAG, "Failed to read /proc/meminfo: " + e.getMessage());
        }

        if (!procSuccess) {
            Log.i(TAG, "Falling back to Android native API for memory info");
            collectMemoryFromActivityManager(context);
        }

        supplementMissingData(context);
        collectAppRuntimeMemory();

        return this;
    }

    private boolean collectMemoryFromProc() throws IOException {
        File meminfoFile = new File(PROC_MEMINFO_PATH);
        if (!meminfoFile.exists() || !meminfoFile.canRead()) {
            Log.w(TAG, "/proc/meminfo not accessible");
            return false;
        }

        double memTotal = 0;
        double memFree = 0;
        double memAvailable = 0;
        double buffers = 0;
        double cached = 0;
        int validFields = 0;
        long startTime = System.currentTimeMillis();
        boolean timedOut = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(meminfoFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (System.currentTimeMillis() - startTime > PROC_READ_TIMEOUT_MS) {
                    Log.w(TAG, "/proc/meminfo read timed out after " + PROC_READ_TIMEOUT_MS + "ms");
                    timedOut = true;
                    break;
                }
                line = line.trim();
                if (line.startsWith("MemTotal:")) {
                    memTotal = parseMeminfoValue(line);
                    validFields++;
                } else if (line.startsWith("MemFree:")) {
                    memFree = parseMeminfoValue(line);
                    validFields++;
                } else if (line.startsWith("MemAvailable:")) {
                    memAvailable = parseMeminfoValue(line);
                    validFields++;
                } else if (line.startsWith("Buffers:")) {
                    buffers = parseMeminfoValue(line);
                    validFields++;
                } else if (line.startsWith("Cached:")) {
                    cached = parseMeminfoValue(line);
                    validFields++;
                }
                if (validFields >= 5) {
                    break;
                }
            }
        }

        if (memTotal <= 0) {
            Log.w(TAG, "/proc/meminfo parsed but MemTotal is invalid");
            return false;
        }

        setDevicesTotalMem(memTotal * 1024);

        if (memAvailable > 0) {
            setDevicesAvailMemory(memAvailable * 1024);
        } else if (memFree > 0) {
            setDevicesAvailMemory((memFree + buffers + cached) * 1024);
        }

        if (timedOut) {
            Log.w(TAG, "/proc/meminfo partial data collected, will supplement with Android API");
        } else {
            Log.d(TAG, "Successfully collected memory from /proc/meminfo");
        }
        return true;
    }

    private void collectMemoryFromActivityManager(Context context) {
        if (context == null) {
            return;
        }
        try {
            ActivityManager activityManager = (ActivityManager)
                    context.getSystemService(Application.ACTIVITY_SERVICE);
            if (activityManager == null) {
                return;
            }
            ActivityManager.MemoryInfo easyMemoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(easyMemoryInfo);

            if (getDevicesTotalMem() <= 0 && easyMemoryInfo.totalMem > 0) {
                setDevicesTotalMem(easyMemoryInfo.totalMem);
            }
            if (getDevicesAvailMemory() <= 0 && easyMemoryInfo.availMem > 0) {
                setDevicesAvailMemory(easyMemoryInfo.availMem);
            }
            if (easyMemoryInfo.threshold > 0) {
                setBgThreshold(easyMemoryInfo.threshold);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get memory from ActivityManager: " + e.getMessage());
        }
    }

    private void supplementMissingData(Context context) {
        if (getDevicesTotalMem() <= 0 || getDevicesAvailMemory() <= 0) {
            collectMemoryFromActivityManager(context);
        }
    }

    private void collectAppRuntimeMemory() {
        try {
            setAppMaxMemory(Runtime.getRuntime().maxMemory());
            setAppFreeMemory(Runtime.getRuntime().freeMemory());
            setAppTotalMemory(Runtime.getRuntime().totalMemory());
        } catch (Exception e) {
            Log.e(TAG, "Failed to get Runtime memory info: " + e.getMessage());
        }
    }

    private static double parseMeminfoValue(String line) {
        try {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                return Double.parseDouble(parts[1]);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse meminfo value: " + line);
        }
        return 0;
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
