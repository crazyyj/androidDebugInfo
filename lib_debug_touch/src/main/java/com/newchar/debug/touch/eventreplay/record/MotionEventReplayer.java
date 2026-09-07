package com.newchar.debug.touch.eventreplay.record;

import android.view.MotionEvent;
import android.view.View;

import com.newchar.debug.touch.eventreplay.model.RecordedEvent;
import com.newchar.debug.touch.eventreplay.model.RecordedEventSequence;
import com.newchar.debug.touch.eventreplay.model.TouchPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * 以“原始触摸事件”形式复现录制（touch 模块原有形式）。
 *
 * <p>把 {@link RecordedEventSequence} 还原为一组 MotionEvent，派发到目标 View。
 * 与 {@link com.newchar.debug.touch.eventreplay.replay.AccessibilityActionReplayer}
 * （accesshelper 形式）互为“事件复现的两种形式”。</p>
 */
public final class MotionEventReplayer {

    private MotionEventReplayer() {
    }

    /** 将事件序列还原为可派发的 MotionEvent 列表（调用方需负责 recycle）。 */
    public static List<MotionEvent> toMotionEvents(RecordedEventSequence seq) {
        List<MotionEvent> out = new ArrayList<>();
        for (RecordedEvent e : seq.events) {
            MotionEvent me = build(e);
            if (me != null) out.add(me);
        }
        return out;
    }

    /** 直接把事件序列派发到目标 View（用于同进程内的触摸回放）。 */
    public static void replay(RecordedEventSequence seq, View target) {
        if (target == null) return;
        for (MotionEvent me : toMotionEvents(seq)) {
            target.dispatchTouchEvent(me);
            me.recycle();
        }
    }

    private static MotionEvent build(RecordedEvent e) {
        int pc = e.points.size();
        if (pc == 0) return null;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[pc];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pc];
        for (int i = 0; i < pc; i++) {
            TouchPoint p = e.points.get(i);
            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            pp.id = (e.pointerIds != null && i < e.pointerIds.length) ? e.pointerIds[i] : i;
            pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
            props[i] = pp;
            MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
            c.x = p.x;
            c.y = p.y;
            c.pressure = p.pressure;
            c.size = p.size;
            coords[i] = c;
        }
        int action = e.action >= 0 ? e.action : (e.actionMasked & 0xff);
        return MotionEvent.obtain(e.downTime, e.eventTime, action, pc, props, coords,
                0, 0, 1f, 1f, 0, 0, 0, 0);
    }
}
