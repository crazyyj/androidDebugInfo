package com.newchar.debug.touch.eventreplay.script;

import com.newchar.debug.touch.eventreplay.model.RecordedEvent;
import com.newchar.debug.touch.eventreplay.model.RecordedEventSequence;
import com.newchar.debug.touch.eventreplay.model.TouchPoint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 验证触摸录制到 shell 输入脚本的关键字段转换。 */
public class InputScriptConverterTest {

    /** 多指手势应保留完整 action、pointerId、时间以及手势间隔。 */
    @Test
    public void convertPreservesMultiTouchFramesAndDelay() {
        RecordedEventSequence sequence = new RecordedEventSequence();
        sequence.createdAt = 123L;
        sequence.activityName = "MainActivity";
        sequence.screenWidth = 1080;
        sequence.screenHeight = 2400;
        sequence.events.add(event(100L, 0, rawPoint(0, 10f, 20f, 10f, 44f)));
        sequence.events.add(event(110L, 261, point(0, 10f, 20f), point(1, 90f, 100f)));
        sequence.events.add(event(120L, 262, point(0, 20f, 30f), point(1, 80f, 90f)));
        sequence.events.add(event(130L, 1, point(0, 20f, 30f)));
        sequence.events.add(event(200L, 0, point(0, 40f, 50f)));
        sequence.events.add(event(220L, 1, point(0, 40f, 50f)));

        InputScript script = InputScriptConverter.convert(sequence);

        assertEquals("LQITS", script.magic);
        assertEquals(2, script.steps.size());
        assertEquals("multi_touch", script.steps.get(0).type);
        assertEquals("continue", script.steps.get(0).failurePolicy);
        assertEquals(261, script.steps.get(0).frames.get(1).action);
        assertEquals(1, script.steps.get(0).frames.get(1).pointers.get(1).id);
        assertEquals(44f, script.steps.get(0).frames.get(0).pointers.get(0).y, 0f);
        assertEquals(70L, script.steps.get(1).delayBeforeMs);
        assertTrue(script.toJsonString().contains("\"sourceWidth\":1080"));
    }

    /** 创建带指定动作和触点的录制事件。 */
    private RecordedEvent event(long eventTime, int action, TouchPoint... points) {
        RecordedEvent event = new RecordedEvent();
        event.eventTime = eventTime;
        event.action = action;
        event.actionMasked = action & 0xff;
        event.pointerCount = points.length;
        event.pointerIds = new int[points.length];
        for (int index = 0; index < points.length; index++) {
            event.pointerIds[index] = points[index].id;
            event.points.add(points[index]);
        }
        return event;
    }

    /** 创建测试触点。 */
    private TouchPoint point(int id, float x, float y) {
        return new TouchPoint(x, y, 1f, 1f, id);
    }

    /** 创建同时包含窗口坐标和屏幕坐标的测试触点。 */
    private TouchPoint rawPoint(int id, float x, float y, float rawX, float rawY) {
        TouchPoint point = point(id, x, y);
        point.rawX = rawX;
        point.rawY = rawY;
        point.hasRawCoordinates = true;
        return point;
    }
}