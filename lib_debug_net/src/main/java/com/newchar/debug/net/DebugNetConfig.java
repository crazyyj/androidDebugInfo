package com.newchar.debug.net;

import java.io.File;

/**
 * DebugNet 运行配置。
 */
public final class DebugNetConfig {

    public static final String KEYSTORE_TYPE_PKCS12 = "PKCS12";
    public static final String KEYSTORE_TYPE_BKS = "BKS";

    private final boolean httpDecodeEnabled;
    private final boolean httpsDecodeEnabled;
    private final String certificatePath;
    private final String certificatePassword;
    private final String keystoreType;
    private final int maxPayloadBytes;

    private DebugNetConfig(Builder builder) {
        this.httpDecodeEnabled = builder.httpDecodeEnabled;
        this.httpsDecodeEnabled = builder.httpsDecodeEnabled;
        this.certificatePath = safeTrim(builder.certificatePath);
        this.certificatePassword = builder.certificatePassword == null ? "" : builder.certificatePassword;
        this.keystoreType = normalizeKeystoreType(builder.keystoreType);
        this.maxPayloadBytes = builder.maxPayloadBytes > 0 ? builder.maxPayloadBytes : 64 * 1024;
    }

    public static DebugNetConfig defaultConfig() {
        return new Builder().build();
    }

    public Builder buildUpon() {
        return new Builder()
                .setHttpDecodeEnabled(httpDecodeEnabled)
                .setHttpsDecodeEnabled(httpsDecodeEnabled)
                .setCertificatePath(certificatePath)
                .setCertificatePassword(certificatePassword)
                .setKeystoreType(keystoreType)
                .setMaxPayloadBytes(maxPayloadBytes);
    }

    public boolean isHttpDecodeEnabled() {
        return httpDecodeEnabled;
    }

    public boolean isHttpsDecodeEnabled() {
        return httpsDecodeEnabled;
    }

    public String getCertificatePath() {
        return certificatePath;
    }

    public String getCertificatePassword() {
        return certificatePassword;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    /**
     * 返回 null 表示配置有效。
     */
    public String validateForStart() {
        if (!httpsDecodeEnabled) {
            return null;
        }
        if (certificatePath.isEmpty()) {
            return "HTTPS 解码已开启，请填写证书绝对路径";
        }
        if (certificatePath.startsWith("file://")) {
            return "证书路径仅支持绝对路径，不支持 file://";
        }
        File file = new File(certificatePath);
        if (!file.isAbsolute()) {
            return "证书路径必须是绝对路径";
        }
        String lowerCasePath = certificatePath.toLowerCase();
        if (!lowerCasePath.endsWith(".p12")
                && !lowerCasePath.endsWith(".pfx")
                && !lowerCasePath.endsWith(".bks")) {
            return "证书文件仅支持 .p12/.pfx/.bks";
        }
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            return "证书文件不存在或不可读";
        }
        if (!KEYSTORE_TYPE_PKCS12.equals(keystoreType) && !KEYSTORE_TYPE_BKS.equals(keystoreType)) {
            return "keystoreType 仅支持 PKCS12 或 BKS";
        }
        return null;
    }

    public static final class Builder {

        private boolean httpDecodeEnabled;
        private boolean httpsDecodeEnabled;
        private String certificatePath = "";
        private String certificatePassword = "";
        private String keystoreType = KEYSTORE_TYPE_PKCS12;
        private int maxPayloadBytes = 64 * 1024;

        public Builder setHttpDecodeEnabled(boolean enabled) {
            this.httpDecodeEnabled = enabled;
            return this;
        }

        public Builder setHttpsDecodeEnabled(boolean enabled) {
            this.httpsDecodeEnabled = enabled;
            return this;
        }

        public Builder setCertificatePath(String path) {
            this.certificatePath = path != null ? path : "";
            return this;
        }

        public Builder setCertificatePassword(String password) {
            this.certificatePassword = password != null ? password : "";
            return this;
        }

        public Builder setKeystoreType(String type) {
            this.keystoreType = type != null ? type : KEYSTORE_TYPE_PKCS12;
            return this;
        }

        public Builder setMaxPayloadBytes(int bytes) {
            this.maxPayloadBytes = bytes > 0 ? bytes : 64 * 1024;
            return this;
        }

        public DebugNetConfig build() {
            return new DebugNetConfig(this);
        }
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeKeystoreType(String value) {
        if (value == null) {
            return KEYSTORE_TYPE_PKCS12;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return KEYSTORE_TYPE_PKCS12;
        }
        if ("P12".equals(normalized) || "PFX".equals(normalized)) {
            return KEYSTORE_TYPE_PKCS12;
        }
        return normalized;
    }
}
