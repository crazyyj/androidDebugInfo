package com.newchar.debug.net;

/**
 * 解码后可供业务继续加工的载荷模型。
 */
public final class DebugNetPayload {

    private final DebugNetEvent event;

    public DebugNetPayload(DebugNetEvent event) {
        this.event = event;
    }

    public DebugNetEvent getEvent() {
        return event;
    }
}
