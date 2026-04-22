package com.newchar.debug.sample;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.newchar.debug.utils.HandleWrapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DiskOperationDemoActivity extends AppCompatActivity {
    private final SimpleDateFormat nameFormat = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US);
    private Handler workerHandler;
    private TextView logView;
    private File externalDemoDir;
    private File internalDemoDir;
    private File lastTouchedFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        workerHandler = HandleWrapper.obtainAsyncHandler(msg -> false);
        externalDemoDir = new File(getExternalFilesDir(null), "disk-demo");
        internalDemoDir = new File(getFilesDir(), "disk-demo");
        setContentView(createContentView());
        ensureDirs();
        appendLog("磁盘 Demo 已启动");
        appendLog("外部目录: " + externalDemoDir.getAbsolutePath());
        appendLog("内部目录: " + internalDemoDir.getAbsolutePath());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (workerHandler != null) {
            workerHandler.removeCallbacksAndMessages(null);
            workerHandler = null;
        }
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        container.setPadding(padding, padding, padding, padding);
        scrollView.addView(container, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        container.addView(button("创建文本文件", v -> runIo("创建文本文件", () -> {
            File file = new File(externalDemoDir, "note_" + timeName() + ".txt");
            writeText(file, "hello disk monitor " + System.currentTimeMillis());
            lastTouchedFile = file;
            return file;
        })));
        container.addView(button("创建图片文件(.jpg)", v -> runIo("创建图片文件", () -> {
            File file = new File(externalDemoDir, "image_" + timeName() + ".jpg");
            writeBytes(file, 96 * 1024, (byte) 0x7F);
            lastTouchedFile = file;
            return file;
        })));
        container.addView(button("创建 APK 文件(.apk)", v -> runIo("创建 APK 文件", () -> {
            File file = new File(externalDemoDir, "demo_" + timeName() + ".apk");
            writeBytes(file, 256 * 1024, (byte) 0x5A);
            lastTouchedFile = file;
            return file;
        })));
        container.addView(button("创建超过 10M 文件", v -> runIo("创建超过 10M 文件", () -> {
            File file = new File(externalDemoDir, "large_" + timeName() + ".bin");
            writeBytes(file, 11 * 1024 * 1024, (byte) 0x33);
            lastTouchedFile = file;
            return file;
        })));
        container.addView(button("写入内部目录文件", v -> runIo("写入内部目录文件", () -> {
            File file = new File(internalDemoDir, "internal_" + timeName() + ".txt");
            writeText(file, "internal file " + System.currentTimeMillis());
            lastTouchedFile = file;
            return file;
        })));
        container.addView(button("追加修改最近文件", v -> runIo("追加修改最近文件", () -> {
            File file = resolveLastFile();
            appendText(file, "\nappend " + System.currentTimeMillis());
            lastTouchedFile = file;
            return file;
        })));
        container.addView(button("删除最近文件", v -> runIo("删除最近文件", () -> {
            File file = resolveLastFile();
            if (file.exists() && !file.delete()) {
                throw new IllegalStateException("delete failed: " + file.getAbsolutePath());
            }
            lastTouchedFile = null;
            return file;
        })));
        container.addView(button("清空 Demo 目录", v -> runIo("清空 Demo 目录", () -> {
            deleteChildren(externalDemoDir);
            deleteChildren(internalDemoDir);
            lastTouchedFile = null;
            return externalDemoDir;
        })));

        logView = new TextView(this);
        logView.setTextColor(0xFF444444);
        logView.setTextSize(13f);
        logView.setPadding(0, dp(12), 0, 0);
        container.addView(logView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private void runIo(String label, IoAction action) {
        if (workerHandler == null) {
            return;
        }
        appendLog("开始: " + label);
        workerHandler.post(() -> {
            try {
                ensureDirs();
                File file = action.run();
                runOnUiThread(() -> appendLog("完成: " + label + " -> " + file.getAbsolutePath()));
            } catch (Throwable t) {
                runOnUiThread(() -> appendLog("失败: " + label + " -> " + t.getMessage()));
            }
        });
    }

    private void ensureDirs() {
        if (externalDemoDir != null && !externalDemoDir.exists()) {
            externalDemoDir.mkdirs();
        }
        if (internalDemoDir != null && !internalDemoDir.exists()) {
            internalDemoDir.mkdirs();
        }
    }

    private File resolveLastFile() {
        if (lastTouchedFile != null) {
            return lastTouchedFile;
        }
        File latest = latestFile(externalDemoDir);
        if (latest == null) {
            latest = latestFile(internalDemoDir);
        }
        if (latest == null) {
            latest = new File(externalDemoDir, "note_" + timeName() + ".txt");
        }
        lastTouchedFile = latest;
        return latest;
    }

    private File latestFile(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return null;
        }
        File latest = null;
        for (File file : files) {
            if (file != null && file.isFile() && (latest == null || file.lastModified() > latest.lastModified())) {
                latest = file;
            }
        }
        return latest;
    }

    private void writeText(File file, String text) throws Exception {
        FileWriter writer = new FileWriter(file, false);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    private void appendText(File file, String text) throws Exception {
        FileWriter writer = new FileWriter(file, true);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    private void writeBytes(File file, int byteCount, byte value) throws Exception {
        FileOutputStream out = new FileOutputStream(file, false);
        try {
            byte[] buffer = new byte[64 * 1024];
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] = value;
            }
            int remaining = byteCount;
            while (remaining > 0) {
                int count = Math.min(buffer.length, remaining);
                out.write(buffer, 0, count);
                remaining -= count;
            }
        } finally {
            out.close();
        }
    }

    private void deleteChildren(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file == null) {
                continue;
            }
            if (file.isDirectory()) {
                deleteChildren(file);
            }
            file.delete();
        }
    }

    private String timeName() {
        return nameFormat.format(new Date());
    }

    private void appendLog(String line) {
        if (logView == null) {
            return;
        }
        CharSequence old = logView.getText();
        logView.setText((old == null || old.length() == 0) ? line : old + "\n" + line);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface IoAction {
        File run() throws Exception;
    }
}
