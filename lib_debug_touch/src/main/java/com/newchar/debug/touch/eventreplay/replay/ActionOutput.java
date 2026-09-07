package com.newchar.debug.touch.eventreplay.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动作执行输出（移植自 WoolHelper accesshelper 的 ActionOutput）。
 *
 * <p>type 取值：
 * 0 文本输出；
 * 1 一次性粘贴；
 * 2 逐段输入剪切板；
 * 3 打开新页面；
 * 10 输出手势 Path；
 * 20 播放媒体。</p>
 */
public final class ActionOutput {

    public int type;
    /** 输出的文本。 */
    public String outputText;
    /** 输出一个点击/滑动/全局事件。 */
    public String outputAction;
    /** 打开新页面的包名/类名。 */
    public String openPkgName;
    public String openClassName;
    /** 手势执行耗时（ms），默认 120。 */
    public long gestureDurationMs = 120L;
    /** 手势路径，每个元素是 {x, y}。 */
    public List<float[]> gesturePath = new ArrayList<>();

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("text", outputText);
        m.put("outputAction", outputAction);
        m.put("openPkgName", openPkgName);
        m.put("openClassName", openClassName);
        m.put("durationMs", gestureDurationMs);
        List<Object> path = new ArrayList<>();
        for (float[] p : gesturePath) {
            List<Object> pt = new ArrayList<>();
            pt.add(p[0]);
            pt.add(p[1]);
            path.add(pt);
        }
        m.put("path", path);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static ActionOutput fromMap(Map<String, Object> m) {
        ActionOutput o = new ActionOutput();
        o.type = asInt(m.get("type"));
        o.outputText = (String) m.get("text");
        o.outputAction = (String) m.get("outputAction");
        o.openPkgName = (String) m.get("openPkgName");
        o.openClassName = (String) m.get("openClassName");
        o.gestureDurationMs = asLong(m.get("durationMs"), 120L);
        List<Object> path = (List<Object>) m.get("path");
        if (path != null) {
            for (Object e : path) {
                List<Object> pt = (List<Object>) e;
                o.gesturePath.add(new float[]{
                        ((Number) pt.get(0)).floatValue(),
                        ((Number) pt.get(1)).floatValue()
                });
            }
        }
        return o;
    }

    static int asInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    static long asLong(Object o, long def) {
        return o == null ? def : ((Number) o).longValue();
    }
}
