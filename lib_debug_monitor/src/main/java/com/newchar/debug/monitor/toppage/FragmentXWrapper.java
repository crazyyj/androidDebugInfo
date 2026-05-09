package com.newchar.debug.monitor.toppage;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.newchar.debug.monitor.IPageLifecycle;
import com.newchar.debug.monitor.Utils;

import java.util.List;

public class FragmentXWrapper {

//    private final WeakReference<FragmentActivity> mActivityRef ;
    private IPageLifecycle  mPageLifecycle;
    private FragmentActivity mHostActivity;
    private FragmentManager.FragmentLifecycleCallbacks mFragmentLifecycleCallbacks;

    public FragmentXWrapper(IPageLifecycle pageLifecycle) {
        mPageLifecycle = pageLifecycle;
    }
    private void initExistFragment(List<Fragment> fragments) {
        for (Fragment f : fragments) {
            if (f == null) {
                continue;
            }
            if (shouldBreakFragmentCallback(f)) {
                continue;
            }
            if (mPageLifecycle != null) {
                mPageLifecycle.onPageCreate(f.getActivity(), f.hashCode(), f.getClass());
                if (f.isResumed() && !f.isHidden()) {
                    mPageLifecycle.onPageVisible(f.getActivity(), f.hashCode(), f.getClass());
                } else {
                    mPageLifecycle.onPageGone(f.getActivity(), f.hashCode(), f.getClass());
                }
            }
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

    //
    public void setFragmentLifeCycle(Object fragmentActivity) {
        if (fragmentActivity instanceof FragmentActivity) {
            FragmentActivity hostActivity = (FragmentActivity) fragmentActivity;
            if (mHostActivity == hostActivity && mFragmentLifecycleCallbacks != null) {
                return;
            }
            release();
            FragmentManager supportFragmentManager = hostActivity.getSupportFragmentManager();
            mFragmentLifecycleCallbacks = new FragmentManager.FragmentLifecycleCallbacks() {

                @Override
                public void onFragmentCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @Nullable Bundle savedInstanceState) {
                    super.onFragmentCreated(fm, f, savedInstanceState);
                    if (shouldBreakFragmentCallback(f)) {
                        return;
                    }
                    if (mPageLifecycle != null) {
                        mPageLifecycle.onPageCreate(f.getActivity(), f.hashCode(), f.getClass());
                    }

                }

                @Override
                public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    super.onFragmentResumed(fm, f);
                    if (shouldBreakFragmentCallback(f)) {
                        return;
                    }
                    if (mPageLifecycle != null) {
                        mPageLifecycle.onPageVisible(f.getActivity(), f.hashCode(), f.getClass());
                    }
                }

                @Override
                public void onFragmentStopped(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    super.onFragmentStopped(fm, f);
                    if (shouldBreakFragmentCallback(f)) {
                        return;
                    }
                    if (mPageLifecycle != null) {
                        mPageLifecycle.onPageGone(f.getActivity(), f.hashCode(), f.getClass());
                    }
                }

                @Override
                public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    super.onFragmentDestroyed(fm, f);
                    if (shouldBreakFragmentCallback(f)) {
                        return;
                    }
                    if (mPageLifecycle != null) {
                        mPageLifecycle.onPageDestroy(f.getActivity(), f.hashCode(), f.getClass());
                    }
                }
            };
            supportFragmentManager.registerFragmentLifecycleCallbacks(mFragmentLifecycleCallbacks, true);
            mHostActivity = hostActivity;
            List<Fragment> fragments = supportFragmentManager.getFragments();
            initExistFragment(fragments);
        } else {
//            mActivityRef = new WeakReference<>(null);
        }
    }

    public void release() {
        if (mHostActivity != null && mFragmentLifecycleCallbacks != null) {
            mHostActivity.getSupportFragmentManager()
                    .unregisterFragmentLifecycleCallbacks(mFragmentLifecycleCallbacks);
        }
        mFragmentLifecycleCallbacks = null;
        mHostActivity = null;
    }
}
