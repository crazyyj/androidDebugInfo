package com.newchar.debug.view;

import java.lang.ref.WeakReference;

/**
 * @author newChar
 * date 2026/2/9
 * @since 统一管理当前可用的 DebugView 引用。
 * @since 迭代版本，（以及描述）
 */
public final class DebugViewStore {

    private static WeakReference<DebugView> sDebugViewRef;
    private static boolean sAudioCapturing;
    private static boolean sVideoCapturing;

    private DebugViewStore() {
    }

    /**
     * 记录当前可用的 DebugView。
     *
     * @param debugView 当前附着的调试视图
     */
    public static void attach(DebugView debugView) {
        if (debugView != null) {
            sDebugViewRef = new WeakReference<>(debugView);
            debugView.updateWebMediaCaptureStatus(sAudioCapturing, sVideoCapturing);
        }
    }

    /**
     * 释放当前 DebugView 引用。
     *
     * @param debugView 即将销毁的调试视图
     */
    public static void detach(DebugView debugView) {
        DebugView cached = get();
        if (cached == debugView && sDebugViewRef != null) {
            sDebugViewRef.clear();
            sDebugViewRef = null;
        }
    }

    /**
     * 获取当前调试视图。
     *
     * @return 当前可用的 DebugView
     */
    public static DebugView get() {
        if (sDebugViewRef == null) {
            return null;
        }
        return sDebugViewRef.get();
    }

    /**
     * 将网页媒体采集状态转交给当前附着的调试视图。
     *
     * @param audioCapturing 是否正在录音
     * @param videoCapturing 是否正在拍摄
     */
    public static void updateWebMediaCaptureStatus(boolean audioCapturing, boolean videoCapturing) {
        sAudioCapturing = audioCapturing;
        sVideoCapturing = videoCapturing;
        DebugView debugView = get();
        if (debugView != null) {
            debugView.updateWebMediaCaptureStatus(audioCapturing, videoCapturing);
        }
    }
}