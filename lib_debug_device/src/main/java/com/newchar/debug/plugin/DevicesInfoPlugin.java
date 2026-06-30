package com.newchar.debug.plugin;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.newchar.debug.utils.DebugUtils;
import com.newchar.debug.utils.ViewUtils;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.device.DeviceMonitor;
import com.newchar.debug.device.DeviceStaticInfoCollector;
import com.newchar.debug.device.DevicesInfoCallback;
import com.newchar.debug.device.DevicesInfoView;
import com.newchar.debug.device.bean.CPUInfo;
import com.newchar.debug.device.bean.DevicesInfo;
import com.newchar.debug.device.bean.MemoryInfo;
import com.newchar.debug.device.bean.PermissionItem;

import java.util.List;

/**
 * 展示设备基础信息与运行时监控数据。
 */
public class DevicesInfoPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "DEVICE_INFO";

    private DevicesInfoView mInfoView;
    private DeviceMonitor mDeviceMonitor;

    /**
     * 返回插件 id。
     *
     * @return 插件 id
     */
    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public String getName() {
        return "设备信息";
    }

    /**
     * 加载设备信息插件视图并绑定数据回调。
     *
     * @param ctx                 插件上下文
     * @param pluginContainerView 插件容器
     */
    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        Context context = pluginContainerView.getContext();
        if (mInfoView == null) {
            mInfoView = new DevicesInfoView(context);
            mInfoView.setBackgroundColor(0x4D808080);
        }
        mDeviceMonitor = DeviceMonitor.getInstance();
        mDeviceMonitor.setDevicesInfoCallback(mDevicesInfoCallback);
        pluginContainerView.addView(mInfoView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mInfoView.post(() -> {
            refreshInfo(context);
            if (mDeviceMonitor != null) {
                mDeviceMonitor.updateAllInfo();
            }
        });
        ViewUtils.setVisibility(mInfoView, View.GONE);
    }

    /**
     * 展示设备信息插件视图。
     */
    @Override
    public void onShow() {
        refreshInfo(mInfoView == null ? null : mInfoView.getContext());
        if (mDeviceMonitor != null) {
            mDeviceMonitor.updateAllInfo();
        }
        ViewUtils.setVisibility(mInfoView, View.VISIBLE);
    }

    /**
     * 隐藏设备信息插件视图。
     */
    @Override
    public void onHide() {
        ViewUtils.setVisibility(mInfoView, View.GONE);
    }

    /**
     * 卸载设备信息插件。
     */
    @Override
    public void onUnload() {
        if (mDeviceMonitor != null) {
            mDeviceMonitor.setDevicesInfoCallback(null);
            mDeviceMonitor.release();
            mDeviceMonitor = null;
        }
        mInfoView = null;
    }

    /**
     * 刷新基础设备信息。
     *
     * @param context 上下文
     */
    private void refreshInfo(Context context) {
        if (context == null || mInfoView == null) {
            return;
        }
        mInfoView.setDevicesInfo("基础设备信息\n" + DeviceStaticInfoCollector.buildDisplayText(context));
    }

    private final DevicesInfoCallback mDevicesInfoCallback = new DevicesInfoCallback() {
        @Override
        public void onMemoryCallback(MemoryInfo memoryInfo) {
            if (mInfoView != null) {
                mInfoView.setMemoryInfo(formatMemoryInfo(memoryInfo));
            }
        }

        @Override
        public void onCPUInfoCallback(CPUInfo info) {
            if (mInfoView != null) {
                mInfoView.setCpuInfo(formatCpuInfo(info));
            }
        }

        @Override
        public void onDevicesInfoCallback(DevicesInfo devicesInfo) {
            if (mInfoView != null) {
                mInfoView.setDevicesInfo(formatDevicesInfo(devicesInfo));
            }
        }

        @Override
        public void onFrameRateUpdate(float frameInterval_ms, float frameRate) {
            if (mInfoView != null) {
                mInfoView.setScreenInfo(formatFrameRateInfo(frameInterval_ms, frameRate));
            }
        }

        @Override
        public void onPermissionCallback(List<PermissionItem> permissions) {
            if (mInfoView != null) {
                mInfoView.setPermissionInfo(permissions);
            }
        }
    };

    /**
     * 格式化 CPU 信息。
     *
     * @param info CPU 信息
     * @return 展示文本
     */
    private static CharSequence formatCpuInfo(CPUInfo info) {
        if (info == null) {
            return "CPU\n暂无数据";
        }
        return "CPU\n使用率 : " + safeText(info.getCpuName());
    }

    /**
     * 格式化内存信息。
     *
     * @param memoryInfo 内存信息
     * @return 展示文本
     */
    private static CharSequence formatMemoryInfo(MemoryInfo memoryInfo) {
        if (memoryInfo == null) {
            return "内存\n暂无数据";
        }
        return "内存\n设备可用 : " + formatBytes(memoryInfo.getDevicesAvailMemory())
                + "\n设备总量 : " + formatBytes(memoryInfo.getDevicesTotalMem())
                + "\nApp 最大 : " + formatBytes(memoryInfo.getAppMaxMemory())
                + "\nApp 可用 : " + formatBytes(memoryInfo.getAppFreeMemory())
                + "\nApp 已分配 : " + formatBytes(memoryInfo.getAppTotalMemory());
    }

    /**
     * 格式化基础设备信息。
     *
     * @param devicesInfo 设备信息
     * @return 展示文本
     */
    private static CharSequence formatDevicesInfo(DevicesInfo devicesInfo) {
        if (devicesInfo == null) {
            return "基础设备信息\n暂无数据";
        }
        return "基础设备信息\n" + devicesInfo.toJson();
    }

    /**
     * 格式化帧率信息。
     *
     * @param frameIntervalMs 帧间隔
     * @param frameRate       帧率
     * @return 展示文本
     */
    private static CharSequence formatFrameRateInfo(float frameIntervalMs, float frameRate) {
        return "帧率\n帧间隔 : " + DebugUtils.keepTwo(frameIntervalMs)
                + " ms\nFPS : " + DebugUtils.keepTwo(frameRate);
    }

    /**
     * 格式化字节数。
     *
     * @param bytes 字节数
     * @return 展示文本
     */
    private static String formatBytes(double bytes) {
        if (bytes < 0) {
            return "不可用";
        }
        if (bytes >= DebugUtils.GB) {
            return DebugUtils.keepTwo(bytes / DebugUtils.GB) + " GB";
        }
        if (bytes >= DebugUtils.MB) {
            return DebugUtils.keepTwo(bytes / DebugUtils.MB) + " MB";
        }
        if (bytes >= DebugUtils.KB) {
            return DebugUtils.keepTwo(bytes / DebugUtils.KB) + " KB";
        }
        return DebugUtils.keepTwo(bytes) + " B";
    }

    /**
     * 返回安全文本。
     *
     * @param text 原始文本
     * @return 非空文本
     */
    private static String safeText(String text) {
        return text == null || text.isEmpty() ? "暂无数据" : text;
    }
}
