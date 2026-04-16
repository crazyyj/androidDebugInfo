package com.newchar.debug.lifecycle;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * @author newChar
 * date 2023/5/11
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public abstract class DefaultActivityCallback implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onActivityCreated(Activity activity,  Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted( Activity activity) {

    }

    @Override
    public void onActivityResumed( Activity activity) {

    }

    @Override
    public void onActivityPaused( Activity activity) {

    }

    @Override
    public void onActivityStopped( Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState( Activity activity,  Bundle outState) {

    }

    @Override
    public void onActivityDestroyed( Activity activity) {

    }
}
