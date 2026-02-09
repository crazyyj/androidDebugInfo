package com.newchar.debugview;

import android.app.Activity;
import android.app.Application;

import com.newchar.debugview.api.PluginManager;
import com.newchar.debugview.api.ScreenDisplayPlugin;
import com.newchar.debugview.lifecycle.AppLifecycleManager;
import com.newchar.debugview.lifecycle.UiContextActivityCallback;
import com.newchar.debugview.utils.DebugUtils;
import com.newchar.debugview.view.DebugView;
import com.newchar.debugview.view.DebugViewStore;

/**
 * @author newChar
 * date 2022/6/15
 * @since 调试工具入口。
 * @since 迭代版本，（以及描述）
 */
public class DebugManager {

    private static volatile DebugManager mDebugManager;
    private UiContextActivityCallback mUiContextActivityCallback;
    private boolean mInitialized;

    private DebugManager() {
    }

    /**
     * 获取调试入口单例。
     *
     * @return 调试入口实例
     */
    public static DebugManager getInstance() {
        if (mDebugManager == null) {
            synchronized (DebugManager.class) {
                if (mDebugManager == null) {
                    mDebugManager = new DebugManager();
                }
            }
        }
        return mDebugManager;
    }

    /**
     * 需要在第一个Activity露出之前，进行初始化
     *
     * @param app Application
     */
    public void initialize(Application app) {
        if (app == null || mInitialized) {
            return;
        }
        DebugUtils.attachApp(app);
        app.registerActivityLifecycleCallbacks(AppLifecycleManager.getInstance());
        if (mUiContextActivityCallback == null) {
            mUiContextActivityCallback = new UiContextActivityCallback();
        }
        AppLifecycleManager.getInstance().addLifecycleCallback(mUiContextActivityCallback);
        AppLifecycleManager.getInstance().addAppCloseCallback(mPluginRegisterCallback);
        mInitialized = true;
    }

    /**
     * 获取当前调试主视图。
     *
     * @return 当前可用的 DebugView
     */
    public DebugView getLogView() {
        return DebugViewStore.get();
    }

    /**
     * 获取指定调试插件实例。
     *
     * @param pluginClass 插件 Class
     * @param <T> 插件类型
     * @return 插件实例
     */
    @SuppressWarnings("unchecked")
    public <T extends ScreenDisplayPlugin> T getPlugin(Class<T> pluginClass) {
        DebugView debugView = getLogView();
        if (debugView == null) {
            return null;
        }
        return (T) debugView.getPlugin(pluginClass);
    }

    /**
     * 销毁调试入口并解绑回调。
     */
    public void destroy() {
        if (mUiContextActivityCallback != null) {
            AppLifecycleManager.getInstance().removeLifecycleCallback(mUiContextActivityCallback);
        }
        AppLifecycleManager.getInstance().removeAppCloseCallback(mPluginRegisterCallback);
        mInitialized = false;
        mDebugManager = null;
    }

    /**
     * 监听应用开关并注册插件。
     */
    private final AppLifecycleManager.AppCloseListener mPluginRegisterCallback =
            new AppLifecycleManager.AppCloseListener() {
                @Override
                public void onAppOpen(Activity firstActivity) {
                    registerOptionalPlugin("com.newchar.debug.logview.LogViewPlugin");
                    registerOptionalPlugin("com.newchar.monitor.plugin.PageTaskTopPlugin");
                    registerOptionalPlugin("com.newchar.deviceview.DevicesInfoPlugin");
                }

                @Override
                public void onAppClose(Activity lastActivity) {
                }
            };

    /**
     * 反射注册可选插件。
     *
     * @param className 插件类名
     */
    @SuppressWarnings("unchecked")
    private void registerOptionalPlugin(String className) {
        try {
            Class<ScreenDisplayPlugin> clazz = (Class<ScreenDisplayPlugin>) Class.forName(className);
            PluginManager.getInstance().registerOnce(clazz.getSimpleName(), clazz);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
