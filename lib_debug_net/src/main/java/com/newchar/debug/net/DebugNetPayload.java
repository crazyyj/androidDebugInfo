package com.newchar.debug.net;

public class DebugNetPayload {

    private DebugNetEvent event;

    public DebugNetPayload(DebugNetEvent event) {
        this.event = event;
    }

    public DebugNetEvent getEvent() {
        return event;
    }

    public void setEvent(DebugNetEvent event) {
        this.event = event;
    }
}
