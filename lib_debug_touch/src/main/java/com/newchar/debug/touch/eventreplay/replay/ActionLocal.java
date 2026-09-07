package com.newchar.debug.touch.eventreplay.replay;

import java.util.HashMap;
import java.util.Map;

/**
 * 动作定位方式（移植自 WoolHelper accesshelper 的 ActionLocal）。
 *
 * <p>type 取值：
 * 0 不需要定位，直接走输出；
 * 1 通过 ViewId 定位；
 * 2 通过 Text 定位；
 * 3 通过坐标定位；
 * 4 手势 Path，无需定位。</p>
 */
public final class ActionLocal {

    public int type;
    /** 要操作的 View 类名（一般为 View 的全类名）。 */
    public String actionView;
    /** ViewId 比对。 */
    public String actionViewId;
    /** 控件文本描述（TextView 文本 / EditText hint 等）。 */
    public String actionTextDesc;
    /** 坐标定位，格式 "x,y"。 */
    public String actionLocation;
    /** 期望满足的 View 状态。 */
    public String actionViewState;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("actionView", actionView);
        m.put("actionViewId", actionViewId);
        m.put("actionTextDesc", actionTextDesc);
        m.put("actionLocation", actionLocation);
        m.put("actionViewState", actionViewState);
        return m;
    }

    public static ActionLocal fromMap(Map<String, Object> m) {
        ActionLocal l = new ActionLocal();
        l.type = asInt(m.get("type"));
        l.actionView = (String) m.get("actionView");
        l.actionViewId = (String) m.get("actionViewId");
        l.actionTextDesc = (String) m.get("actionTextDesc");
        l.actionLocation = (String) m.get("actionLocation");
        l.actionViewState = (String) m.get("actionViewState");
        return l;
    }

    static int asInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }
}
