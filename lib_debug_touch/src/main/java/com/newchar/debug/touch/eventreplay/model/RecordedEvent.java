package com.newchar.debug.touch.eventreplay.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一条被录制下来的触摸事件（对应一个 MotionEvent）。
 *
 * <p>与原 touch 模块把 MotionEvent 通过 Parcel 写成二进制 MTES 文件不同，
 * 这里把事件拆成可 JSON 化的字段，使“保存下来的 event”可以被新代码直接读取与处理。</p>
 */
public final class RecordedEvent {

    /** 手势按下时刻（ns，来自 MotionEvent.getDownTime）。 */
    public long downTime;
    /** 本事件发生的时刻（ns，来自 MotionEvent.getEventTime）。 */
    public long eventTime;
    /** 完整 action 值（含 pointerIndex 编码）。 */
    public int action;
    /** 仅 action 掩码（MotionEvent.getActionMasked()）。 */
    public int actionMasked;
    /** 指针数量。 */
    public int pointerCount;
    /** 各指针 id，长度 = pointerCount。 */
    public int[] pointerIds;
    /** 各指针坐标/压力快照，长度 = pointerCount。 */
    public List<TouchPoint> points;

    public RecordedEvent() {
        points = new ArrayList<>();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("dt", downTime);
        m.put("et", eventTime);
        m.put("a", action);
        m.put("am", actionMasked);
        m.put("pc", pointerCount);
        List<Object> ids = new ArrayList<>();
        if (pointerIds != null) {
            for (int id : pointerIds) ids.add(id);
        }
        m.put("ids", ids);
        List<Object> pts = new ArrayList<>();
        for (TouchPoint tp : points) pts.add(tp.toMap());
        m.put("pts", pts);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static RecordedEvent fromMap(Map<String, Object> m) {
        RecordedEvent e = new RecordedEvent();
        e.downTime = asLong(m.get("dt"));
        e.eventTime = asLong(m.get("et"));
        e.action = asInt(m.get("a"));
        e.actionMasked = asInt(m.get("am"), e.action & 0xff);
        e.pointerCount = asInt(m.get("pc"));
        List<Object> ids = (List<Object>) m.get("ids");
        if (ids != null) {
            e.pointerIds = new int[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                e.pointerIds[i] = ((Number) ids.get(i)).intValue();
            }
        } else {
            e.pointerIds = new int[e.pointerCount];
        }
        List<Object> pts = (List<Object>) m.get("pts");
        if (pts != null) {
            for (Object o : pts) e.points.add(TouchPoint.fromMap((Map<String, Object>) o));
        }
        // 以实际解析到的点数为准，保证 pointerIds 与点对齐
        e.pointerCount = e.points.size();
        if (e.pointerIds.length != e.pointerCount) {
            int[] fixed = new int[e.pointerCount];
            for (int i = 0; i < e.pointerCount; i++) {
                fixed[i] = i < e.pointerIds.length ? e.pointerIds[i] : i;
            }
            e.pointerIds = fixed;
        }
        return e;
    }

    static long asLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    static int asInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    static int asInt(Object o, int def) {
        return o == null ? def : ((Number) o).intValue();
    }
}
