package com.newchar.debug.touch;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import com.newchar.debug.utils.HandleWrapper;
import com.newchar.debug.utils.DebugForegroundNotificationManager;

import java.io.DataOutputStream;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** MediaProjection 录屏服务：MediaCodec 编码后同时写 MP4 并可选推送 H264 裸流。 */
public class ScreenRecordService extends Service {

    public static final String ACTION_START = "com.newchar.debug.touch.action.START_SCREEN_RECORD";
    public static final String ACTION_STOP = "com.newchar.debug.touch.action.STOP_SCREEN_RECORD";
    public static final String ACTION_PAUSE = "com.newchar.debug.touch.action.PAUSE_SCREEN_RECORD";
    public static final String ACTION_RESUME = "com.newchar.debug.touch.action.RESUME_SCREEN_RECORD";
    public static final String ACTION_START_CAMERA = "com.newchar.debug.touch.action.START_CAMERA";
    public static final String ACTION_STOP_CAMERA = "com.newchar.debug.touch.action.STOP_CAMERA";
    public static final String ACTION_SWITCH_CAMERA = "com.newchar.debug.touch.action.SWITCH_CAMERA";
    public static final String ACTION_CAPTURE_PHOTO = "com.newchar.debug.touch.action.CAPTURE_PHOTO";
    public static final String ACTION_CONFIGURE_STREAM_TRANSPORT = "com.newchar.debug.touch.action.CONFIGURE_STREAM_TRANSPORT";
    public static final String EXTRA_CAMERA_STREAM_HOST = "com.newchar.debug.touch.extra.CAMERA_STREAM_HOST";
    public static final String EXTRA_STREAM_KIND = "com.newchar.debug.touch.extra.STREAM_KIND";
    public static final String EXTRA_DIRECT_HOST = "com.newchar.debug.touch.extra.DIRECT_HOST";
    public static final String EXTRA_DIRECT_PORT = "com.newchar.debug.touch.extra.DIRECT_PORT";
    public static final String EXTRA_REVERSE_PORT = "com.newchar.debug.touch.extra.REVERSE_PORT";
    public static final String EXTRA_SESSION_TOKEN = "com.newchar.debug.touch.extra.SESSION_TOKEN";
    public static final String EXTRA_ALLOW_DIRECT_FALLBACK = "com.newchar.debug.touch.extra.ALLOW_DIRECT_FALLBACK";
    public static final String STREAM_KIND_SCREEN = "screen";
    public static final String STREAM_KIND_CAMERA = "camera";

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

    private static final int CAMERA_STREAM_PORT = 6668;
    private static final int CAMERA_STREAM_MAGIC = 0x4E434332; // "NCC2"，包含每帧方向元数据
    private static final int CAMERA_WIDTH = 640;
    private static final int CAMERA_HEIGHT = 480;
    private static final int CAMERA_FRAME_RATE = 15;
    private static final int CAMERA_BIT_RATE = 800 * 1000;

    private MediaProjection mMediaProjection;
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private Surface mEncoderSurface;
    private VirtualDisplay mVirtualDisplay;
    private int mVideoTrackIndex = -1;
    private boolean mMuxerStarted;
    private boolean mPaused;
    private H264StreamClient mStreamClient;
    private StreamTransportConfig mScreenTransportConfig;
    private final Handler mRecordHandler = HandleWrapper.obtainAsyncHandler(message -> false);

    // 相机预览推流相关（独立于录屏链路）
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraSession;
    private MediaCodec mCameraEncoder;
    private Surface mCameraEncoderSurface;
    private H264StreamClient mCameraStreamClient;
    private ImageReader mPhotoReader;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private PowerManager.WakeLock mWakeLock;
    private boolean mCameraRunning;
    /** 拍照 surface 是否真的挂进了捕获会话（LEGACY 降级后为 false）。 */
    private boolean mCameraPhotoReady;
    private int mSensorOrientation;
    private int mCameraFacing = CameraCharacteristics.LENS_FACING_BACK;
    private int mCameraPreviewRotation;
    private boolean mUseFrontCamera;
    /** 相机流目标主机；USB 使用设备回环地址，Wi-Fi 使用 PC 的局域网地址。 */
    private String mCameraStreamHost = "127.0.0.1";
    private StreamTransportConfig mCameraTransportConfig;
    /** 相机重建代号，用于忽略旧 Camera2 回调，避免其在切换后关闭新会话。 */
    private long mCameraSessionToken;
    /** 相机已发送帧计数，仅用于排障日志。 */
    private int mCameraFrameSent = 0;
    private final Runnable mCameraDrainTask = new Runnable() {
        @Override
        public void run() {
            if (!mCameraRunning) {
                return;
            }
            drainCameraEncoder(false);
            if (mCameraHandler != null) {
                mCameraHandler.postDelayed(this, DRAIN_INTERVAL_MS);
            }
        }
    };

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

    /** 启动相机预览推流，无需 MediaProjection 授权。 */
    public static void startCamera(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ScreenRecordService.class);
        intent.setAction(ACTION_START_CAMERA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent);
        } else {
            appContext.startService(intent);
        }
    }

    /** 停止相机预览推流。 */
    public static void stopCamera(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ScreenRecordService.class);
        intent.setAction(ACTION_STOP_CAMERA);
        appContext.startService(intent);
    }

    /** 切换前后摄像头；相机未启动时只记录下一次启动要使用的镜头。 */
    public static void switchCamera(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ScreenRecordService.class);
        intent.setAction(ACTION_SWITCH_CAMERA);
        appContext.startService(intent);
    }

    /** 请求拍摄一张全分辨率照片。 */
    public static void capturePhoto(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ScreenRecordService.class);
        intent.setAction(ACTION_CAPTURE_PHOTO);
        appContext.startService(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_CONFIGURE_STREAM_TRANSPORT.equals(action)) {
            configureStreamTransport(intent);
        } else if (ACTION_STOP.equals(action)) {
            stopRecord();
            stopSelf();
        } else if (ACTION_PAUSE.equals(action)) {
            pauseRecord();
        } else if (ACTION_RESUME.equals(action)) {
            resumeRecord();
        } else if (ACTION_START.equals(action)) {
            startRecord(intent.getIntExtra(EXTRA_RESULT_CODE, ActivityResultCodes.RESULT_CANCELED),
                    intent.getParcelableExtra(EXTRA_RESULT_DATA));
        } else if (ACTION_START_CAMERA.equals(action)) {
            String streamHost = intent.getStringExtra(EXTRA_CAMERA_STREAM_HOST);
            if (streamHost != null && !streamHost.trim().isEmpty()) {
                mCameraStreamHost = streamHost.trim();
            }
            startCamera();
        } else if (ACTION_STOP_CAMERA.equals(action)) {
            stopCamera();
            if (!ScreenRecordManager.isRecording()) {
                stopSelf();
            }
        } else if (ACTION_SWITCH_CAMERA.equals(action)) {
            switchCamera();
        } else if (ACTION_CAPTURE_PHOTO.equals(action)) {
            capturePhoto();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopCamera();
        stopRecord();
        super.onDestroy();
    }

    /** 保存 PC 在 ADB 可用时下发的双链路配置，并让正在推流的客户端立即重连。 */
    private void configureStreamTransport(Intent intent) {
        StreamTransportConfig config = StreamTransportConfig.fromIntent(intent);
        if (config == null) {
            Log.w(TAG, "忽略不完整的推流传输配置");
            return;
        }
        if (STREAM_KIND_CAMERA.equals(config.streamKind)) {
            mCameraTransportConfig = config;
            if (mCameraStreamClient != null) mCameraStreamClient.updateConfig(config);
        } else {
            mScreenTransportConfig = config;
            if (mStreamClient != null) mStreamClient.updateConfig(config);
        }
        Log.i(TAG, "已更新" + config.streamKind + "推流传输配置");
    }

    /** 按是否收到 v2 配置选择双链路客户端或保留旧版 reverse-only 客户端。 */
    private H264StreamClient createStreamClient(StreamTransportConfig config, int legacyPort, int magic) {
        return config == null ? new H264StreamClient(legacyPort, magic) : new H264StreamClient(magic, config);
    }

    /** 初始化 MediaCodec、MP4 封装器及 VirtualDisplay。 */
    private void startRecord(int resultCode, Intent resultData) {
        if (ScreenRecordManager.isRecording()) {
            return;
        }
        if (resultData == null) {
            Log.w(TAG, "缺少 MediaProjection 授权数据，无法恢复屏幕推流");
            Toast.makeText(getApplicationContext(), "屏幕录制授权不可用，请重新授权", Toast.LENGTH_LONG).show();
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
                mStreamClient = createStreamClient(mScreenTransportConfig, STREAM_PORT, STREAM_MAGIC);
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

    /** 创建 Surface 输入的 H264 编码器（录屏默认码率/帧率）。 */
    private MediaCodec createEncoder(int width, int height) throws Exception {
        return createEncoder(width, height, BIT_RATE, FRAME_RATE);
    }

    /** 创建 Surface 输入的 H264 编码器，允许指定码率与帧率（相机预览复用）。 */
    private MediaCodec createEncoder(int width, int height, int bitRate, int frameRate) throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
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
                            (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0,
                            (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0, 0);
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
        releaseSharedForeground(DebugForegroundNotificationManager.OWNER_SCREEN_RECORD);
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

    // ===================== 相机预览推流 =====================

    private final ImageReader.OnImageAvailableListener mPhotoListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) {
                    return;
                }
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                // 优先 App 私有 DCIM 目录：无需 WRITE_EXTERNAL_STORAGE 权限，全版本可用。
                File dir = getExternalFilesDir(Environment.DIRECTORY_DCIM);
                if (dir == null) {
                    dir = new File("/sdcard/DCIM/Camera");
                }
                if (!dir.exists() && !dir.mkdirs()) {
                    Log.w(TAG, "无法创建相机照片目录: " + dir);
                }
                String time = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
                File file = new File(dir, "ncam_" + time + ".jpg");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(bytes);
                }
                Log.i(TAG, "相机照片已保存: " + file.getAbsolutePath());
            } catch (Throwable throwable) {
                Log.e(TAG, "保存相机照片失败", throwable);
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    };

    /**
     * 相机启动入口：先进入前台（Android 10+ 后台应用不得随意启动 Activity），再检查运行时权限。
     * 权限未授予时拉起透明 Activity 申请，结果回调后重新进入本方法；权限就绪则走真正的开相机流程。
     */
    private void startCamera() {
        if (mCameraRunning) {
            return;
        }
        try {
            startAsForegroundCamera();
        } catch (Throwable throwable) {
            Log.e(TAG, "startAsForegroundCamera failed", throwable);
            stopCamera();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "CAMERA 权限未授予，拉起权限申请 Activity");
            Intent permIntent = new Intent(this, CameraPermissionActivity.class);
            permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permIntent);
            return;
        }
        startCameraInternal();
    }

    /** 权限就绪后真正启动相机预览推流：起工作线程并打开相机。 */
    private void startCameraInternal() {
        final long sessionToken = ++mCameraSessionToken;
        mCameraRunning = true;
        ScreenRecordManager.setCameraStreaming(true);
        // 点亮屏幕：后台服务无法直接弹 Activity，用带 ACQUIRE_CAUSES_WAKE_UP 的唤醒锁把屏幕点亮，
        // 方便在 PC 端远程查看相机预览时设备端屏幕同步亮起。
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                // ACQUIRE_CAUSES_WAKE_UP = 0x10000000：点亮已熄灭的屏幕。该常量在部分编译 SDK 桩中缺定义，
                // 但运行时（API>=17，本库 minSdk 23）必然存在，故用字面量兜底。
                mWakeLock = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK | 0x10000000,
                        "newlq:camwake");
                mWakeLock.acquire(30 * 60 * 1000L);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "获取点亮屏幕唤醒锁失败（不影响预览）", throwable);
        }
        mCameraThread = new HandlerThread("CameraStreamThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
        mCameraHandler.post(() -> openCamera(sessionToken));
    }

    /** 在相机线程中销毁旧会话并重启，使切换镜头不与正在排帧的编码器竞争。 */
    private void switchCamera() {
        mUseFrontCamera = !mUseFrontCamera;
        if (!mCameraRunning || mCameraHandler == null) {
            return;
        }
        mCameraHandler.post(() -> {
            stopCamera();
            startCamera();
        });
    }

    /** 打开后置摄像头（无后置时回退到首个摄像头）。 */
    private void openCamera(final long sessionToken) {
        if (!isActiveCameraSession(sessionToken)) {
            return;
        }
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            Log.e(TAG, "CameraManager 不可用");
            stopCamera();
            stopSelf();
            return;
        }
        try {
            String cameraId = getCameraId(cameraManager, mUseFrontCamera);
            if (cameraId == null) {
                Log.e(TAG, "未发现可用摄像头");
                stopCamera();
                stopSelf();
                return;
            }
            mSensorOrientation = getSensorOrientation(cameraManager, cameraId);
            mCameraFacing = getCameraFacing(cameraManager, cameraId);
            mCameraPreviewRotation = getOutputRotation(mSensorOrientation, mCameraFacing, getDisplayRotation());
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    if (!isActiveCameraSession(sessionToken)) {
                        runQuietly(camera::close);
                        return;
                    }
                    mCameraDevice = camera;
                    try {
                        configureCamera(camera, cameraManager, cameraId, sessionToken);
                    } catch (Throwable throwable) {
                        Log.e(TAG, "configureCamera failed", throwable);
                        stopCameraIfActive(sessionToken);
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    runQuietly(camera::close);
                    stopCameraIfActive(sessionToken);
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e(TAG, "相机打开失败: " + error);
                    runQuietly(camera::close);
                    stopCameraIfActive(sessionToken);
                }
            }, mCameraHandler);
        } catch (CameraAccessException | SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "openCamera failed", e);
            stopCameraIfActive(sessionToken);
        }
    }

    /** 创建相机编码器、拍照 ImageReader 与捕获会话，并启动预览推流。 */
    private void configureCamera(CameraDevice camera, CameraManager cameraManager, String cameraId,
            long sessionToken) throws Exception {
        if (!isActiveCameraSession(sessionToken)) {
            runQuietly(camera::close);
            return;
        }
        Size previewSize = choosePreviewSize(cameraManager, cameraId);
        Size photoSize = choosePhotoSize(cameraManager, cameraId);
        Log.i(TAG, "相机配置: 预览=" + previewSize + " 拍照=" + photoSize
                + " legacy=" + isLegacyLevel(cameraManager, cameraId)
                + " 可选预览=" + dumpSizes(cameraManager, cameraId));

        mCameraEncoder = createEncoder(previewSize.getWidth(), previewSize.getHeight(), CAMERA_BIT_RATE, CAMERA_FRAME_RATE);
        mCameraEncoderSurface = mCameraEncoder.createInputSurface();
        mCameraEncoder.start();

        mPhotoReader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(), ImageFormat.JPEG, 2);
        mPhotoReader.setOnImageAvailableListener(mPhotoListener, mCameraHandler);

        openPreviewSession(camera, true, sessionToken);
    }

    /**
     * 建立捕获会话并启动预览。
     *
     * LEGACY HAL 设备（如 vivo 1820 等老机型）对「编码器 surface + 全分辨率 JPEG」的组合支持很差，
     * 常在 setRepeatingRequest 抛 `must configure device with valid surfaces`。
     * 因此首次带拍照 surface 尝试，失败则自动降级为「仅编码器 surface」的纯预览会话。
     */
    private void openPreviewSession(CameraDevice camera, boolean withPhoto, final long sessionToken) {
        if (!isActiveCameraSession(sessionToken)) {
            return;
        }
        List<Surface> targets = new ArrayList<>();
        targets.add(mCameraEncoderSurface);
        if (withPhoto && mPhotoReader != null) {
            targets.add(mPhotoReader.getSurface());
        }
        try {
            camera.createCaptureSession(targets, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (!isActiveCameraSession(sessionToken)) {
                        runQuietly(session::close);
                        return;
                    }
                    mCameraSession = session;
                    try {
                        CaptureRequest.Builder preview = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        preview.addTarget(mCameraEncoderSurface);
                        session.setRepeatingRequest(preview.build(), null, mCameraHandler);
                        mCameraPhotoReady = withPhoto;
                        mCameraStreamClient = createStreamClient(mCameraTransportConfig, CAMERA_STREAM_PORT, CAMERA_STREAM_MAGIC);
                        mCameraHandler.post(mCameraDrainTask);
                        Log.i(TAG, withPhoto ? "相机预览已启动" : "相机预览已启动（降级：仅预览，不支持拍照）");
                    } catch (Throwable throwable) {
                        if (withPhoto) {
                            Log.w(TAG, "带拍照的会话无法提交预览请求，降级为仅预览会话", throwable);
                            openPreviewSession(camera, false, sessionToken);
                            return;
                        }
                        Log.e(TAG, "启动相机预览失败", throwable);
                        stopCameraIfActive(sessionToken);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    runQuietly(session::close);
                    if (!isActiveCameraSession(sessionToken)) {
                        return;
                    }
                    if (withPhoto) {
                        Log.w(TAG, "带拍照的会话配置失败，降级为仅预览会话");
                        openPreviewSession(camera, false, sessionToken);
                        return;
                    }
                    Log.e(TAG, "相机捕获会话配置失败");
                    stopCameraIfActive(sessionToken);
                }
            }, mCameraHandler);
        } catch (Throwable throwable) {
            if (withPhoto) {
                Log.w(TAG, "创建带拍照的会话异常，降级为仅预览会话", throwable);
                openPreviewSession(camera, false, sessionToken);
                return;
            }
            Log.e(TAG, "创建相机会话失败", throwable);
            stopCameraIfActive(sessionToken);
        }
    }

    /** 选择低延迟 4:3 预览尺寸，按 800×600、640×480、其余 4:3 的顺序降级。 */
    private static Size choosePreviewSize(CameraManager cm, String cameraId) {
        Size[] candidates = outputSizes(cm, cameraId);
        if (candidates == null || candidates.length == 0) {
            return new Size(CAMERA_WIDTH, CAMERA_HEIGHT);
        }
        Size preferred = findSize(candidates, 800, 600);
        if (preferred != null) return preferred;
        preferred = findSize(candidates, CAMERA_WIDTH, CAMERA_HEIGHT);
        if (preferred != null) return preferred;
        for (Size size : candidates) {
            if (size.getWidth() * 3 == size.getHeight() * 4 || size.getWidth() * 4 == size.getHeight() * 3) return size;
        }
        return candidates[0];
    }

    /** 在相机声明的输出列表中查找精确尺寸，并兼容横竖两个方向。 */
    @Nullable
    private static Size findSize(Size[] candidates, int width, int height) {
        for (Size size : candidates) {
            if ((size.getWidth() == width && size.getHeight() == height)
                    || (size.getWidth() == height && size.getHeight() == width)) return size;
        }
        return null;
    }

    /** 取设备支持的编码器/预览输出尺寸列表，尽量覆盖不同 API 与厂商实现。 */
    private static Size[] outputSizes(CameraManager cm, String cameraId) {
        try {
            StreamConfigurationMap map = cm.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                return null;
            }
            Size[] sizes = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                sizes = map.getOutputSizes(MediaCodec.class);
            }
            if (sizes == null || sizes.length == 0) {
                sizes = map.getOutputSizes(SurfaceTexture.class);
            }
            if (sizes == null || sizes.length == 0) {
                sizes = map.getOutputSizes(ImageFormat.PRIVATE);
            }
            return sizes;
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** 拍照尺寸：取最大 JPEG 但收敛到 1080p，避免 LEGACY 设备因超大 JPEG 让会话组合超限。 */
    private static Size choosePhotoSize(CameraManager cm, String cameraId) {
        Size largest = new Size(1920, 1080);
        try {
            largest = getLargestJpegSize(cm, cameraId);
        } catch (Throwable ignored) {
            // 取不到就直接用 1080p 兜底
        }
        int maxPixels = 1920 * 1080;
        if (largest.getWidth() * largest.getHeight() <= maxPixels) {
            return largest;
        }
        try {
            StreamConfigurationMap map = cm.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] jpegs = map != null ? map.getOutputSizes(ImageFormat.JPEG) : null;
            if (jpegs != null) {
                Size best = null;
                for (Size size : jpegs) {
                    int pixels = size.getWidth() * size.getHeight();
                    if (pixels > maxPixels) {
                        continue;
                    }
                    if (best == null || pixels > best.getWidth() * best.getHeight()) {
                        best = size;
                    }
                }
                if (best != null) {
                    return best;
                }
            }
        } catch (Throwable ignored) {
            // 回落到按比例缩放
        }
        double scale = Math.sqrt((double) maxPixels / (largest.getWidth() * largest.getHeight()));
        return new Size(even(largest.getWidth() * scale), even(largest.getHeight() * scale));
    }

    /** 是否为 LEGACY HAL 设备。 */
    private static boolean isLegacyLevel(CameraManager cm, String cameraId) {
        try {
            Integer level = cm.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            return level != null && level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
        } catch (Throwable throwable) {
            return false;
        }
    }

    /** 输出尺寸清单，仅用于排障日志。 */
    private static String dumpSizes(CameraManager cm, String cameraId) {
        Size[] sizes = outputSizes(cm, cameraId);
        if (sizes == null) {
            return "未知";
        }
        StringBuilder builder = new StringBuilder();
        for (Size size : sizes) {
            builder.append(size.getWidth()).append('x').append(size.getHeight()).append(' ');
        }
        return builder.toString().trim();
    }

    private static int even(double value) {
        int result = (int) (value / 2);
        return result * 2;
    }

    /** 循环取出相机编码帧并经 adb reverse 6668 推流到 PC。 */
    private void drainCameraEncoder(boolean endOfStream) {
        MediaCodec encoder = mCameraEncoder;
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
                sendCameraCodecConfig(encoder.getOutputFormat());
            } else if (index >= 0) {
                writeCameraBuffer(encoder, index, info);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    return;
                }
            }
        }
    }

    /** 取出一帧相机编码数据并推送到 PC。 */
    private void writeCameraBuffer(MediaCodec encoder, int index, MediaCodec.BufferInfo info) {
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
                // 归一化为 Annex-B（起始码 00 00 00 01 分隔的 NAL 流）。
                // 部分设备（尤其 LEGACY HAL，如 vivo 1820）的编码器在 ByteBuffer 模式下输出为
                // AVCC 长度前缀格式（每个 NAL 以 4 字节大端长度开头），而非 Annex-B 起始码。
                // 若只补起始码会把 4 字节长度当成 NAL 头 → FFmpeg 解不出后续帧（表现为持续收到数据却不出画面）。
                // toAnnexB 会识别两种格式：已是 Annex-B 原样返回，是 AVCC 则把每个长度前缀替换为起始码。
                byte[] normalized = toAnnexB(payload);
                if (mCameraStreamClient != null) {
                    mCameraStreamClient.send(normalized, info.presentationTimeUs,
                            (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0,
                            (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0, mCameraPreviewRotation);
                }
                mCameraFrameSent++;
                if (mCameraFrameSent % 30 == 0) {
                    Log.i(TAG, "相机已向 PC 发送 " + mCameraFrameSent + " 帧（端口" + CAMERA_STREAM_PORT + "）");
                }
            }
        } finally {
            encoder.releaseOutputBuffer(index, false);
        }
    }

    /** 从编码器输出格式取出 SPS/PPS，确保 PC 在首个 IDR 前就拿到完整 H264 配置。 */
    private void sendCameraCodecConfig(MediaFormat format) {
        if (mCameraStreamClient == null) {
            return;
        }
        byte[] sps = readCodecConfig(format, "csd-0");
        byte[] pps = readCodecConfig(format, "csd-1");
        if (sps.length == 0 && pps.length == 0) {
            return;
        }
        byte[] config = new byte[sps.length + pps.length];
        System.arraycopy(sps, 0, config, 0, sps.length);
        System.arraycopy(pps, 0, config, sps.length, pps.length);
        mCameraStreamClient.send(config, 0L, true, false, mCameraPreviewRotation);
    }

    /** 复制 MediaFormat 内的 H264 codec config，并统一为 Annex-B 起始码格式。 */
    private static byte[] readCodecConfig(MediaFormat format, String key) {
        try {
            ByteBuffer buffer = format.getByteBuffer(key);
            if (buffer == null || !buffer.hasRemaining()) {
                return new byte[0];
            }
            ByteBuffer copy = buffer.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            return toAnnexB(bytes);
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }

    /**
     * 将 MediaCodec 输出的 H264 单元归一化为 Annex-B（起始码 00 00 00 01 分隔的 NAL 流）。
     *
     * 不同设备/芯片的 MediaCodec 在 ByteBuffer 模式下输出差异很大：
     * - 部分直接输出 Annex-B（以 00 00 00 01 / 00 00 01 开头）→ 原样返回；
     * - 部分（尤其 LEGACY HAL，如 vivo 1820）输出 AVCC 长度前缀（每个 NAL 前 4 字节大端长度）→ 转换；
     * - 个别情况单元本身无起始码 → 兜底补一个 4 字节起始码。
     *
     * FFmpeg 的 h264 解封装器需要 Annex-B 才能正确切分帧边界；AVCC 不转换会导致「首帧（SPS/PPS）能解析、
     * 后续帧解不出」——即 PC 端持续收到数据却不出画面、最终超时。
     */
    private static byte[] toAnnexB(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        // 已是 Annex-B：以 4 字节或 3 字节起始码开头，直接放行。
        if (data.length >= 4 && data[0] == 0 && data[1] == 0 && data[2] == 0 && data[3] == 1) {
            return data;
        }
        if (data.length >= 3 && data[0] == 0 && data[1] == 0 && data[2] == 1) {
            return data;
        }
        // 判定是否为 AVCC：首 4 字节为第一个 NAL 长度，且 NAL 头字节（紧随长度之后）是合法 H264 NAL 类型。
        if (data.length >= 5) {
            int firstLen = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
                    | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            int nalHeader = data[4] & 0xFF;
            int forbidden = nalHeader & 0x80;
            int nalType = nalHeader & 0x1F;
            if (firstLen > 0 && firstLen <= data.length - 4 && forbidden == 0
                    && nalType >= 1 && nalType <= 21) {
                return avccToAnnexB(data);
            }
        }
        // 兜底：当作单个无起始码的 NAL，补一个 4 字节起始码。
        byte[] out = new byte[data.length + 4];
        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        out[3] = 1;
        System.arraycopy(data, 0, out, 4, data.length);
        return out;
    }

    /** 将 AVCC（4 字节长度前缀）字节流转换为 Annex-B（4 字节起始码）字节流，长度不变。 */
    private static byte[] avccToAnnexB(byte[] data) {
        byte[] out = new byte[data.length];
        int pos = 0;
        int outPos = 0;
        while (pos + 4 <= data.length) {
            int len = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                    | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
            if (len < 0 || pos + 4 + len > data.length) {
                break;
            }
            out[outPos++] = 0;
            out[outPos++] = 0;
            out[outPos++] = 0;
            out[outPos++] = 1;
            System.arraycopy(data, pos + 4, out, outPos, len);
            outPos += len;
            pos += 4 + len;
        }
        if (outPos == data.length) {
            return out;
        }
        byte[] trimmed = new byte[outPos];
        System.arraycopy(out, 0, trimmed, 0, outPos);
        return trimmed;
    }

    /** 请求拍摄一张全分辨率照片（单次 STILL_CAPTURE，按设备方向旋转 JPEG）。 */
    private void capturePhoto() {
        if (!mCameraRunning || !mCameraPhotoReady || mCameraSession == null
                || mCameraDevice == null || mPhotoReader == null) {
            Log.w(TAG, "capturePhoto 忽略：相机未运行或未支持拍照");
            return;
        }
        try {
            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(mPhotoReader.getSurface());
            builder.set(CaptureRequest.JPEG_ORIENTATION,
                    getOutputRotation(mSensorOrientation, mCameraFacing, getDisplayRotation()));
            mCameraSession.capture(builder.build(), null, mCameraHandler);
        } catch (Throwable throwable) {
            Log.e(TAG, "capturePhoto failed", throwable);
        }
    }

    /** 释放相机相关资源（不在此处 stopSelf，避免与 onDestroy 递归）。 */
    private void stopCamera() {
        mCameraSessionToken++;
        mCameraRunning = false;
        mCameraPhotoReady = false;
        ScreenRecordManager.setCameraStreaming(false);
        if (mWakeLock != null) {
            runQuietly(mWakeLock::release);
            mWakeLock = null;
        }
        if (mCameraHandler != null) {
            mCameraHandler.removeCallbacks(mCameraDrainTask);
        }
        drainCameraEncoder(true);
        if (mCameraSession != null) {
            runQuietly(mCameraSession::close);
            mCameraSession = null;
        }
        if (mCameraDevice != null) {
            runQuietly(mCameraDevice::close);
            mCameraDevice = null;
        }
        if (mCameraEncoder != null) {
            runQuietly(mCameraEncoder::stop);
            runQuietly(mCameraEncoder::release);
            mCameraEncoder = null;
        }
        if (mCameraEncoderSurface != null) {
            runQuietly(mCameraEncoderSurface::release);
            mCameraEncoderSurface = null;
        }
        if (mCameraStreamClient != null) {
            mCameraStreamClient.close();
            mCameraStreamClient = null;
        }
        if (mPhotoReader != null) {
            runQuietly(mPhotoReader::close);
            mPhotoReader = null;
        }
        if (mCameraThread != null) {
            runQuietly(mCameraThread::quitSafely);
            mCameraThread = null;
            mCameraHandler = null;
        }
        releaseSharedForeground(DebugForegroundNotificationManager.OWNER_CAMERA);
    }

    /** 仅在回调仍属于当前相机会话时停止，避免旧会话异步回调误伤新会话。 */
    private void stopCameraIfActive(long sessionToken) {
        if (isActiveCameraSession(sessionToken)) {
            stopCamera();
            if (!ScreenRecordManager.isRecording()) {
                stopSelf();
            }
        }
    }

    /** 判断 Camera2 异步回调是否仍属于当前正在运行的相机会话。 */
    private boolean isActiveCameraSession(long sessionToken) {
        return mCameraRunning && sessionToken == mCameraSessionToken;
    }

    /** 返回指定朝向的摄像头 id；该朝向不存在时回退到第一个可用摄像头。 */
    private static String getCameraId(CameraManager cm, boolean useFront) throws CameraAccessException {
        int targetFacing = useFront ? CameraCharacteristics.LENS_FACING_FRONT
                : CameraCharacteristics.LENS_FACING_BACK;
        String fallback = null;
        for (String id : cm.getCameraIdList()) {
            Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (fallback == null) {
                fallback = id;
            }
            if (facing != null && facing == targetFacing) {
                return id;
            }
        }
        return fallback;
    }

    /** 查询当前摄像头朝向；查询失败时按后置处理，保证方向计算有稳定回退。 */
    private static int getCameraFacing(CameraManager cm, String cameraId) {
        try {
            Integer facing = cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING);
            return facing == null ? CameraCharacteristics.LENS_FACING_BACK : facing;
        } catch (Throwable ignored) {
            return CameraCharacteristics.LENS_FACING_BACK;
        }
    }

    /** 返回相机支持的最大 JPEG 输出尺寸。 */
    private static Size getLargestJpegSize(CameraManager cm, String cameraId) throws CameraAccessException {
        CameraCharacteristics characteristics = cm.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return new Size(1920, 1080);
        }
        Size largest = null;
        for (Size size : map.getOutputSizes(ImageFormat.JPEG)) {
            if (largest == null || size.getWidth() * size.getHeight() > largest.getWidth() * largest.getHeight()) {
                largest = size;
            }
        }
        return largest != null ? largest : new Size(1920, 1080);
    }

    /** 返回相机传感器方向（度）。 */
    private static int getSensorOrientation(CameraManager cm, String cameraId) throws CameraAccessException {
        Integer orientation = cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION);
        return orientation != null ? orientation : 90;
    }

    /** 获取当前显示方向，供预览流与 JPEG 使用同一套人眼正向计算。 */
    private int getDisplayRotation() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        return wm == null ? Surface.ROTATION_0 : wm.getDefaultDisplay().getRotation();
    }

    /** 根据传感器、镜头朝向和设备旋转计算人眼正向的图像旋转角度。 */
    private static int getOutputRotation(int sensorOrientation, int cameraFacing, int deviceRotation) {
        int deviceDegrees;
        switch (deviceRotation) {
            case Surface.ROTATION_90:
                deviceDegrees = 90;
                break;
            case Surface.ROTATION_180:
                deviceDegrees = 180;
                break;
            case Surface.ROTATION_270:
                deviceDegrees = 270;
                break;
            default:
                deviceDegrees = 0;
                break;
        }
        if (cameraFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            return (sensorOrientation - deviceDegrees + 360) % 360;
        }
        return (sensorOrientation + deviceDegrees) % 360;
    }

    /** 创建前台服务相机通知（类型含 CAMERA，录屏同时运行时合并 MEDIA_PROJECTION）。 */
    private void startAsForegroundCamera() {
        int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ScreenRecordManager.isRecording()) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            }
        }
        startSharedForeground(DebugForegroundNotificationManager.OWNER_CAMERA, type);
    }

    /** 创建前台服务通知。 */
    private void startAsForeground() {
        int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ScreenRecordManager.isCameraStreaming()) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            }
        }
        startSharedForeground(DebugForegroundNotificationManager.OWNER_SCREEN_RECORD, type);
    }

    /** 以共享通知启动当前服务前台状态，并登记本次启用的具体功能。 */
    private void startSharedForeground(String owner, int type) {
        Notification notification = DebugForegroundNotificationManager.acquire(getApplicationContext(), owner);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(DebugForegroundNotificationManager.getNotificationId(), notification, type);
        } else {
            startForeground(DebugForegroundNotificationManager.getNotificationId(), notification);
        }
    }

    /** 仅释放当前功能；录屏与相机均关闭后才解除服务自身的前台绑定。 */
    private void releaseSharedForeground(String owner) {
        DebugForegroundNotificationManager.release(getApplicationContext(), owner);
        if (ScreenRecordManager.isRecording() || ScreenRecordManager.isCameraStreaming()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(false);
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
            Log.w(TAG, "MediaProjection 已失效，无法自动恢复屏幕推流");
            Toast.makeText(getApplicationContext(), "屏幕录制授权已失效，请重新授权", Toast.LENGTH_LONG).show();
            stopRecord();
            stopSelf();
        }
    };

    /** 将 H264 编码帧优先经 reverse、失败后按预置 LAN 地址直连到 PC。 */
    private static final class H264StreamClient {
        private static final int PROTOCOL_VERSION = 2;
        private static final int TRANSPORT_REVERSE = 1;
        private static final int TRANSPORT_DIRECT = 2;
        private final int streamMagic;
        private StreamTransportConfig mConfig;
        private Socket mSocket;
        private DataOutputStream mOutput;
        private byte[] mCodecConfig;
        private boolean mNeedCodecConfig;
        private int mRotationDegrees;
        private int mTransport = TRANSPORT_REVERSE;
        private long mNextConnectAt;
        private long mReconnectDelayMs = 1_000L;
        private boolean mConnecting;

        /** 使用旧协议的 reverse-only 客户端，供尚未升级的设备端继续使用。 */
        H264StreamClient(int port, int magic) {
            this(magic, StreamTransportConfig.legacy(port));
        }

        /** 使用 PC 预下发的双链路配置创建客户端。 */
        H264StreamClient(int magic, StreamTransportConfig config) {
            this.streamMagic = magic;
            this.mConfig = config;
        }

        /** 原子替换会话配置，并从 reverse 开始建立新连接。 */
        void updateConfig(StreamTransportConfig config) {
            close();
            mConfig = config;
            mTransport = TRANSPORT_REVERSE;
            mNextConnectAt = 0L;
            mReconnectDelayMs = 1_000L;
        }

        /** 发送一帧；连接不可用时仅跳过当前帧，不终止编码或 MediaProjection。 */
        synchronized void send(byte[] payload, long ptsUs, boolean codecConfig, boolean keyFrame, int rotationDegrees) {
            mRotationDegrees = rotationDegrees;
            if (codecConfig) mCodecConfig = mergeCodecConfig(mCodecConfig, payload);
            if (!ensureConnected()) return;
            try {
                if (mNeedCodecConfig && mCodecConfig != null && !codecConfig) writeFrame(mCodecConfig, 0L, true, false);
                writeFrame(payload, ptsUs, codecConfig, keyFrame);
                if (codecConfig || mCodecConfig != null) mNeedCodecConfig = false;
            } catch (Exception e) {
                Log.w(TAG, "推流发送失败，将尝试切换或重连: " + e.getMessage());
                onWriteFailure();
            }
        }

        /** 合并分开上报的 SPS/PPS，重连后先完整补发，避免 PC 解码器只有一半配置。 */
        private static byte[] mergeCodecConfig(byte[] current, byte[] incoming) {
            if (current == null || current.length == 0) {
                return incoming.clone();
            }
            byte[] merged = new byte[current.length + incoming.length];
            System.arraycopy(current, 0, merged, 0, current.length);
            System.arraycopy(incoming, 0, merged, current.length, incoming.length);
            return merged;
        }

        /** 按 v1/v2 帧协议写入一个 H264 单元。 */
        private void writeFrame(byte[] payload, long ptsUs, boolean codecConfig, boolean keyFrame) throws Exception {
            mOutput.writeInt(streamMagic);
            mOutput.writeInt(payload.length);
            mOutput.writeLong(ptsUs);
            if (mConfig.protocolVersion >= PROTOCOL_VERSION) {
                mOutput.writeInt(mRotationDegrees);
                mOutput.writeInt((codecConfig ? 1 : 0) | (keyFrame ? 2 : 0));
            } else if (streamMagic == CAMERA_STREAM_MAGIC) {
                mOutput.writeInt(mRotationDegrees);
            }
            mOutput.write(payload);
            mOutput.flush();
        }

        /** 根据当前路由连接 PC；初始与配置更新后始终优先 reverse。 */
        private synchronized boolean ensureConnected() {
            if (mSocket != null && mSocket.isConnected() && !mSocket.isClosed()) {
                return true;
            }
            if (mConnecting || System.currentTimeMillis() < mNextConnectAt) return false;
            mConnecting = true;
            new Thread(this::connectInBackground, "H264StreamConnect").start();
            return false;
        }

        /** 在非编码线程中建立 TCP 连接，避免断网时阻塞 MediaCodec drain。 */
        private void connectInBackground() {
            StreamTransportConfig config;
            int transport;
            synchronized (this) {
                config = mConfig;
                transport = mTransport;
            }
            try {
                String host = transport == TRANSPORT_DIRECT ? config.directHost : "127.0.0.1";
                int port = transport == TRANSPORT_DIRECT ? config.directPort : config.reversePort;
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 2_000);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                writeHello(output, config, transport);
                synchronized (this) {
                    if (config != mConfig || transport != mTransport) {
                        socket.close();
                    } else {
                        mSocket = socket;
                        mOutput = output;
                        mNeedCodecConfig = true;
                        mNextConnectAt = 0L;
                        mReconnectDelayMs = 1_000L;
                        Log.i(TAG, "推流已通过" + transportName() + "连接 PC:" + host + ":" + port);
                    }
                }
            } catch (Exception e) {
                synchronized (this) {
                    Log.w(TAG, "推流 " + transportName() + " 连接失败: " + e.getMessage());
                    if (config == mConfig && transport == mTransport) onConnectFailure();
                }
            } finally {
                synchronized (this) {
                    mConnecting = false;
                }
            }
        }

        /** v2 连接先发鉴权握手；旧端配置保留原始裸帧协议。 */
        private void writeHello(DataOutputStream output, StreamTransportConfig config, int transport) throws Exception {
            if (config.protocolVersion < PROTOCOL_VERSION) return;
            output.writeInt(streamMagic);
            output.writeShort(PROTOCOL_VERSION);
            output.writeByte(streamMagic == CAMERA_STREAM_MAGIC ? 2 : 1);
            output.writeByte(transport);
            output.writeShort(config.sessionToken.length);
            output.write(config.sessionToken);
            output.flush();
        }

        /** reverse 写失败后，仅在已预置 direct 配置时把当前会话切换到 direct。 */
        private void onWriteFailure() {
            boolean canFallback = mTransport == TRANSPORT_REVERSE && mConfig.allowDirectFallback;
            close();
            if (canFallback) mTransport = TRANSPORT_DIRECT;
            scheduleReconnect();
        }

        /** reverse 连不通时切 direct；direct 失败则保持 direct 并按退避重连，避免 ADB 恢复抢占。 */
        private void onConnectFailure() {
            boolean canFallback = mTransport == TRANSPORT_REVERSE && mConfig.allowDirectFallback;
            close();
            if (canFallback) mTransport = TRANSPORT_DIRECT;
            scheduleReconnect();
        }

        /** 按有上限的指数退避安排下一次连接，防止每一帧都触发 TCP connect。 */
        private void scheduleReconnect() {
            mNextConnectAt = System.currentTimeMillis() + mReconnectDelayMs;
            mReconnectDelayMs = Math.min(30_000L, mReconnectDelayMs * 2L);
        }

        /** 返回当前传输方式用于不含敏感数据的排障日志。 */
        private String transportName() {
            return mTransport == TRANSPORT_DIRECT ? "直连" : "reverse";
        }

        /** 关闭当前推流连接。 */
        synchronized void close() {
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

    /** Android 端仅保存在内存中的单流双端点配置；token 不落盘也不写日志。 */
    private static final class StreamTransportConfig {
        final String streamKind;
        final String directHost;
        final int directPort;
        final int reversePort;
        final byte[] sessionToken;
        final boolean allowDirectFallback;
        final int protocolVersion;

        StreamTransportConfig(String streamKind, String directHost, int directPort, int reversePort,
                              byte[] sessionToken, boolean allowDirectFallback, int protocolVersion) {
            this.streamKind = streamKind;
            this.directHost = directHost;
            this.directPort = directPort;
            this.reversePort = reversePort;
            this.sessionToken = sessionToken;
            this.allowDirectFallback = allowDirectFallback;
            this.protocolVersion = protocolVersion;
        }

        /** 从 ADB service action 读取并校验一份 v2 会话配置。 */
        static StreamTransportConfig fromIntent(Intent intent) {
            String kind = intent.getStringExtra(EXTRA_STREAM_KIND);
            String host = intent.getStringExtra(EXTRA_DIRECT_HOST);
            String token = intent.getStringExtra(EXTRA_SESSION_TOKEN);
            int directPort = intent.getIntExtra(EXTRA_DIRECT_PORT, 0);
            int reversePort = intent.getIntExtra(EXTRA_REVERSE_PORT, 0);
            byte[] sessionToken = decodeSessionToken(token);
            if ((!STREAM_KIND_SCREEN.equals(kind) && !STREAM_KIND_CAMERA.equals(kind))
                    || host == null || host.trim().isEmpty() || token == null || token.isEmpty()
                    || sessionToken == null || directPort <= 0 || reversePort <= 0) return null;
            return new StreamTransportConfig(kind, host, directPort, reversePort, sessionToken,
                    intent.getBooleanExtra(EXTRA_ALLOW_DIRECT_FALLBACK, true), 2);
        }

        /** 解码 PC 经 ADB extra 传入的 URL-safe 会话 token，非法值不建立未鉴权会话。 */
        @Nullable
        private static byte[] decodeSessionToken(@Nullable String token) {
            if (token == null || token.isEmpty()) return null;
            try {
                return Base64.decode(token, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        /** 构建不含 token 的 v1 reverse-only 配置，保证旧入口功能不变。 */
        static StreamTransportConfig legacy(int port) {
            return new StreamTransportConfig("", "", 0, port, new byte[0], false, 1);
        }
    }

    private static final class ActivityResultCodes {
        static final int RESULT_CANCELED = 0;
    }
}