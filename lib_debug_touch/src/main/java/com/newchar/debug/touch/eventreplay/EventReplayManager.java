package com.newchar.debug.touch.eventreplay;

import android.accessibilityservice.AccessibilityService;
import android.view.View;

import com.newchar.debug.touch.eventreplay.model.RecordedEventSequence;
import com.newchar.debug.touch.eventreplay.record.MotionEventReplayer;
import com.newchar.debug.touch.eventreplay.record.TouchEventRecorder;
import com.newchar.debug.touch.eventreplay.replay.AccessibilityActionReplayer;
import com.newchar.debug.touch.eventreplay.replay.ActionStream;
import com.newchar.debug.touch.eventreplay.replay.TouchToActionConverter;

import java.io.File;
import java.io.IOException;

/**
 * 事件复现管理器：把 accesshelper 模块与 touch 模块融合后的统一入口。
 *
 * <p>能力概览：
 * <ol>
 *   <li><b>录制</b>：通过 {@link TouchEventRecorder} 采集原始触摸事件；</li>
 *   <li><b>JSON 化</b>：录制结果以 {@link RecordedEventSequence} 的 JSON 形式保存，
 *       新代码可随时读取并处理；</li>
 *   <li><b>形式一（touch）</b>：{@link #replayAsTouch} 以原始 MotionEvent 复现；</li>
 *   <li><b>形式二（accesshelper，另一种形式）</b>：{@link #replayAsAccessibility}
 *       先把事件转换为 {@link ActionStream}（语义动作流），再通过无障碍服务手势复现；</li>
 *   <li><b>外部事件处理</b>：{@link #loadActionStream} 可直接解析外部给定的动作流 JSON。</li>
 * </ol>
 * </p>
 */
public final class EventReplayManager {

    private final TouchEventRecorder mRecorder = new TouchEventRecorder();
    private final AccessibilityActionReplayer mAccessReplayer = new AccessibilityActionReplayer();

    public TouchEventRecorder recorder() {
        return mRecorder;
    }

    /** 开始录制。 */
    public void startRecording() {
        mRecorder.start();
    }

    /** 停止录制。 */
    public void stopRecording() {
        mRecorder.stop();
    }

    /** 将录制结果保存为 JSON 文件。 */
    public void saveRecordedEvent(File file) throws IOException {
        mRecorder.saveToFile(file);
    }

    /** 读取并解析事件序列 JSON（新代码处理事件的入口之一）。 */
    public static RecordedEventSequence loadEventSequence(File file) throws IOException {
        return RecordedEventSequence.readFromFile(file);
    }

    /** 形式一：以原始触摸事件复现。 */
    public void replayAsTouch(RecordedEventSequence seq, View target) {
        MotionEventReplayer.replay(seq, target);
    }

    /**
     * 形式二（事件复现的另一种形式）：将事件序列转换为 accesshelper 风格动作流，
     * 通过无障碍服务手势复现。
     */
    public boolean replayAsAccessibility(RecordedEventSequence seq, AccessibilityService service) {
        ActionStream stream = TouchToActionConverter.convert(seq);
        return mAccessReplayer.replay(service, stream);
    }

    /**
     * 直接解析外部给定的动作流 JSON（accesshelper 形式），供新代码处理/复现。
     */
    public static ActionStream loadActionStream(String json) {
        return ActionStream.fromJsonString(json);
    }
}
