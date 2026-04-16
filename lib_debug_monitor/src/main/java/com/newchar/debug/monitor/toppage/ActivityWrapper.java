package com.newchar.debug.monitor.toppage;

import android.app.Activity;

import com.newchar.debug.lifecycle.PageLifecycleWrapper;

import java.lang.ref.WeakReference;

/**
 * @author newChar
 * date 2023/7/22
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class ActivityWrapper extends PageLifecycleWrapper<Activity> {


    public ActivityWrapper(Activity activity, int newState) {
        setPageRef(activity);
        recordQueueTime();
        changePageState(newState);
    }

    @Override
    public void setPageRef(Activity activity) {
        mPageRef = new WeakReference<>(activity);
    }

    @Override
    public int hashCode() {
        Activity host;
        if (getPageRef() != null && (host = getPageRef()) != null) {
            return host.hashCode();
        }
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        Activity host;
        if (getPageRef() != null && (host = getPageRef()) != null) {
            if (obj instanceof ActivityWrapper && ((ActivityWrapper) obj).getPageRef() != null && ((ActivityWrapper) obj).getPageRef() != null) {
                return host.equals(((ActivityWrapper) obj).getPageRef());
            }
        }
        return false;
    }
}
