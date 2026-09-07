package com.newchar.debug.plugin;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.newchar.debug.admin.DeviceAdminManager;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.monitor.strict.Strict;
import com.newchar.debug.utils.ViewUtils;

/**
 * @author newChar
 * @since 严格模式监控插件。
 * <p>
 * 当设备管理员权限未授予时，展示可点击的申请 TextView；
 * 授予后，展示一个开关用于启停严格模式策略。
 */
public class StrictMonitorPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "Strict_Monitor";

    private LinearLayout mContainer;
    private TextView mTitleView;
    private TextView mApplyAdminView;
    private Switch mStrictSwitch;
    private TextView mStatusView;

    private boolean mAdminActive = false;

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public String getName() {
        return "严格模式";
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        Context context = pluginContainerView.getContext();
        mContainer = new LinearLayout(context);
        mContainer.setOrientation(LinearLayout.VERTICAL);
        mContainer.setPadding(dp2px(context, 12), dp2px(context, 12), dp2px(context, 12), dp2px(context, 12));
        mContainer.setBackgroundColor(0x4D808080);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, dp2px(context, 8), 0, dp2px(context, 8));

        // 标题
        mTitleView = new TextView(context);
        mTitleView.setTextSize(16);
        mTitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        mTitleView.setText("严格模式监控");
        mContainer.addView(mTitleView, layoutParams);

        // 状态文本
        mStatusView = new TextView(context);
        mStatusView.setTextSize(13);
        mStatusView.setGravity(Gravity.CENTER_HORIZONTAL);
        mContainer.addView(mStatusView, layoutParams);

        // 申请权限 TextView（admin 未授予时可见）
        mApplyAdminView = new TextView(context);
        mApplyAdminView.setTextSize(14);
        mApplyAdminView.setGravity(Gravity.CENTER);
        mApplyAdminView.setText("点击申请设备管理员权限");
        mApplyAdminView.setTextColor(Color.parseColor("#1976D2"));
        mApplyAdminView.setPadding(dp2px(context, 16), dp2px(context, 12), dp2px(context, 16), dp2px(context, 12));
        mApplyAdminView.setOnClickListener(v -> requestAdminPermission(context));
        mContainer.addView(mApplyAdminView, layoutParams);

        // 严格模式开关（admin 授予后可见）
        mStrictSwitch = new Switch(context);
        mStrictSwitch.setTextSize(14);
        mStrictSwitch.setText("严格模式");
        mStrictSwitch.setChecked(Strict.isEnabled());
        mStrictSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Strict.startAll();
            } else {
                Strict.stopAll();
            }
            updateStatusText();
        });
        mContainer.addView(mStrictSwitch, layoutParams);

        refreshAdminState(context);
        updateUIState();

        pluginContainerView.addView(mContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ViewUtils.setVisibility(mContainer, View.GONE);
    }

    @Override
    public void onShow() {
        Context context = mContainer == null ? null : mContainer.getContext();
        if (context != null) {
            // 每次 onShow 都重新检查 admin 状态，处理用户从系统设置返回的情况
            boolean wasActive = mAdminActive;
            refreshAdminState(context);
            if (wasActive != mAdminActive) {
                updateUIState();
            }
        }
        ViewUtils.setVisibility(mContainer, View.VISIBLE);
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(mContainer, View.GONE);
    }

    @Override
    public void onUnload() {
        if (mStrictSwitch != null) {
            mStrictSwitch.setOnCheckedChangeListener(null);
        }
        mContainer = null;
        mTitleView = null;
        mApplyAdminView = null;
        mStrictSwitch = null;
        mStatusView = null;
    }

    private void refreshAdminState(Context context) {
        mAdminActive = DeviceAdminManager.getInstance().isActive(context);
    }

    private void updateUIState() {
        if (mApplyAdminView == null || mStrictSwitch == null) {
            return;
        }
        if (mAdminActive) {
            mApplyAdminView.setVisibility(View.GONE);
            mStrictSwitch.setVisibility(View.VISIBLE);
            mStrictSwitch.setChecked(Strict.isEnabled());
        } else {
            mApplyAdminView.setVisibility(View.VISIBLE);
            mStrictSwitch.setVisibility(View.GONE);
        }
        updateStatusText();
    }

    private void updateStatusText() {
        if (mStatusView == null) {
            return;
        }
        if (!mAdminActive) {
            mStatusView.setText("未获取设备管理员权限");
            mStatusView.setTextColor(Color.GRAY);
        } else if (Strict.isEnabled()) {
            mStatusView.setText("严格模式：已开启");
            mStatusView.setTextColor(Color.parseColor("#388E3C"));
        } else {
            mStatusView.setText("严格模式：已关闭");
            mStatusView.setTextColor(Color.GRAY);
        }
    }

    private void requestAdminPermission(Context context) {
        DeviceAdminManager.getInstance().apply(context, new DeviceAdminManager.AdminResultCallback() {
            @Override
            public void onResult(boolean granted, String message) {
                refreshAdminState(context);
                updateUIState();
                if (mContainer != null) {
                    Toast.makeText(mContainer.getContext(),
                            granted ? "管理员权限已授予" : "管理员权限未授予",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private int dp2px(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
