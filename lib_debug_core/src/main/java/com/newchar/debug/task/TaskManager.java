package com.newchar.debug.task;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 后台任务管理器。
 *
 * 通过 {@link DebugTaskService} 统一执行任务：
 * 1. {@link #submit(Context, DebugTask)} 将任务注册到静态注册表，并启动服务
 * 2. 服务取出任务，用 CompletableFuture 异步执行
 * 3. 完成后通过返回值 {@link CompletableFuture} 回调调用方（支持 whenComplete 等链式回调）
 */
public final class TaskManager {

    private static final String TAG = "TaskManager";

    /** 服务 Action：执行任务 */
    public static final String ACTION_EXECUTE = "com.newchar.debug.task.action.EXECUTE";
    /** Extra：任务 id */
    public static final String EXTRA_TASK_ID = "com.newchar.debug.task.extra.TASK_ID";

    private static final ConcurrentHashMap<String, DebugTask<?>> sTasks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<Object>> sFutures = new ConcurrentHashMap<>();
    private static final AtomicBoolean sServiceStarted = new AtomicBoolean(false);

    private TaskManager() {
    }

    /**
     * 提交一个后台任务（自动生成任务 id），返回 CompletableFuture。
     *
     * @param context 上下文
     * @param task    任务
     * @param <T>     结果类型
     * @return 任务 CompletableFuture，可通过 whenComplete/thenAccept 获取结果回调
     */
    public static <T> CompletableFuture<T> submit(Context context, DebugTask<T> task) {
        return submit(context, UUID.randomUUID().toString(), task);
    }

    /**
     * 提交一个后台任务（指定任务 id），返回 CompletableFuture。
     *
     * @param context 上下文
     * @param taskId  任务 id（用于取消/查询）
     * @param task    任务
     * @param <T>     结果类型
     * @return 任务 CompletableFuture
     */
    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> submit(Context context, String taskId, DebugTask<T> task) {
        if (context == null || task == null) {
            throw new IllegalArgumentException("context and task must not be null");
        }
        Context appContext = context.getApplicationContext();
        sTasks.put(taskId, task);
        CompletableFuture<Object> future = new CompletableFuture<>();
        sFutures.put(taskId, future);

        Intent intent = new Intent(appContext, DebugTaskService.class);
        intent.setAction(ACTION_EXECUTE);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        startServiceCompat(appContext, intent);
        return (CompletableFuture<T>) future;
    }

    /**
     * 取消一个任务（仅能取消尚未执行的任务；正在执行的任务无法中断）。
     *
     * @param taskId 任务 id
     * @return true 表示任务从未执行过且被移除
     */
    public static boolean cancel(String taskId) {
        DebugTask<?> removed = sTasks.remove(taskId);
        CompletableFuture<Object> future = sFutures.remove(taskId);
        if (removed != null && future != null) {
            future.completeExceptionally(new java.util.concurrent.CancellationException("task cancelled: " + taskId));
            return true;
        }
        return false;
    }

    /** 服务从注册表取出任务执行。 */
    static DebugTask<?> take(String taskId) {
        return sTasks.remove(taskId);
    }

    /** 服务执行完成后回调成功。 */
    @SuppressWarnings("unchecked")
    static void complete(String taskId, Object result, DebugTask<?> task) {
        CompletableFuture<Object> future = sFutures.remove(taskId);
        if (future != null) {
            future.complete(result);
        }
        try {
            if (task != null) {
                ((DebugTask<Object>) task).onSuccess(result);
            }
        } catch (Throwable t) {
            Log.w(TAG, "onSuccess callback failed: " + taskId, t);
        }
    }

    /** 服务执行失败后回调失败。 */
    static void fail(String taskId, Throwable throwable, DebugTask<?> task) {
        CompletableFuture<Object> future = sFutures.remove(taskId);
        if (future != null) {
            future.completeExceptionally(throwable);
        }
        try {
            if (task != null) {
                task.onFailure(throwable);
            }
        } catch (Throwable t) {
            Log.w(TAG, "onFailure callback failed: " + taskId, t);
        }
    }

    private static void startServiceCompat(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            sServiceStarted.set(true);
        } catch (Throwable t) {
            Log.e(TAG, "start task service failed", t);
            sServiceStarted.set(false);
        }
    }
}
