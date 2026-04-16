package com.newchar.debug.lifecycle;

import android.os.SystemClock;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

/**
 * @author newChar
 * date 2023/7/4
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public abstract class PageLifecycleWrapper<T> {

    public static final int UNKNOW = 0;
    public static final int CREATE = 1;
    public static final int STARTED = 2;
    public static final int RESUMED = 3;
    public static final int PAUSED = 4;
    public static final int STOPPED = 5;
    public static final int DESTROYED = 6;

    protected WeakReference<T> mPageRef;

    /**
     * 入队时间
     */
    private final LinkedList<Long> inQueueTime = new LinkedList<>();

    /**
     * 当前状态
     */
    private int state = UNKNOW;


    public T getPageRef() {
        return mPageRef.get();
    }

    public abstract void setPageRef(T pageRef);

    public void recordQueueTime() {
        inQueueTime.add(SystemClock.uptimeMillis());
    }

    public Long[] getQueueTime() {
        return inQueueTime.toArray(new Long[0]);
    }

    public void changePageState(int newState) {
        this.state = newState;
    }

    public void release() {
        if (mPageRef != null) {
            mPageRef.clear();
            mPageRef = null;
        }
        inQueueTime.clear();
        state = UNKNOW;
    }

}
