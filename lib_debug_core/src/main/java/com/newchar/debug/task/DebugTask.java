package com.newchar.debug.task;

/**
 * 后台任务定义。提交给 {@link TaskManager}，由 {@link DebugTaskService} 统一异步执行。
 *
 * @param <T> 任务结果类型
 */
public interface DebugTask<T> {

    /**
     * 任务执行逻辑（在 DebugTaskService 的线程池中运行）。
     * 支持长驻任务：可在内部阻塞（如 wait/CountDownLatch），
     * 配合 {@link #onCancel()} 实现协作式停止。
     *
     * @return 任务结果
     * @throws Exception 任务失败时抛出
     */
    T execute() throws Exception;

    /**
     * 任务成功回调（在完成线程回调，通常为服务线程池）。
     *
     * @param result 任务结果
     */
    default void onSuccess(T result) {
    }

    /**
     * 任务失败回调。
     *
     * @param throwable 失败原因
     */
    default void onFailure(Throwable throwable) {
    }

    /**
     * 协作式停止请求。长驻任务应在收到请求后尽快退出 {@link #execute()}。
     * 非长驻任务无需处理。
     */
    default void onCancel() {
    }
}