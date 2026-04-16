package com.newchar.debug.touch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import com.newchar.debug.utils.HandleWrapper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MediaProjection 屏幕录制服务，输出到 externalCacheDir/.v。
 */
public class ScreenRecordService extends Service {

    public static final String ACTION_START = "com.newchar.debug.touch.action.START_SCREEN_RECORD";
    public static final String ACTION_STOP = "com.newchar.debug.touch.action.STOP_SCREEN_RECORD";
    public static final String ACTION_PAUSE = "com.newchar.debug.touch.action.PAUSE_SCREEN_RECORD";
    public static final String ACTION_RESUME = "com.newchar.debug.touch.action.RESUME_SCREEN_RECORD";

    private static final String TAG = "ScreenRecordService";
    private static final String EXTRA_RESULT_CODE = "result_code";
    private static final String EXTRA_RESULT_DATA = "result_data";
    private static final String CHANNEL_ID = "touch_restore_screen_record";
    private static final int NOTIFICATION_ID = 41201;
    private static final int FRAME_RATE = 30;
    private static final int BIT_RATE = 6 * 1000 * 1000;

    private MediaProjection mMediaProjection;
    private MediaRecorder mMediaRecorder;
    private VirtualDisplay mVirtualDisplay;
    private final Handler mRecordHandler = HandleWrapper.obtainAsyncHandler(message -> false);

    public static void start(Context context, int resultCode, Intent resultData) {
        if (context == null || resultData == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ScreenRecordService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_RESULT_CODE, resultCode);
        intent.putExtra(EXTRA_RESULT_DATA, resultData);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent);
        } else {
            appContext.startService(intent);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRecord();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_PAUSE.equals(intent.getAction())) {
            pauseRecord();
            return START_STICKY;
        }
        if (intent != null && ACTION_RESUME.equals(intent.getAction())) {
            resumeRecord();
            return START_STICKY;
        }
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, ActivityResultCodes.RESULT_CANCELED);
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            startRecord(resultCode, resultData);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopRecord();
        super.onDestroy();
    }

    private void startRecord(int resultCode, Intent resultData) {
        if (ScreenRecordManager.isRecording() || resultData == null) {
            return;
        }
        try {
            startAsForeground();
            MediaProjectionManager projectionManager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (projectionManager == null) {
                stopSelf();
                return;
            }
            mMediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
            if (mMediaProjection == null) {
                stopSelf();
                return;
            }
            mMediaProjection.registerCallback(mProjectionCallback, mRecordHandler);
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = ensureEven(metrics.widthPixels);
            int height = ensureEven(metrics.heightPixels);
            int densityDpi = metrics.densityDpi;
            File outputFile = buildOutputFile();
            if (outputFile == null) {
                stopSelf();
                return;
            }
            mMediaRecorder = buildRecorder(width, height, outputFile);
            Surface surface = mMediaRecorder.getSurface();
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                    "TouchRestoreScreenRecord",
                    width,
                    height,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    mRecordHandler
            );
            mMediaRecorder.start();
            ScreenRecordManager.setPaused(false);
            ScreenRecordManager.setRecording(true);
        } catch (Throwable throwable) {
            Log.e(TAG, "startRecord failed", throwable);
            stopRecord();
            stopSelf();
        }
    }

    private MediaRecorder buildRecorder(int width, int height, File outputFile) throws Exception {
        MediaRecorder recorder = new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setVideoSize(width, height);
        recorder.setVideoFrameRate(FRAME_RATE);
        recorder.setVideoEncodingBitRate(BIT_RATE);
        recorder.setOutputFile(outputFile.getAbsolutePath());
        recorder.setOnErrorListener((mr, what, extra) -> {
            stopRecord();
            stopSelf();
        });
        recorder.prepare();
        return recorder;
    }

    private void pauseRecord() {
        if (!ScreenRecordManager.isRecording() || ScreenRecordManager.isPaused() || mMediaRecorder == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        try {
            mMediaRecorder.pause();
            ScreenRecordManager.setPaused(true);
        } catch (Throwable throwable) {
            Log.e(TAG, "pauseRecord failed", throwable);
        }
    }

    private void resumeRecord() {
        if (!ScreenRecordManager.isRecording() || !ScreenRecordManager.isPaused() || mMediaRecorder == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        try {
            mMediaRecorder.resume();
            ScreenRecordManager.setPaused(false);
        } catch (Throwable throwable) {
            Log.e(TAG, "resumeRecord failed", throwable);
        }
    }

    private void stopRecord() {
        ScreenRecordManager.setRecording(false);
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaRecorder != null) {
            try {
                mMediaRecorder.stop();
            } catch (Throwable ignored) {
            }
            try {
                mMediaRecorder.reset();
                mMediaRecorder.release();
            } catch (Throwable ignored) {
            }
            mMediaRecorder = null;
        }
        if (mMediaProjection != null) {
            try {
                mMediaProjection.unregisterCallback(mProjectionCallback);
            } catch (Throwable ignored) {
            }
            try {
                mMediaProjection.stop();
            } catch (Throwable ignored) {
            }
            mMediaProjection = null;
        }
        stopForeground(true);
    }

    private void startAsForeground() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Touch Restore Screen Record",
                    NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_camera);
        builder.setContentTitle("屏幕录制中");
        builder.setContentText("TouchRestore 正在录制屏幕");
        builder.setOngoing(true);
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private File buildOutputFile() {
        File cacheDir = getExternalCacheDir();
        if (cacheDir == null) {
            return null;
        }
        File dir = new File(cacheDir, ".v");
        if (!((dir.exists() && dir.isDirectory()) || dir.mkdirs())) {
            return null;
        }
        String time = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return new File(dir, "screen-" + time + ".mp4");
    }

    private static int ensureEven(int value) {
        return value % 2 == 0 ? value : value - 1;
    }

    private final MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            stopRecord();
            stopSelf();
        }
    };

    private static final class ActivityResultCodes {
        static final int RESULT_CANCELED = 0;
    }
}
