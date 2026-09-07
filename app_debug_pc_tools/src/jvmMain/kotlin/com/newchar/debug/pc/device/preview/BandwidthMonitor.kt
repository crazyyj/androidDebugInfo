package com.newchar.debug.pc.device.preview

private const val MAX_RENDER_FPS = 20
private const val MIN_RENDER_FPS = 5
private const val DEGRADE_FPS_THRESHOLD = 5
private const val DEGRADE_DURATION_SECONDS = 3

/** 原始帧流的实时吞吐与帧率快照。 */
internal data class RawStreamMetrics(
    val arrivalFps: Int = 0,
    val bytesPerSecond: Long = 0L,
    val targetRenderFps: Int = MAX_RENDER_FPS,
)

/**
 * 原始帧的带宽观察器。
 *
 * 该类不尝试缩减 adb 管道字节数，只控制 PC 的渲染频率，并在持续低帧率时请求降级。
 */
internal class BandwidthMonitor(
    private val onMetricsChanged: (RawStreamMetrics) -> Unit,
    private val onDegradeRequested: () -> Unit,
) {
    private var secondStartedAt = System.currentTimeMillis()
    private var bytesThisSecond = 0L
    private var framesThisSecond = 0
    private var secondsBelowThreshold = 0
    private var targetRenderFps = MAX_RENDER_FPS
    private var nextRenderAt = 0L

    /** 记录一帧到达，并返回该帧是否需要进入昂贵的 YUV 转换与渲染流程。 */
    fun onFrameReceived(frameBytes: Int, now: Long = System.currentTimeMillis()): Boolean {
        bytesThisSecond += frameBytes
        framesThisSecond++
        if (now - secondStartedAt >= 1_000L) completeSecond(now)
        return if (now >= nextRenderAt) {
            nextRenderAt = now + 1_000L / targetRenderFps.coerceAtLeast(MIN_RENDER_FPS)
            true
        } else {
            false
        }
    }

    /** 汇总上一秒数据，更新目标渲染帧率并按连续低帧率发出降级请求。 */
    private fun completeSecond(now: Long) {
        val fps = framesThisSecond
        targetRenderFps = fps.coerceIn(MIN_RENDER_FPS, MAX_RENDER_FPS)
        onMetricsChanged(RawStreamMetrics(fps, bytesThisSecond, targetRenderFps))
        secondsBelowThreshold = if (fps < DEGRADE_FPS_THRESHOLD) secondsBelowThreshold + 1 else 0
        if (secondsBelowThreshold >= DEGRADE_DURATION_SECONDS) {
            secondsBelowThreshold = 0
            onDegradeRequested()
        }
        secondStartedAt = now
        bytesThisSecond = 0L
        framesThisSecond = 0
    }
}
