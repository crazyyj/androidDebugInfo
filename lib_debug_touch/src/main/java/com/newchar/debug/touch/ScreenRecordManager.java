package com.newchar.debug.touch;

import android.content.Context;
import android.content.Intent;

/**
 * 屏幕录制控制入口。
 */
public final class ScreenRecordManager {

    private static volatile boolean sRecording;
    private static volatile boolean sPaused;

    private ScreenRecordManager() {
    }

    public static void start(Context context) {
        if (context == null || sRecording) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ScreenRecordPermissionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
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

    static void setRecording(boolean recording) {
        sRecording = recording;
        if (!recording) {
            sPaused = false;
        }
    }

    static void setPaused(boolean paused) {
        sPaused = paused;
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
