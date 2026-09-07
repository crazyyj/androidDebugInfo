package com.newchar.debug.touch.eventreplay.replay;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Build;

import java.util.List;

/**
 * 将动作中的手势路径转换为系统可执行的 GestureDescription 并下发
 * （移植自 WoolHelper accesshelper 的 GesturePerformer）。
 *
 * <p>这是 accesshelper 风格“事件复现”的执行器：把转换后的手势动作，
 * 通过无障碍服务的 dispatchGesture 真实模拟出来。</p>
 */
public final class GesturePerformer {

    private final GesturePathAdapter mAdapter;

    public GesturePerformer() {
        this(new DefaultGesturePathAdapter());
    }

    public GesturePerformer(GesturePathAdapter adapter) {
        mAdapter = adapter;
    }

    /** 以一组路径点 + 耗时执行手势。 */
    public boolean perform(AccessibilityService service, List<PointF> rawPoints, long durationMs) {
        if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        List<PointF> points = mAdapter.adapt(rawPoints);
        if (points == null || points.isEmpty()) {
            return false;
        }
        Path path = new Path();
        PointF first = points.get(0);
        path.moveTo(first.x, first.y);
        for (int i = 1; i < points.size(); i++) {
            PointF p = points.get(i);
            path.lineTo(p.x, p.y);
        }
        long duration = Math.max(1L, mAdapter.adaptDuration(durationMs));
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return service.dispatchGesture(gesture, null, null);
    }

    /** 便捷方法：直接基于 ActionOutput 的手势执行。 */
    public boolean perform(AccessibilityService service, ActionOutput output) {
        if (output == null || output.gesturePath.isEmpty()) {
            return false;
        }
        List<PointF> pts = new java.util.ArrayList<>();
        for (float[] p : output.gesturePath) pts.add(new PointF(p[0], p[1]));
        return perform(service, pts, output.gestureDurationMs);
    }
}
