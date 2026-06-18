package com.newchar.debug.browser.store;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;

/**
 * @author newChar
 * date 2024/3/21
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
final class MemoryWebResourcesCache extends WebResourcesCache {

    private static final LruCache<String, WebResources> mCacheResources = new LruCache<String, WebResources>(100) {
        @Override
        protected int sizeOf(@NonNull String key, @NonNull WebResources value) {
            return super.sizeOf(key, value);
        }

        @Nullable
        @Override
        protected WebResources create(@NonNull String key) {

            return super.create(key);
        }

        @Override
        protected void entryRemoved(boolean evicted, @NonNull String key, @NonNull WebResources oldValue, @Nullable WebResources newValue) {
            super.entryRemoved(evicted, key, oldValue, newValue);

        }
    };

    public MemoryWebResourcesCache(WebResourcesCache resources) {
        super(resources);
    }

    @Override
    public WebResources get(Uri requestResourceUri) {
        // cache 超时,等计算处理.
        String requestKey = requestResourceUri.toString();
//        WebResources webResources  = mCacheResources.get(requestKey);
//        if (webResources == null) {
        WebResources webResources = getNext().get(requestResourceUri);
//            if (webResources != null) {
//                mCacheResources.put(requestKey, webResources);
//            }
//        }
        return webResources;
    }

}
