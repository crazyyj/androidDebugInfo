package com.newchar.debug.deviceview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.newchar.debug.utils.DebugUtils;
import com.newchar.debug.deviceview.bean.DevicesInfo;

import java.util.Arrays;
import java.util.Locale;

public final class DeviceStaticInfoCollector {

    private DeviceStaticInfoCollector() {
    }

    static DevicesInfo collect(Context context) {
        DevicesInfo info = DevicesInfo.getInstance();
        info.setManufacturer(DebugUtils.getDeviceManufacturer());
        info.setBrand(DebugUtils.getDeviceBrand());
        info.setProduct(DebugUtils.getDeviceProduct());
        info.setModel(DebugUtils.getDeviceModel());
        info.setBoard(DebugUtils.getDeviceBoard());
        info.setDeviceName(DebugUtils.getDeviceName());
        info.setHardware(DebugUtils.getDeviceHardwareName());
        info.setDisplay(DebugUtils.getDeviceDisplay());
        info.setFingerprint(DebugUtils.getDeviceFingerprint());
        info.setHost(DebugUtils.getDeviceHost());
        info.setBuildId(DebugUtils.getDeviceId());
        info.setBuildUser(DebugUtils.getDeviceUser());
        info.setBuildType(Build.TYPE);
        info.setBuildTags(Build.TAGS);
        info.setBootloader(Build.BOOTLOADER);
        info.setAndroidVersion(DebugUtils.getDeviceAndroidVersion());
        info.setSDKVersion(DebugUtils.getDeviceSDK());
        info.setSupportedAbis(joinAbis());
        info.setLanguage(Locale.getDefault().toLanguageTag());
        info.setSecurityPatch(readSecurityPatch());
        info.setAndroidId(readAndroidId(context));
        info.setSerial(readSerial(context));
        info.setImei(readImei(context));
        return info;
    }

    public static CharSequence buildDisplayText(Context context) {
        DevicesInfo info = collect(context);
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "厂商", info.getManufacturer());
        appendLine(builder, "品牌", info.getBrand());
        appendLine(builder, "产品名", info.getProduct());
        appendLine(builder, "设备名", info.getDeviceName());
        appendLine(builder, "设备型号", info.getModel());
        appendLine(builder, "主板", info.getBoard());
        appendLine(builder, "硬件", info.getHardware());
        appendLine(builder, "Display", info.getDisplay());
        appendLine(builder, "Fingerprint", info.getFingerprint());
        appendLine(builder, "Host", info.getHost());
        appendLine(builder, "Build ID", info.getBuildId());
        appendLine(builder, "Build User", info.getBuildUser());
        appendLine(builder, "Build Type", info.getBuildType());
        appendLine(builder, "Build Tags", info.getBuildTags());
        appendLine(builder, "Bootloader", info.getBootloader());
        appendLine(builder, "Android 版本", info.getAndroidVersion());
        appendLine(builder, "Android SDK", String.valueOf(info.getSDKVersion()));
        appendLine(builder, "安全补丁", info.getSecurityPatch());
        appendLine(builder, "支持 ABI", info.getSupportedAbis());
        appendLine(builder, "系统语言", info.getLanguage());
        appendLine(builder, "Android ID", info.getAndroidId());
        appendLine(builder, "Serial", info.getSerial());
        appendLine(builder, "IMEI", info.getImei());
        return builder.toString();
    }

    private static void appendLine(StringBuilder builder, String label, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(label).append(" : ").append(value);
    }

    private static String joinAbis() {
        String[] abis = Build.SUPPORTED_ABIS;
        return abis == null || abis.length == 0 ? null : Arrays.toString(abis);
    }

    private static String readSecurityPatch() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? Build.VERSION.SECURITY_PATCH : null;
    }

    private static String readAndroidId(Context context) {
        if (context == null) {
            return null;
        }
        try {
            return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressLint({"HardwareIds", "MissingPermission"})
    private static String readSerial(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return null;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!hasPermission(context, Manifest.permission.READ_PHONE_STATE)) {
                    return null;
                }
                return Build.getSerial();
            }
            return Build.SERIAL;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressLint({"HardwareIds", "MissingPermission"})
    private static String readImei(Context context) {
        if (context == null || !hasPermission(context, Manifest.permission.READ_PHONE_STATE)) {
            return null;
        }
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null || !packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return null;
        }
        try {
            TelephonyManager telephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager == null) {
                return null;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return telephonyManager.getImei();
            }
            return telephonyManager.getDeviceId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasPermission(Context context, String permission) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return context.getPackageManager().checkPermission(permission, context.getPackageName())
                    == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }
}
