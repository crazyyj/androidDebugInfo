package com.newchar.probe.input;

/** 输入执行过程的暂停与取消控制器。 */
final class PlaybackControl {

    private final Object pauseLock = new Object();
    private volatile boolean paused;
    private volatile boolean cancelled;

    /** 暂停后续事件注入。 */
    void pause() {
        paused = true;
    }

    /** 恢复执行并唤醒等待线程。 */
    void resume() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }

    /** 取消执行并唤醒等待线程。 */
    void cancel() {
        cancelled = true;
        resume();
    }

    /** 返回任务是否已取消。 */
    boolean isCancelled() {
        return cancelled;
    }

    /** 阻塞到允许继续或任务取消。 */
    boolean awaitRunnable() throws InterruptedException {
        synchronized (pauseLock) {
            while (paused && !cancelled) pauseLock.wait();
        }
        return !cancelled;
    }

    /** 可被暂停和取消的分段等待。 */
    boolean sleep(long durationMs) throws InterruptedException {
        long remaining = Math.max(0L, durationMs);
        while (!cancelled && remaining > 0L) {
            if (!awaitRunnable()) return false;
            long slice = Math.min(remaining, 40L);
            Thread.sleep(slice);
            remaining -= slice;
        }
        return !cancelled;
    }
}