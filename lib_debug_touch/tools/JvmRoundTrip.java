package com.newchar.debug.touch.eventreplay;

import com.newchar.debug.touch.eventreplay.model.RecordedEvent;
import com.newchar.debug.touch.eventreplay.model.RecordedEventSequence;
import com.newchar.debug.touch.eventreplay.model.TouchPoint;
import com.newchar.debug.touch.eventreplay.replay.Action;
import com.newchar.debug.touch.eventreplay.replay.ActionStream;
import com.newchar.debug.touch.eventreplay.replay.TouchToActionConverter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯 JVM 验证（无需 Android）：证明“保存下来的 event 已经 JSON 化、可以被新代码处理”。
 *
 * <p>运行：javac 编译 model + replay 下的纯 Java 文件后，执行本类。
 * 它会：1) 构造一段触摸录制；2) 序列化为 JSON 并落盘；3) 反序列化回对象；
 * 4) 转换为 accesshelper 风格动作流（另一种形式）并同样 JSON 化、回读。</p>
 */
public class JvmRoundTrip {

    public static void main(String[] args) throws Exception {
        File outDir = new File("lib_debug_touch/eventreplay/sample");
        outDir.mkdirs();

        RecordedEventSequence seq = buildSampleSequence();

        // 1) 序列化为 JSON
        String json = seq.toJsonString();
        System.out.println("=== RecordedEventSequence JSON ===");
        System.out.println(pretty(json));
        File seqFile = new File(outDir, "recorded_event_sample.json");
        seq.writeToFile(seqFile);

        // 2) 新代码读取并处理（反序列化）
        RecordedEventSequence parsed = RecordedEventSequence.readFromFile(seqFile);
        check(parsed.eventCount() == seq.eventCount(), "event count mismatch");
        check(parsed.magic.equals(RecordedEventSequence.MAGIC), "magic mismatch");
        System.out.println("[OK] 新代码成功读取事件序列，事件数 = " + parsed.eventCount());

        // 3) 转换为 accesshelper 风格动作流（另一种事件复现形式）
        ActionStream stream = TouchToActionConverter.convert(parsed);
        String streamJson = stream.toJsonString();
        System.out.println("\n=== ActionStream (另一种形式) JSON ===");
        System.out.println(pretty(streamJson));
        File streamFile = new File(outDir, "action_stream_sample.json");
        Files.write(streamFile.toPath(), streamJson.getBytes(StandardCharsets.UTF_8));

        // 4) 新代码读取并处理动作流
        ActionStream parsedStream = ActionStream.fromJsonString(
                new String(Files.readAllBytes(streamFile.toPath()), StandardCharsets.UTF_8));
        check(parsedStream.actions.size() == stream.actions.size(), "action count mismatch");
        for (Action a : parsedStream.actions) {
            System.out.println("[OK] 解析到动作 action=" + a.action
                    + " localType=" + (a.actionLocal == null ? "-" : a.actionLocal.type)
                    + " outputType=" + (a.actionOutput == null ? "-" : a.actionOutput.type));
        }

        System.out.println("\n全部校验通过 ✅  示例文件已生成:");
        System.out.println("  - " + seqFile.getAbsolutePath());
        System.out.println("  - " + streamFile.getAbsolutePath());
    }

    private static RecordedEventSequence buildSampleSequence() {
        RecordedEventSequence seq = new RecordedEventSequence();
        seq.createdAt = 1694000000000L;
        seq.activityName = "MainActivity";
        seq.screenWidth = 1080;
        seq.screenHeight = 2400;
        seq.source = "touch";

        long base = 1_000_000_000L;

        // 点击：DOWN @ (540,800) -> UP @ (542,802)
        seq.events.add(down(base, base, 540f, 800f, 0));
        seq.events.add(up(base, base + 60_000_000L, 542f, 802f, 0));

        // 滑动：DOWN @ (300,1800) -> MOVE x3 -> UP @ (300,600)
        long t = base + 200_000_000L;
        seq.events.add(down(t, t, 300f, 1800f, 0));
        seq.events.add(move(t, t + 30_000_000L, 300f, 1500f, 0));
        seq.events.add(move(t, t + 60_000_000L, 300f, 1100f, 0));
        seq.events.add(move(t, t + 90_000_000L, 300f, 850f, 0));
        seq.events.add(up(t, t + 120_000_000L, 300f, 600f, 0));

        return seq;
    }

    private static RecordedEvent down(long downTime, long eventTime, float x, float y, int id) {
        RecordedEvent e = new RecordedEvent();
        e.downTime = downTime;
        e.eventTime = eventTime;
        e.action = 0;       // ACTION_DOWN
        e.actionMasked = 0;
        e.pointerCount = 1;
        e.pointerIds = new int[]{id};
        e.points = new ArrayList<>();
        e.points.add(new TouchPoint(x, y, 1f, 1f, id));
        return e;
    }

    private static RecordedEvent move(long downTime, long eventTime, float x, float y, int id) {
        RecordedEvent e = down(downTime, eventTime, x, y, id);
        e.action = 2;       // ACTION_MOVE
        e.actionMasked = 2;
        return e;
    }

    private static RecordedEvent up(long downTime, long eventTime, float x, float y, int id) {
        RecordedEvent e = down(downTime, eventTime, x, y, id);
        e.action = 1;       // ACTION_UP
        e.actionMasked = 1;
        return e;
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    /** 极简缩进美化，便于阅读生成的 JSON。 */
    private static String pretty(String json) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            switch (c) {
                case '{':
                case '[':
                    sb.append(c).append('\n');
                    indent += 2;
                    sb.append(spaces(indent));
                    break;
                case '}':
                case ']':
                    indent -= 2;
                    sb.append('\n').append(spaces(indent)).append(c);
                    break;
                case ',':
                    sb.append(c).append('\n').append(spaces(indent));
                    break;
                case ':':
                    sb.append(": ");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String spaces(int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) s.append(' ');
        return s.toString();
    }
}
