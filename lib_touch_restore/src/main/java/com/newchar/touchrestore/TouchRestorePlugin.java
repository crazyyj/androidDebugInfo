package com.newchar.touchrestore;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.newchar.debugview.api.PluginContext;
import com.newchar.debugview.api.ScreenDisplayPlugin;

/**
 * 触摸事件采集调试插件。
 */
public class TouchRestorePlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "TOUCH_RESTORE";
    private static final int STATE_CLOSE = 0;
    private static final int STATE_OPEN = 1;

    private final MotionManager mMotionManager = new MotionManager();
    private int mState = STATE_CLOSE;
    private LinearLayout mContainerView;
    private Button mRecordButton;

    /**
     * 返回插件唯一标识。
     *
     * @return 插件 id
     */
    @Override
    public String id() {
        return TAG_PLUGIN;
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
        closeCollect();
        mContainerView = null;
        mRecordButton = null;
    }

    /**
     * 获取当前采集状态。
     *
     * @return 0 表示关闭，1 表示打开
     */
    public int getState() {
        return mState;
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

        mRecordButton = new Button(context);
        mRecordButton.setTextColor(Color.BLACK);
        mRecordButton.setOnClickListener(view -> toggleCollectState());
        mContainerView.addView(mRecordButton,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * 切换采集状态。
     */
    private void toggleCollectState() {
        if (mState == STATE_OPEN) {
            closeCollect();
        } else {
            openCollect();
        }
        updateButtonState();
    }

    /**
     * 打开触摸事件采集。
     */
    private void openCollect() {
        mState = STATE_OPEN;
        mMotionManager.start();
    }

    /**
     * 关闭触摸事件采集。
     */
    private void closeCollect() {
        mState = STATE_CLOSE;
        mMotionManager.stop();
    }

    /**
     * 刷新按钮展示状态。
     */
    private void updateButtonState() {
        if (mRecordButton == null) {
            return;
        }
        if (mState == STATE_OPEN) {
            mRecordButton.setText("触摸采集中，点击关闭");
        } else {
            mRecordButton.setText("开始触摸记录");
        }
    }
}
