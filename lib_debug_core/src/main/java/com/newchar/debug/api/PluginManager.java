package com.newchar.debug.api;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author newChar
 * date 2024/12/21
 * @since 插件管理
 * @since 迭代版本，（以及描述）
 */
public final class PluginManager {

    private final Map<String, Class<ScreenDisplayPlugin>> mAllPlugin = new HashMap<>();

    private static PluginManager mPluginManager;

    public static PluginManager getInstance() {
        if (mPluginManager == null) {
            mPluginManager =  new PluginManager();
        }
        return mPluginManager;
    }

    public <T extends Class<ScreenDisplayPlugin>> T getPlugin(String id) {
        return (T) mAllPlugin.get(id);
    }

    public Collection<Class<ScreenDisplayPlugin>> getAllPlugin() {
        return Collections.unmodifiableCollection(mAllPlugin.values());
    }

    public void registerOnce(String pluginTag, Class<ScreenDisplayPlugin> pluginClass) {
        try {
            if (!mAllPlugin.containsKey(pluginTag)) {
                mAllPlugin.put(pluginTag, pluginClass);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void register(String pluginTag, Class<ScreenDisplayPlugin> pluginClass) {
        try {
            mAllPlugin.put(pluginTag, pluginClass);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

}
