package com.newchar.debug.browser.store;

import android.net.Uri;
import android.webkit.WebResourceRequest;

/**
 * @author newChar
 * date 2024/3/21
 * @since 本地静态数据仓库
 * @since 迭代版本，（以及描述）
 */
public class WebResourcesStore {

//    private final List<WebResourcesCache> mCacheStore = new ArrayList<>();

    private final WebResourcesCache mRealCaches;

    public WebResourcesStore(WebResourcesCache caches) {
//        Collections.addAll(mCacheStore, caches);
        mRealCaches = caches;
    }

    /**
     * 多种类型的抽象 比如 js css 图片等.
     *
     * @param request WebResourceRequest
     * @return WebResourcesCache
     */
    public static WebResources getResources(WebResourceRequest request) {
        if (request == null) {
            return null;
        }
        // 请求网络获取.
        NetWebResourcesCache netWebResources = new NetWebResourcesCache(null);
        netWebResources.setRequest(request);
        // 磁盘获取
        LocalWebResourcesCache fileWebResources = new LocalWebResourcesCache(netWebResources);
        // 内存获取
        MemoryWebResourcesCache memoryWebResources = new MemoryWebResourcesCache(fileWebResources);
        return new WebResourcesStore(memoryWebResources).get(request.getUrl());
    }

    public WebResources get(Uri cacheKeyUri){
        return mRealCaches.get(cacheKeyUri);
    }

}
