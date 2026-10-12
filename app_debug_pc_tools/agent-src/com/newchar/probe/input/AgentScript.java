package com.newchar.probe.input;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** shell 输入 agent 使用的脚本内存模型。 */
final class AgentScript {

    String scriptId = "";
    String name = "";
    int sourceWidth;
    int sourceHeight;
    String targetPackage = "";
    final List<Step> steps = new ArrayList<>();

    /** 解析并校验 LQITS v1/v2 脚本。 */
    static AgentScript parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        int version = root.optInt("version");
        if (!"LQITS".equals(root.optString("magic")) || version < 1 || version > 2) {
            throw new JSONException("仅支持 LQITS v1/v2 脚本");
        }
        AgentScript script = new AgentScript();
        script.scriptId = root.optString("scriptId", "script");
        script.name = root.optString("name", script.scriptId);
        script.sourceWidth = root.optInt("sourceWidth");
        script.sourceHeight = root.optInt("sourceHeight");
        script.targetPackage = root.optString("targetPackage");
        appendSteps(script, root.optJSONArray("steps"));
        validateSteps(script, version);
        return script;
    }

    /** 解析脚本步骤集合。 */
    private static void appendSteps(AgentScript script, JSONArray values) throws JSONException {
        if (values == null) return;
        for (int index = 0; index < values.length(); index++) {
            script.steps.add(Step.parse(values.getJSONObject(index), index));
        }
    }

    /** 校验步骤 ID、条件等待版本和跳转目标。 */
    private static void validateSteps(AgentScript script, int version) throws JSONException {
        if (script.steps.isEmpty()) throw new JSONException("脚本没有可执行步骤");
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < script.steps.size(); index++) {
            Step step = script.steps.get(index);
            if (step.type.isEmpty()) throw new JSONException("第 " + (index + 1) + " 步缺少 type");
            if (version == 2 && !step.hasExplicitId) throw new JSONException("第 " + (index + 1) + " 步缺少 id");
            if (version == 2 && !ids.add(step.id)) throw new JSONException("步骤 id 重复: " + step.id);
            validateWaitStep(script, step, version, index);
        }
    }

    /** 校验条件等待步骤的字段组合。 */
    private static void validateWaitStep(AgentScript script, Step step, int version, int index)
            throws JSONException {
        if (step.waitState == null) return;
        String prefix = "第 " + (index + 1) + " 步";
        if (version != 2) throw new JSONException(prefix + " waitState 仅支持 LQITS v2");
        if (!"wait".equals(step.type)) throw new JSONException(prefix + " waitState 只能用于 wait");
        if (step.waitMs < 1L || step.waitMs > 600000L) throw new JSONException(prefix + " waitMs 超出范围");
        if (step.retryCount != 0) throw new JSONException(prefix + " waitState 的 retryCount 必须为 0");
        if (!"stop".equals(step.timeoutPolicy) && !"jump".equals(step.timeoutPolicy)) {
            throw new JSONException(prefix + " timeoutPolicy 无效");
        }
        if ("jump".equals(step.timeoutPolicy) && findStep(script, step.jumpTargetId) < 0) {
            throw new JSONException("跳转目标不存在: " + step.jumpTargetId);
        }
    }

    /** 返回步骤 ID 对应下标，不存在时返回 -1。 */
    static int findStep(AgentScript script, String id) {
        for (int index = 0; index < script.steps.size(); index++) {
            if (script.steps.get(index).id.equals(id)) return index;
        }
        return -1;
    }

    /** 一条脚本步骤。 */
    static final class Step {
        String id;
        boolean hasExplicitId;
        String name;
        String description;
        String type;
        String coordinateMode;
        String failurePolicy;
        String packageName;
        long delayBeforeMs;
        long durationMs;
        long waitMs;
        float x;
        float y;
        int keyCode;
        int retryCount;
        String timeoutPolicy;
        String jumpTargetId;
        final List<Point> points = new ArrayList<>();
        final List<Frame> frames = new ArrayList<>();
        Selector selector;
        WaitState waitState;
        JSONObject source;

        /** 解析一条步骤及其轨迹、事件帧和节点选择器。 */
        static Step parse(JSONObject value, int index) throws JSONException {
            Step step = new Step();
            step.source = value;
            step.hasExplicitId = value.has("id") && !value.optString("id").isEmpty();
            step.id = step.hasExplicitId ? value.optString("id") : "step_" + index;
            step.name = value.optString("name");
            step.description = value.optString("description");
            step.type = value.optString("type");
            step.coordinateMode = value.optString("coordinateMode", "source_pixel");
            step.failurePolicy = value.optString("failurePolicy", "continue");
            step.packageName = value.optString("packageName");
            step.delayBeforeMs = value.optLong("delayBeforeMs");
            step.durationMs = value.optLong("durationMs", 100L);
            step.waitMs = value.optLong("waitMs", step.durationMs);
            step.x = (float) value.optDouble("x");
            step.y = (float) value.optDouble("y");
            step.keyCode = value.optInt("keyCode");
            step.retryCount = Math.max(0, value.optInt("retryCount"));
            step.timeoutPolicy = value.optString("timeoutPolicy", "stop");
            step.jumpTargetId = value.optString("jumpTargetId");
            appendPoints(step.points, value.optJSONArray("points"));
            appendFrames(step.frames, value.optJSONArray("frames"));
            step.selector = Selector.parse(value.optJSONObject("selector"));
            JSONObject waitStateValue = value.optJSONObject("waitState");
            if (value.has("waitState") && !value.isNull("waitState") && waitStateValue == null) {
                throw new JSONException("第 " + (index + 1) + " 步 waitState 必须为对象");
            }
            step.waitState = WaitState.parse(waitStateValue, index);
            return step;
        }
    }

    /** 条件等待的轮询和组合配置。 */
    static final class WaitState {
        long pollIntervalMs;
        String match;
        int consecutiveMatches;
        int maxTimeoutCount;
        final List<Condition> conditions = new ArrayList<>();

        /** 解析并校验条件等待配置。 */
        static WaitState parse(JSONObject value, int stepIndex) throws JSONException {
            if (value == null) return null;
            WaitState state = new WaitState();
            state.pollIntervalMs = value.optLong("pollIntervalMs", 250L);
            state.match = value.optString("match", "all");
            state.consecutiveMatches = value.optInt("consecutiveMatches", 1);
            state.maxTimeoutCount = value.optInt("maxTimeoutCount", 10);
            appendConditions(state.conditions, value.optJSONArray("conditions"), stepIndex);
            state.validate(stepIndex);
            return state;
        }

        /** 校验轮询范围、组合方式和超时次数。 */
        private void validate(int stepIndex) throws JSONException {
            String prefix = "第 " + (stepIndex + 1) + " 步";
            if (pollIntervalMs < 50L || pollIntervalMs > 5000L) throw new JSONException(prefix + " pollIntervalMs 超出范围");
            if (!"all".equals(match) && !"any".equals(match)) throw new JSONException(prefix + " match 无效");
            if (consecutiveMatches < 1 || consecutiveMatches > 10) {
                throw new JSONException(prefix + " consecutiveMatches 超出范围");
            }
            if (maxTimeoutCount < 1 || maxTimeoutCount > 100) {
                throw new JSONException(prefix + " maxTimeoutCount 超出范围");
            }
            if (conditions.isEmpty()) throw new JSONException(prefix + " conditions 不能为空");
        }
    }

    /** 一条 UI、前台组件或截图区域颜色条件。 */
    static final class Condition {
        String kind;
        Selector selector;
        boolean expected;
        String attribute;
        String operator;
        String value;
        boolean ignoreCase;
        String packageName;
        String activityName;
        String coordinateMode;
        float left;
        float top;
        float right;
        float bottom;
        String color;
        int tolerance;
        float minMatchRatio;
        int sampleStride;
        Pattern regexPattern;

        /** 解析并校验一条等待条件。 */
        static Condition parse(JSONObject value, int stepIndex, int conditionIndex) throws JSONException {
            Condition condition = new Condition();
            condition.parseCommon(value);
            condition.parseRegion(value);
            condition.validate(stepIndex, conditionIndex);
            return condition;
        }

        /** 解析条件的通用和 UI 字段。 */
        private void parseCommon(JSONObject source) {
            kind = source.optString("kind");
            selector = Selector.parse(source.optJSONObject("selector"));
            expected = source.optBoolean("expected", true);
            attribute = source.optString("attribute");
            operator = source.optString("operator", "equals");
            value = source.optString("value");
            ignoreCase = source.optBoolean("ignoreCase", true);
            packageName = source.optString("packageName");
            activityName = source.optString("activityName");
        }

        /** 解析截图区域颜色字段。 */
        private void parseRegion(JSONObject source) {
            coordinateMode = source.optString("coordinateMode", "normalized");
            left = (float) source.optDouble("left");
            top = (float) source.optDouble("top");
            right = (float) source.optDouble("right");
            bottom = (float) source.optDouble("bottom");
            color = source.optString("color");
            tolerance = source.optInt("tolerance", 12);
            minMatchRatio = (float) source.optDouble("minMatchRatio", 0.65d);
            sampleStride = source.optInt("sampleStride", 2);
        }

        /** 按条件类型校验必要字段。 */
        private void validate(int stepIndex, int conditionIndex) throws JSONException {
            String prefix = "第 " + (stepIndex + 1) + " 步条件 " + (conditionIndex + 1);
            if ("ui_exists".equals(kind)) requireSelector(prefix);
            else if ("ui_attribute".equals(kind)) validateUiAttribute(prefix);
            else if ("foreground".equals(kind)) {
                if (packageName.isEmpty()) throw new JSONException(prefix + " 缺少 packageName");
            } else if ("region_color".equals(kind)) validateRegion(prefix);
            else throw new JSONException(prefix + " kind 无效: " + kind);
        }

        /** 校验 UI 属性、操作符及正则表达式。 */
        private void validateUiAttribute(String prefix) throws JSONException {
            requireSelector(prefix);
            if (!oneOf(attribute, "text", "contentDescription", "selected", "checked", "enabled", "focused")) {
                throw new JSONException(prefix + " attribute 无效");
            }
            if (!oneOf(operator, "equals", "not_equals", "contains", "regex")) {
                throw new JSONException(prefix + " operator 无效");
            }
            if (oneOf(attribute, "selected", "checked", "enabled", "focused")
                    && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new JSONException(prefix + " 布尔属性 value 无效");
            }
            if (value.length() > 1000) throw new JSONException(prefix + " value 过长");
            if ("regex".equals(operator)) compileRegex(prefix);
        }

        /** 校验截图区域和颜色采样字段。 */
        private void validateRegion(String prefix) throws JSONException {
            if (!oneOf(coordinateMode, "normalized", "source_pixel", "pixel")) {
                throw new JSONException(prefix + " coordinateMode 无效");
            }
            if (right <= left || bottom <= top) throw new JSONException(prefix + " 截图区域无效");
            if ("normalized".equals(coordinateMode)
                    && (left < 0f || top < 0f || right > 1f || bottom > 1f)) {
                throw new JSONException(prefix + " normalized 坐标越界");
            }
            if (!"target_ratio".equals(operator)) throw new JSONException(prefix + " 仅支持 target_ratio");
            if (!color.matches("#[0-9a-fA-F]{6}")) throw new JSONException(prefix + " color 必须为 #RRGGBB");
            if (tolerance < 0 || tolerance > 255) throw new JSONException(prefix + " tolerance 超出范围");
            if (minMatchRatio < 0f || minMatchRatio > 1f) throw new JSONException(prefix + " minMatchRatio 超出范围");
            if (sampleStride < 1 || sampleStride > 50) throw new JSONException(prefix + " sampleStride 超出范围");
        }

        /** 要求条件包含至少一个节点定位字段。 */
        private void requireSelector(String prefix) throws JSONException {
            if (selector == null) throw new JSONException(prefix + " 缺少 selector");
        }

        /** 预编译正则表达式并应用大小写规则。 */
        private void compileRegex(String prefix) throws JSONException {
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
            try {
                regexPattern = Pattern.compile(value, flags);
            } catch (PatternSyntaxException error) {
                throw new JSONException(prefix + " regex 无效: " + error.getDescription());
            }
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

    /** 解析等待条件数组。 */
    private static void appendConditions(List<Condition> output, JSONArray values, int stepIndex)
            throws JSONException {
        if (values == null) return;
        for (int index = 0; index < values.length(); index++) {
            output.add(Condition.parse(values.getJSONObject(index), stepIndex, index));
        }
    }

    /** 判断字符串是否等于任一允许值。 */
    private static boolean oneOf(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) return true;
        }
        return false;
    }
}