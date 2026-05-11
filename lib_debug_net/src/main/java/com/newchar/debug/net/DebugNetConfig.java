package com.newchar.debug.net;

public class DebugNetConfig {

    public static final String KEYSTORE_TYPE_BKS = "BKS";
    public static final String KEYSTORE_TYPE_PKCS12 = "PKCS12";

    private final boolean httpDecodeEnabled;
    private final boolean httpsDecodeEnabled;
    private final String certificatePath;
    private final String certificatePassword;
    private final String keystoreType;

    private DebugNetConfig(Builder builder) {
        this.httpDecodeEnabled = builder.httpDecodeEnabled;
        this.httpsDecodeEnabled = builder.httpsDecodeEnabled;
        this.certificatePath = builder.certificatePath;
        this.certificatePassword = builder.certificatePassword;
        this.keystoreType = builder.keystoreType;
    }

    public static DebugNetConfig defaultConfig() {
        return new Builder().build();
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

    public String validateForStart() {
        if (httpsDecodeEnabled) {
            if (certificatePath == null || certificatePath.isEmpty()) {
                return "HTTPS 解码已启用，但未配置证书路径";
            }
            if (certificatePassword == null || certificatePassword.isEmpty()) {
                return "HTTPS 解码已启用，但未配置证书密码";
            }
        }
        return null;
    }

    public static class Builder {
        private boolean httpDecodeEnabled = false;
        private boolean httpsDecodeEnabled = false;
        private String certificatePath = "";
        private String certificatePassword = "";
        private String keystoreType = KEYSTORE_TYPE_PKCS12;

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

        public DebugNetConfig build() {
            return new DebugNetConfig(this);
        }
    }
}
