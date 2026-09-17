package com.newchar.debug.touch.eventreplay.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 单个触摸点（指针）的快照。对应 MotionEvent 中某一个 pointer 的几何与压力信息。
 */
public final class TouchPoint {

    /** 横坐标（像素）。 */
    public float x;
    /** 纵坐标（像素）。 */
    public float y;
    /** 压力 0~1。 */
    public float pressure;
    /** 触点尺寸 0~1。 */
    public float size;
    /** 指针 id（MotionEvent 中的 pointerId）。 */
    public int id;
    /** 原始屏幕横坐标。 */
    public float rawX;
    /** 原始屏幕纵坐标。 */
    public float rawY;
    /** 是否包含可用于跨 App 注入的原始屏幕坐标。 */
    public boolean hasRawCoordinates;

    public TouchPoint() {
    }

    public TouchPoint(float x, float y, float pressure, float size, int id) {
        this.x = x;
        this.y = y;
        this.pressure = pressure;
        this.size = size;
        this.id = id;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("x", x);
        m.put("y", y);
        m.put("p", pressure);
        m.put("s", size);
        m.put("id", id);
        if (hasRawCoordinates) {
            m.put("rx", rawX);
            m.put("ry", rawY);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    public static TouchPoint fromMap(Map<String, Object> m) {
        TouchPoint tp = new TouchPoint();
        tp.x = asFloat(m.get("x"));
        tp.y = asFloat(m.get("y"));
        tp.pressure = asFloat(m.get("p"), 1f);
        tp.size = asFloat(m.get("s"), 1f);
        tp.id = asInt(m.get("id"));
        tp.hasRawCoordinates = m.containsKey("rx") && m.containsKey("ry");
        tp.rawX = asFloat(m.get("rx"));
        tp.rawY = asFloat(m.get("ry"));
        return tp;
    }

    static float asFloat(Object o) {
        return o == null ? 0f : ((Number) o).floatValue();
    }

    static float asFloat(Object o, float def) {
        return o == null ? def : ((Number) o).floatValue();
    }

    static int asInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }
}