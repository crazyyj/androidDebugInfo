package com.newchar.debug.plugin;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.newchar.debug.touch.ScreenRecordManager;
import com.newchar.debug.touch.eventreplay.EventReplayManager;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;

import java.io.File;
import java.io.IOException;

/**
 * 触摸事件与屏幕录制调试插件。
 */
public class TouchRestorePlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "TOUCH_RESTORE";
    private static final int STATE_CLOSE = 0;
    private static final int STATE_OPEN = 1;

    private final EventReplayManager mEventReplayManager = new EventReplayManager();
    private int mTouchState = STATE_CLOSE;
    private File mLastInputScriptFile;
    private ScrollView mContainerView;
    private Button mTouchRecordButton;
    private Button mScreenStartButton;
    private Button mStreamStartButton;
    private Button mScreenPauseButton;
    private Button mScreenStopButton;
    private Button mCameraStartButton;
    private Button mCameraStopButton;
    private boolean mScreenStartButtonLocked;

    /**
     * 返回插件唯一标识。
     *
     * @return 插件 id
     */
    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public String getName() {
        return "触摸回放";
    }

    /**
     * 加载插件 UI。
     *
     * @param ctx                 插件上下文
     * @param pluginContainerView 插件容器
     */
    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        initContainerView(pluginContainerView.getContext());
        pluginContainerView.addView(mContainerView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        updateButtonState();
        mContainerView.setVisibility(View.GONE);
    }

    /**
     * 展示插件 UI。
     */
    @Override
    public void onShow() {
        updateButtonState();
        if (mContainerView != null) {
            mContainerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 隐藏插件 UI。
     */
    @Override
    public void onHide() {
        if (mContainerView != null) {
            mContainerView.setVisibility(View.GONE);
        }
    }

    /**
     * 卸载插件并关闭采集。
     */
    @Override
    public void onUnload() {
        closeTouchCollect();
        if (mContainerView != null) {
            ScreenRecordManager.stop(mContainerView.getContext());
        }
        mContainerView = null;
        mTouchRecordButton = null;
        mScreenStartButton = null;
        mStreamStartButton = null;
        mScreenPauseButton = null;
        mScreenStopButton = null;
        mCameraStartButton = null;
        mCameraStopButton = null;
        mScreenStartButtonLocked = false;
    }

    /**
     * 获取当前触摸采集状态。
     *
     * @return 0 表示关闭，1 表示打开
     */
    public int getState() {
        return mTouchState;
    }

    /**
     * 返回最近一次触摸录制生成的 shell 输入脚本。
     *
     * @return 尚未生成时返回 null
     */
    public File getLastInputScriptFile() {
        return mLastInputScriptFile;
    }

    /**
     * 初始化插件容器。
     *
     * @param context 上下文
     */
    private void initContainerView(Context context) {
        if (mContainerView != null) {
            return;
        }
        mContainerView = new ScrollView(context);
        mContainerView.setFillViewport(true);
        LinearLayout content = new LinearLayout(context);
        content.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        content.setOrientation(LinearLayout.VERTICAL);
        mContainerView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mContainerView.setBackgroundColor(0x33FFFFFF);
        mContainerView.setPadding(32, 32, 32, 32);

        mTouchRecordButton = new Button(context);
        mTouchRecordButton.setTextColor(Color.BLACK);
        mTouchRecordButton.setOnClickListener(view -> toggleTouchCollectState());
        content.addView(mTouchRecordButton,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        mScreenStartButton = new Button(context);
        mScreenStartButton.setTextColor(Color.BLACK);
        mScreenStartButton.setOnClickListener(view -> startScreenRecord());
        content.addView(mScreenStartButton,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        mStreamStartButton = new Button(context);
        mStreamStartButton.setTextColor(Color.BLACK);
        mStreamStartButton.setOnClickListener(view -> startRealtimeStream());
        content.addView(mStreamStartButton,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        mScreenPauseButton = new Button(context);
        mScreenPauseButton.setTextColor(Color.BLACK);
        mScreenPauseButton.setOnClickListener(view -> toggleScreenPauseState());
        content.addView(mScreenPauseButton,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        mScreenStopButton = new Button(context);
        mScreenStopButton.setTextColor(Color.BLACK);
        mScreenStopButton.setOnClickListener(view -> stopScreenRecord());
        content.addView(mScreenStopButton,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        mCameraStartButton = new Button(context);
        mCameraStartButton.setTextColor(Color.BLACK);
        mCameraStartButton.setOnClickListener(view -> startCameraPreview());
        content.addView(mCameraStartButton,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        mCameraStopButton = new Button(context);
        mCameraStopButton.setTextColor(Color.BLACK);
        mCameraStopButton.setOnClickListener(view -> stopCameraPreview());
        content.addView(mCameraStopButton,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * 切换触摸采集状态。
     */
    private void toggleTouchCollectState() {
        if (mTouchState == STATE_OPEN) {
            closeTouchCollect();
        } else {
            openTouchCollect();
        }
        updateButtonState();
    }

    private void startScreenRecord() {
        if (mContainerView == null || mScreenStartButtonLocked || ScreenRecordManager.isRecording()) {
            return;
        }
        mScreenStartButtonLocked = true;
        ScreenRecordManager.start(mContainerView.getContext());
        updateButtonState();
    }

    /** 启动同时保存 MP4 与推送 H264 的实时录屏。 */
    private void startRealtimeStream() {
        if (mContainerView == null || mScreenStartButtonLocked || ScreenRecordManager.isRecording()) {
            return;
        }
        mScreenStartButtonLocked = true;
        ScreenRecordManager.startStream(mContainerView.getContext());
        updateButtonState();
    }

    private void toggleScreenPauseState() {
        if (mContainerView == null || !ScreenRecordManager.isRecording()) {
            return;
        }
        if (ScreenRecordManager.isPaused()) {
            ScreenRecordManager.resume(mContainerView.getContext());
        } else {
            ScreenRecordManager.pause(mContainerView.getContext());
        }
        updateButtonState();
    }

    private void stopScreenRecord() {
        if (mContainerView == null || !ScreenRecordManager.isRecording()) {
            return;
        }
        ScreenRecordManager.stop(mContainerView.getContext());
        mScreenStartButtonLocked = false;
        updateButtonState();
    }

    /** 启动设备端相机预览推流（无需屏幕录制权限）。 */
    private void startCameraPreview() {
        if (mContainerView == null || ScreenRecordManager.isCameraStreaming()) {
            return;
        }
        ScreenRecordManager.startCamera(mContainerView.getContext());
        updateButtonState();
    }

    /** 停止设备端相机预览推流。 */
    private void stopCameraPreview() {
        if (mContainerView == null || !ScreenRecordManager.isCameraStreaming()) {
            return;
        }
        ScreenRecordManager.stopCamera(mContainerView.getContext());
        updateButtonState();
    }

    /**
     * 打开触摸事件采集。
     */
    private void openTouchCollect() {
        mTouchState = STATE_OPEN;
        mEventReplayManager.startRecording();
        if (mContainerView != null) {
            Context context = mContainerView.getContext();
            mEventReplayManager.recorder().setScreenSize(
                    context.getResources().getDisplayMetrics().widthPixels,
                    context.getResources().getDisplayMetrics().heightPixels);
        }
    }

    /**
     * 关闭触摸事件采集。
     */
    private void closeTouchCollect() {
        if (mTouchState == STATE_CLOSE) {
            return;
        }
        mTouchState = STATE_CLOSE;
        mEventReplayManager.stopRecording();
        saveInputScript();
    }

    /** 将本次采集写入外部缓存目录的 LQITS 脚本。 */
    private void saveInputScript() {
        if (mContainerView == null || mEventReplayManager.recorder().getSequence().eventCount() == 0) {
            return;
        }
        File cacheDir = mContainerView.getContext().getExternalCacheDir();
        if (cacheDir == null) {
            return;
        }
        File directory = new File(cacheDir, ".v/scripts");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            return;
        }
        File script = new File(directory, "touch_" + System.currentTimeMillis() + ".lqits");
        try {
            mEventReplayManager.saveInputScript(script);
            mLastInputScriptFile = script;
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * 刷新按钮展示状态。
     */
    private void updateButtonState() {
        if (mTouchRecordButton != null) {
            if (mTouchState == STATE_OPEN) {
                mTouchRecordButton.setText("停止录制 touch 事件");
            } else {
                mTouchRecordButton.setText("启动录制 touch 事件");
            }
        }
        boolean recording = ScreenRecordManager.isRecording();
        if (!recording) {
            mScreenStartButtonLocked = false;
        }
        if (mScreenStartButton != null) {
            mScreenStartButton.setText(recording ? "屏幕录制已启动" : "启动录制屏幕");
            mScreenStartButton.setTextColor(mScreenStartButtonLocked || recording ? Color.GRAY : Color.BLACK);
        }
        if (mStreamStartButton != null) {
            mStreamStartButton.setText(recording && ScreenRecordManager.isStreaming()
                    ? "实时推流录屏已启动" : "启动实时推流录屏");
            mStreamStartButton.setTextColor(mScreenStartButtonLocked || recording ? Color.GRAY : Color.BLACK);
        }
        if (mScreenPauseButton != null) {
            mScreenPauseButton.setText(ScreenRecordManager.isPaused() ? "继续录制屏幕" : "暂停录制屏幕");
            mScreenPauseButton.setTextColor(recording ? Color.BLACK : Color.GRAY);
        }
        if (mScreenStopButton != null) {
            mScreenStopButton.setText("停止录制屏幕");
            mScreenStopButton.setTextColor(recording ? Color.BLACK : Color.GRAY);
        }
        boolean cameraStreaming = ScreenRecordManager.isCameraStreaming();
        if (mCameraStartButton != null) {
            mCameraStartButton.setText(cameraStreaming ? "相机预览已启动" : "启动相机预览");
            mCameraStartButton.setTextColor(cameraStreaming ? Color.GRAY : Color.BLACK);
        }
        if (mCameraStopButton != null) {
            mCameraStopButton.setText("停止相机预览");
            mCameraStopButton.setTextColor(cameraStreaming ? Color.BLACK : Color.GRAY);
        }
    }
}