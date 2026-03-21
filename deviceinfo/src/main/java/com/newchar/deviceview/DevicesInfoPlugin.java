package com.newchar.deviceview;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.newchar.debug.common.utils.ViewUtils;
import com.newchar.debugview.api.PluginContext;
import com.newchar.debugview.api.ScreenDisplayPlugin;

/**
 * 仅展示固定设备信息，不承担调试入口行为。
 */
public class DevicesInfoPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "DEVICE_INFO";

    private ScrollView mContainerView;
    private TextView mInfoView;

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        Context context = pluginContainerView.getContext();
        if (mContainerView == null) {
            mContainerView = new ScrollView(context);
            mInfoView = new TextView(context);
            mInfoView.setTextColor(Color.DKGRAY);
            mInfoView.setPadding(24, 20, 24, 20);
            mContainerView.addView(mInfoView,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        refreshInfo(context);
        pluginContainerView.addView(mContainerView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        ViewUtils.setVisibility(mContainerView, View.GONE);
    }

    @Override
    public void onShow() {
        refreshInfo(mContainerView == null ? null : mContainerView.getContext());
        ViewUtils.setVisibility(mContainerView, View.VISIBLE);
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(mContainerView, View.GONE);
    }

    @Override
    public void onUnload() {
        mInfoView = null;
        mContainerView = null;
    }

    private void refreshInfo(Context context) {
        if (context == null || mInfoView == null) {
            return;
        }
        mInfoView.setText(DeviceStaticInfoCollector.buildDisplayText(context));
    }
}
