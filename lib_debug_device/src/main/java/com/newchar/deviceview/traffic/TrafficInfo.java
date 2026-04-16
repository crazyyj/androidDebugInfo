package com.newchar.deviceview.traffic;

/**
 * @author newChar
 * date 2025/6/18
 * @since 应用网络流量信息
 * @since 迭代版本，（以及描述）
 */
public final class TrafficInfo {

    private final long mRxBytes;
    private final long mTxBytes;
    private final long mRxSpeedBytes;
    private final long mTxSpeedBytes;
    private final long mSampleTimeMs;

    /**
     * 构建一次采样的流量信息。
     *
     * @param rxBytes       接收总字节数
     * @param txBytes       发送总字节数
     * @param rxSpeedBytes  接收速率（字节/秒）
     * @param txSpeedBytes  发送速率（字节/秒）
     * @param sampleTimeMs  采样时间点
     */
    public TrafficInfo(long rxBytes, long txBytes, long rxSpeedBytes, long txSpeedBytes, long sampleTimeMs) {
        mRxBytes = rxBytes;
        mTxBytes = txBytes;
        mRxSpeedBytes = rxSpeedBytes;
        mTxSpeedBytes = txSpeedBytes;
        mSampleTimeMs = sampleTimeMs;
    }

    /**
     * 获取接收总字节数。
     *
     * @return 接收总字节数
     */
    public long getRxBytes() {
        return mRxBytes;
    }

    /**
     * 获取发送总字节数。
     *
     * @return 发送总字节数
     */
    public long getTxBytes() {
        return mTxBytes;
    }

    /**
     * 获取接收速率（字节/秒）。
     *
     * @return 接收速率
     */
    public long getRxSpeedBytes() {
        return mRxSpeedBytes;
    }

    /**
     * 获取发送速率（字节/秒）。
     *
     * @return 发送速率
     */
    public long getTxSpeedBytes() {
        return mTxSpeedBytes;
    }

    /**
     * 获取采样时间点。
     *
     * @return 时间戳毫秒
     */
    public long getSampleTimeMs() {
        return mSampleTimeMs;
    }
}
