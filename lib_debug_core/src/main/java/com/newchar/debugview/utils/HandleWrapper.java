package com.newchar.debugview.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.Process;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author newChar
 * date 2024/12/21
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class HandleWrapper {

    private static HandlerThread mThread1 = new HandlerThread("default_HandlerThread", Process.THREAD_PRIORITY_DEFAULT);
    private static HandlerThread mThread2 = new HandlerThread("bg_HandlerThread", Process.THREAD_PRIORITY_BACKGROUND);
    private static final Handler mMainThread = new Handler(Looper.getMainLooper()/*, (message) -> { return false }*/);
    private static final AtomicInteger mThreadSelector = new AtomicInteger(1);

    public static Handler getMainHandler() {
        return mMainThread;
    }

    /**
     * 返回一个临时异步 Handler，适用于轻量、无结果回传的短任务。
     */
    public static Handler obtainAsyncHandler(Handler.Callback callback) {
        HandlerThread temp = getAliveThread();
        return new Handler(temp.getLooper(), callback);
    }

    public static void execUIIdleOnce(Runnable r){
        Looper.getMainLooper().getQueue()
                .addIdleHandler(() -> {
                    try {
                        r.run();
                    } catch (Exception e) {
                        Log.e("Handle", "execUIIdleOnce", e);
                    }
                    return false;
                });
    }

    private static HandlerThread getAliveThread() {
        final int selector = mThreadSelector.getAndIncrement();
        // 偶数 -> thread2；奇数 -> thread1
        if ((selector & 1) == 1) {
            mThread1 = ensureThreadAlive(mThread1, "default_HandlerThread", Process.THREAD_PRIORITY_DEFAULT);
            return mThread1;
        }
        mThread2 = ensureThreadAlive(mThread2, "bg_HandlerThread", Process.THREAD_PRIORITY_BACKGROUND);
        return mThread2;
    }

    private static synchronized HandlerThread ensureThreadAlive(
            HandlerThread thread, String name, int priority) {
        if (!isThreadUsable(thread)) {
            thread = new HandlerThread(name, priority);
            thread.start();
            return thread;
        }
        return thread;
    }

    private static boolean isThreadUsable(HandlerThread thread) {
        if (thread == null || !thread.isAlive()) {
            return false;
        }
        final Looper looper = thread.getLooper();
        if (looper == null) {
            return false;
        }
        final Thread looperThread = looper.getThread();
        if (looperThread == null) {
            return false;
        }
        return looperThread.isAlive();
    }

    private void sendMainMessage(Runnable r, long time) {
        mMainThread.postDelayed(r, time);
    }

    public void releaseH(Handler a) {
        a.removeCallbacksAndMessages(null);
    }

}
