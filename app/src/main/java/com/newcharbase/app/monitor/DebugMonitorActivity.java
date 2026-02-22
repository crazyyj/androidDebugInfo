package com.newcharbase.app.monitor;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.newchar.monitor.jvmti.DebugStackMotion;
import com.newchar.monitor.jvmti.DebugStackMotionAgent;
import com.newchar.monitor.jvmti.DebugStackMotionCallback;
import com.newchar.monitor.plugin.MethodFieldMonitorPlugin;
import com.newchar.monitor.plugin.PageTaskTopPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class DebugMonitorActivity extends AppCompatActivity {
    private static final long FIELD_UPDATE_INTERVAL_MS = 2000L;

    private final List<String> eventLogs = new ArrayList<>();
    private final MonitorData monitorData = new MonitorData();
    private final Runnable fieldChangeTask = new Runnable() {
        @Override
        public void run() {
            applyFieldChanges();
            if (handler != null) {
                handler.postDelayed(this, FIELD_UPDATE_INTERVAL_MS);
            }
        }
    };
    private ArrayAdapter<String> adapter;
    private Handler handler;
    private int monitorCount = 0;
    private String monitorState = "init";
    private static long sMonitorVersion = 0L;

    /**
     * 初始化调试监控界面。
     *
     * @param savedInstanceState 状态
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ListView listView = new ListView(this);
        setContentView(listView);
        handler = new Handler(Looper.getMainLooper());
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, eventLogs);
        listView.setAdapter(adapter);

        PageTaskTopPlugin.clearWatchFields();
        PageTaskTopPlugin.addWatchField(DebugMonitorActivity.class, "monitorCount");
        PageTaskTopPlugin.addWatchField(DebugMonitorActivity.class, "monitorState");
        PageTaskTopPlugin.addWatchField(DebugMonitorActivity.class, "sMonitorVersion");

        MethodFieldMonitorPlugin.addWatchClassPrefix("com.newcharbase.app.monitor");
        MethodFieldMonitorPlugin.addWatchMethod(DebugMonitorActivity.class, "onCreate");
        MethodFieldMonitorPlugin.addWatchMethod(DebugMonitorActivity.class, "applyFieldChanges");
        MethodFieldMonitorPlugin.addWatchField(DebugMonitorActivity.class, "monitorCount");
        MethodFieldMonitorPlugin.addWatchField(DebugMonitorActivity.class, "monitorState");
        MethodFieldMonitorPlugin.addWatchField(DebugMonitorActivity.class, "sMonitorVersion");
        MethodFieldMonitorPlugin.addWatchField(MonitorData.class, "totalCost");
        MethodFieldMonitorPlugin.addWatchField(MonitorData.class, "enable");
        MethodFieldMonitorPlugin.addWatchField(MonitorData.class, "lastTag");

        DebugStackMotionAgent.startAgent();
        DebugStackMotionAgent.setFieldModificationEnabled(true);
        DebugStackMotion.setCallback(new DebugStackMotionCallback() {
            @Override
            public void onMethodVisit(Method method, boolean isEnter, Throwable error) {
                // 当前仅开启 METHOD_ENTRY 回调，这里预留即可。
            }

            @Override
            public void onVariableVisit(Field field, boolean isSet, Throwable error) {
                if (!isMonitoredField(field)) {
                    return;
                }
                String state = isSet ? "Set" : "Get";
                String owner = field.getDeclaringClass().getSimpleName();
                String value = readFieldValue(field);
                Log.e("AAA","Var" + state + ": " + owner + "#" + field.getName() + " = " + value);
            }
        });
        startFieldChangeLoop();
        // 示例注册监控方法
        // DebugStackMotion.registerClassAndMethod(YourClass.class, "yourMethod");
    }

    private void startFieldChangeLoop() {
        applyFieldChanges();
        handler.postDelayed(fieldChangeTask, FIELD_UPDATE_INTERVAL_MS);
    }

    private void applyFieldChanges() {
        monitorCount += 1;
        monitorState = "tick_" + monitorCount;
        sMonitorVersion += 1;
        monitorData.totalCost += 100;
        monitorData.enable = !monitorData.enable;
        monitorData.lastTag = "tag_" + monitorCount;
    }

    private boolean isMonitoredField(Field field) {
        Class<?> owner = field.getDeclaringClass();
        String name = field.getName();
        if (owner == DebugMonitorActivity.class) {
            return "monitorCount".equals(name)
                    || "monitorState".equals(name)
                    || "sMonitorVersion".equals(name);
        }
        if (owner == MonitorData.class) {
            return "totalCost".equals(name)
                    || "enable".equals(name)
                    || "lastTag".equals(name);
        }
        return false;
    }

    private String readFieldValue(Field field) {
        try {
            field.setAccessible(true);
            Class<?> owner = field.getDeclaringClass();
            if (owner == DebugMonitorActivity.class) {
                Object target = Modifier.isStatic(field.getModifiers()) ? null : this;
                return String.valueOf(field.get(target));
            }
            if (owner == MonitorData.class) {
                return String.valueOf(field.get(monitorData));
            }
        } catch (Exception ignored) {
        }
        return "<unknown>";
    }

    /**
     * 添加日志并刷新列表。
     *
     * @param log 日志
     */
    private void addLog(String log) {
        handler.post(() -> {
            eventLogs.add(0, log);
            adapter.notifyDataSetChanged();
        });
    }

    /**
     * 释放资源。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacks(fieldChangeTask);
        }
        // 仅释放当前页面回调，避免影响全局监控插件生命周期。
        DebugStackMotion.setCallback(null);
    }

    private static final class MonitorData {
        int totalCost;
        boolean enable;
        String lastTag;
    }
}
