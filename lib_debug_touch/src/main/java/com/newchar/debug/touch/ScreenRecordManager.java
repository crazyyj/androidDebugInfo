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
    private static volatile boolean sCameraStreaming;

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

    /** 启动相机预览推流（无需 MediaProjection 授权）。 */
    public static void startCamera(Context context) {
        sendAction(context, ScreenRecordService.ACTION_START_CAMERA);
    }

    /** 停止相机预览推流。 */
    public static void stopCamera(Context context) {
        sendAction(context, ScreenRecordService.ACTION_STOP_CAMERA);
    }

    /** 切换前后摄像头，服务会自动重建相机预览会话。 */
    public static void switchCamera(Context context) {
        sendAction(context, ScreenRecordService.ACTION_SWITCH_CAMERA);
    }

    /** 请求拍摄一张全分辨率照片。 */
    public static void capturePhoto(Context context) {
        sendAction(context, ScreenRecordService.ACTION_CAPTURE_PHOTO);
    }

    /** 返回相机预览推流是否正在运行。 */
    public static boolean isCameraStreaming() {
        return sCameraStreaming;
    }

    static void setCameraStreaming(boolean streaming) {
        sCameraStreaming = streaming;
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
