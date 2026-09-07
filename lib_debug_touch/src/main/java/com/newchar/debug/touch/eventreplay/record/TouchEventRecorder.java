package com.newchar.debug.touch.eventreplay.record;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.Window;

import com.newchar.debug.touch.eventreplay.model.RecordedEvent;
import com.newchar.debug.touch.eventreplay.model.RecordedEventSequence;
import com.newchar.debug.touch.eventreplay.model.TouchPoint;
import com.newchar.debug.lifecycle.AppLifecycleManager;
import com.newchar.debug.lifecycle.DefaultActivityCallback;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 触摸事件采集器（融合自原 touch 模块 RecordTouchEventTask / MotionManager）。
 *
 * <p>通过在 Activity 的 Window.Callback 上挂载动态代理，拦截 dispatchTouchEvent，
 * 把原始 MotionEvent 转换为 {@link RecordedEvent} 并收集进
 * {@link RecordedEventSequence}；停止后可直接以 JSON 形式保存。</p>
 */
public final class TouchEventRecorder extends DefaultActivityCallback {

    private final Map<Activity, TouchCallbackProxy> mHooks = new WeakHashMap<>();
    private final RecordedEventSequence mSequence = new RecordedEventSequence();
    private boolean mStarted;
    private boolean mRegistered;
    private boolean mCollect = true;

    public void start() {
        if (mStarted) return;
        mStarted = true;
        mCollect = true;
        mSequence.createdAt = System.currentTimeMillis();
        registerLifecycleIfNeed();
        for (Activity a : AppLifecycleManager.getInstance().getAllActivity()) {
            hook(a);
        }
    }

    public void stop() {
        if (!mStarted) return;
        mStarted = false;
        mCollect = false;
        unregisterLifecycleIfNeed();
        for (Activity a : new ArrayList<>(mHooks.keySet())) {
            restore(a);
        }
    }

    public boolean isStarted() {
        return mStarted;
    }

    public void setActivityName(String name) {
        mSequence.activityName = name;
    }

    public void setScreenSize(int width, int height) {
        mSequence.screenWidth = width;
        mSequence.screenHeight = height;
    }

    /** 返回当前正在收集的事件序列（停止后仍可访问，用于保存或转换）。 */
    public RecordedEventSequence getSequence() {
        return mSequence;
    }

    /** 将录制结果以 JSON 形式保存。 */
    public void saveToFile(File file) throws IOException {
        mSequence.writeToFile(file);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        super.onActivityCreated(activity, savedInstanceState);
        hook(activity);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        super.onActivityResumed(activity);
        hook(activity);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        super.onActivityDestroyed(activity);
        restore(activity);
    }

    private void registerLifecycleIfNeed() {
        if (mRegistered) return;
        AppLifecycleManager.getInstance().addLifecycleCallback(this);
        mRegistered = true;
    }

    private void unregisterLifecycleIfNeed() {
        if (!mRegistered) return;
        AppLifecycleManager.getInstance().removeLifecycleCallback(this);
        mRegistered = false;
    }

    private void hook(Activity activity) {
        if (!mStarted || activity == null || activity.getWindow() == null || mHooks.containsKey(activity)) {
            return;
        }
        TouchCallbackProxy proxy = TouchCallbackProxy.install(activity, this);
        if (proxy != null) mHooks.put(activity, proxy);
    }

    private void restore(Activity activity) {
        if (activity == null) return;
        TouchCallbackProxy proxy = mHooks.remove(activity);
        if (proxy != null) proxy.restore(activity);
    }

    void onEventRecorded(RecordedEvent e) {
        if (mCollect) mSequence.events.add(e);
    }

    static final class TouchCallbackProxy implements InvocationHandler {

        private static final String METHOD_TOUCH_EVENT = "dispatchTouchEvent";

        private final WeakReference<Activity> mActivityRef;
        private final TouchEventRecorder mOwner;
        private Window.Callback mOrigin;

        static TouchCallbackProxy install(Activity activity, TouchEventRecorder owner) {
            if (activity == null || activity.getWindow() == null) {
                return null;
            }
            try {
                TouchCallbackProxy existing = findInstalled(activity.getWindow().getCallback());
                if (existing != null) return existing;
                TouchCallbackProxy handler = new TouchCallbackProxy(activity, owner);
                Window.Callback proxy = (Window.Callback) Proxy.newProxyInstance(
                        Window.Callback.class.getClassLoader(),
                        new Class[]{Window.Callback.class}, handler);
                activity.getWindow().setCallback(proxy);
                return handler;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private TouchCallbackProxy(Activity activity, TouchEventRecorder owner) {
            mActivityRef = new WeakReference<>(activity);
            mOwner = owner;
            mOrigin = activity.getWindow().getCallback();
        }

        static TouchCallbackProxy findInstalled(Window.Callback cb) {
            if (cb == null || !Proxy.isProxyClass(cb.getClass())) {
                return null;
            }
            InvocationHandler h = Proxy.getInvocationHandler(cb);
            return h instanceof TouchCallbackProxy ? (TouchCallbackProxy) h : null;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (METHOD_TOUCH_EVENT.equals(method.getName())
                    && args != null && args[0] instanceof MotionEvent) {
                record((MotionEvent) args[0]);
            }
            if (mOrigin != null) {
                return method.invoke(mOrigin, args);
            }
            return null;
        }

        void restore(Activity activity) {
            if (activity == null || activity.getWindow() == null || mOrigin == null) {
                return;
            }
            Window.Callback cur = activity.getWindow().getCallback();
            if (findInstalled(cur) == this) {
                activity.getWindow().setCallback(mOrigin);
            }
            mOrigin = null;
        }

        private void record(MotionEvent me) {
            if (me == null) return;
            RecordedEvent e = new RecordedEvent();
            e.downTime = me.getDownTime();
            e.eventTime = me.getEventTime();
            e.action = me.getAction();
            e.actionMasked = me.getActionMasked();
            int pc = me.getPointerCount();
            e.pointerCount = pc;
            e.pointerIds = new int[pc];
            e.points = new ArrayList<>();
            for (int i = 0; i < pc; i++) {
                int id = me.getPointerId(i);
                e.pointerIds[i] = id;
                e.points.add(new TouchPoint(me.getX(i), me.getY(i), me.getPressure(i), me.getSize(i), id));
            }
            Activity a = mActivityRef.get();
            if (a != null && mOwner.mSequence.activityName == null) {
                mOwner.mSequence.activityName = a.getClass().getSimpleName();
            }
            mOwner.onEventRecorded(e);
        }
    }
}
