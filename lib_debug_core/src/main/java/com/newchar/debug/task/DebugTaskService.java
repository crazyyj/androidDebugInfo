package com.newchar.debug.task;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.newchar.debug.utils.DebugUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 后台任务服务。所有 {@link TaskManager} 提交的任务都在此服务中执行。
 *
 * 任务处理流程：
 * 1. 收到 ACTION_EXECUTE Intent（携带 taskId）
 * 2. 从 TaskManager 注册表取出任务
 * 3. 使用服务线程池 + {@link CompletableFuture#supplyAsync} 异步执行
 * 4. 完成后回调 TaskManager，回调调用方
 */
public class DebugTaskService extends Service {

    private static final String TAG = "DebugTaskService";
    private static final String CHANNEL_ID = "debug_task_service";
    private static final int NOTIFICATION_ID = 0x5A57;

    private static final AtomicInteger sActiveTasks = new AtomicInteger(0);

    /** 服务线程池：所有任务共用一个池，串行/并发由任务自身决定 */
    private ExecutorService mExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        mExecutor = Executors.newCachedThreadPool(
                r -> {
                    Thread t = new Thread(r, "DebugTaskWorker");
                    t.setDaemon(false);
                    return t;
                });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat();
        if (intent != null && TaskManager.ACTION_EXECUTE.equals(intent.getAction())) {
            String taskId = intent.getStringExtra(TaskManager.EXTRA_TASK_ID);
            if (taskId != null) {
                handleTask(taskId);
            }
        }
        // 前台服务不返回 START_STICKY 自动重启（任务由调用方提交驱动）
        return START_NOT_STICKY;
    }

    /**
     * 取出任务并用 CompletableFuture 异步执行，完成后回调。
     */
    private void handleTask(final String taskId) {
        final DebugTask<?> task = TaskManager.take(taskId);
        if (task == null) {
            Log.w(TAG, "task not found: " + taskId);
            stopSelfIfIdle();
            return;
        }
        sActiveTasks.incrementAndGet();
        CompletableFuture.supplyAsync(() -> {
            try {
                return task.execute();
            } catch (Throwable t) {
                throw new java.util.concurrent.CompletionException(t);
            }
        }, mExecutor).whenComplete((result, throwable) -> {
            sActiveTasks.decrementAndGet();
            try {
                if (throwable != null) {
                    Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                            && throwable.getCause() != null
                            ? throwable.getCause() : throwable;
                    TaskManager.fail(taskId, cause, task);
                } else {
                    TaskManager.complete(taskId, result, task);
                }
            } finally {
                stopSelfIfIdle();
            }
        });
    }

    /**
     * 没有正在执行的任务时停止服务（前台服务由下一个任务再次拉起）。
     */
    private void stopSelfIfIdle() {
        if (sActiveTasks.get() <= 0) {
            stopForegroundCompat();
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
        }
        sActiveTasks.set(0);
        super.onDestroy();
    }

    private void startForegroundCompat() {
        try {
            Notification notification = DebugUtils.buildForegroundNotification(getApplicationContext());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable t) {
            Log.e(TAG, "startForeground failed", t);
        }
    }

    private void stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Throwable ignored) {
        }
    }
}
