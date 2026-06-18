package com.newchar.debug.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Created by newlq on 2017/2/9.
 * 请在Application调用init方法初始化当前类
 */

public class ApplicationCompat {

    private static Handler mHandler;
    private static int mMainProcessId;
    private static Thread mMainThread;
    private static Context mApplication;

    public static void init(Context context) {
        mHandler = new Handler();
        mMainThread = Looper.getMainLooper().getThread();
        mMainProcessId = android.os.Process.myTid();
        mApplication = context.getApplicationContext();
    }

    public static Context getContext(){
        return mApplication;
    }

    public static Handler getHandler() {
        return mHandler;
    }

    public static Thread getMainThread() {
        return mMainThread;
    }

    public static int getMainProcessId() {
        return mMainProcessId;
    }

}