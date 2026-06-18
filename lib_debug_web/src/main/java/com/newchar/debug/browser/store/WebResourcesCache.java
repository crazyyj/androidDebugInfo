package com.newchar.debug.browser.store;

import android.net.Uri;

/**
 * @author newChar
 * date 2024/3/21
 * @since Web 本地缓存
 * @since 迭代版本，（以及描述）
 */
public abstract class WebResourcesCache {

    private final WebResourcesCache mNextCache;

    public WebResourcesCache(WebResourcesCache resources) {
        mNextCache = resources;
    }

    public WebResourcesCache getNext() {
        return mNextCache;
    }

    public abstract WebResources get(Uri requestResourceUri);

}
