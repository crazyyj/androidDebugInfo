package com.newchar.debug.plugin;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.base.utils.ViewUtils;
import com.newchar.debug.device.disk.DiskMonitor;
import com.newchar.debug.device.disk.DiskSnapshot;

public class DiskInfoPlugin extends ScreenDisplayPlugin {
    public static final String TAG_PLUGIN = "DISK_INFO";

    private ScrollView rootView;
    private TextView contentView;
    private DiskMonitor diskMonitor;

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        Context context = pluginContainerView.getContext();
        if (rootView == null) {
            rootView = new ScrollView(context);
            rootView.setBackgroundColor(0x4D808080);
            contentView = new TextView(context);
            contentView.setTextColor(Color.DKGRAY);
            contentView.setTextSize(13f);
            int padding = dp(context, 12);
            contentView.setPadding(padding, padding, padding, padding);
            rootView.addView(contentView, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (diskMonitor == null) {
            diskMonitor = new DiskMonitor(context);
            diskMonitor.setListener(new DiskMonitor.Listener() {
                @Override
                public void onDiskSnapshot(DiskSnapshot snapshot) {
                    if (contentView != null && snapshot != null) {
                        contentView.setText(snapshot.toDisplayText());
                    }
                }

                @Override
                public void onDiskEvent(String eventText) {
                    if (diskMonitor != null) {
                        diskMonitor.refreshAsync();
                    }
                }
            });
        }
        if (rootView.getParent() == null) {
            pluginContainerView.addView(rootView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        ViewUtils.setVisibility(rootView, View.GONE);
    }

    @Override
    public void onShow() {
        ViewUtils.setVisibility(rootView, View.VISIBLE);
        if (diskMonitor != null) {
            diskMonitor.start();
        }
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(rootView, View.GONE);
    }

    @Override
    public void onUnload() {
        if (diskMonitor != null) {
            diskMonitor.stop();
            diskMonitor.setListener(null);
            diskMonitor = null;
        }
        if (rootView != null && rootView.getParent() instanceof ViewGroup) {
            ((ViewGroup) rootView.getParent()).removeView(rootView);
        }
        contentView = null;
        rootView = null;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
