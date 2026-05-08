package com.newchar.debug.base.helper;

import android.os.Looper;
import android.os.MessageQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadHelper {

    private static int mThreads = 5;
    private static ExecutorService threadPool;
    private static final Looper mMainLooper = Looper.getMainLooper();
    private static MessageQueue.IdleHandler mIdle;

    public static void runThread(Runnable r) {
        synchronized (r) {
            if (threadPool == null) {
                threadPool = Executors.newFixedThreadPool(mThreads);
            }
        }
        threadPool.execute(r);
    }

    public static void setThreads(int threads) {
        mThreads = threads;
    }

    public static void futureOnceMainTask(Runnable r) {
        mMainLooper.getQueue().addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                try {
                    r.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        });
    }

    public static void futureAlwaysMainTask(Runnable r) {
        mMainLooper.getQueue().addIdleHandler(getIdle());
    }

    private static MessageQueue.IdleHandler getIdle() {
        if (mIdle == null){
            mIdle = new MessageQueue.IdleHandler() {
                @Override
                public boolean queueIdle() {
                    try {

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
                }
            };
        }
        return mIdle;
    }

}
