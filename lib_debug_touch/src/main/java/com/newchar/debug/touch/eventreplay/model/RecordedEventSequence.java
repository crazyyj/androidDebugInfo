package com.newchar.debug.touch.eventreplay.model;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一段被录制下来的事件序列（一次操作/一次页面交互）。
 *
 * <p>这是“事件 JSON 化”的核心载体：把原本写在二进制 MTES 文件里的触摸录制，
 * 以 JSON 形式保存下来，从而可以被新代码读取、解析与复现。</p>
 */
public final class RecordedEventSequence {

    public static final String MAGIC = "LQERS"; // newlq event replay sequence
    public static final int VERSION = 1;

    public String magic = MAGIC;
    public int version = VERSION;
    /** 创建时间戳（ms）。 */
    public long createdAt;
    /** 录制时所在页面（Activity 简单类名）。 */
    public String activityName;
    /** 屏幕宽度，便于回放时做坐标映射。 */
    public int screenWidth;
    /** 屏幕高度。 */
    public int screenHeight;
    /** 录制来源：touch（原始触摸采集）。 */
    public String source = "touch";
    /** 事件列表。 */
    public final List<RecordedEvent> events = new ArrayList<>();

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("magic", magic);
        m.put("version", version);
        m.put("createdAt", createdAt);
        m.put("activityName", activityName);
        m.put("screenWidth", screenWidth);
        m.put("screenHeight", screenHeight);
        m.put("source", source);
        List<Object> evs = new ArrayList<>();
        for (RecordedEvent e : events) evs.add(e.toMap());
        m.put("events", evs);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static RecordedEventSequence fromMap(Map<String, Object> m) {
        RecordedEventSequence seq = new RecordedEventSequence();
        seq.magic = (String) m.getOrDefault("magic", MAGIC);
        seq.version = asInt(m.get("version"));
        seq.createdAt = asLong(m.get("createdAt"));
        seq.activityName = (String) m.get("activityName");
        seq.screenWidth = asInt(m.get("screenWidth"));
        seq.screenHeight = asInt(m.get("screenHeight"));
        seq.source = (String) m.getOrDefault("source", "touch");
        List<Object> evs = (List<Object>) m.get("events");
        if (evs != null) {
            for (Object o : evs) seq.events.add(RecordedEvent.fromMap((Map<String, Object>) o));
        }
        return seq;
    }

    /** 序列化为 JSON 字符串。 */
    public String toJsonString() {
        return Json.toJson(toMap());
    }

    /** 从 JSON 字符串解析（新代码处理事件入口）。 */
    public static RecordedEventSequence fromJsonString(String json) {
        Object root = Json.fromJson(json);
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("Invalid event sequence json");
        }
        return fromMap((Map<String, Object>) root);
    }

    /** 保存到文件（JSON 形式）。 */
    public void writeToFile(File file) throws IOException {
        Files.write(file.toPath(), toJsonString().getBytes(StandardCharsets.UTF_8));
    }

    /** 从文件读取（新代码处理事件入口）。 */
    public static RecordedEventSequence readFromFile(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return fromJsonString(new String(bytes, StandardCharsets.UTF_8));
    }

    public int eventCount() {
        return events.size();
    }

    static long asLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    static int asInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }
}
