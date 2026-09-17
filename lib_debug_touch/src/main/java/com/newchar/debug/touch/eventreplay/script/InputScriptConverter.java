package com.newchar.debug.touch.eventreplay.script;

import com.newchar.debug.touch.eventreplay.model.RecordedEvent;
import com.newchar.debug.touch.eventreplay.model.RecordedEventSequence;
import com.newchar.debug.touch.eventreplay.model.TouchPoint;

import java.util.ArrayList;
import java.util.List;

/** 将 touch 模块录制的 MotionEvent 序列转换为 shell agent 脚本。 */
public final class InputScriptConverter {

    private static final float TAP_MAX_DISTANCE = 12f;
    private static final long LONG_PRESS_MIN_MS = 500L;

    /** 将事件序列转换为可跨设备执行的脚本。 */
    public static InputScript convert(RecordedEventSequence sequence) {
        InputScript script = new InputScript();
        script.scriptId = "touch_" + sequence.createdAt;
        script.name = valueOrDefault(sequence.activityName, script.scriptId);
        script.sourceWidth = sequence.screenWidth;
        script.sourceHeight = sequence.screenHeight;
        List<List<RecordedEvent>> gestures = splitGestures(sequence.events);
        appendGestureSteps(script, gestures);
        return script;
    }

    /** 将所有手势追加为独立步骤并保留手势间隔。 */
    private static void appendGestureSteps(InputScript script, List<List<RecordedEvent>> gestures) {
        long previousEnd = -1L;
        for (int index = 0; index < gestures.size(); index++) {
            List<RecordedEvent> gesture = gestures.get(index);
            InputScript.Step step = buildStep(gesture, index);
            if (step == null) continue;
            long start = gesture.get(0).eventTime;
            step.delayBeforeMs = previousEnd < 0L ? 0L : Math.max(0L, start - previousEnd);
            previousEnd = gesture.get(gesture.size() - 1).eventTime;
            script.steps.add(step);
        }
    }

    /** 按 ACTION_DOWN 到 ACTION_UP/CANCEL 切分手势。 */
    private static List<List<RecordedEvent>> splitGestures(List<RecordedEvent> events) {
        List<List<RecordedEvent>> result = new ArrayList<>();
        List<RecordedEvent> current = null;
        for (RecordedEvent event : events) {
            int masked = event.actionMasked;
            if (masked == 0) current = startGesture(result, current);
            if (current != null) current.add(event);
            if (current != null && (masked == 1 || masked == 3)) {
                result.add(current);
                current = null;
            }
        }
        if (current != null && !current.isEmpty()) result.add(current);
        return result;
    }

    /** 开始新手势，并保留上一个未正常结束的手势。 */
    private static List<RecordedEvent> startGesture(
            List<List<RecordedEvent>> result, List<RecordedEvent> current) {
        if (current != null && !current.isEmpty()) result.add(current);
        return new ArrayList<>();
    }

    /** 根据指针数量、位移和持续时间选择步骤类型。 */
    private static InputScript.Step buildStep(List<RecordedEvent> events, int index) {
        if (events.isEmpty() || events.get(0).points.isEmpty()) return null;
        InputScript.Step step = new InputScript.Step();
        step.id = "touch_" + index;
        step.durationMs = gestureDuration(events);
        if (containsMultiplePointers(events)) {
            step.type = "multi_touch";
            appendFrames(step, events);
        } else {
            appendSinglePointerAction(step, events);
        }
        return step;
    }

    /** 判断手势是否出现多个触点。 */
    private static boolean containsMultiplePointers(List<RecordedEvent> events) {
        for (RecordedEvent event : events) {
            if (event.pointerCount > 1) return true;
        }
        return false;
    }

    /** 根据首尾点生成点击、长按或单指轨迹步骤。 */
    private static void appendSinglePointerAction(InputScript.Step step, List<RecordedEvent> events) {
        TouchPoint first = firstPoint(events);
        TouchPoint last = lastPoint(events);
        double distance = Math.hypot(last.x - first.x, last.y - first.y);
        step.x = first.x;
        step.y = first.y;
        if (distance <= TAP_MAX_DISTANCE) {
            step.type = step.durationMs >= LONG_PRESS_MIN_MS ? "long_press" : "tap";
            return;
        }
        step.type = "gesture";
        appendPath(step, events);
    }

    /** 将单指事件转换为带相对时间的轨迹点。 */
    private static void appendPath(InputScript.Step step, List<RecordedEvent> events) {
        long start = events.get(0).eventTime;
        for (RecordedEvent event : events) {
            if (event.points.isEmpty()) continue;
            InputScript.Point point = copyPoint(event.points.get(0));
            point.timeMs = Math.max(0L, event.eventTime - start);
            step.points.add(point);
        }
    }

    /** 将原始多指 MotionEvent 转换为脚本事件帧。 */
    private static void appendFrames(InputScript.Step step, List<RecordedEvent> events) {
        long start = events.get(0).eventTime;
        for (RecordedEvent event : events) {
            InputScript.Frame frame = new InputScript.Frame();
            frame.timeMs = Math.max(0L, event.eventTime - start);
            frame.action = event.action;
            for (TouchPoint point : event.points) frame.pointers.add(copyPoint(point));
            step.frames.add(frame);
        }
    }

    /** 复制触点字段，避免脚本持有录制模型对象。 */
    private static InputScript.Point copyPoint(TouchPoint source) {
        InputScript.Point result = new InputScript.Point();
        result.id = source.id;
        result.x = source.hasRawCoordinates ? source.rawX : source.x;
        result.y = source.hasRawCoordinates ? source.rawY : source.y;
        result.pressure = source.pressure;
        result.size = source.size;
        return result;
    }

    /** 返回手势持续时间。 */
    private static long gestureDuration(List<RecordedEvent> events) {
        long start = events.get(0).eventTime;
        long end = events.get(events.size() - 1).eventTime;
        return Math.max(1L, end - start);
    }

    /** 返回手势首个触点。 */
    private static TouchPoint firstPoint(List<RecordedEvent> events) {
        return events.get(0).points.get(0);
    }

    /** 返回手势最后一个有效触点。 */
    private static TouchPoint lastPoint(List<RecordedEvent> events) {
        for (int index = events.size() - 1; index >= 0; index--) {
            if (!events.get(index).points.isEmpty()) return events.get(index).points.get(0);
        }
        return firstPoint(events);
    }

    /** 空文本时使用默认值。 */
    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    /** 禁止实例化转换器。 */
    private InputScriptConverter() {
    }
}