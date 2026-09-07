package com.newchar.debug.router;

import android.content.Intent;
import java.util.HashMap;
import java.util.Map;

/**
 * @author newChar
 * @since 结果代理管理器。管理 pending 的 ResultProxyEntry，并在 Activity 返回结果时分发回调。
 */
public class ResultProxyManager {

    private static final class Holder {
        static final ResultProxyManager INSTANCE = new ResultProxyManager();
    }

    public static ResultProxyManager getInstance() {
        return Holder.INSTANCE;
    }

    private final Map<Integer, ResultProxyEntry> mEntries = new HashMap<>();

    /**
     * 注册一个代理条目。
     *
     * @param id       任务 id
     * @param entry    代理条目
     */
    public void register(int id, ResultProxyEntry entry) {
        mEntries.put(id, entry);
    }

    /**
     * Activity 返回结果时分发回调。
     *
     * @param id        任务 id
     * @param requestCode requestCode
     * @param resultCode  resultCode
     * @param data      返回的 Intent 数据
     */
    public void onResult(int id, int requestCode, int resultCode, Intent data) {
        ResultProxyEntry entry = mEntries.remove(id);
        if (entry != null && entry.callback != null) {
            try {
                entry.callback.onResult(id, requestCode, resultCode, data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 移除指定的代理条目。
     *
     * @param id 任务 id
     */
    public void unregister(int id) {
        mEntries.remove(id);
    }
}
