package com.newchar.debug.touch.eventreplay;

import com.newchar.debug.touch.eventreplay.model.RecordedEvent;
import com.newchar.debug.touch.eventreplay.model.RecordedEventSequence;
import com.newchar.debug.touch.eventreplay.model.TouchPoint;
import com.newchar.debug.touch.eventreplay.replay.Action;
import com.newchar.debug.touch.eventreplay.replay.ActionStream;
import com.newchar.debug.touch.eventreplay.replay.TouchToActionConverter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 纯 JVM 验证（无需 Android）：证明“保存下来的 event 已经 JSON 化、可以被新代码处理”。
 *
 * <p>以 JUnit 单测运行（{@code :lib_debug_touch:testDebugUnitTest}），不再单独以脚本形式执行。
 * 它会：1) 构造一段触摸录制；2) 序列化为 JSON 并落盘；3) 反序列化回对象；
 * 4) 转换为 accesshelper 风格动作流（另一种形式）并同样 JSON 化、回读。</p>
 */
public class JvmRoundTripTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void recordedEventSequenceRoundTrip() throws Exception {
        File outDir = tmp.newFolder("sample");

        RecordedEventSequence seq = buildSampleSequence();

        // 1) 序列化为 JSON
        String json = seq.toJsonString();
        System.out.println("=== RecordedEventSequence JSON ===");
        System.out.println(pretty(json));
        File seqFile = new File(outDir, "recorded_event_sample.json");
        seq.writeToFile(seqFile);

        // 2) 新代码读取并处理（反序列化）
        RecordedEventSequence parsed = RecordedEventSequence.readFromFile(seqFile);
        assertEquals("event count mismatch", seq.eventCount(), parsed.eventCount());
        assertEquals("magic mismatch", RecordedEventSequence.MAGIC, parsed.magic);
        System.out.println("[OK] 新代码成功读取事件序列，事件数 = " + parsed.eventCount());
    }

    @Test
    public void actionStreamRoundTrip() throws Exception {
        File outDir = tmp.newFolder("sample");

        RecordedEventSequence seq = buildSampleSequence();
        RecordedEventSequence parsed = RecordedEventSequence.readFromFile(writeSequence(seq, outDir));

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
        assertEquals("action count mismatch", stream.actions.size(), parsedStream.actions.size());
        for (Action a : parsedStream.actions) {
            assertNotNull(a.action);
            System.out.println("[OK] 解析到动作 action=" + a.action
                    + " localType=" + (a.actionLocal == null ? "-" : a.actionLocal.type)
                    + " outputType=" + (a.actionOutput == null ? "-" : a.actionOutput.type));
        }
    }

    /** 随工程提交的示例 JSON（形式一）必须始终可被新代码解析。 */
    @Test
    public void committedRecordedEventSampleRemainsReadable() throws Exception {
        RecordedEventSequence seq = RecordedEventSequence.fromJsonString(
                readResource("/eventreplay/sample/recorded_event_sample.json"));
        assertEquals(RecordedEventSequence.MAGIC, seq.magic);
        assertEquals("MainActivity", seq.activityName);
        assertEquals(1080, seq.screenWidth);
        assertEquals(2400, seq.screenHeight);
        // 点击（DOWN+UP）+ 滑动（DOWN+3xMOVE+UP）= 7 条事件
        assertEquals(7, seq.eventCount());
    }

    /** 随工程提交的示例 JSON（形式二，动作流）必须始终可被新代码解析。 */
    @Test
    public void committedActionStreamSampleRemainsReadable() throws Exception {
        ActionStream stream = ActionStream.fromJsonString(
                readResource("/eventreplay/sample/action_stream_sample.json"));
        assertEquals(2, stream.actions.size());
        assertEquals("1", stream.actions.get(0).action);    // 点击
        assertEquals("10", stream.actions.get(1).action);  // 手势
    }

    /** 读取测试资源文件内容。 */
    private static String readResource(String path) throws Exception {
        InputStream in = JvmRoundTripTest.class.getResourceAsStream(path);
        assertNotNull("missing test resource: " + path, in);
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    /** 将事件序列写入临时目录并返回文件。 */
    private File writeSequence(RecordedEventSequence seq, File outDir) throws Exception {
        File seqFile = new File(outDir, "recorded_event_sample.json");
        seq.writeToFile(seqFile);
        return seqFile;
    }

    private RecordedEventSequence buildSampleSequence() {
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

    private RecordedEvent down(long downTime, long eventTime, float x, float y, int id) {
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

    private RecordedEvent move(long downTime, long eventTime, float x, float y, int id) {
        RecordedEvent e = down(downTime, eventTime, x, y, id);
        e.action = 2;       // ACTION_MOVE
        e.actionMasked = 2;
        return e;
    }

    private RecordedEvent up(long downTime, long eventTime, float x, float y, int id) {
        RecordedEvent e = down(downTime, eventTime, x, y, id);
        e.action = 1;       // ACTION_UP
        e.actionMasked = 1;
        return e;
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
