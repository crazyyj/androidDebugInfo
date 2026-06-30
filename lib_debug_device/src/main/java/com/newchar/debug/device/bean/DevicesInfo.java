package com.newchar.debug.device.bean;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author newChar
 * date 2023/1/8
 * @since 设备信息（不可变值对象）
 */
public final class DevicesInfo {

    public static final String KEY_MANUFACTURER = "厂商";
    public static final String KEY_BRAND = "品牌";
    public static final String KEY_PRODUCT = "产品名";
    public static final String KEY_DEVICE_NAME = "设备名";
    public static final String KEY_MODEL = "设备型号";
    public static final String KEY_BOARD = "主板";
    public static final String KEY_HARDWARE = "硬件";
    public static final String KEY_DISPLAY = "Display";
    public static final String KEY_FINGERPRINT = "Fingerprint";
    public static final String KEY_HOST = "Host";
    public static final String KEY_BUILD_ID = "Build-ID";
    public static final String KEY_BUILD_USER = "Build-User";
    public static final String KEY_BUILD_TYPE = "Build-Type";
    public static final String KEY_BUILD_TAGS = "Build-Tags";
    public static final String KEY_BOOTLOADER = "Bootloader";
    public static final String KEY_SDK_VERSION = "Android-SDK";
    public static final String KEY_ANDROID_VERSION = "Android版本";
    public static final String KEY_SECURITY_PATCH = "安全补丁";
    public static final String KEY_SUPPORTED_ABIS = "支持ABI";
    public static final String KEY_LANGUAGE = "系统语言";
    public static final String KEY_ANDROID_ID = "AndroidId";
    public static final String KEY_SERIAL = "Serial";
    public static final String KEY_IMEI = "IMEI";

    private final String board;
    private final String brand;
    private final String manufacturer;
    private final String product;
    private final String deviceName;
    private final String androidVersion;
    private final String securityPatch;
    private final String hardware;
    private final int sdkVersion;
    private final String model;
    private final String display;
    private final String fingerprint;
    private final String host;
    private final String buildId;
    private final String buildUser;
    private final String buildType;
    private final String buildTags;
    private final String bootloader;
    private final String supportedAbis;
    private final String language;
    private final String androidId;
    private final String serial;
    private final String imei;

    private DevicesInfo(Builder builder) {
        this.board = builder.board;
        this.brand = builder.brand;
        this.manufacturer = builder.manufacturer;
        this.product = builder.product;
        this.deviceName = builder.deviceName;
        this.androidVersion = builder.androidVersion;
        this.securityPatch = builder.securityPatch;
        this.hardware = builder.hardware;
        this.sdkVersion = builder.sdkVersion;
        this.model = builder.model;
        this.display = builder.display;
        this.fingerprint = builder.fingerprint;
        this.host = builder.host;
        this.buildId = builder.buildId;
        this.buildUser = builder.buildUser;
        this.buildType = builder.buildType;
        this.buildTags = builder.buildTags;
        this.bootloader = builder.bootloader;
        this.supportedAbis = builder.supportedAbis;
        this.language = builder.language;
        this.androidId = builder.androidId;
        this.serial = builder.serial;
        this.imei = builder.imei;
    }

    public String getBrand() {
        return brand;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getProduct() {
        return product;
    }

    public String getModel() {
        return model;
    }

    public String getBoard() {
        return board;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getHardware() {
        return hardware;
    }

    public String getAndroidVersion() {
        return androidVersion;
    }

    public String getSecurityPatch() {
        return securityPatch;
    }

    public int getSDKVersion() {
        return sdkVersion;
    }

    public String getDisplay() {
        return display;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getHost() {
        return host;
    }

    public String getBuildId() {
        return buildId;
    }

    public String getBuildUser() {
        return buildUser;
    }

    public String getBuildType() {
        return buildType;
    }

    public String getBuildTags() {
        return buildTags;
    }

    public String getBootloader() {
        return bootloader;
    }

    public String getSupportedAbis() {
        return supportedAbis;
    }

    public String getLanguage() {
        return language;
    }

    public String getAndroidId() {
        return androidId;
    }

    public String getSerial() {
        return serial;
    }

    public String getImei() {
        return imei;
    }

    /**
     * 返回按固定顺序组织的键值对，JSON 与 UI 展示都基于此 Map，避免字段列表重复维护。
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        putIfPresent(map, KEY_MANUFACTURER, manufacturer);
        putIfPresent(map, KEY_BRAND, brand);
        putIfPresent(map, KEY_PRODUCT, product);
        putIfPresent(map, KEY_DEVICE_NAME, deviceName);
        putIfPresent(map, KEY_MODEL, model);
        putIfPresent(map, KEY_BOARD, board);
        putIfPresent(map, KEY_HARDWARE, hardware);
        putIfPresent(map, KEY_DISPLAY, display);
        putIfPresent(map, KEY_FINGERPRINT, fingerprint);
        putIfPresent(map, KEY_HOST, host);
        putIfPresent(map, KEY_BUILD_ID, buildId);
        putIfPresent(map, KEY_BUILD_USER, buildUser);
        putIfPresent(map, KEY_BUILD_TYPE, buildType);
        putIfPresent(map, KEY_BUILD_TAGS, buildTags);
        putIfPresent(map, KEY_BOOTLOADER, bootloader);
        if (sdkVersion > 0) {
            map.put(KEY_SDK_VERSION, String.valueOf(sdkVersion));
        }
        putIfPresent(map, KEY_ANDROID_VERSION, androidVersion);
        putIfPresent(map, KEY_SECURITY_PATCH, securityPatch);
        putIfPresent(map, KEY_SUPPORTED_ABIS, supportedAbis);
        putIfPresent(map, KEY_LANGUAGE, language);
        putIfPresent(map, KEY_ANDROID_ID, androidId);
        putIfPresent(map, KEY_SERIAL, serial);
        putIfPresent(map, KEY_IMEI, imei);
        return map;
    }

    /**
     * 手动拼接 JSON，不依赖 org.json，减少对象分配。
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : toMap().entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":");
            sb.append('"').append(escapeJson(entry.getValue())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    private static String escapeJson(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String board;
        private String brand;
        private String manufacturer;
        private String product;
        private String deviceName;
        private String androidVersion;
        private String securityPatch;
        private String hardware;
        private int sdkVersion;
        private String model;
        private String display;
        private String fingerprint;
        private String host;
        private String buildId;
        private String buildUser;
        private String buildType;
        private String buildTags;
        private String bootloader;
        private String supportedAbis;
        private String language;
        private String androidId;
        private String serial;
        private String imei;

        public Builder board(String board) {
            this.board = normalize(board);
            return this;
        }

        public Builder brand(String brand) {
            this.brand = normalize(brand);
            return this;
        }

        public Builder manufacturer(String manufacturer) {
            this.manufacturer = normalize(manufacturer);
            return this;
        }

        public Builder product(String product) {
            this.product = normalize(product);
            return this;
        }

        public Builder deviceName(String deviceName) {
            this.deviceName = normalize(deviceName);
            return this;
        }

        public Builder androidVersion(String androidVersion) {
            this.androidVersion = normalize(androidVersion);
            return this;
        }

        public Builder securityPatch(String securityPatch) {
            this.securityPatch = normalize(securityPatch);
            return this;
        }

        public Builder hardware(String hardware) {
            this.hardware = normalize(hardware);
            return this;
        }

        public Builder sdkVersion(int sdkVersion) {
            this.sdkVersion = sdkVersion;
            return this;
        }

        public Builder model(String model) {
            this.model = normalize(model);
            return this;
        }

        public Builder display(String display) {
            this.display = normalize(display);
            return this;
        }

        public Builder fingerprint(String fingerprint) {
            this.fingerprint = normalize(fingerprint);
            return this;
        }

        public Builder host(String host) {
            this.host = normalize(host);
            return this;
        }

        public Builder buildId(String buildId) {
            this.buildId = normalize(buildId);
            return this;
        }

        public Builder buildUser(String buildUser) {
            this.buildUser = normalize(buildUser);
            return this;
        }

        public Builder buildType(String buildType) {
            this.buildType = normalize(buildType);
            return this;
        }

        public Builder buildTags(String buildTags) {
            this.buildTags = normalize(buildTags);
            return this;
        }

        public Builder bootloader(String bootloader) {
            this.bootloader = normalize(bootloader);
            return this;
        }

        public Builder supportedAbis(String supportedAbis) {
            this.supportedAbis = normalize(supportedAbis);
            return this;
        }

        public Builder language(String language) {
            this.language = normalize(language);
            return this;
        }

        public Builder androidId(String androidId) {
            this.androidId = normalize(androidId);
            return this;
        }

        public Builder serial(String serial) {
            this.serial = normalize(serial);
            return this;
        }

        public Builder imei(String imei) {
            this.imei = normalize(imei);
            return this;
        }

        public DevicesInfo build() {
            return new DevicesInfo(this);
        }

        private static String normalize(String value) {
            if (value == null || value.isEmpty()) {
                return null;
            }
            String trimmed = value.trim();
            if ("unknown".equalsIgnoreCase(trimmed)) {
                return null;
            }
            return trimmed;
        }
    }

}
