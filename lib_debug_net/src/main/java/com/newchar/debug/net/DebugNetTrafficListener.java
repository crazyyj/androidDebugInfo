package com.newchar.debug.net;

/**
 * 网络事件监听器。返回 false 可中断后续 listener；默认实现应直接返回 true。
 */
public interface DebugNetTrafficListener {

    /**
     * 处理一次网络事件。listener 可以修改 event 的 displayText/textColor 影响后续展示。
     */
    boolean onTrafficEvent(DebugNetEvent event);
}
