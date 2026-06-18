package com.newchar.debug.browser.store;

import android.net.Uri;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author newChar
 * date 2024/3/21
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class WebResources {

    private InputStream mResourcesInputSteam;

    private final Map<String, String> mResponseHeader = new HashMap<>();

    private int stateCode;

    public int getStateCode() {
        return stateCode;
    }

    public void setStateCode(int stateCode) {
        this.stateCode = stateCode;
    }

    public void setInputSteam(InputStream inputStream) {
        mResourcesInputSteam = inputStream;
    }

    public InputStream getInputSteam() {
        return mResourcesInputSteam;
    }

    public void addHeader(String headerKey, String headerValue) {
        mResponseHeader.put(headerKey, headerValue);
    }

    public void setResponseHeader(Map<String, String> newHeader) {
        mResponseHeader.clear();
        mResponseHeader.putAll(newHeader);
    }

    public Map<String, String> getResponseHeader() {
        return mResponseHeader;
    }

    interface ResourceFindListener {
        void onResourceFind(WebResources resources);
    }

}
