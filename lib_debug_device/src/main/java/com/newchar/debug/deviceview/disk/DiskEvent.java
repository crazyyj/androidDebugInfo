package com.newchar.debug.deviceview.disk;

final class DiskEvent {
    final long timeMillis;
    final String action;
    final String path;

    DiskEvent(long timeMillis, String action, String path) {
        this.timeMillis = timeMillis;
        this.action = action;
        this.path = path;
    }
}
