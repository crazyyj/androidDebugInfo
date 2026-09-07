package com.newchar.debug.router;

import android.content.Intent;

/**
 * @author newChar
 * @since 结果代理回调接口。Activity 返回结果时通过此接口透传给插件。
 */
public interface ResultProxyCallback {
    /**
     * 接收 startActivityForResult 的返回结果。
     *
     * @param id        任务 id
     * @param requestCode requestCode
     * @param resultCode  resultCode
     * @param data      返回的 Intent 数据
     */
    void onResult(int id, int requestCode, int resultCode, Intent data);
}
