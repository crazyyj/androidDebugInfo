package com.newchar.debug.base.arch;

import android.app.Application;

import com.newchar.debug.base.helper.ApplicationCompat;

/**
 * Created by Newlq on 2015/12/30.
 */

public abstract class BaseApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationCompat.init(getApplicationContext());

//        mMainThread.setUncaughtExceptionHandler(CrashHandler.getInstance());

        initOtherJar();

        initProject();
    }



    /**
     * 初始化核心第三方支持库一类的操作， 无返回值
     */
    public void initOtherJar() {}

    /**
     * 默认登录, 获取本地getFileDir()路径 中的 *.properties 文件中的 文件信息.去登录
     * 没有则默认进入 不登陆
     * 初始化当前工程 例如默认登录等。有一个Object返回值
     */
    protected abstract Object initProject();

}
