package com.newchar.debug.plugin;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.newchar.debug.touch.MotionManager;
import com.newchar.debug.touch.ScreenRecordManager;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;

/**
 * 触摸事件与屏幕录制调试插件。
 */
public class TouchRestorePlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "TOUCH_RESTORE";
    private static final int STATE_CLOSE = 0;
    private static final int STATE_OPEN = 1;

    private final MotionManager mMotionManager = new MotionManager();
    private int mTouchState = STATE_CLOSE;
    private LinearLayout mContainerView;
    private Button mTouchRecordButton;
    private Button mScreenStartButton;
    private Button mScreenPauseButton;
    private Button mScreenStopButton;
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
        mScreenPauseButton = null;
        mScreenStopButton = null;
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
     * 初始化插件容器。
     *
     * @param context 上下文
     */
    private void initContainerView(Context context) {
        if (mContainerView != null) {
            return;
        }
        mContainerView = new LinearLayout(context);
        mContainerView.setGravity(Gravity.CENTER);
        mContainerView.setOrientation(LinearLayout.VERTICAL);
        mContainerView.setBackgroundColor(0x33FFFFFF);
        mContainerView.setPadding(32, 32, 32, 32);

        mTouchRecordButton = new Button(context);
        mTouchRecordButton.setTextColor(Color.BLACK);
        mTouchRecordButton.setOnClickListener(view -> toggleTouchCollectState());
        mContainerView.addView(mTouchRecordButton,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        mScreenStartButton = new Button(context);
        mScreenStartButton.setTextColor(Color.BLACK);
        mScreenStartButton.setOnClickListener(view -> startScreenRecord());
        mContainerView.addView(mScreenStartButton,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        mScreenPauseButton = new Button(context);
        mScreenPauseButton.setTextColor(Color.BLACK);
        mScreenPauseButton.setOnClickListener(view -> toggleScreenPauseState());
        mContainerView.addView(mScreenPauseButton,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        mScreenStopButton = new Button(context);
        mScreenStopButton.setTextColor(Color.BLACK);
        mScreenStopButton.setOnClickListener(view -> stopScreenRecord());
        mContainerView.addView(mScreenStopButton,
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

    /**
     * 打开触摸事件采集。
     */
    private void openTouchCollect() {
        mTouchState = STATE_OPEN;
        mMotionManager.start();
    }

    /**
     * 关闭触摸事件采集。
     */
    private void closeTouchCollect() {
        mTouchState = STATE_CLOSE;
        mMotionManager.stop();
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
        if (mScreenPauseButton != null) {
            mScreenPauseButton.setText(ScreenRecordManager.isPaused() ? "继续录制屏幕" : "暂停录制屏幕");
            mScreenPauseButton.setTextColor(recording ? Color.BLACK : Color.GRAY);
        }
        if (mScreenStopButton != null) {
            mScreenStopButton.setText("停止录制屏幕");
            mScreenStopButton.setTextColor(recording ? Color.BLACK : Color.GRAY);
        }
    }
}
