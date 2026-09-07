package com.newchar.debug.touch.eventreplay.replay;

import com.newchar.debug.touch.eventreplay.model.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作流 / 事件流（移植自 WoolHelper accesshelper 的 ActionSteam）。
 *
 * <p>一条动作流对应某个 App 包名的一组有序动作，可被无障碍服务按序复现。
 * 本模块中，它也是“事件复现的另一种形式”——把原始触摸序列转换为可被
 * 无障碍服务消费的 JSON 动作流。</p>
 */
public final class ActionStream {

    public static final String MAGIC = "LQAST"; // newlq action stream
    public static final int VERSION = 1;

    public String magic = MAGIC;
    public int version = VERSION;
    /** 目标应用包名。 */
    public String pkgName = "";
    /** 循环执行次数，-1 表示循环。 */
    public int execTimes = 1;
    /** 是否可用。 */
    public boolean available;
    /** 关联页面。 */
    public String activityName;
    /** 有序动作列表。 */
    public final List<Action> actions = new ArrayList<>();

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("magic", magic);
        m.put("version", version);
        m.put("pkgName", pkgName);
        m.put("execTimes", execTimes);
        m.put("available", available);
        m.put("activityName", activityName);
        List<Object> acts = new ArrayList<>();
        for (Action a : actions) acts.add(a.toMap());
        m.put("actionList", acts);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static ActionStream fromMap(Map<String, Object> m) {
        ActionStream s = new ActionStream();
        s.magic = (String) m.getOrDefault("magic", MAGIC);
        s.version = asInt(m.get("version"));
        s.pkgName = (String) m.get("pkgName");
        s.execTimes = asInt(m.get("execTimes"), 1);
        Object av = m.get("available");
        s.available = av instanceof Boolean ? (Boolean) av : false;
        s.activityName = (String) m.get("activityName");
        List<Object> acts = (List<Object>) m.get("actionList");
        if (acts != null) {
            for (Object o : acts) {
                Action a = Action.fromMap((Map<String, Object>) o);
                if (a != null) s.actions.add(a);
            }
        }
        return s;
    }

    /** 序列化为 JSON 字符串。 */
    public String toJsonString() {
        return Json.toJson(toMap());
    }

    /** 从 JSON 字符串解析（新代码处理动作流入口）。 */
    public static ActionStream fromJsonString(String json) {
        Object root = Json.fromJson(json);
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("Invalid action stream json");
        }
        return fromMap((Map<String, Object>) root);
    }

    static int asInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    static int asInt(Object o, int def) {
        return o == null ? def : ((Number) o).intValue();
    }
}
