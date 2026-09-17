package com.newchar.probe.wifi;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Base64;

import java.util.List;

/** 以 shell UID 运行的 WiFi 控制入口。 */
public final class WifiToolMain {

    /** 执行 WiFi 指令，并将可解析结果输出到标准输出。 */
    public static void main(String[] args) {
        try {
            Context context = systemContext();
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                throw new IllegalStateException("WifiManager 不可用");
            }
            dispatch(wifiManager, args);
        } catch (Throwable throwable) {
            System.out.println("FATAL|" + throwable.getClass().getName() + ": " + throwable.getMessage());
            Throwable cause = throwable.getCause();
            while (cause != null) {
                System.out.println("CAUSE|" + cause.getClass().getName() + ": " + cause.getMessage());
                cause = cause.getCause();
            }
            throwable.printStackTrace(System.out);
        }
        System.exit(0);
    }

    /** 按第一段参数分发具体 WiFi 操作。 */
    private static void dispatch(WifiManager manager, String[] args) {
        String action = args.length == 0 ? "" : args[0];
        if ("enable".equals(action) || "disable".equals(action)) {
            printResult(manager.setWifiEnabled("enable".equals(action)));
        } else if ("scan".equals(action)) {
            printScanResults(manager);
        } else if ("status".equals(action)) {
            printStatus(manager);
        } else if ("connect".equals(action)) {
            connect(manager, valueAt(args, 1), valueAt(args, 2));
        } else if ("connect-saved".equals(action)) {
            connectSaved(manager, valueAt(args, 1));
        } else if ("disconnect".equals(action)) {
            printResult(manager.disconnect());
        } else if ("forget".equals(action)) {
            forget(manager, valueAt(args, 1));
        } else {
            throw new IllegalArgumentException("未知 WiFi 操作: " + action);
        }
    }

    /** 输出当前 WiFi 开关及已连接 SSID。 */
    private static void printStatus(WifiManager manager) {
        WifiInfo info = manager.getConnectionInfo();
        String ssid = info == null ? "" : normalizeSsid(info.getSSID());
        System.out.println("STATUS|" + manager.isWifiEnabled() + "|" + encode(ssid));
    }

    /** 发起扫描并输出扫描结果。 */
    private static void printScanResults(WifiManager manager) {
        manager.startScan();
        List<ScanResult> results = manager.getScanResults();
        if (results == null) {
            System.out.println("RESULT|false|无可用扫描结果");
            return;
        }
        for (ScanResult result : results) {
            System.out.println("SCAN|" + encode(result.SSID) + "|" + encode(result.BSSID)
                    + "|" + result.level + "|" + encode(result.capabilities));
        }
        System.out.println("RESULT|true|" + results.size());
    }

    /** 使用传统配置接口连接指定网络，供 Android 10 及以下版本使用。 */
    private static void connect(WifiManager manager, String ssid, String password) {
        if (ssid.isEmpty()) {
            throw new IllegalArgumentException("SSID 为空");
        }
        WifiConfiguration configuration = new WifiConfiguration();
        configuration.SSID = quote(ssid);
        if (password.isEmpty()) {
            configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else {
            configuration.preSharedKey = quote(password);
        }
        int networkId = manager.addNetwork(configuration);
        boolean success = networkId >= 0 && manager.enableNetwork(networkId, true) && manager.reconnect();
        System.out.println("RESULT|" + success + "|" + (success ? "已请求连接" : "连接请求失败"));
    }

    /** 删除本机保存的指定 WiFi 配置。 */
    private static void forget(WifiManager manager, String ssid) {
        List<WifiConfiguration> configs = manager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                if (ssid.equals(normalizeSsid(config.SSID))) {
                    printResult(manager.removeNetwork(config.networkId) && manager.saveConfiguration());
                    return;
                }
            }
        }
        System.out.println("RESULT|false|未找到已保存的网络");
    }

    /** 连接已由系统保存的 WiFi 配置，不需要读取或存储密码。 */
    private static void connectSaved(WifiManager manager, String ssid) {
        List<WifiConfiguration> configs = manager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                if (ssid.equals(normalizeSsid(config.SSID))) {
                    printResult(manager.enableNetwork(config.networkId, true) && manager.reconnect());
                    return;
                }
            }
        }
        System.out.println("RESULT|false|未找到已保存的网络");
    }

    /** 返回参数指定位置的文本，缺失时返回空字符串。 */
    private static String valueAt(String[] args, int index) {
        return index < args.length ? args[index] : "";
    }

    /** 将可含分隔符的文本编码为 Base64，供 PC 端稳定解析。 */
    private static String encode(String value) {
        return Base64.encodeToString((value == null ? "" : value).getBytes(), Base64.NO_WRAP);
    }

    /** 去除系统返回 SSID 两侧的双引号。 */
    private static String normalizeSsid(String value) {
        if (value == null || "<unknown ssid>".equals(value)) {
            return "";
        }
        return value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    /** 对传统 WiFi 配置字段添加双引号。 */
    private static String quote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    /** 输出统一的操作结果协议。 */
    private static void printResult(boolean success) {
        System.out.println("RESULT|" + success + "|" + (success ? "操作完成" : "操作失败"));
    }

    /** 通过隐藏 API 获取系统上下文。 */
    private static Context systemContext() throws Exception {
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getDeclaredMethod("systemMain").invoke(null);
        return (Context) activityThread.getDeclaredMethod("getSystemContext").invoke(thread);
    }

    private WifiToolMain() {
    }
}
