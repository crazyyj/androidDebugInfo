package com.newchar.debug.plugin;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.device.disk.DiskMonitor;
import com.newchar.debug.device.disk.DiskSnapshot;
import com.newchar.debug.device.disk.StorageCollector;
import com.newchar.debug.device.disk.StorageInfo;
import com.newchar.debug.utils.HandleWrapper;
import com.newchar.debug.utils.ViewUtils;

public class DiskInfoPlugin extends ScreenDisplayPlugin {
    public static final String TAG_PLUGIN = "DISK_INFO";

    private static final long STORAGE_REFRESH_INTERVAL_MS = 1000L;

    private ScrollView rootView;
    private TextView storageInfoView;
    private TextView contentView;
    private DiskMonitor diskMonitor;
    private final Handler mMainHandler = HandleWrapper.getMainHandler();

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public String getName() {
        return "磁盘信息";
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        Context context = pluginContainerView.getContext();
        if (rootView == null) {
            rootView = new ScrollView(context);
            rootView.setBackgroundColor(0x4D808080);

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            rootView.addView(container, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            storageInfoView = new TextView(context);
            storageInfoView.setTextColor(Color.DKGRAY);
            storageInfoView.setTextSize(13f);
            int padding = dp(context, 12);
            storageInfoView.setPadding(padding, padding, padding, 0);
            container.addView(storageInfoView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            contentView = new TextView(context);
            contentView.setTextColor(Color.DKGRAY);
            contentView.setTextSize(13f);
            contentView.setPadding(padding, padding, padding, padding);
            container.addView(contentView, new LinearLayout.LayoutParams(
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
        refreshStorageInfo();
        startStorageRefreshLoop();
        if (diskMonitor != null) {
            diskMonitor.start();
        }
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(rootView, View.GONE);
        stopStorageRefreshLoop();
        if (diskMonitor != null) {
            diskMonitor.stop();
        }
    }

    @Override
    public void onUnload() {
        stopStorageRefreshLoop();
        if (diskMonitor != null) {
            diskMonitor.stop();
            diskMonitor.setListener(null);
            diskMonitor = null;
        }
        if (rootView != null && rootView.getParent() instanceof ViewGroup) {
            ((ViewGroup) rootView.getParent()).removeView(rootView);
        }
        storageInfoView = null;
        contentView = null;
        rootView = null;
    }

    private void refreshStorageInfo() {
        if (storageInfoView == null) {
            return;
        }
        StorageInfo info = StorageCollector.collect();
        storageInfoView.setText("存储\n内部总量 : " + formatBytes(info.getDevicesTotalSize())
                + "\n内部可用 : " + formatBytes(info.getDevicesAvailableSize())
                + "\n内部空闲 : " + formatBytes(info.getDevicesFreeSize())
                + "\n外部总量 : " + formatBytes(info.getExternalTotalSize())
                + "\n外部可用 : " + formatBytes(info.getExternalAvailableSize())
                + "\n外部空闲 : " + formatBytes(info.getExternalFreeSize()));
    }

    private void startStorageRefreshLoop() {
        mMainHandler.removeCallbacks(mStorageRefreshTask);
        mMainHandler.postDelayed(mStorageRefreshTask, STORAGE_REFRESH_INTERVAL_MS);
    }

    private void stopStorageRefreshLoop() {
        mMainHandler.removeCallbacks(mStorageRefreshTask);
    }

    private final Runnable mStorageRefreshTask = new Runnable() {
        @Override
        public void run() {
            refreshStorageInfo();
            if (rootView != null && rootView.getVisibility() == View.VISIBLE) {
                mMainHandler.postDelayed(this, STORAGE_REFRESH_INTERVAL_MS);
            }
        }
    };

    private static String formatBytes(double bytes) {
        if (bytes < 0) {
            return "不可用";
        }
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.2f", bytes / 1024 / 1024 / 1024) + " GB";
        }
        if (bytes >= 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.2f", bytes / 1024 / 1024) + " MB";
        }
        if (bytes >= 1024) {
            return String.format(java.util.Locale.US, "%.2f", bytes / 1024) + " KB";
        }
        return String.format(java.util.Locale.US, "%.2f", bytes) + " B";
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
