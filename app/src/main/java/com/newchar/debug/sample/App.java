package com.newchar.debug.sample;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;

import com.newchar.debug.utils.ApplicationCompat;

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

    /** 初始化宿主，并声明辅助资源探测所需的网络环境约束。 */
    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationCompat.init(this);
        // 本宿主及所接入 SDK 不安装全局 HTTP Authenticator，辅助探测才可显式启用。
        com.newchar.debug.browser.store.WebResourceMetadataObserver
                .setHostConfirmedNoGlobalAuthenticator(true);
//        new DebugStackMotion().stringFromJNI();
//        initTimeCold = System.currentTimeMillis();
//        WebView.setWebContentsDebuggingEnabled(true);
//        Toast.makeText(getApplicationContext(), "初始化的时间:"+(System.currentTimeMillis() - initTimeCold), Toast.LENGTH_SHORT).show();
    }

}