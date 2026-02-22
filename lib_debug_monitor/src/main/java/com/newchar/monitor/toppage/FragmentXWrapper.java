package com.newchar.monitor.toppage;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.newchar.monitor.plugin.IPageLifecycle;

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
                    if (mPageLifecycle != null) {
                        mPageLifecycle.onPageCreate(f.getActivity(), f.hashCode(), f.getClass());
                    }

                }

                @Override
                public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    super.onFragmentResumed(fm, f);
                    if (mPageLifecycle != null) {
                        mPageLifecycle.onPageVisible(f.getActivity(), f.hashCode(), f.getClass());
                    }
                }

                @Override
                public void onFragmentStopped(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    super.onFragmentStopped(fm, f);
                    if (mPageLifecycle != null) {
                        mPageLifecycle.onPageGone(f.getActivity(), f.hashCode(), f.getClass());
                    }
                }

                @Override
                public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                    super.onFragmentDestroyed(fm, f);
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
//
//    private void createLifecycleCallback() {
//        Application application = mAppCtxRef.get();
//        if (application != null) {
//            application.registerActivityLifecycleCallbacks(activityCallback);
//        }
//    }
//
//    public void release() {
//        if (mAppCtxRef != null) {
//            Application application = mAppCtxRef.get();
//            if (application != null) {
//                application.unregisterActivityLifecycleCallbacks(activityCallback);
//            }
//            mAppCtxRef.clear();
//            mAppCtxRef = null;
//        }
//    }
//
//    private final Application.ActivityLifecycleCallbacks activityCallback = new DefaultActivityCallback() {
//
//        private final WeakHashMap<Activity, FragmentManager.FragmentLifecycleCallbacks> mRef = new WeakHashMap<>();
//
//        @Override
//        public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
//            FragmentManager.FragmentLifecycleCallbacks fragmentLifeCycleCallback
//                    = (FragmentManager.FragmentLifecycleCallbacks) getFragmentLifeCycleCallback();
//            mRef.put(activity, fragmentLifeCycleCallback);
//            if (activity instanceof AppCompatActivity) {
//                FragmentManager supportFragmentManager =
//                        ((AppCompatActivity) activity).getSupportFragmentManager();
//                supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifeCycleCallback, true);
//            } else {
//                // 其他的Fragment
//            }
//        }
//
//        @Override
//        public void onActivityStopped(@NonNull Activity activity) {
//
//        }
//
//        @Override
//        public void onActivityDestroyed(@NonNull Activity activity) {
//            FragmentManager.FragmentLifecycleCallbacks removeCallback = mRef.remove(activity);
//            if (removeCallback == null) {
//                return;
//            }
//            if (activity instanceof AppCompatActivity) {
//                FragmentManager supportFragmentManager =
//                        ((AppCompatActivity) activity).getSupportFragmentManager();
//                supportFragmentManager.unregisterFragmentLifecycleCallbacks(removeCallback);
//            }
//        }
//
//    };
//
//    private Object getFragmentLifeCycleCallback() {
//        return new FragmentManager.FragmentLifecycleCallbacks() {
//            @Override
//            public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
//                super.onFragmentResumed(fm, f);
//                // Fragment 可见了。
//                String visibleFragName_Flag = f.getClass().getName() + "@" + f.hashCode();
//                // callback out
//
//            }
//
//            @Override
//            public void onFragmentStopped(@NonNull FragmentManager fm, @NonNull Fragment f) {
//                super.onFragmentStopped(fm, f);
//                // Fragment 不可见了。
//                String invisibleFragName_Flag = f.getClass().getName() + "@" + f.hashCode();
//                // callback out
//
//            }
//
//            @Override
//            public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
//                super.onFragmentDestroyed(fm, f);
//
//            }
//        };
//    }
//
//    public interface TopFragmentCallback {
//
//        void onVisibleChanged(String fragmentName, int visible);
//
//        default void onFragmentCreated(String fragmentName, int created){}
//
//    }
//
}
