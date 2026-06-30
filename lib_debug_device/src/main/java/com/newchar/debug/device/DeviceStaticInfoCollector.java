package com.newchar.debug.device;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.newchar.debug.utils.DebugUtils;
import com.newchar.debug.device.bean.DevicesInfo;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public final class DeviceStaticInfoCollector {

    private static final Object CACHE_LOCK = new Object();
    private static volatile DevicesInfo sCached;

    private DeviceStaticInfoCollector() {
    }

    /**
     * 收集设备静态信息。首次调用后会缓存结果，后续直接返回缓存实例。
     * Context 为空时返回空对象，避免调用方 NPE。
     */
    static DevicesInfo collect(Context context) {
        if (context == null) {
            return DevicesInfo.builder().build();
        }
        DevicesInfo cached = sCached;
        if (cached != null) {
            return cached;
        }
        synchronized (CACHE_LOCK) {
            if (sCached != null) {
                return sCached;
            }
            sCached = build(context);
            return sCached;
        }
    }

    public static CharSequence buildDisplayText(Context context) {
        DevicesInfo info = collect(context);
        StringBuilder builder = new StringBuilder(512);
        for (Map.Entry<String, String> entry : info.toMap().entrySet()) {
            appendLine(builder, entry.getKey(), entry.getValue());
        }
        return builder.toString();
    }

    private static DevicesInfo build(Context context) {
        return DevicesInfo.builder()
                .manufacturer(DebugUtils.getDeviceManufacturer())
                .brand(DebugUtils.getDeviceBrand())
                .product(DebugUtils.getDeviceProduct())
                .model(DebugUtils.getDeviceModel())
                .board(DebugUtils.getDeviceBoard())
                .deviceName(DebugUtils.getDeviceName())
                .hardware(DebugUtils.getDeviceHardwareName())
                .display(DebugUtils.getDeviceDisplay())
                .fingerprint(DebugUtils.getDeviceFingerprint())
                .host(DebugUtils.getDeviceHost())
                .buildId(DebugUtils.getDeviceId())
                .buildUser(DebugUtils.getDeviceUser())
                .buildType(Build.TYPE)
                .buildTags(Build.TAGS)
                .bootloader(Build.BOOTLOADER)
                .androidVersion(DebugUtils.getDeviceAndroidVersion())
                .sdkVersion(DebugUtils.getDeviceSDK())
                .supportedAbis(joinAbis())
                .language(Locale.getDefault().toLanguageTag())
                .securityPatch(readSecurityPatch())
                .androidId(readAndroidId(context))
                .serial(readSerial(context))
                .imei(readImei(context))
                .build();
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
        } catch (SecurityException ignored) {
            return null;
        }
    }

    @SuppressLint({"HardwareIds", "MissingPermission"})
    private static String readSerial(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!hasPermission(context, Manifest.permission.READ_PHONE_STATE)) {
                    return "无权限";
                }
                return Build.getSerial();
            }
            return Build.SERIAL;
        } catch (SecurityException e) {
            return "权限受限";
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressLint({"HardwareIds", "MissingPermission"})
    private static String readImei(Context context) {
        if (context == null) {
            return null;
        }
        if (!hasPermission(context, Manifest.permission.READ_PHONE_STATE)) {
            return "无权限";
        }
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null || !packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return "不支持";
        }
        try {
            TelephonyManager telephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager == null) {
                return null;
            }
            String result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result = telephonyManager.getImei();
            } else {
                result = telephonyManager.getDeviceId();
            }
            return result;
        } catch (SecurityException e) {
            return "权限受限";
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
