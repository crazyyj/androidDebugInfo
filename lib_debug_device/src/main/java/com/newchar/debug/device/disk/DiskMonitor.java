package com.newchar.debug.device.disk;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import com.newchar.debug.utils.HandleWrapper;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DiskMonitor {
    public interface Listener {
        void onDiskSnapshot(DiskSnapshot snapshot);

        void onDiskEvent(String eventText);
    }

    private static final int MSG_SCAN = 1;
    private static final int MSG_EVENT = 2;
    private static final int MAX_EVENT_COUNT = 30;
    private static final SimpleDateFormat EVENT_TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss", Locale.US);

    private final Context appContext;
    private final Handler mainHandler = HandleWrapper.getMainHandler();
    private final Handler workerHandler;
    private final ArrayDeque<DiskEvent> recentEvents = new ArrayDeque<>();
    private final File eventLogFile;
    private final DiskDirectoryObserver directoryObserver;
    private Listener listener;
    private boolean started;

    public DiskMonitor(Context context) {
        appContext = context == null ? null : context.getApplicationContext();
        workerHandler = HandleWrapper.obtainAsyncHandler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what == MSG_SCAN) {
                    scanNow();
                    return true;
                }
                if (msg.what == MSG_EVENT && msg.obj instanceof DiskEvent) {
                    handleEvent((DiskEvent) msg.obj);
                    return true;
                }
                return false;
            }
        });
        eventLogFile = buildEventLogFile(appContext);
        directoryObserver = new DiskDirectoryObserver(new DiskDirectoryObserver.Callback() {
            @Override
            public void onDiskEvent(String action, String path) {
                enqueueEvent(action, path);
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        if (started) {
            refreshAsync();
            return;
        }
        started = true;
        if (appContext != null) {
            directoryObserver.start(DiskRepository.buildScopes(appContext));
        }
        refreshAsync();
    }

    public void stop() {
        started = false;
        directoryObserver.stop();
        workerHandler.removeCallbacksAndMessages(null);
    }

    public void refreshAsync() {
        HandleWrapper.execUIIdleOnce(new Runnable() {
            @Override
            public void run() {
                if (workerHandler.hasMessages(MSG_SCAN)) {
                    return;
                }
                workerHandler.sendEmptyMessage(MSG_SCAN);
            }
        });
    }

    private void enqueueEvent(String action, String path) {
        if (!started) {
            return;
        }
        if (isEventLogPath(path)) {
            return;
        }
        Message msg = Message.obtain(workerHandler, MSG_EVENT, new DiskEvent(System.currentTimeMillis(), action, path));
        workerHandler.sendMessage(msg);
    }

    private void handleEvent(DiskEvent event) {
        synchronized (recentEvents) {
            recentEvents.addFirst(event);
            while (recentEvents.size() > MAX_EVENT_COUNT) {
                recentEvents.removeLast();
            }
        }
        appendEventLog(event);
        final String eventText = formatEvent(event);
        final Listener current = listener;
        if (current != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    current.onDiskEvent(eventText);
                }
            });
        }
        scanNow();
    }

    private void scanNow() {
        if (appContext == null) {
            return;
        }
        List<DiskEvent> events;
        synchronized (recentEvents) {
            events = new ArrayList<>(recentEvents);
        }
        final DiskSnapshot snapshot = DiskRepository.scan(appContext, events, eventLogFile);
        final Listener current = listener;
        if (current != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    current.onDiskSnapshot(snapshot);
                }
            });
        }
    }

    private void appendEventLog(DiskEvent event) {
        if (eventLogFile == null || event == null) {
            return;
        }
        File parent = eventLogFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileWriter writer = null;
        try {
            writer = new FileWriter(eventLogFile, true);
            writer.write(formatEvent(event));
            writer.write('\n');
        } catch (Throwable ignored) {
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static File buildEventLogFile(Context context) {
        if (context == null) {
            return null;
        }
        File dir = new File(context.getFilesDir(), "disk-monitor");
        return new File(dir, "disk-events.log");
    }

    private boolean isEventLogPath(String path) {
        if (eventLogFile == null || path == null) {
            return false;
        }
        String logPath = eventLogFile.getAbsolutePath();
        File parent = eventLogFile.getParentFile();
        String parentPath = parent == null ? null : parent.getAbsolutePath();
        return path.equals(logPath)
                || (parentPath != null && (path.equals(parentPath) || path.startsWith(parentPath + File.separator)));
    }

    private static String formatEvent(DiskEvent event) {
        if (event == null) {
            return "";
        }
        String time = EVENT_TIME_FORMAT.format(new Date(event.timeMillis));
        return time + " " + event.action + " " + event.path;
    }
}
