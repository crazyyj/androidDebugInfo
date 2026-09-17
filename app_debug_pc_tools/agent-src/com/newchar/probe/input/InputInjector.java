package com.newchar.probe.input;

import android.content.Context;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.List;

/** 通过 shell UID 与隐藏 InputManager API 注入按键和多指事件。 */
final class InputInjector {

    private static final int INJECT_WAIT_FOR_FINISH = 2;

    private final Object inputManager;
    private final Method injectMethod;

    /** 解析系统 InputManager 及隐藏注入方法。 */
    InputInjector(Context context) {
        Object manager = null;
        Method method = null;
        try {
            manager = context.getSystemService(Context.INPUT_SERVICE);
            method = manager.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
            method.setAccessible(true);
        } catch (Throwable ignored) {
            manager = null;
            method = null;
        }
        inputManager = manager;
        injectMethod = method;
    }

    /** 返回隐藏注入接口是否可调用。 */
    boolean isAvailable() {
        return inputManager != null && injectMethod != null;
    }

    /** 注入点击或长按。 */
    boolean injectPress(float x, float y, long durationMs, PlaybackControl control) throws Exception {
        long downTime = SystemClock.uptimeMillis();
        boolean downSuccess = injectSingle(downTime, downTime, MotionEvent.ACTION_DOWN, x, y);
        if (!control.sleep(Math.max(1L, durationMs))) {
            injectSingle(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, x, y);
            return false;
        }
        boolean upSuccess = injectSingle(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y);
        if (!upSuccess) {
            injectSingle(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, x, y);
        }
        return downSuccess && upSuccess;
    }

    /** 按轨迹点的相对时间注入单指手势。 */
    boolean injectPath(List<AgentScript.Point> points, CoordinateMapper mapper,
            PlaybackControl control) throws Exception {
        if (points.isEmpty()) return false;
        long downTime = SystemClock.uptimeMillis();
        AgentScript.Point first = points.get(0);
        boolean success = injectPoint(downTime, MotionEvent.ACTION_DOWN, first, mapper);
        long previous = first.timeMs;
        for (int index = 1; index < points.size(); index++) {
            AgentScript.Point point = points.get(index);
            if (!control.sleep(Math.max(0L, point.timeMs - previous))) return cancelPath(downTime, point, mapper);
            int action = index == points.size() - 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            success = injectPoint(downTime, action, point, mapper) && success;
            previous = point.timeMs;
        }
        if (points.size() == 1) {
            success = injectPoint(downTime, MotionEvent.ACTION_UP, first, mapper) && success;
        }
        return success;
    }

    /** 按录制帧原样注入多指 MotionEvent。 */
    boolean injectFrames(List<AgentScript.Frame> frames, CoordinateMapper mapper,
            PlaybackControl control) throws Exception {
        if (frames.isEmpty()) return false;
        long downTime = SystemClock.uptimeMillis();
        long previous = 0L;
        boolean success = true;
        for (AgentScript.Frame frame : frames) {
            if (!control.sleep(Math.max(0L, frame.timeMs - previous))) return false;
            success = injectFrame(downTime, frame, mapper) && success;
            previous = frame.timeMs;
        }
        return success;
    }

    /** 注入一次按键按下和抬起。 */
    boolean injectKey(int keyCode) {
        long downTime = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0);
        boolean downSuccess = inject(down);
        boolean upSuccess = inject(up);
        return downSuccess && upSuccess;
    }

    /** 在取消单指轨迹时补发 ACTION_CANCEL。 */
    private boolean cancelPath(long downTime, AgentScript.Point point, CoordinateMapper mapper) {
        injectPoint(downTime, MotionEvent.ACTION_CANCEL, point, mapper);
        return false;
    }

    /** 映射一个轨迹点并注入。 */
    private boolean injectPoint(long downTime, int action, AgentScript.Point point,
            CoordinateMapper mapper) {
        float[] mapped = mapper.map(point.x, point.y);
        return injectSingle(downTime, SystemClock.uptimeMillis(), action, mapped[0], mapped[1]);
    }

    /** 构造并注入一帧多指事件。 */
    private boolean injectFrame(long downTime, AgentScript.Frame frame, CoordinateMapper mapper) {
        int count = frame.pointers.size();
        if (count == 0) return false;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
        for (int index = 0; index < count; index++) {
            properties[index] = pointerProperties(frame.pointers.get(index));
            coordinates[index] = pointerCoordinates(frame.pointers.get(index), mapper);
        }
        MotionEvent event = obtainMotionEvent(downTime, frame.action, properties, coordinates);
        return injectAndRecycle(event);
    }

    /** 创建单指事件并执行注入。 */
    private boolean injectSingle(long downTime, long eventTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        return injectAndRecycle(event);
    }

    /** 根据 pointerId 创建指针属性。 */
    private MotionEvent.PointerProperties pointerProperties(AgentScript.Point point) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = point.id;
        properties.toolType = MotionEvent.TOOL_TYPE_FINGER;
        return properties;
    }

    /** 映射坐标并创建指针坐标。 */
    private MotionEvent.PointerCoords pointerCoordinates(AgentScript.Point point,
            CoordinateMapper mapper) {
        float[] mapped = mapper.map(point.x, point.y);
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.x = mapped[0];
        coordinates.y = mapped[1];
        coordinates.pressure = point.pressure;
        coordinates.size = point.size;
        return coordinates;
    }

    /** 创建带完整指针数组的 MotionEvent。 */
    private MotionEvent obtainMotionEvent(long downTime, int action,
            MotionEvent.PointerProperties[] properties, MotionEvent.PointerCoords[] coordinates) {
        long eventTime = SystemClock.uptimeMillis();
        return MotionEvent.obtain(downTime, eventTime, action, properties.length,
                properties, coordinates, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    /** 注入并释放 MotionEvent。 */
    private boolean injectAndRecycle(MotionEvent event) {
        try {
            return inject(event);
        } finally {
            event.recycle();
        }
    }

    /** 调用隐藏 InputManager.injectInputEvent。 */
    private boolean inject(InputEvent event) {
        if (!isAvailable()) return false;
        try {
            Object result = injectMethod.invoke(inputManager, event, INJECT_WAIT_FOR_FINISH);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 将脚本坐标映射到目标屏幕。 */
    static final class CoordinateMapper {
        private final int sourceWidth;
        private final int sourceHeight;
        private final int targetWidth;
        private final int targetHeight;
        private final String mode;

        /** 保存源屏幕、目标屏幕及坐标模式。 */
        CoordinateMapper(int sourceWidth, int sourceHeight, int[] targetSize, String mode) {
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.targetWidth = targetSize[0];
            this.targetHeight = targetSize[1];
            this.mode = mode == null ? "source_pixel" : mode;
        }

        /** 映射并裁剪单个坐标。 */
        float[] map(float x, float y) {
            float mappedX = x;
            float mappedY = y;
            if ("normalized".equals(mode)) {
                mappedX = x * targetWidth;
                mappedY = y * targetHeight;
            } else if ("source_pixel".equals(mode) && sourceWidth > 0 && sourceHeight > 0) {
                mappedX = x * targetWidth / sourceWidth;
                mappedY = y * targetHeight / sourceHeight;
            }
            return new float[]{clamp(mappedX, targetWidth), clamp(mappedY, targetHeight)};
        }

        /** 将坐标限制在屏幕范围内。 */
        private float clamp(float value, int limit) {
            return Math.max(0f, Math.min(value, Math.max(0, limit - 1)));
        }
    }
}