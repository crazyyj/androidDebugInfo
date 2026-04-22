package com.newchar.debug.deviceview.disk;

import android.os.FileObserver;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DiskDirectoryObserver {
    interface Callback {
        void onDiskEvent(String action, String path);
    }

    private static final int WATCH_MASK = FileObserver.CREATE
            | FileObserver.DELETE
            | FileObserver.MOVED_FROM
            | FileObserver.MOVED_TO
            | FileObserver.MODIFY
            | FileObserver.CLOSE_WRITE
            | FileObserver.DELETE_SELF
            | FileObserver.MOVE_SELF;

    private final Callback callback;
    private final Map<String, FileObserver> observers = new HashMap<>();

    DiskDirectoryObserver(Callback callback) {
        this.callback = callback;
    }

    synchronized void start(List<DiskScope> scopes) {
        stop();
        if (scopes == null) {
            return;
        }
        for (DiskScope scope : scopes) {
            watchRecursively(scope == null ? null : scope.dir);
        }
    }

    synchronized void stop() {
        for (FileObserver observer : observers.values()) {
            if (observer != null) {
                observer.stopWatching();
            }
        }
        observers.clear();
    }

    private void watchRecursively(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        watchDir(dir);
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child != null && child.isDirectory()) {
                watchRecursively(child);
            }
        }
    }

    private void watchDir(final File dir) {
        final String root = dir.getAbsolutePath();
        if (observers.containsKey(root)) {
            return;
        }
        FileObserver observer = new FileObserver(root, WATCH_MASK) {
            @Override
            public void onEvent(int event, String path) {
                if (path == null) {
                    path = "";
                }
                String fullPath = new File(root, path).getAbsolutePath();
                String action = actionName(event);
                if ((event & (FileObserver.CREATE | FileObserver.MOVED_TO)) != 0) {
                    File created = new File(fullPath);
                    if (created.isDirectory()) {
                        synchronized (DiskDirectoryObserver.this) {
                            watchRecursively(created);
                        }
                    }
                }
                if (callback != null) {
                    callback.onDiskEvent(action, fullPath);
                }
            }
        };
        observer.startWatching();
        observers.put(root, observer);
    }

    private static String actionName(int event) {
        int masked = event & FileObserver.ALL_EVENTS;
        if ((masked & FileObserver.CREATE) != 0) return "CREATE";
        if ((masked & FileObserver.DELETE) != 0) return "DELETE";
        if ((masked & FileObserver.MOVED_FROM) != 0) return "MOVED_FROM";
        if ((masked & FileObserver.MOVED_TO) != 0) return "MOVED_TO";
        if ((masked & FileObserver.CLOSE_WRITE) != 0) return "CLOSE_WRITE";
        if ((masked & FileObserver.MODIFY) != 0) return "MODIFY";
        if ((masked & FileObserver.DELETE_SELF) != 0) return "DELETE_SELF";
        if ((masked & FileObserver.MOVE_SELF) != 0) return "MOVE_SELF";
        return "EVENT_" + masked;
    }
}
