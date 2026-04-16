package com.newchar.debug.monitor.toppage;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Build;

import com.newchar.debug.monitor.plugin.IPageLifecycle;

import android.content.Context;
import android.util.SparseArray;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * @author newChar
 * date 2025/6/29
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class FragmentWrapper {

    private IPageLifecycle mPageLifecycle;
    private Activity mHostActivity;
    private FragmentManager.FragmentLifecycleCallbacks mPlatformCallbacks;
    private FragmentManager.OnBackStackChangedListener mLegacyListener;

    // Legacy tracking for pre-O platform fragments
    private final HashSet<Integer> mKnown = new HashSet<>();
    private final Map<Integer, WeakReference<Fragment>> mFragRefs = new HashMap<>();
    private final Map<Integer, Class<?>> mFragClasses = new HashMap<>();

    public FragmentWrapper(IPageLifecycle lifecycle) {
        mPageLifecycle = lifecycle;
    }

    public void setFragmentLifecycle(Activity activity) {
        if (mHostActivity == activity && (mPlatformCallbacks != null || mLegacyListener != null)) {
            return;
        }
        release();
        mHostActivity = activity;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mPlatformCallbacks = new FragmentLifecycleCallbacks(mPageLifecycle);
            activity.getFragmentManager().registerFragmentLifecycleCallbacks(mPlatformCallbacks, true);
        } else {
            // Pre-O fallback: use back stack listener + reflection to enumerate fragments
            registerLegacyCallbacks(activity);
        }
    }

    // --- Legacy support below API 26 ---
    private void registerLegacyCallbacks(Activity activity) {
        FragmentManager fm = activity.getFragmentManager();
        mLegacyListener = new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                notifyFragments(activity);
            }
        };
        fm.addOnBackStackChangedListener(mLegacyListener);
        // Initial scan
        notifyFragments(activity);
    }

    public void release() {
        if (mHostActivity != null) {
            FragmentManager fm = mHostActivity.getFragmentManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mPlatformCallbacks != null) {
                fm.unregisterFragmentLifecycleCallbacks(mPlatformCallbacks);
            }
            if (mLegacyListener != null) {
                fm.removeOnBackStackChangedListener(mLegacyListener);
            }
        }
        mPlatformCallbacks = null;
        mLegacyListener = null;
        mHostActivity = null;
        mKnown.clear();
        mFragRefs.clear();
        mFragClasses.clear();
    }

    private void notifyFragments(Activity activity) {
        List<Fragment> fragments = safeGetFragments(activity.getFragmentManager());
        HashSet<Integer> activeIds = new HashSet<>();
        for (Fragment f : fragments) {
            if (f == null) continue;
            int id = f.hashCode();
            activeIds.add(id);
            if (!mKnown.contains(id)) {
                mKnown.add(id);
                mFragRefs.put(id, new WeakReference<>(f));
                mFragClasses.put(id, f.getClass());
                if (mPageLifecycle != null) {
                    Context ctx = f.getContext() != null ? f.getContext() : activity;
                    mPageLifecycle.onPageCreate(ctx, id, f.getClass());
                }
            }
            // Visibility check (best-effort): isVisible covers added/resumed/visible/view non-null
            boolean visible = f.isVisible();
            Context ctx = f.getContext() != null ? f.getContext() : activity;
            if (mPageLifecycle != null) {
                if (visible) {
                    mPageLifecycle.onPageVisible(ctx, id, f.getClass());
                } else {
                    mPageLifecycle.onPageGone(ctx, id, f.getClass());
                }
            }
        }
        // Detect removed fragments and dispatch destroy
        List<Integer> removed = new ArrayList<>();
        for (Integer knownId : mKnown) {
            if (!activeIds.contains(knownId)) {
                removed.add(knownId);
            }
        }
        for (Integer rid : removed) {
            WeakReference<Fragment> ref = mFragRefs.remove(rid);
            Class<?> clazz = mFragClasses.remove(rid);
            mKnown.remove(rid);
            Fragment f = ref != null ? ref.get() : null;
            Context ctx = (f != null && f.getContext() != null) ? f.getContext() : activity;
            if (mPageLifecycle != null && clazz != null) {
                mPageLifecycle.onPageDestroy(ctx, rid, clazz);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Fragment> safeGetFragments(FragmentManager fm) {
        List<Fragment> result = new ArrayList<>();
        // Try mAdded
        try {
            Field added = fm.getClass().getDeclaredField("mAdded");
            added.setAccessible(true);
            Object list = added.get(fm);
            if (list instanceof List) {
                result.addAll((List<Fragment>) list);
            }
        } catch (Throwable ignored) {}
        // Try mActive (SparseArray<Fragment>)
        try {
            Field active = fm.getClass().getDeclaredField("mActive");
            active.setAccessible(true);
            Object sa = active.get(fm);
            if (sa instanceof SparseArray) {
                SparseArray<Fragment> spar = (SparseArray<Fragment>) sa;
                for (int i = 0; i < spar.size(); i++) {
                    Fragment f = spar.valueAt(i);
                    if (f != null && !result.contains(f)) {
                        result.add(f);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }
}
