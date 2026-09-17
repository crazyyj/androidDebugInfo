package com.newchar.probe.input;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** shell 输入 agent 使用的脚本内存模型。 */
final class AgentScript {

    String scriptId = "";
    String name = "";
    int sourceWidth;
    int sourceHeight;
    String targetPackage = "";
    final List<Step> steps = new ArrayList<>();

    /** 解析并校验 LQITS v1 脚本。 */
    static AgentScript parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!"LQITS".equals(root.optString("magic")) || root.optInt("version") != 1) {
            throw new JSONException("仅支持 LQITS v1 脚本");
        }
        AgentScript script = new AgentScript();
        script.scriptId = root.optString("scriptId", "script");
        script.name = root.optString("name", script.scriptId);
        script.sourceWidth = root.optInt("sourceWidth");
        script.sourceHeight = root.optInt("sourceHeight");
        script.targetPackage = root.optString("targetPackage");
        appendSteps(script, root.optJSONArray("steps"));
        return script;
    }

    /** 解析脚本步骤集合。 */
    private static void appendSteps(AgentScript script, JSONArray values) throws JSONException {
        if (values == null) return;
        for (int index = 0; index < values.length(); index++) {
            script.steps.add(Step.parse(values.getJSONObject(index), index));
        }
    }

    /** 一条脚本步骤。 */
    static final class Step {
        String id;
        String type;
        String coordinateMode;
        String failurePolicy;
        String packageName;
        String expectedPackage;
        long delayBeforeMs;
        long durationMs;
        long waitMs;
        long timeoutMs;
        float x;
        float y;
        int keyCode;
        int retryCount;
        final List<Point> points = new ArrayList<>();
        final List<Frame> frames = new ArrayList<>();
        Selector selector;

        /** 解析一条步骤及其轨迹、事件帧和节点选择器。 */
        static Step parse(JSONObject value, int index) throws JSONException {
            Step step = new Step();
            step.id = value.optString("id", "step_" + index);
            step.type = value.optString("type");
            step.coordinateMode = value.optString("coordinateMode", "source_pixel");
            step.failurePolicy = value.optString("failurePolicy", "continue");
            step.packageName = value.optString("packageName");
            step.expectedPackage = value.optString("expectedPackage");
            step.delayBeforeMs = value.optLong("delayBeforeMs");
            step.durationMs = value.optLong("durationMs", 100L);
            step.waitMs = value.optLong("waitMs", step.durationMs);
            step.timeoutMs = value.optLong("timeoutMs", 3000L);
            step.x = (float) value.optDouble("x");
            step.y = (float) value.optDouble("y");
            step.keyCode = value.optInt("keyCode");
            step.retryCount = Math.max(0, value.optInt("retryCount"));
            appendPoints(step.points, value.optJSONArray("points"));
            appendFrames(step.frames, value.optJSONArray("frames"));
            step.selector = Selector.parse(value.optJSONObject("selector"));
            return step;
        }
    }

    /** 一个单指轨迹点或多指触点。 */
    static final class Point {
        int id;
        float x;
        float y;
        float pressure;
        float size;
        long timeMs;

        /** 解析一个触点。 */
        static Point parse(JSONObject value) {
            Point point = new Point();
            point.id = value.optInt("id");
            point.x = (float) value.optDouble("x");
            point.y = (float) value.optDouble("y");
            point.pressure = (float) value.optDouble("pressure", 1d);
            point.size = (float) value.optDouble("size", 1d);
            point.timeMs = value.optLong("timeMs");
            return point;
        }
    }

    /** 一帧完整的多指 MotionEvent。 */
    static final class Frame {
        long timeMs;
        int action;
        final List<Point> pointers = new ArrayList<>();

        /** 解析一个多指事件帧。 */
        static Frame parse(JSONObject value) throws JSONException {
            Frame frame = new Frame();
            frame.timeMs = value.optLong("timeMs");
            frame.action = value.optInt("action");
            appendPoints(frame.pointers, value.optJSONArray("pointers"));
            return frame;
        }
    }

    /** 节点定位条件，所有非空字段同时匹配。 */
    static final class Selector {
        String resourceId;
        String text;
        String contentDescription;
        String className;

        /** 解析节点选择器；没有有效字段时返回 null。 */
        static Selector parse(JSONObject value) {
            if (value == null) return null;
            Selector selector = new Selector();
            selector.resourceId = value.optString("resourceId");
            selector.text = value.optString("text");
            selector.contentDescription = value.optString("contentDescription");
            selector.className = value.optString("className");
            return selector.isEmpty() ? null : selector;
        }

        /** 判断选择器是否没有任何条件。 */
        boolean isEmpty() {
            return resourceId.isEmpty() && text.isEmpty()
                    && contentDescription.isEmpty() && className.isEmpty();
        }
    }

    /** 解析触点数组。 */
    private static void appendPoints(List<Point> output, JSONArray values) throws JSONException {
        if (values == null) return;
        for (int index = 0; index < values.length(); index++) {
            output.add(Point.parse(values.getJSONObject(index)));
        }
    }

    /** 解析多指帧数组。 */
    private static void appendFrames(List<Frame> output, JSONArray values) throws JSONException {
        if (values == null) return;
        for (int index = 0; index < values.length(); index++) {
            output.add(Frame.parse(values.getJSONObject(index)));
        }
    }
}