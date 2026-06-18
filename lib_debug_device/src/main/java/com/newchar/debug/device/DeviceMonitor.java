package com.newchar.debug.device;

import android.os.Handler;
import android.os.Message;
import android.view.Choreographer;

import com.newchar.debug.device.bean.DevicesInfo;
import com.newchar.debug.utils.DebugUtils;
import com.newchar.debug.utils.HandleWrapper;
import com.newchar.debug.device.bean.CPUInfo;
import com.newchar.debug.device.bean.MemoryInfo;
import com.newchar.debug.device.disk.StorageInfo;
import com.newchar.debug.device.devices.DevicesInfoFactory;
import com.newchar.debug.device.devices.ICPUProvider;
import com.newchar.debug.device.traffic.TrafficInfo;
import com.newchar.debug.device.traffic.TrafficMonitor;

public class DeviceMonitor {

    private static final int MSG_UPDATE_CPU = 1;
    private static final int MSG_UPDATE_MEMORY = 2;
    private static final int MSG_UPDATE_STORAGE = 3;
    private static final int MSG_UPDATE_TRAFFIC = 4;
    private static final int MSG_UPDATE_FRAME_RATE = 5;
    private static final int MSG_UPDATE_DEVICES_INFO = 6;
    private static final int MSG_STOP_UPDATE_FRAME_RATE = 7;

    private Handler mDevicesMonitorHandler;
    private Handler mDevicesHandler;
    private DevicesInfoCallback mDevicesInfoCallback;
    private TrafficMonitor mTrafficMonitor;
    private boolean mFrameRateRunning;

    private static volatile DeviceMonitor mDeviceMonitor;

    public static DeviceMonitor getInstance() {
        if (mDeviceMonitor == null) {
            synchronized (DeviceMonitor.class) {
                if (mDeviceMonitor == null) {
                    mDeviceMonitor = new DeviceMonitor();
                }
            }
        }
        return mDeviceMonitor;
    }

    private final Handler.Callback mCallback = new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_CPU:
                    buildCPUInfo();
                    sendMonitorMessageDelayed(MSG_UPDATE_CPU, 1000L);
                    break;
                case MSG_UPDATE_TRAFFIC:
                    buildTrafficInfo();
                    sendMonitorMessageDelayed(MSG_UPDATE_TRAFFIC, 1000L);
                    break;
                case MSG_UPDATE_MEMORY:
                    buildMemoryInfo();
                    sendMonitorMessageDelayed(MSG_UPDATE_MEMORY, 1000L);
                    break;
                case MSG_UPDATE_STORAGE:
                    buildStorageInfo();
                    sendMonitorMessageDelayed(MSG_UPDATE_STORAGE, 1000L);
                    break;
                case MSG_UPDATE_FRAME_RATE:
                    postStartFrameRate();
                    break;
                case MSG_STOP_UPDATE_FRAME_RATE:
                    postStopFrameRate();
                    break;
                case MSG_UPDATE_DEVICES_INFO:
                    buildDevicesInfo();
                    break;
                default:
                    break;
            }
            return false;
        }

        private void callbackCpuInfo() {
            if (mDevicesInfoCallback != null) {
                postUiCallback(() -> mDevicesInfoCallback.onCPUInfoCallback(CPUInfo.getInstance()));
            }
        }

        private void callbackStorageInfo() {
            if (mDevicesInfoCallback != null) {
                postUiCallback(() -> mDevicesInfoCallback.onStorageCallback(StorageInfo.getInstance()));
            }
        }

        private void callbackMemoryInfo() {
            if (mDevicesInfoCallback != null) {
                postUiCallback(() -> mDevicesInfoCallback.onMemoryCallback(MemoryInfo.getInstance()));
            }
        }

        private void callbackDevicesInfo() {
            if (mDevicesInfoCallback != null) {
                postUiCallback(() -> mDevicesInfoCallback.onDevicesInfoCallback(DevicesInfo.getInstance()));
            }
        }

        private void callbackTrafficInfo(TrafficInfo info) {
            if (mDevicesInfoCallback != null && info != null) {
                postUiCallback(() -> mDevicesInfoCallback.onTrafficUpdate(info));
            }
        }

        private void buildCPUInfo() {
            CPUInfo cpuInfo = CPUInfo.getInstance();
            ICPUProvider provider = DevicesInfoFactory.getCpuInfo("");
            cpuInfo.setCpuName("" + provider.getCpuUsage(android.os.Process.myPid()));
            callbackCpuInfo();
        }

        private void buildDevicesInfo() {
            DeviceStaticInfoCollector.collect(DebugUtils.app());
            callbackDevicesInfo();
        }

        private void buildStorageInfo() {
            StorageInfo storageInfo = StorageInfo.getInstance();

            storageInfo.setDevicesFreeSize(getDevicesFreeSize());
            storageInfo.setDevicesTotalSize(getDevicesTotalSize());
            storageInfo.setDevicesAvailableSize(getDevicesAvailableSize());

            storageInfo.setExternalFreeSize(getExternalFreeSize());
            storageInfo.setExternalTotalSize(getExternalTotalSize());
            storageInfo.setExternalAvailableSize(getExternalAvailableSize());

            callbackStorageInfo();
        }

        private void buildMemoryInfo() {
            MemoryInfo.getInstance().getMemoryInfo(DebugUtils.app());
            callbackMemoryInfo();
        }

        private void buildTrafficInfo() {
            if (mTrafficMonitor == null) {
                return;
            }
            TrafficInfo info = mTrafficMonitor.sample();
            callbackTrafficInfo(info);
        }

    };

    private DeviceMonitor() {
        mDevicesMonitorHandler = HandleWrapper.obtainAsyncHandler(mCallback);
        mDevicesHandler = HandleWrapper.getMainHandler();
        mTrafficMonitor = new TrafficMonitor(DebugUtils.app());
    }

    public void setDevicesInfoCallback(DevicesInfoCallback callback) {
        this.mDevicesInfoCallback = callback;
    }

    public void updateAllInfo() {
        updateCPUInfo();
        updateMemoryInfo();
        updateStorageInfo();
        updateDevicesInfo();
        updateTrafficInfo();
        updateFrameRateInfo();
    }

    public void forceUpdateAllInfo() {

    }

    public void updateCPUInfo() {
        sendMonitorMessage(MSG_UPDATE_CPU);
    }

    public void updateFrameRateInfo() {
        postStartFrameRate();
    }

    public void updateMemoryInfo() {
        sendMonitorMessage(MSG_UPDATE_MEMORY);
    }

    public void updateStorageInfo() {
        sendMonitorMessage(MSG_UPDATE_STORAGE);
    }

    public void updateDevicesInfo() {
        sendMonitorMessage(MSG_UPDATE_DEVICES_INFO);
    }

    public void updateTrafficInfo() {
        sendMonitorMessage(MSG_UPDATE_TRAFFIC);
    }

    private void sendMonitorMessage(int what) {
        if (mDevicesMonitorHandler == null || mDevicesMonitorHandler.hasMessages(what)) {
            return;
        }
        mDevicesMonitorHandler.sendEmptyMessage(what);
    }

    private void sendMonitorMessageDelayed(int what, long delayMillis) {
        if (mDevicesMonitorHandler == null || mDevicesMonitorHandler.hasMessages(what)) {
            return;
        }
        mDevicesMonitorHandler.sendEmptyMessageDelayed(what, delayMillis);
    }

    private void postUiCallback(Runnable runnable) {
        if (mDevicesHandler == null || runnable == null) {
            return;
        }
        mDevicesHandler.post(() -> {
            if (mDevicesInfoCallback != null) {
                runnable.run();
            }
        });
    }

    private void postStartFrameRate() {
        if (mDevicesHandler == null) {
            return;
        }
        mDevicesHandler.post(this::startFrameRateOnUiThread);
    }

    private void postStopFrameRate() {
        if (mDevicesHandler == null) {
            return;
        }
        mDevicesHandler.post(this::stopFrameRateOnUiThread);
    }

    private void startFrameRateOnUiThread() {
        if (mFrameRateRunning) {
            return;
        }
        mFrameRateRunning = true;
        Choreographer.getInstance().postFrameCallback(mFrameCallback);
    }

    private void stopFrameRateOnUiThread() {
        if (!mFrameRateRunning) {
            return;
        }
        mFrameRateRunning = false;
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
    }

    private static boolean isExternalAvailable() {
        return android.os.Environment.MEDIA_MOUNTED.equals(
                android.os.Environment.getExternalStorageState());
    }

    private static long getDevicesTotalSize() {
        return newDataStatFs().getTotalBytes();
    }

    private static long getDevicesAvailableSize() {
        return newDataStatFs().getAvailableBytes();
    }

    private static long getDevicesFreeSize() {
        return newDataStatFs().getFreeBytes();
    }

    private static long getExternalAvailableSize() {
        if (!isExternalAvailable()) {
            return -1L;
        }
        return newExternalStatFs().getAvailableBytes();
    }

    private static long getExternalFreeSize() {
        if (!isExternalAvailable()) {
            return -1L;
        }
        return newExternalStatFs().getFreeBytes();
    }

    private static long getExternalTotalSize() {
        if (!isExternalAvailable()) {
            return -1L;
        }
        return newExternalStatFs().getTotalBytes();
    }

    private static android.os.StatFs newDataStatFs() {
        return new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
    }

    private static android.os.StatFs newExternalStatFs() {
        return new android.os.StatFs(android.os.Environment.getExternalStorageDirectory().getPath());
    }

    private final Choreographer.FrameCallback mFrameCallback = new Choreographer.FrameCallback() {

        private long mLastFrameNanoTime;

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!mFrameRateRunning) {
                return;
            }
            if (mLastFrameNanoTime != 0) {
                long frameInterval = frameTimeNanos - mLastFrameNanoTime;
                float frameInterval_ms = frameInterval / 1000_000F;
                float frameRate = (1000F / frameInterval_ms);
                callbackFrameUpdate(frameInterval_ms, frameRate);
            }
            mLastFrameNanoTime = frameTimeNanos;
            if (mFrameRateRunning) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        private void callbackFrameUpdate(float frameInterval_ms, float frameRate) {
            if (mDevicesInfoCallback != null) {
                mDevicesInfoCallback.onFrameRateUpdate(frameInterval_ms, frameRate);
            }
        }

    };

    public void release() {
        mDeviceMonitor = null;
        mDevicesInfoCallback = null;
        postStopFrameRate();
        if (mTrafficMonitor != null) {
            mTrafficMonitor.release();
            mTrafficMonitor = null;
        }
        if (mDevicesHandler != null) {
            mDevicesHandler = null;
        }
        if (mDevicesMonitorHandler != null) {
            mDevicesMonitorHandler.removeCallbacksAndMessages(null);
            mDevicesMonitorHandler = null;
        }
    }

}
