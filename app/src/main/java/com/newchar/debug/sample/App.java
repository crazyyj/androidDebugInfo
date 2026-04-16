package com.debugview.sample;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;

import com.newchar.debug.common.helper.ApplicationCompat;

/**
 * @author newChar
 * date 2023/7/7
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class App extends Application {

    public static long initTimeCold = 0L;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        registerComponentCallbacks(new ComponentCallbacks2() {
            @Override
            public void onTrimMemory(int level) {

            }

            @Override
            public void onConfigurationChanged(Configuration newConfig) {

            }

            @Override
            public void onLowMemory() {

            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationCompat.init(this);
//        new DebugStackMotion().stringFromJNI();
//        initTimeCold = System.currentTimeMillis();
//        WebView.setWebContentsDebuggingEnabled(true);
//        Toast.makeText(getApplicationContext(), "初始化的时间:"+(System.currentTimeMillis() - initTimeCold), Toast.LENGTH_SHORT).show();
    }

}
