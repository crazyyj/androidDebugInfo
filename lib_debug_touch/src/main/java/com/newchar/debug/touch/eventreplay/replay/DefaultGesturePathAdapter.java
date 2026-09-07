package com.newchar.debug.touch.eventreplay.replay;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 默认手势路径适配器：直接复制原始路径（移植自 WoolHelper accesshelper）。
 *
 * <p>后续可替换为贝塞尔拟合等实现，以提升复现质量。</p>
 */
public class DefaultGesturePathAdapter implements GesturePathAdapter {

    @Override
    public List<PointF> adapt(List<PointF> rawPoints) {
        if (rawPoints == null || rawPoints.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(rawPoints);
    }
}
