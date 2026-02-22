package com.newchar.debugview.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

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
    private static volatile int mThreadSelector = 1;

    public static Handler getMainHandler() {
        return mMainThread;
    }

    public static Handler getThreadHandler(Handler.Callback callback) {
        HandlerThread temp = getAliveThread();
        return new Handler(temp.getLooper(), callback);
    }

    private static HandlerThread getAliveThread() {
        final int selector = mThreadSelector;
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
