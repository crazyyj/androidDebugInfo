package com.newchar.deviceview;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.newchar.deviceview.devices.Strict;
import com.newchar.debug.common.utils.ViewUtils;
import com.newchar.debugview.api.PluginContext;
import com.newchar.debugview.api.ScreenDisplayPlugin;

import java.io.File;

/**
 * @author newChar
 * date 2025/6/18
 * @since 设备调试入口插件
 * @since 迭代版本，（以及描述）
 */
public class DevicesInfoPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "DEVICE_DEBUG";

    private ViewGroup mContainerView;

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    /**
     * 插件加载时创建调试入口界面。
     *
     * @param ctx                插件上下文
     * @param pluginContainerView 插件容器
     */
    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        mContainerView = buildContainer(pluginContainerView.getContext());
        pluginContainerView.addView(mContainerView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 显示插件视图。
     */
    @Override
    public void onShow() {
        ViewUtils.setVisibility(mContainerView, View.VISIBLE);
    }

    /**
     * 隐藏插件视图。
     */
    @Override
    public void onHide() {
        ViewUtils.setVisibility(mContainerView, View.GONE);
    }

    /**
     * 插件卸载时释放引用。
     */
    @Override
    public void onUnload() {
        mContainerView = null;
    }

    /**
     * 构建包含调试入口按钮的容器。
     *
     * @param context 上下文
     * @return 容器视图
     */
    private ViewGroup buildContainer(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(buildStrictRow(context));
        layout.addView(buildActionView(context, "关闭严格模式", v -> Strict.stopAll()));
        layout.addView(buildActionView(context, "开发者选项", v ->
                openPage(v, new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))));
        layout.addView(buildActionView(context, "应用设置", v ->
                openPage(v, buildAppSettingsIntent(v.getContext()))));
        layout.addView(buildActionView(context, "系统设置", v ->
                openPage(v, new Intent(Settings.ACTION_SETTINGS))));
        return layout;
    }

    /**
     * 构建严格模式与清理入口行。
     *
     * @param context 上下文
     * @return 行视图
     */
    private View buildStrictRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(buildActionView(context, "开启严格模式", v -> Strict.startAll()));
        row.addView(buildActionView(context, "清理存储", this::confirmClearStorage));
        return row;
    }

    /**
     * 创建可点击的入口文本。
     *
     * @param context  上下文
     * @param title    标题
     * @param listener 点击回调
     * @return 文本视图
     */
    private View buildActionView(Context context, String title, View.OnClickListener listener) {
        TextView textView = new TextView(context);
        textView.setText(title);
        textView.setPadding(24, 20, 24, 20);
        textView.setOnClickListener(listener);
        return textView;
    }

    /**
     * 弹窗确认清理存储。
     *
     * @param view 触发视图
     */
    private void confirmClearStorage(View view) {
        Context context = view.getContext();
        new AlertDialog.Builder(context)
                .setTitle("清理存储")
                .setMessage("确认清理当前应用存储吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", (dialog, which) -> clearAppStorage(view))
                .show();
    }

    /**
     * 清理当前应用存储内容。
     *
     * @param view 触发视图
     */
    private void clearAppStorage(View view) {
        Context context = view.getContext().getApplicationContext();
        boolean cacheResult = deleteDir(context.getCacheDir());
        boolean filesResult = deleteDir(context.getFilesDir());
        boolean externalResult = true;
        File externalCache = context.getExternalCacheDir();
        if (externalCache != null) {
            externalResult = deleteDir(externalCache);
        }
        Toast.makeText(view.getContext(),
                (cacheResult && filesResult && externalResult) ? "已清理" : "清理部分失败",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * 递归删除目录内容。
     *
     * @param dir 目录
     * @return 是否成功
     */
    private boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return true;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (!deleteDir(file)) {
                        return false;
                    }
                } else if (!file.delete()) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

    /**
     * 构建当前应用的设置页 Intent。
     *
     * @param context 上下文
     * @return Intent
     */
    private Intent buildAppSettingsIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        return intent;
    }

    /**
     * 打开调试入口页面。
     *
     * @param view   触发视图
     * @param intent 目标 Intent
     */
    private void openPage(View view, Intent intent) {
        Context context = view.getContext();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "无法打开页面", Toast.LENGTH_SHORT).show();
        }
    }
}
