package com.newchar.debug.monitor.toppage;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;

import com.newchar.debug.monitor.IPageLifecycle;
import com.newchar.debug.monitor.Utils;

@RequiresApi(api = Build.VERSION_CODES.O)
public class FragmentLifecycleCallbacks extends FragmentManager.FragmentLifecycleCallbacks {

    private final IPageLifecycle mPageLifecycle;

    public FragmentLifecycleCallbacks(IPageLifecycle pageLifecycle) {
        mPageLifecycle = pageLifecycle;
    }


    @Override
    public void onFragmentCreated(FragmentManager fm, Fragment f, Bundle savedInstanceState) {
        super.onFragmentCreated(fm, f, savedInstanceState);
        if (shouldBreakFragmentCallback(f)) {
            return;
        }
        if (mPageLifecycle != null) {
            mPageLifecycle.onPageCreate(f.getContext(), f.hashCode(), f.getClass());
        }
    }

    @Override
    public void onFragmentResumed(FragmentManager fm, Fragment f) {
        super.onFragmentResumed(fm, f);
        if (shouldBreakFragmentCallback(f)) {
            return;
        }
        if (mPageLifecycle != null) {
            mPageLifecycle.onPageVisible(f.getContext(), f.hashCode(), f.getClass());
        }
    }

    @Override
    public void onFragmentStopped(FragmentManager fm, Fragment f) {
        super.onFragmentStopped(fm, f);
        if (shouldBreakFragmentCallback(f)) {
            return;
        }
        if (mPageLifecycle != null) {
            mPageLifecycle.onPageGone(f.getContext(), f.hashCode(), f.getClass());
        }
    }

    @Override
    public void onFragmentDestroyed(FragmentManager fm, Fragment f) {
        super.onFragmentDestroyed(fm, f);
        if (shouldBreakFragmentCallback(f)) {
            return;
        }
        if (mPageLifecycle != null) {
            mPageLifecycle.onPageDestroy(f.getContext(), f.hashCode(), f.getClass());
        }
    }

    /**
     * 判断 Fragment 回调是否需要中断。
     *
     * @param fragment 当前 Fragment
     * @return true 表示不进入 Fragment 监控流程
     */
    private boolean shouldBreakFragmentCallback(Fragment fragment) {
        return fragment != null && Utils.isDialogFragmentClass(fragment.getClass());
    }

}
