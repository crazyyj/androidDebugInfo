package com.newchar.debug.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.GestureResultCallback;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 将 LQITS 坐标步骤转换为 AccessibilityService 手势。 */
final class AccessibilityGestureDispatcher {

    private final AccessibilityService mService;
    private final int mSourceWidth;
    private final int mSourceHeight;

    /** 保存服务和录制分辨率。 */
    AccessibilityGestureDispatcher(AccessibilityService service, int sourceWidth, int sourceHeight) {
        mService = service;
        mSourceWidth = sourceWidth;
        mSourceHeight = sourceHeight;
    }

    /** 执行指定位置的点击或长按。 */
    boolean press(JSONObject step, long durationMs) throws InterruptedException {
        float[] point = map(step.optDouble("x"), step.optDouble("y"),
                step.optString("coordinateMode", "source_pixel"));
        Path path = new Path();
        path.moveTo(point[0], point[1]);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0L,
                Math.max(1L, durationMs)));
        return dispatch(builder.build(), durationMs);
    }

    /** 执行单指轨迹。 */
    boolean gesture(JSONObject step) throws InterruptedException {
        JSONArray points = step.optJSONArray("points");
        if (points == null || points.length() < 2) return false;
        Path path = path(points, step.optString("coordinateMode", "source_pixel"));
        long duration = Math.max(1L, step.optLong("durationMs", lastTime(points)));
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0L, duration));
        return dispatch(builder.build(), duration);
    }

    /** 将每个 pointerId 的轨迹作为同时执行的无障碍手势。 */
    boolean multiTouch(JSONObject step) throws InterruptedException {
        Map<Integer, Track> tracks = tracks(step.optJSONArray("frames"));
        if (tracks.isEmpty()) return false;
        GestureDescription.Builder builder = new GestureDescription.Builder();
        long maximumEnd = 1L;
        String mode = step.optString("coordinateMode", "source_pixel");
        for (Track track : tracks.values()) {
            long duration = Math.max(1L, track.endMs - track.startMs);
            builder.addStroke(new GestureDescription.StrokeDescription(
                    path(track.points, mode), Math.max(0L, track.startMs), duration));
            maximumEnd = Math.max(maximumEnd, track.startMs + duration);
        }
        return dispatch(builder.build(), maximumEnd);
    }

    /** 构建一组脚本点对应的屏幕路径。 */
    private Path path(JSONArray points, String mode) {
        Path path = new Path();
        for (int index = 0; index < points.length(); index++) {
            JSONObject point = points.optJSONObject(index);
            float[] mapped = map(point.optDouble("x"), point.optDouble("y"), mode);
            if (index == 0) path.moveTo(mapped[0], mapped[1]);
            else path.lineTo(mapped[0], mapped[1]);
        }
        return path;
    }

    /** 从多指帧中整理每个 pointerId 的独立轨迹。 */
    private Map<Integer, Track> tracks(JSONArray frames) {
        Map<Integer, Track> result = new LinkedHashMap<>();
        for (int frameIndex = 0; frames != null && frameIndex < frames.length(); frameIndex++) {
            JSONObject frame = frames.optJSONObject(frameIndex);
            JSONArray pointers = frame.optJSONArray("pointers");
            long timeMs = Math.max(0L, frame.optLong("timeMs"));
            appendFrame(result, pointers, timeMs);
        }
        return result;
    }

    /** 将一帧全部触点追加到对应 pointerId 轨迹。 */
    private void appendFrame(Map<Integer, Track> tracks, JSONArray pointers, long timeMs) {
        for (int index = 0; pointers != null && index < pointers.length(); index++) {
            JSONObject point = pointers.optJSONObject(index);
            int id = point.optInt("id", index);
            Track track = tracks.get(id);
            if (track == null) {
                track = new Track(timeMs);
                tracks.put(id, track);
            }
            track.points.put(point);
            track.endMs = timeMs;
        }
    }

    /** 将脚本坐标映射到设备真实显示区域。 */
    private float[] map(double x, double y, String mode) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) mService.getSystemService(
                AccessibilityService.WINDOW_SERVICE);
        manager.getDefaultDisplay().getRealMetrics(metrics);
        if ("normalized".equals(mode)) {
            return new float[]{(float) x * metrics.widthPixels, (float) y * metrics.heightPixels};
        }
        if ("source_pixel".equals(mode)) {
            return new float[]{(float) x * metrics.widthPixels / Math.max(1, mSourceWidth),
                    (float) y * metrics.heightPixels / Math.max(1, mSourceHeight)};
        }
        return new float[]{(float) x, (float) y};
    }

    /** 分发手势并等待系统回调确认完成。 */
    private boolean dispatch(GestureDescription gesture, long durationMs)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);
        boolean accepted = mService.dispatchGesture(gesture, new GestureResultCallback() {
            /** 记录系统已完成手势。 */
            @Override
            public void onCompleted(GestureDescription value) {
                completed.set(true);
                latch.countDown();
            }

            /** 记录系统取消手势。 */
            @Override
            public void onCancelled(GestureDescription value) {
                latch.countDown();
            }
        }, new Handler(Looper.getMainLooper()));
        if (!accepted) return false;
        latch.await(Math.max(3000L, durationMs + 2000L), TimeUnit.MILLISECONDS);
        return completed.get();
    }

    /** 返回点数组最后一项的相对时间。 */
    private long lastTime(JSONArray points) {
        JSONObject last = points.optJSONObject(points.length() - 1);
        return last == null ? 1L : Math.max(1L, last.optLong("timeMs", 1L));
    }

    /** 一个 pointerId 的手势轨迹。 */
    private static final class Track {
        final JSONArray points = new JSONArray();
        final long startMs;
        long endMs;

        /** 创建以首帧时间为起点的轨迹。 */
        Track(long startMs) {
            this.startMs = startMs;
            this.endMs = startMs;
        }
    }
}
