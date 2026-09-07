package com.newchar.debug.touch.eventreplay.replay;

import com.newchar.debug.touch.eventreplay.model.RecordedEvent;
import com.newchar.debug.touch.eventreplay.model.RecordedEventSequence;
import com.newchar.debug.touch.eventreplay.model.TouchPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * 把“触摸录制序列”转换为“accesshelper 风格的动作流”。
 *
 * <p>这是事件复现的<b>另一种形式</b>：原本是逐帧原始触摸坐标（touch 形式），
 * 经过本转换器后变成“点击 / 手势”语义动作（accesshelper 形式），
 * 可以交给无障碍服务以 {@link GesturePerformer} 复现，也更容易跨设备/跨分辨率迁移。</p>
 *
 * <p>转换规则：
 * 1. 以 ACTION_DOWN / ACTION_UP 切分出一个个连续手势；
 * 2. 起点与终点距离 &lt; 阈值（默认 12px）视为<b>点击</b>（action=1，按坐标定位）；
 * 3. 否则视为<b>手势/滑动</b>（action=10，actionLocal.type=4，path=采样点）。</p>
 */
public final class TouchToActionConverter {

    /** 判定为“点击”的最大位移阈值（像素）。 */
    public static float TAP_MAX_DISTANCE = 12f;

    private TouchToActionConverter() {
    }

    public static ActionStream convert(RecordedEventSequence seq) {
        ActionStream stream = new ActionStream();
        stream.activityName = seq.activityName;
        stream.available = true;

        List<Gesture> gestures = splitGestures(seq.events);
        int index = 0;
        for (Gesture g : gestures) {
            Action action = buildAction(g, "act_" + (index++));
            if (action != null) stream.actions.add(action);
        }
        return stream;
    }

    /** 按 ACTION_DOWN -> ACTION_UP 切分连续手势。 */
    private static List<Gesture> splitGestures(List<RecordedEvent> events) {
        List<Gesture> result = new ArrayList<>();
        Gesture cur = null;
        for (RecordedEvent e : events) {
            int masked = e.actionMasked >= 0 ? e.actionMasked : (e.action & 0xff);
            if (masked == 0 /*DOWN*/) {
                if (cur != null) result.add(cur);
                cur = new Gesture();
                cur.events.add(e);
            } else if (cur != null) {
                cur.events.add(e);
                if (masked == 1 /*UP*/) {
                    result.add(cur);
                    cur = null;
                }
            }
        }
        if (cur != null) result.add(cur);
        return result;
    }

    private static Action buildAction(Gesture g, String id) {
        if (g.events.isEmpty()) return null;
        List<float[]> path = new ArrayList<>();
        for (RecordedEvent e : g.events) {
            if (!e.points.isEmpty()) {
                TouchPoint p = e.points.get(0);
                path.add(new float[]{p.x, p.y});
            }
        }
        if (path.isEmpty()) return null;

        float[] first = path.get(0);
        float[] last = path.get(path.size() - 1);
        double dist = Math.hypot(last[0] - first[0], last[1] - first[1]);

        Action action = new Action();
        action.id = id;
        action.actionPage = "";
        action.actionTimes = 1;

        if (dist < TAP_MAX_DISTANCE) {
            // 点击
            action.action = "1";
            action.actionLocal = new ActionLocal();
            action.actionLocal.type = 3; // 坐标定位
            action.actionLocal.actionLocation = ((int) first[0]) + "," + ((int) first[1]);
            action.actionOutput = new ActionOutput();
            action.actionOutput.type = 0;
        } else {
            // 手势 / 滑动
            action.action = "10";
            action.actionLocal = new ActionLocal();
            action.actionLocal.type = 4; // 手势 Path，无需定位
            action.actionOutput = new ActionOutput();
            action.actionOutput.type = 10;
            action.actionOutput.gesturePath = path;
            long dur = g.events.get(g.events.size() - 1).eventTime - g.events.get(0).eventTime;
            action.actionOutput.gestureDurationMs = Math.max(80L, dur);
        }
        return action;
    }

    private static final class Gesture {
        final List<RecordedEvent> events = new ArrayList<>();
    }
}
