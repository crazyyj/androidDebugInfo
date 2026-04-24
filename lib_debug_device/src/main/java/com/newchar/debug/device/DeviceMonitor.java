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

/**
 * @author newChar
 * date 2022/7/5
 * @since 1.0 设备帧率，CPU，内存，卡顿等信息记录
 * @since 迭代版本，（以及描述）
 */
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

    /**
     * 设置设备信息回调。
     *
     * @param callback 设备信息回调
     */
    public void setDevicesInfoCallback(DevicesInfoCallback callback) {
        this.mDevicesInfoCallback = callback;
    }

    /**
     * 更新全部设备监控信息。
     */
    public void updateAllInfo() {
        updateCPUInfo();
        updateMemoryInfo();
        updateStorageInfo();
        updateDevicesInfo();
        updateTrafficInfo();
        updateFrameRateInfo();
    }


    /**
     * 强制刷新数据
     */
    public void forceUpdateAllInfo() {

    }

    /**
     * 更新CPU信息
     */
    public void updateCPUInfo() {
        sendMonitorMessage(MSG_UPDATE_CPU);
    }

    /**
     * 更新主线程帧率信息
     */
    public void updateFrameRateInfo() {
        postStartFrameRate();
    }

    /**
     * 更新内存信息
     */
    public void updateMemoryInfo() {
        sendMonitorMessage(MSG_UPDATE_MEMORY);
    }

    /**
     * 更新设备存储信息
     */
    public void updateStorageInfo() {
        sendMonitorMessage(MSG_UPDATE_STORAGE);
    }

    /**
     * 更新设备信息, 手机型号。cpu型号。等。
     */
    public void updateDevicesInfo() {
        sendMonitorMessage(MSG_UPDATE_DEVICES_INFO);
    }

    /**
     * 更新网络流量信息
     */
    public void updateTrafficInfo() {
        sendMonitorMessage(MSG_UPDATE_TRAFFIC);
    }

    /**
     * 发送工作线程采样消息。
     *
     * @param what 消息类型
     */
    private void sendMonitorMessage(int what) {
        if (mDevicesMonitorHandler == null || mDevicesMonitorHandler.hasMessages(what)) {
            return;
        }
        mDevicesMonitorHandler.sendEmptyMessage(what);
    }

    /**
     * 延迟发送工作线程采样消息。
     *
     * @param what        消息类型
     * @param delayMillis 延迟时间
     */
    private void sendMonitorMessageDelayed(int what, long delayMillis) {
        if (mDevicesMonitorHandler == null || mDevicesMonitorHandler.hasMessages(what)) {
            return;
        }
        mDevicesMonitorHandler.sendEmptyMessageDelayed(what, delayMillis);
    }

    /**
     * 投递 UI 回调。
     *
     * @param runnable UI 回调任务
     */
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

    /**
     * 在 UI 线程启动帧率采样。
     */
    private void postStartFrameRate() {
        if (mDevicesHandler == null) {
            return;
        }
        mDevicesHandler.post(this::startFrameRateOnUiThread);
    }

    /**
     * 在 UI 线程停止帧率采样。
     */
    private void postStopFrameRate() {
        if (mDevicesHandler == null) {
            return;
        }
        mDevicesHandler.post(this::stopFrameRateOnUiThread);
    }

    /**
     * 启动帧率采样。
     */
    private void startFrameRateOnUiThread() {
        if (mFrameRateRunning) {
            return;
        }
        mFrameRateRunning = true;
        Choreographer.getInstance().postFrameCallback(mFrameCallback);
    }

    /**
     * 停止帧率采样。
     */
    private void stopFrameRateOnUiThread() {
        if (!mFrameRateRunning) {
            return;
        }
        mFrameRateRunning = false;
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
    }

    /**
     * 外部存储是否存在可用
     */
    private static boolean isExternalAvailable() {
        return android.os.Environment.MEDIA_MOUNTED.equals(
                android.os.Environment.getExternalStorageState());
    }

    /**
     * 获取手机内部总的存储空间
     *
     * @return 总大小
     */
    private static long getDevicesTotalSize() {
        return newDataStatFs().getTotalBytes();
    }

    /**
     * 获取手机内部可用的存储空间
     *
     * @return 总大小
     */
    private static long getDevicesAvailableSize() {
        return newDataStatFs().getAvailableBytes();
    }

    /**
     * 获取手机内部可用的存储空间
     *
     * @return 总大小
     */
    private static long getDevicesFreeSize() {
        return newDataStatFs().getFreeBytes();
    }

    /**
     * 获取手机外部空间总的存储空间
     *
     * @return 总大小
     */
    private static long getExternalAvailableSize() {
        if (!isExternalAvailable()) {
            return -1L;
        }
        return newExternalStatFs().getAvailableBytes();
    }

    /**
     * 获取手机外部空间总的存储空间
     *
     * @return 总大小
     */
    private static long getExternalFreeSize() {
        if (!isExternalAvailable()) {
            return -1L;
        }
        return newExternalStatFs().getFreeBytes();
    }

    /**
     * 获取手机内部总的存储空间
     *
     * @return 总大小
     */
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

//        private static final long MIN_FRAME_TIME_MS = 3 * 16;

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!mFrameRateRunning) {
                return;
            }
            if (mLastFrameNanoTime != 0) { // mLastFrameNanoTime 上一次绘制的时间
                long frameInterval = frameTimeNanos - mLastFrameNanoTime; // 计算两帧的时间间隔
                // 如果时间间隔大于最小时间间隔，3*16ms，小于最大的时间间隔，60*16ms，就认为是掉帧，累加统计该时间
                // 此处解释一下原因： 因为正常情况下，两帧的间隔都是在16ms以内 ,如果我们统计到的两帧间隔时间大于三倍的普通绘制时间，
                // 我们就认为是出现了卡顿，之所以设置最大时间间隔，是为了有时候页面不刷新绘制的时候，不做统计处理
                float frameInterval_ms = frameInterval / 1000_000F;
                float frameRate = (1000F / frameInterval_ms);
//                if (frameInterval_ms > MIN_FRAME_TIME_MS) {
//                    // 掉帧了。
//                    callbackFrameDropping(frameInterval_ms, frameRate);
//                } else {
                // 正常
                callbackFrameUpdate(frameInterval_ms, frameRate);
//                }
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

    /**
     * 释放设备监控资源。
     */
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
