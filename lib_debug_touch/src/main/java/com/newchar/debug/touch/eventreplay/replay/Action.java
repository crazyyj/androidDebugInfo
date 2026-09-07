package com.newchar.debug.touch.eventreplay.replay;

import java.util.HashMap;
import java.util.Map;

/**
 * 单个动作（移植自 WoolHelper accesshelper 的 Action）。
 *
 * <p>action 取值参考：1 点击、2 长按、3/4 横向滑动(左右)、5/6 纵向滑动(上下)、10 手势。</p>
 */
public final class Action {

    /** 1 普通事件（需要被触发）；-1 工具事件（被引用、无状态）。 */
    public int type = 1;
    /** 动作唯一标识。 */
    public String id = "";
    /** 动作类型（见类注释）。 */
    public String action = "";
    /** 动作所属页面。 */
    public String actionPage = "";
    /** 执行次数，0 为无限循环。 */
    public int actionTimes = 1;
    /** 跟随执行的下一组动作 id。 */
    public String actionFollowUp = "";
    /** 用于比对 Dialog 等场景的类名。 */
    public String actionClassName = "";
    /** 定位信息。 */
    public ActionLocal actionLocal;
    /** 输出信息。 */
    public ActionOutput actionOutput;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("actionId", id);
        m.put("action", action);
        m.put("actionPage", actionPage);
        m.put("actionTimes", actionTimes);
        m.put("actionFollowUp", actionFollowUp);
        m.put("actionClassName", actionClassName);
        if (actionLocal != null) m.put("actionLocal", actionLocal.toMap());
        if (actionOutput != null) m.put("actionOutput", actionOutput.toMap());
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Action fromMap(Map<String, Object> m) {
        Action a = new Action();
        a.type = asInt(m.get("type"), 1);
        a.id = (String) m.get("actionId");
        a.action = (String) m.get("action");
        a.actionPage = (String) m.get("actionPage");
        a.actionTimes = asInt(m.get("actionTimes"), 1);
        a.actionFollowUp = (String) m.get("actionFollowUp");
        a.actionClassName = (String) m.get("actionClassName");
        Map<String, Object> local = (Map<String, Object>) m.get("actionLocal");
        if (local != null) a.actionLocal = ActionLocal.fromMap(local);
        Map<String, Object> out = (Map<String, Object>) m.get("actionOutput");
        if (out != null) a.actionOutput = ActionOutput.fromMap(out);
        return a;
    }

    static int asInt(Object o, int def) {
        return o == null ? def : ((Number) o).intValue();
    }
}
