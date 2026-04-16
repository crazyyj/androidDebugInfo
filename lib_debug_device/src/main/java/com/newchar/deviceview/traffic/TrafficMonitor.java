package com.newchar.deviceview.traffic;

import android.content.Context;
import android.net.TrafficStats;
import android.os.SystemClock;

/**
 * @author newChar
 * date 2025/6/18
 * @since 应用网络流量采样
 * @since 迭代版本，（以及描述）
 */
public final class TrafficMonitor {

    private final int mUid;
    private long mLastRxBytes;
    private long mLastTxBytes;
    private long mLastSampleTimeMs;

    /**
     * 初始化流量监控器。
     *
     * @param context 上下文
     */
    public TrafficMonitor(Context context) {
        Context appContext = context.getApplicationContext();
        mUid = appContext.getApplicationInfo().uid;
        mLastRxBytes = getUidRxBytes();
        mLastTxBytes = getUidTxBytes();
        mLastSampleTimeMs = SystemClock.elapsedRealtime();
    }

    /**
     * 采样当前应用流量数据。
     *
     * @return 流量采样信息
     */
    public TrafficInfo sample() {
        long nowTimeMs = SystemClock.elapsedRealtime();
        long rxBytes = getUidRxBytes();
        long txBytes = getUidTxBytes();
        long durationMs = Math.max(1L, nowTimeMs - mLastSampleTimeMs);
        long rxSpeed = (rxBytes - mLastRxBytes) * 1000L / durationMs;
        long txSpeed = (txBytes - mLastTxBytes) * 1000L / durationMs;

        mLastRxBytes = rxBytes;
        mLastTxBytes = txBytes;
        mLastSampleTimeMs = nowTimeMs;

        return new TrafficInfo(rxBytes, txBytes, rxSpeed, txSpeed, nowTimeMs);
    }

    /**
     * 释放资源。
     */
    public void release() {
        mLastRxBytes = 0L;
        mLastTxBytes = 0L;
        mLastSampleTimeMs = 0L;
    }

    private long getUidRxBytes() {
        long value = TrafficStats.getUidRxBytes(mUid);
        return value < 0 ? 0 : value;
    }

    private long getUidTxBytes() {
        long value = TrafficStats.getUidTxBytes(mUid);
        return value < 0 ? 0 : value;
    }
}
