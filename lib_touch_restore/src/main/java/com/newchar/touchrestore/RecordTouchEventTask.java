package com.newchar.touchrestore;

import android.app.Activity;
import android.os.Parcel;
import android.view.MotionEvent;
import android.view.Window;

import com.newchar.debugview.utils.DebugUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author newChar
 * date 2023/9/15
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
class RecordTouchEventTask implements Runnable {

    private final WeakReference<Activity> mHostCallback;

    /**
     * 创建触摸事件记录任务。
     *
     * @param hostCallback 宿主页面
     */
    public RecordTouchEventTask(Activity hostCallback) {
        mHostCallback = new WeakReference<>(hostCallback);
    }

    /**
     * 执行当前页面触摸事件采集 Hook。
     */
    @Override
    public void run() {
        Activity activity = mHostCallback.get();
        if (activity == null) {
            return;
        }
        install(activity);
    }

    /**
     * 安装触摸事件采集代理。
     *
     * @param activity 需要采集的页面
     * @return 当前采集代理
     */
    static TouchCallbackProxy install(Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return null;
        }
        try {
            TouchCallbackProxy installedProxy = findInstalledProxy(activity.getWindow().getCallback());
            if (installedProxy != null) {
                return installedProxy;
            }
            TouchCallbackProxy invocationHandler = new TouchCallbackProxy(activity);
            Window.Callback callbackProxy = (Window.Callback) Proxy.newProxyInstance(
                    Window.Callback.class.getClassLoader(),
                    new Class[]{Window.Callback.class}, invocationHandler);
            activity.getWindow().setCallback(callbackProxy);
            return invocationHandler;
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 恢复页面原始触摸事件回调。
     *
     * @param activity 页面实例
     * @param proxy    采集代理
     */
    static void restore(Activity activity, TouchCallbackProxy proxy) {
        if (activity == null || proxy == null) {
            return;
        }
        proxy.restore(activity);
    }

    /**
     * 查找已经安装的触摸事件代理。
     *
     * @param callback 当前窗口回调
     * @return 已安装的采集代理
     */
    private static TouchCallbackProxy findInstalledProxy(Window.Callback callback) {
        if (callback == null || !Proxy.isProxyClass(callback.getClass())) {
            return null;
        }
        InvocationHandler handler = Proxy.getInvocationHandler(callback);
        if (handler instanceof TouchCallbackProxy) {
            return (TouchCallbackProxy) handler;
        }
        return null;
    }

    /**
     * 构建触摸事件记录文件。
     *
     * @param activity 页面实例
     * @return 记录文件
     */
    private static File buildRecordFile(Activity activity) {
        if (activity == null || activity.getExternalCacheDir() == null) {
            return null;
        }
        File dir = new File(activity.getExternalCacheDir(), "motion");
        if ((dir.exists() && dir.isDirectory()) || dir.mkdirs()) {
            return new File(dir, activity.getClass().getSimpleName() + "#" + activity.hashCode() + ".rec");
        }
        return null;
    }

    static final class TouchCallbackProxy implements InvocationHandler {
        private static final String METHOD_TOUCH_EVENT = "dispatchTouchEvent";
        private final WeakReference<Activity> mPagRef;
        /**
         * 原始 callback
         */
        private Window.Callback mOriginCallback;
        private final File mRecordFile;

        /**
         * 创建触摸事件采集代理。
         *
         * @param activity 页面实例
         */
        TouchCallbackProxy(Activity activity) {
            mPagRef = new WeakReference<>(activity);
            mOriginCallback = activity.getWindow().getCallback();
            mRecordFile = buildRecordFile(activity);
        }

        /**
         * 代理窗口回调，记录触摸事件后继续分发原始事件。
         *
         * @param proxy  代理对象
         * @param method 被调用的方法
         * @param args   方法参数
         * @return 原始回调返回值
         * @throws Throwable 原始调用异常
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (METHOD_TOUCH_EVENT.equals(method.getName())
                    && (args != null && args[0] instanceof MotionEvent)) {
                writeMotionEvent((MotionEvent) args[0]);
            }
            return invokeOrigin(method, args);
        }

        /**
         * 恢复窗口原始回调。
         *
         * @param activity 页面实例
         */
        void restore(Activity activity) {
            if (activity == null || activity.getWindow() == null || mOriginCallback == null) {
                return;
            }
            Window.Callback currentCallback = activity.getWindow().getCallback();
            if (findInstalledProxy(currentCallback) == this) {
                activity.getWindow().setCallback(mOriginCallback);
            }
            mOriginCallback = null;
        }

        /**
         * 获取原始窗口回调。
         *
         * @return 原始窗口回调
         */
        Window.Callback getOriginCallback() {
            return mOriginCallback;
        }

        /**
         * 写入单次触摸事件。
         *
         * @param motionEvent 触摸事件
         */
        private void writeMotionEvent(MotionEvent motionEvent) {
            if (motionEvent == null || mRecordFile == null) {
                return;
            }
            Parcel obtain = Parcel.obtain();
            obtain.setDataPosition(0);
            motionEvent.writeToParcel(obtain, 0);
            byte[] marshall = obtain.marshall();
            obtain.recycle();

            DebugUtils.appendBytesToFile(marshall, mRecordFile);
        }

        /**
         * 调用原始窗口回调。
         *
         * @param method 被调用的方法
         * @param args   方法参数
         * @return 原始回调返回值
         * @throws Throwable 原始调用异常
         */
        private Object invokeOrigin(Method method, Object[] args) throws Throwable {
            Activity activity = mPagRef.get();
            Object result = null;
            if (activity != null && mOriginCallback != null) {
                result = method.invoke(mOriginCallback, args);
            } else {
                mOriginCallback = null;
            }
            return result;
        }

    }

}
