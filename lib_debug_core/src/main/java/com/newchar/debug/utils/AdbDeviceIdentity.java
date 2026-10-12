package com.newchar.debug.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 保存 PC 经 ADB 下发的设备序列号，并将其作为跨链路的稳定设备标识。
 */
public final class AdbDeviceIdentity {

    public static final String ACTION_STORE_ADB_SERIAL = "com.newchar.debug.action.STORE_ADB_SERIAL";
    public static final String EXTRA_ADB_SERIAL = "adb_serial";
    private static final String TAG = "AdbDeviceIdentity";
    private static final String PREFERENCES = "pc_device_identity";
    private static final String KEY_ADB_SERIAL = "adb_serial";
    private static final String FILE_NAME = "adb_serial.txt";

    private AdbDeviceIdentity() {
    }

    /** 保存来自 PC 的 ADB 序列号，并尽力写入用户指定的 Downloads 目录。 */
    public static void saveFromAdb(Context context, String adbSerial) {
        if (context == null || !isValidSerial(adbSerial)) {
            return;
        }
        String normalized = adbSerial.trim();
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(KEY_ADB_SERIAL, normalized).apply();
        writeDownloadCopy(context, normalized);
    }

    /** 返回用于 PC 协议和设备卡片归并的稳定标识；首次未下发时兼容旧实现。 */
    public static String resolve(Context context) {
        if (context != null) {
            SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            String serial = preferences.getString(KEY_ADB_SERIAL, "");
            if (isValidSerial(serial)) {
                return serial.trim();
            }
        }
        return DebugUtils.getDeviceId();
    }

    /** 判断序列号是否可安全写入以竖线分隔的 PC 通信协议。 */
    private static boolean isValidSerial(String value) {
        return value != null && !value.trim().isEmpty() && value.length() <= 256 && value.indexOf('|') < 0;
    }

    /** 按系统存储模型将序列号写入 Downloads/app/.型号/adb_serial.txt。 */
    private static void writeDownloadCopy(Context context, String serial) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeModernDownload(context, serial);
            } else {
                writeLegacyDownload(serial);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "写入 Downloads 设备标识失败", throwable);
        }
    }

    /** 使用 MediaStore 写入 Android 10 及以上的公共 Downloads 目录。 */
    private static void writeModernDownload(Context context, String serial) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/app/." + safeModelName() + "/";
        Uri target = findModernDownload(resolver, relativePath);
        if (target == null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);
            target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        }
        if (target == null) {
            throw new IOException("无法创建 Downloads 标识文件");
        }
        try (OutputStream output = resolver.openOutputStream(target, "wt")) {
            if (output == null) throw new IOException("无法打开 Downloads 标识文件");
            output.write(serial.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 查询 App 先前写入的标识文件，避免每次 ADB 连接都生成重复文件。 */
    private static Uri findModernDownload(ContentResolver resolver, String relativePath) {
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                + MediaStore.Downloads.RELATIVE_PATH + "=?";
        try (Cursor cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Downloads._ID}, selection,
                new String[]{FILE_NAME, relativePath}, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, String.valueOf(id));
            }
        }
        return null;
    }

    /** 使用旧版文件 API 写入 Android 9 及以下系统。 */
    private static void writeLegacyDownload(String serial) throws IOException {
        File download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File directory = new File(new File(download, "app"), "." + safeModelName());
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("无法创建 Downloads 标识目录");
        }
        try (FileOutputStream output = new FileOutputStream(new File(directory, FILE_NAME), false)) {
            output.write(serial.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 将型号收敛为目录安全名称，避免厂商字符造成跨文件系统路径歧义。 */
    private static String safeModelName() {
        String model = Build.MODEL == null ? "unknown" : Build.MODEL.trim();
        String safe = model.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return safe.isEmpty() ? "unknown" : safe;
    }
}