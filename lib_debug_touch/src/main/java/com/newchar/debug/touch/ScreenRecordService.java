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
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
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

import java.io.DataOutputStream;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** MediaProjection 录屏服务：MediaCodec 编码后同时写 MP4 并可选推送 H264 裸流。 */
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
    private static final int STREAM_PORT = 6667;
    private static final int STREAM_MAGIC = 0x4E435348;
    private static final long DRAIN_INTERVAL_MS = 10L;

    private MediaProjection mMediaProjection;
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private Surface mEncoderSurface;
    private VirtualDisplay mVirtualDisplay;
    private int mVideoTrackIndex = -1;
    private boolean mMuxerStarted;
    private boolean mPaused;
    private H264StreamClient mStreamClient;
    private final Handler mRecordHandler = HandleWrapper.obtainAsyncHandler(message -> false);

    /** 按授权结果启动前台录制服务。 */
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
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopRecord();
            stopSelf();
        } else if (ACTION_PAUSE.equals(action)) {
            pauseRecord();
        } else if (ACTION_RESUME.equals(action)) {
            resumeRecord();
        } else if (ACTION_START.equals(action)) {
            startRecord(intent.getIntExtra(EXTRA_RESULT_CODE, ActivityResultCodes.RESULT_CANCELED),
                    intent.getParcelableExtra(EXTRA_RESULT_DATA));
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopRecord();
        super.onDestroy();
    }

    /** 初始化 MediaCodec、MP4 封装器及 VirtualDisplay。 */
    private void startRecord(int resultCode, Intent resultData) {
        if (ScreenRecordManager.isRecording() || resultData == null) {
            return;
        }
        try {
            startAsForeground();
            mMediaProjection = obtainProjection(resultCode, resultData);
            if (mMediaProjection == null) {
                stopSelf();
                return;
            }
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = ensureEven(metrics.widthPixels);
            int height = ensureEven(metrics.heightPixels);
            mMuxer = new MediaMuxer(buildOutputFile().getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mEncoder = createEncoder(width, height);
            mEncoderSurface = mEncoder.createInputSurface();
            mEncoder.start();
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("TouchRestoreScreenRecord", width, height,
                    metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mEncoderSurface, null, mRecordHandler);
            if (ScreenRecordManager.isStreaming()) {
                mStreamClient = new H264StreamClient();
            }
            mPaused = false;
            ScreenRecordManager.setPaused(false);
            ScreenRecordManager.setRecording(true);
            mRecordHandler.post(mDrainTask);
        } catch (Throwable throwable) {
            Log.e(TAG, "startRecord failed", throwable);
            stopRecord();
            stopSelf();
        }
    }

    /** 获取投屏授权并注册系统停止回调。 */
    private MediaProjection obtainProjection(int resultCode, Intent resultData) {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            return null;
        }
        MediaProjection projection = manager.getMediaProjection(resultCode, resultData);
        if (projection != null) {
            projection.registerCallback(mProjectionCallback, mRecordHandler);
        }
        return projection;
    }

    /** 创建 Surface 输入的 H264 编码器。 */
    private MediaCodec createEncoder(int width, int height) throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return encoder;
    }

    /** 暂停写入文件和推流，但继续消费编码器输出避免缓冲区阻塞。 */
    private void pauseRecord() {
        if (!ScreenRecordManager.isRecording() || mPaused) {
            return;
        }
        mPaused = true;
        ScreenRecordManager.setPaused(true);
    }

    /** 恢复写入文件和推流。 */
    private void resumeRecord() {
        if (!ScreenRecordManager.isRecording() || !mPaused) {
            return;
        }
        mPaused = false;
        ScreenRecordManager.setPaused(false);
    }

    /** 循环取出编码帧，分别交给 MP4 封装器和实时推流连接。 */
    private void drainEncoder(boolean endOfStream) {
        MediaCodec encoder = mEncoder;
        if (encoder == null) {
            return;
        }
        if (endOfStream) {
            runQuietly(encoder::signalEndOfInputStream);
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int index = encoder.dequeueOutputBuffer(info, endOfStream ? 10_000 : 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                startMuxer(encoder.getOutputFormat());
            } else if (index >= 0) {
                writeEncodedBuffer(encoder, index, info);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    return;
                }
            }
        }
    }

    /** 启动 MP4 封装器并记录视频轨道。 */
    private void startMuxer(MediaFormat format) {
        if (mMuxer == null || mMuxerStarted) {
            return;
        }
        mVideoTrackIndex = mMuxer.addTrack(format);
        mMuxer.start();
        mMuxerStarted = true;
    }

    /** 复制当前编码帧，写 MP4 并按自定义帧协议推送到 PC。 */
    private void writeEncodedBuffer(MediaCodec encoder, int index, MediaCodec.BufferInfo info) {
        ByteBuffer buffer = encoder.getOutputBuffer(index);
        if (buffer == null) {
            encoder.releaseOutputBuffer(index, false);
            return;
        }
        try {
            if (info.size > 0) {
                buffer.position(info.offset);
                buffer.limit(info.offset + info.size);
                byte[] payload = new byte[info.size];
                buffer.get(payload);
                if (mStreamClient != null && !mPaused) {
                    mStreamClient.send(payload, info.presentationTimeUs,
                            (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0);
                }
                if (mMuxerStarted && !mPaused && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);
                    mMuxer.writeSampleData(mVideoTrackIndex, buffer, info);
                }
            }
        } finally {
            encoder.releaseOutputBuffer(index, false);
        }
    }

    /** 停止帧循环并按依赖顺序释放录制资源。 */
    private void stopRecord() {
        ScreenRecordManager.setRecording(false);
        mRecordHandler.removeCallbacks(mDrainTask);
        drainEncoder(true);
        releaseVirtualDisplay();
        releaseEncoder();
        releaseMuxer();
        releaseProjection();
        if (mStreamClient != null) {
            mStreamClient.close();
            mStreamClient = null;
        }
        stopForeground(true);
    }

    /** 释放 VirtualDisplay。 */
    private void releaseVirtualDisplay() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    /** 释放编码器和输入 Surface。 */
    private void releaseEncoder() {
        if (mEncoder != null) {
            runQuietly(mEncoder::stop);
            runQuietly(mEncoder::release);
            mEncoder = null;
        }
        if (mEncoderSurface != null) {
            mEncoderSurface.release();
            mEncoderSurface = null;
        }
    }

    /** 关闭 MP4 封装器。 */
    private void releaseMuxer() {
        if (mMuxer != null) {
            if (mMuxerStarted) {
                runQuietly(mMuxer::stop);
            }
            runQuietly(mMuxer::release);
            mMuxer = null;
        }
        mVideoTrackIndex = -1;
        mMuxerStarted = false;
    }

    /** 注销并停止 MediaProjection。 */
    private void releaseProjection() {
        if (mMediaProjection != null) {
            runQuietly(() -> mMediaProjection.unregisterCallback(mProjectionCallback));
            runQuietly(() -> mMediaProjection.stop());
            mMediaProjection = null;
        }
    }

    /** 创建前台服务通知。 */
    private void startAsForeground() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    "Touch Restore Screen Record", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("屏幕录制中")
                .setContentText(ScreenRecordManager.isStreaming() ? "录制并实时推流中" : "TouchRestore 正在录制屏幕")
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, builder.build());
        }
    }

    /** 创建 MP4 文件路径。 */
    private File buildOutputFile() {
        File cacheDir = getExternalCacheDir();
        File dir = cacheDir == null ? null : new File(cacheDir, ".v");
        if (dir == null || !((dir.exists() && dir.isDirectory()) || dir.mkdirs())) {
            throw new IllegalStateException("无法创建录屏目录");
        }
        String time = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return new File(dir, "screen-" + time + ".mp4");
    }

    /** 保证编码器使用偶数尺寸。 */
    private static int ensureEven(int value) {
        return value % 2 == 0 ? value : value - 1;
    }

    /** 执行资源释放操作并忽略已停止状态导致的异常。 */
    private static void runQuietly(Runnable action) {
        try {
            action.run();
        } catch (Throwable ignored) {
        }
    }

    private final Runnable mDrainTask = new Runnable() {
        @Override
        public void run() {
            if (!ScreenRecordManager.isRecording()) {
                return;
            }
            drainEncoder(false);
            mRecordHandler.postDelayed(this, DRAIN_INTERVAL_MS);
        }
    };

    private final MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            stopRecord();
            stopSelf();
        }
    };

    /** 将 H264 编码帧经 adb reverse 的 6667 端口发送到 PC。 */
    private static final class H264StreamClient {
        private Socket mSocket;
        private DataOutputStream mOutput;
        private byte[] mCodecConfig;
        private boolean mNeedCodecConfig;

        /** 发送一帧，PC 未开启服务时安静跳过本帧。 */
        void send(byte[] payload, long ptsUs, boolean codecConfig) {
            if (codecConfig) {
                mCodecConfig = payload.clone();
            }
            if (!ensureConnected()) {
                return;
            }
            try {
                if (mNeedCodecConfig && mCodecConfig != null && !codecConfig) {
                    writeFrame(mCodecConfig, 0L);
                    mNeedCodecConfig = false;
                }
                writeFrame(payload, ptsUs);
                if (codecConfig) {
                    mNeedCodecConfig = false;
                }
            } catch (Exception ignored) {
                close();
            }
        }

        /** 按 PC 端约定的长度前缀协议写入一个 H264 单元。 */
        private void writeFrame(byte[] payload, long ptsUs) throws Exception {
            mOutput.writeInt(STREAM_MAGIC);
            mOutput.writeInt(payload.length);
            mOutput.writeLong(ptsUs);
            mOutput.write(payload);
            mOutput.flush();
        }

        /** 连接设备回环地址，由 adb reverse 转发到 PC 端 6667 服务。 */
        private boolean ensureConnected() {
            if (mSocket != null && mSocket.isConnected() && !mSocket.isClosed()) {
                return true;
            }
            try {
                mSocket = new Socket();
                mSocket.connect(new InetSocketAddress("127.0.0.1", STREAM_PORT), 1_000);
                mOutput = new DataOutputStream(mSocket.getOutputStream());
                mNeedCodecConfig = true;
                return true;
            } catch (Exception ignored) {
                close();
                return false;
            }
        }

        /** 关闭当前推流连接。 */
        void close() {
            try {
                if (mOutput != null) {
                    mOutput.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (mSocket != null) {
                    mSocket.close();
                }
            } catch (Exception ignored) {
            }
            mOutput = null;
            mSocket = null;
        }
    }

    private static final class ActivityResultCodes {
        static final int RESULT_CANCELED = 0;
    }
}
