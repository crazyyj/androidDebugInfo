package com.newchar.debug.touch.eventreplay.replay;

import android.graphics.PointF;

import java.util.List;

/**
 * 手势路径适配器（移植自 WoolHelper accesshelper）。
 *
 * <p>后续可在此做贝塞尔拟合、降噪、分辨率归一化等处理，
 * 使转换出的手势动作在不同设备上都能稳定复现。</p>
 */
public interface GesturePathAdapter {

    /** 对原始路径点进行适配，返回实际下发的路径点。 */
    List<PointF> adapt(List<PointF> rawPoints);

    /** 对期望耗时做节奏优化，默认原样返回。 */
    default long adaptDuration(long rawDuration) {
        return rawDuration;
    }
}
