package com.newchar.debug.touch.eventreplay.script;

import com.newchar.debug.touch.eventreplay.model.Json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可由 PC shell agent 执行的触摸脚本。
 *
 * <p>坐标默认采用录制设备像素，执行端根据 sourceWidth/sourceHeight 映射到目标屏幕。</p>
 */
public final class InputScript {

    public static final String MAGIC = "LQITS";
    public static final int VERSION = 1;

    /** 脚本格式标识。 */
    public String magic = MAGIC;
    /** 脚本格式版本。 */
    public int version = VERSION;
    /** 脚本唯一标识。 */
    public String scriptId = "";
    /** 脚本显示名称。 */
    public String name = "";
    /** 录制屏幕宽度。 */
    public int sourceWidth;
    /** 录制屏幕高度。 */
    public int sourceHeight;
    /** 可选目标应用包名。 */
    public String targetPackage = "";
    /** 有序执行步骤。 */
    public final List<Step> steps = new ArrayList<>();

    /** 将脚本转换为稳定的 JSON 字段映射。 */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("magic", magic);
        result.put("version", version);
        result.put("scriptId", scriptId);
        result.put("name", name);
        result.put("sourceWidth", sourceWidth);
        result.put("sourceHeight", sourceHeight);
        result.put("targetPackage", targetPackage);
        result.put("steps", mapSteps());
        return result;
    }

    /** 序列化脚本为 JSON。 */
    public String toJsonString() {
        return Json.toJson(toMap());
    }

    /** 将脚本保存到指定文件。 */
    public void writeToFile(File file) throws IOException {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(toJsonString().getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }

    /** 转换所有步骤，避免调用方直接处理内部集合。 */
    private List<Object> mapSteps() {
        List<Object> result = new ArrayList<>();
        for (Step step : steps) result.add(step.toMap());
        return result;
    }

    /** 一条可执行步骤。 */
    public static final class Step {
        /** 步骤标识。 */
        public String id = "";
        /** tap、long_press、gesture 或 multi_touch。 */
        public String type = "";
        /** source_pixel、normalized 或 pixel。 */
        public String coordinateMode = "source_pixel";
        /** 步骤执行前等待时长。 */
        public long delayBeforeMs;
        /** 手势或长按时长。 */
        public long durationMs;
        /** 点击横坐标。 */
        public float x;
        /** 点击纵坐标。 */
        public float y;
        /** 兼容失败策略字段；当前执行器始终继续尝试后续步骤。 */
        public String failurePolicy = "continue";
        /** 失败重试次数。 */
        public int retryCount;
        /** 单指轨迹。 */
        public final List<Point> points = new ArrayList<>();
        /** 多指原始事件帧。 */
        public final List<Frame> frames = new ArrayList<>();

        /** 将步骤转换为 JSON 字段映射。 */
        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            putBaseFields(result);
            result.put("points", mapPoints(points));
            result.put("frames", mapFrames());
            return result;
        }

        /** 写入步骤基础字段。 */
        private void putBaseFields(Map<String, Object> result) {
            result.put("id", id);
            result.put("type", type);
            result.put("coordinateMode", coordinateMode);
            result.put("delayBeforeMs", delayBeforeMs);
            result.put("durationMs", durationMs);
            result.put("x", x);
            result.put("y", y);
            result.put("failurePolicy", failurePolicy);
            result.put("retryCount", retryCount);
        }

        /** 转换多指事件帧。 */
        private List<Object> mapFrames() {
            List<Object> result = new ArrayList<>();
            for (Frame frame : frames) result.add(frame.toMap());
            return result;
        }
    }

    /** 一帧 MotionEvent 的脚本表达。 */
    public static final class Frame {
        /** 相对当前手势开始的时间。 */
        public long timeMs;
        /** 含 pointerIndex 的完整 action。 */
        public int action;
        /** 当前全部触点。 */
        public final List<Point> pointers = new ArrayList<>();

        /** 将事件帧转换为 JSON 字段映射。 */
        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("timeMs", timeMs);
            result.put("action", action);
            result.put("pointers", mapPoints(pointers));
            return result;
        }
    }

    /** 一个带时间与 pointerId 的触点。 */
    public static final class Point {
        /** pointerId。 */
        public int id;
        /** 横坐标。 */
        public float x;
        /** 纵坐标。 */
        public float y;
        /** 压力。 */
        public float pressure = 1f;
        /** 触点尺寸。 */
        public float size = 1f;
        /** 相对手势开始的时间。 */
        public long timeMs;

        /** 将触点转换为 JSON 字段映射。 */
        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("x", x);
            result.put("y", y);
            result.put("pressure", pressure);
            result.put("size", size);
            result.put("timeMs", timeMs);
            return result;
        }
    }

    /** 转换触点集合。 */
    private static List<Object> mapPoints(List<Point> points) {
        List<Object> result = new ArrayList<>();
        for (Point point : points) result.add(point.toMap());
        return result;
    }
}