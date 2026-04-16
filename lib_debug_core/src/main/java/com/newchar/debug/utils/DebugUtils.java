package com.newchar.debug.utils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.ViewGroup;

import com.newchar.debug.common.utils.ViewUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;

/**
 * @author newChar
 * date 2022/7/8
 * @since 保持Application对象
 * @since 迭代版本，（以及描述）
 */
public class DebugUtils {

    public static final long KB = 1024;
    public static final long MB = 1024 * 1024;
    public static final double GB = 1024 * 1024 * 1024;
    private static final DecimalFormat twoFormat = new DecimalFormat("0.00");

    public static final String FLAG_NEED_FOREGROUND = "NEED_FOREGROUND";

    private static SharedPreferences mKVStore;
    private static final String name_sp_file = "a";
    private static final String key_request_flow_q = "b";

    private static WeakReference<Application> mAppRef;

    public static void attachApp(Application app) {
        if (app != null && (mAppRef == null || null == mAppRef.get())) {
            mAppRef = new WeakReference<>(app);
        }
        mKVStore = app.getSharedPreferences(
                name_sp_file, Context.MODE_PRIVATE
        );
    }

    public static void detachApp() {
        if (mAppRef != null) {
            mAppRef.clear();
            mAppRef = null;
        }
    }

    public static Application app() {
        return mAppRef.get();
    }

    @SuppressLint({"HardwareIds", "MissingPermission"})
    public static String getIMEI(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            String deviceId = tm.getDeviceId();
            if (deviceId != null) {
                return deviceId;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取屏幕宽高
     *
     * @param context context
     * @return 屏幕大小
     */
    public static Size getScreenSize(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return new Size(displayMetrics.widthPixels, displayMetrics.heightPixels);
    }

    /**
     * 获取厂商名
     */
    public static String getDeviceManufacturer() {
        return Build.MANUFACTURER;
    }

    /**
     * 获取产品名
     **/
    public static String getDeviceProduct() {
        return Build.PRODUCT;
    }

    /**
     * 获取手机品牌
     */
    public static String getDeviceBrand() {
        return Build.BRAND;
    }

    /**
     * 获取手机型号
     */
    public static String getDeviceModel() {
        return Build.MODEL;
    }

    /**
     * 获取手机主板名
     */
    public static String getDeviceBoard() {
        return Build.BOARD;
    }

    /**
     * 设备名
     **/
    public static String getDeviceName() {
        return Build.DEVICE;
    }

    /**
     * Fingerprint 信息
     **/
    public static String getDeviceFingerprint() {
        return Build.FINGERPRINT;
    }

    /**
     * 硬件名
     **/
    public static String getDeviceHardwareName() {
        return Build.HARDWARE;
    }

    /**
     * 主机
     **/
    public static String getDeviceHost() {
        return Build.HOST;
    }

    /**
     * 显示ID
     **/
    public static String getDeviceDisplay() {
        return Build.DISPLAY;
    }

    /**
     * ID
     */
    public static String getDeviceId() {
        return Build.ID;
    }

    /**
     * 获取手机用户名
     */
    public static String getDeviceUser() {
        return Build.USER;
    }

    /**
     * 获取手机 硬件序列号
     */
    @SuppressLint({"MissingPermission", "HardwareIds"})
    public static String getDeviceSerial() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Build.getSerial();
        } else {
            return Build.SERIAL;
        }
    }

    /**
     * 处理服务是否需要开启前台服务
     *
     * @param intent 服务接收的 Intent 数据
     * @return true 需要
     */
    public static boolean isNeedForeground(Intent intent) {
        return getDeviceSDK() >= Build.VERSION_CODES.Q
                && intent != null && intent.getBooleanExtra(FLAG_NEED_FOREGROUND, false);
    }

    /**
     * 获取手机Android 系统SDK
     *
     * @return
     */
    public static int getDeviceSDK() {
        return Build.VERSION.SDK_INT;
    }

    /**
     * 获取手机Android 版本
     *
     * @return
     */
    public static String getDeviceAndroidVersion() {
        return Build.VERSION.RELEASE;
    }

    /**
     * 获取当前手机系统语言。
     */
    public static String getDeviceDefaultLanguage() {
        return Locale.getDefault().getLanguage();
    }

    public static boolean copy(Context context, String textForCopy) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData primaryClip = ClipData.newPlainText(context.getPackageName(), textForCopy);
        clipboard.setPrimaryClip(primaryClip);
        if (clipboard.hasPrimaryClip()) {
            ClipData.Item itemAt = clipboard.getPrimaryClip().getItemAt(0);
            return textForCopy != null && textForCopy.contentEquals(itemAt.getText());
        }
        return false;
    }

    /*
     * 构建前台服务通知
     */
    public static Notification buildForegroundNotification(Context context) {
        // 当用户点击通知时，可以打开你的Activity
        Intent notificationIntent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE // Android 12+ 要求设置此flag
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, "1");
            createNotificationChannel(context);
        } else {
            builder = new Notification.Builder(context);
            builder.setSound(null, null); // 前台服务通知通常不需要声音
            builder.setVibrate(new long[0]); // 通常也不需要震动
        }
        builder.setContentIntent(pendingIntent);
        return builder.build();
    }

    /**
     * 创建通知渠道 (适用于 Android 8.0 (API 26) 及更高版本)
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "DebugView is Aive";
            String description = "Channel for My Foreground Service";
            int importance = NotificationManager.IMPORTANCE_LOW; // 前台服务通知通常使用低或默认优先级
            NotificationChannel channel = new NotificationChannel("1", name, importance);
            channel.setDescription(description);
            channel.setSound(null, null); // 前台服务通知通常不需要声音
            channel.enableVibration(false); // 通常也不需要震动

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public static boolean isCanFLow(Context context) {
        return Settings.canDrawOverlays(context) && mKVStore.getBoolean(key_request_flow_q, true);
    }

    public static void requestFlow(Context context) {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * bitmap 压缩图片
     *
     * @param src       原图
     * @param maxWidth  结果的最大宽度
     * @param maxHeight 结果的最大高度
     * @return 压缩后的Bitmap
     */
    public static Bitmap compressImage(Bitmap src, int maxWidth, int maxHeight) {
        Bitmap result = null;
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        BitmapFactory.Options options = new BitmapFactory.Options();
        if (srcWidth < maxWidth && srcHeight < maxHeight) {
            // 全面小于目标结果最大值，保持不变，对采样率做出改变。
//            options.inMutable = true;
            options.inBitmap = src;
        } else if (srcWidth < maxWidth || srcHeight < maxHeight) {
//            options.inMutable = true;
            options.inBitmap = src;
            // 至少有一个大于原图，则把长的按照比例缩短
            float rate = srcWidth / (float) maxWidth;
            //TODO 都大于，根据原图比例，
            // 看 宽度 和 最大宽度的比例，高度和最大高度的比例，
            // 找出较小的比例，把原图某边除以 最大某边的更大比例，作为比例值，用原图的另一个边除以这个比例值得到相应的长度
            Matrix matrix = new Matrix();
            matrix.setScale(rate, rate);
            result = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, false);
        } else {
            result = src;
        }

        // 压缩采样
        int sampleSize = 2;
        while ((srcWidth / sampleSize) > maxWidth || (srcHeight / sampleSize) > maxHeight) {
            sampleSize *= 2;
        }
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_4444;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (result == null) {
            src.compress(Bitmap.CompressFormat.JPEG, 10, outputStream);
        } else {
            result.compress(Bitmap.CompressFormat.JPEG, 10, outputStream);
        }

        byte[] srcByteData = outputStream.toByteArray();
        result = BitmapFactory.decodeByteArray(srcByteData, 0, srcByteData.length, options);
        return result;
    }

    /**
     * 保存bitmap到本地
     *
     * @param src  图片
     * @param path 地址
     * @return 是否保存成功
     */
    public static boolean saveBitmap(Bitmap src, File path) {
        if (src == null || src.isRecycled()) {
            // 图片保存失败
            return false;
        }
        if (path == null) {
            // 路径不合法。
            return false;
        }
        if (path.isDirectory()) {
            if (!path.exists()) {
                path.mkdirs();
            }
            path = new File(path, System.currentTimeMillis() + ".jpg");
        } else {
            File parentFile = path.getParentFile();
            if (parentFile.isDirectory()) {
                if (!parentFile.exists()) {
                    parentFile.mkdirs();
                }
            }
        }
        String fileName = path.getName();
        Bitmap.CompressFormat imageFormat =
                fileName.endsWith(".png") ? Bitmap.CompressFormat.PNG
                        : (fileName.endsWith(".webp") ? Bitmap.CompressFormat.WEBP
                        : Bitmap.CompressFormat.JPEG);
        try (OutputStream saveImageStream
                     = new BufferedOutputStream(new FileOutputStream(path))) {
            return src.compress(imageFormat, 100, saveImageStream);
            // 保存成功。
        } catch (Exception e) {
            // 保存失败
        }
        return false;
    }

    public static <T> boolean hasData(List<T> data) {
        return data != null && !data.isEmpty();
    }

    public static <T> boolean indexEnableForList(List<T> data, int index) {
        return hasData(data) && 0 <= index && index < data.size();
    }

    /**
     * 应用是否处于调试环境
     * SystemProperties.getInt("ro.debuggable", 0) == 1
     *
     * @return true 调试环境
     */
    public static boolean isDebuggable() {
        ApplicationInfo info = app().getApplicationInfo();
        return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) == ApplicationInfo.FLAG_DEBUGGABLE;
    }

    /**
     * 保留两位小数
     *
     * @param number 原数字
     * @return 保留了两位小数后的结果
     */
    public static String keepTwo(double number) {
        return twoFormat.format(number);
    }

    /**
     * 向文件加入byte[],
     * 以一次文件为单位，允许丢失10个事件，缓存数据一次写入，需要提取 BufferedOutputStream 为公共对象，减少IO次数
     */
    public static void appendBytesToFile(byte[] bytes, File file) {
        final int bufferSize = Math.max(bytes.length, 2047);
        final char lineEnd = System.lineSeparator().charAt(0);
        try (OutputStream out = new BufferedOutputStream(
                new FileOutputStream(file, true), bufferSize)) {
            out.write(bytes);
            out.write(lineEnd);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    public static Application getProviderApp(ContentProvider owner) {
        Context appCtx = owner.getContext().getApplicationContext();
        if (appCtx instanceof Application) {
            return ((Application) appCtx);
        }
        return null;
    }

    public static Application hookActivityThreadApp() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method sCurrentActivityThread = activityThread.getMethod("currentApplication");
            Object applicationObject = sCurrentActivityThread.invoke(activityThread);
            if (applicationObject instanceof Application) {
                return ((Application) applicationObject);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
