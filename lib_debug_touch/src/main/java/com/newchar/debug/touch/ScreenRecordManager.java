package com.newchar.debug.touch;

import android.content.Context;
import android.content.Intent;

/**
 * 屏幕录制控制入口。
 */
public final class ScreenRecordManager {

    private static volatile boolean sRecording;
    private static volatile boolean sPaused;
    private static volatile boolean sStreaming;

    private ScreenRecordManager() {
    }

    public static void start(Context context) {
        requestProjection(context, false);
    }

    /** 请求录屏授权并在编码时同步推送 H264 实时流。 */
    public static void startStream(Context context) {
        requestProjection(context, true);
    }

    public static void pause(Context context) {
        sendAction(context, ScreenRecordService.ACTION_PAUSE);
    }

    public static void resume(Context context) {
        sendAction(context, ScreenRecordService.ACTION_RESUME);
    }

    public static void stop(Context context) {
        sendAction(context, ScreenRecordService.ACTION_STOP);
    }

    public static boolean isRecording() {
        return sRecording;
    }

    public static boolean isPaused() {
        return sPaused;
    }

    /** 返回当前录屏是否开启 PC 实时推流。 */
    public static boolean isStreaming() {
        return sStreaming;
    }

    static void setRecording(boolean recording) {
        sRecording = recording;
        if (!recording) {
            sPaused = false;
            sStreaming = false;
        }
    }

    static void setPaused(boolean paused) {
        sPaused = paused;
    }

    /** 请求 MediaProjection 权限，并记录本次录制的推流开关。 */
    private static void requestProjection(Context context, boolean streaming) {
        if (context == null || sRecording) {
            return;
        }
        sStreaming = streaming;
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ScreenRecordPermissionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
    }

    private static void sendAction(Context context, String action) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ScreenRecordService.class);
        intent.setAction(action);
        appContext.startService(intent);
    }
}
