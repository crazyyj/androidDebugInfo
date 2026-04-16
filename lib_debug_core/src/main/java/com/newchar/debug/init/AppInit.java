package com.newchar.debug.init;

import android.annotation.SuppressLint;
import android.app.AppComponentFactory;
import android.app.Application;

import com.newchar.debug.DebugManager;

/**
 * @author newChar
 * date 2025/4/6
 * @since 初始化入口.
 * @since 迭代版本，（以及描述）
 */
@SuppressLint("NewApi")
class AppInit extends AppComponentFactory {

    @Override
    public Application instantiateApplication(ClassLoader cl, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        // 给出初始化入口.
        Application application = super.instantiateApplication(cl, className);
        DebugManager.getInstance().initialize(application);
        return application;
    }

}
