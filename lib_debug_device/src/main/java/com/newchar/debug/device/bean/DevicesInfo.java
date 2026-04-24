package com.newchar.debug.device.bean;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author newChar
 * date 2023/1/8
 * @since 设备信息
 * @since 迭代版本，（以及描述）
 */
public class DevicesInfo {

    private String mBoard;
    private String mBrand;
    private String mManufacturer;
    private String mProduct;
    private String mDeviceName;
    private String mAndroidVersion;
    private String mSecurityPatch;
    private String mHardware;
    private int mSDKVersion;
    private String mModel;
    private String mDisplay;
    private String mFingerprint;
    private String mHost;
    private String mBuildId;
    private String mBuildUser;
    private String mBuildType;
    private String mBuildTags;
    private String mBootloader;
    private String mSupportedAbis;
    private String mLanguage;
    private String mAndroidId;
    private String mSerial;
    private String mImei;

    private static volatile DevicesInfo mDevicesInfo;

    public static DevicesInfo getInstance(){
        if (mDevicesInfo == null) {
            mDevicesInfo = new DevicesInfo();
        }

        return mDevicesInfo;
    }

    public String getBrand() {
        return mBrand;
    }

    public void setBrand(String brand) {
        this.mBrand = brand;
    }

    public String getManufacturer() {
        return mManufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.mManufacturer = manufacturer;
    }

    public String getProduct() {
        return mProduct;
    }

    public void setProduct(String product) {
        this.mProduct = product;
    }

    public String getModel() {
        return mModel;
    }

    public void setModel(String model) {
        this.mModel = model;
    }

    public String getBoard() {
        return mBoard;
    }

    public void setBoard(String board) {
        this.mBoard = board;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public void setDeviceName(String deviceName) {
        this.mDeviceName = deviceName;
    }

    public String getHardware() {
        return mHardware;
    }

    public void setHardware(String hardware) {
        this.mHardware = hardware;
    }

    public String getAndroidVersion() {
        return mAndroidVersion;
    }

    public void setAndroidVersion(String androidVersion) {
        this.mAndroidVersion = androidVersion;
    }

    public String getSecurityPatch() {
        return mSecurityPatch;
    }

    public void setSecurityPatch(String securityPatch) {
        this.mSecurityPatch = securityPatch;
    }

    public int getSDKVersion() {
        return mSDKVersion;
    }

    public void setSDKVersion(int sdkVersion) {
        this.mSDKVersion = sdkVersion;
    }

    public String getDisplay() {
        return mDisplay;
    }

    public void setDisplay(String display) {
        this.mDisplay = display;
    }

    public String getFingerprint() {
        return mFingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.mFingerprint = fingerprint;
    }

    public String getHost() {
        return mHost;
    }

    public void setHost(String host) {
        this.mHost = host;
    }

    public String getBuildId() {
        return mBuildId;
    }

    public void setBuildId(String buildId) {
        this.mBuildId = buildId;
    }

    public String getBuildUser() {
        return mBuildUser;
    }

    public void setBuildUser(String buildUser) {
        this.mBuildUser = buildUser;
    }

    public String getBuildType() {
        return mBuildType;
    }

    public void setBuildType(String buildType) {
        this.mBuildType = buildType;
    }

    public String getBuildTags() {
        return mBuildTags;
    }

    public void setBuildTags(String buildTags) {
        this.mBuildTags = buildTags;
    }

    public String getBootloader() {
        return mBootloader;
    }

    public void setBootloader(String bootloader) {
        this.mBootloader = bootloader;
    }

    public String getSupportedAbis() {
        return mSupportedAbis;
    }

    public void setSupportedAbis(String supportedAbis) {
        this.mSupportedAbis = supportedAbis;
    }

    public String getLanguage() {
        return mLanguage;
    }

    public void setLanguage(String language) {
        this.mLanguage = language;
    }

    public String getAndroidId() {
        return mAndroidId;
    }

    public void setAndroidId(String androidId) {
        this.mAndroidId = androidId;
    }

    public String getSerial() {
        return mSerial;
    }

    public void setSerial(String serial) {
        this.mSerial = serial;
    }

    public String getImei() {
        return mImei;
    }

    public void setImei(String imei) {
        this.mImei = imei;
    }

    public String toJson(){
        JSONObject rootJson = new JSONObject();
        try {
            putIfNotEmpty(rootJson, "厂商", getManufacturer());
            putIfNotEmpty(rootJson, "品牌", getBrand());
            putIfNotEmpty(rootJson, "产品名", getProduct());
            putIfNotEmpty(rootJson, "设备名", getDeviceName());
            putIfNotEmpty(rootJson, "设备型号", getModel());
            putIfNotEmpty(rootJson, "主板", getBoard());
            putIfNotEmpty(rootJson, "硬件", getHardware());
            putIfNotEmpty(rootJson, "Display", getDisplay());
            putIfNotEmpty(rootJson, "Fingerprint", getFingerprint());
            putIfNotEmpty(rootJson, "Host", getHost());
            putIfNotEmpty(rootJson, "Build-ID", getBuildId());
            putIfNotEmpty(rootJson, "Build-User", getBuildUser());
            putIfNotEmpty(rootJson, "Build-Type", getBuildType());
            putIfNotEmpty(rootJson, "Build-Tags", getBuildTags());
            putIfNotEmpty(rootJson, "Bootloader", getBootloader());
            rootJson.put("Android-SDK", getSDKVersion());
            putIfNotEmpty(rootJson, "Android版本", getAndroidVersion());
            putIfNotEmpty(rootJson, "安全补丁", getSecurityPatch());
            putIfNotEmpty(rootJson, "支持ABI", getSupportedAbis());
            putIfNotEmpty(rootJson, "系统语言", getLanguage());
            putIfNotEmpty(rootJson, "AndroidId", getAndroidId());
            putIfNotEmpty(rootJson, "Serial", getSerial());
            putIfNotEmpty(rootJson, "IMEI", getImei());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return rootJson.toString();
    }

    private void putIfNotEmpty(JSONObject rootJson, String key, String value) throws JSONException {
        if (value != null && !value.isEmpty()) {
            rootJson.put(key, value);
        }
    }

}
