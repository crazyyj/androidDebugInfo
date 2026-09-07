package com.newchar.debug.router;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * @author newChar
 * @since 结果代理路由器。为插件提供 startActivityForResult 的能力。
 * 插件无法直接调用 startActivityForResult（不在 Activity 生命周期中），
 * 通过 ResultProxyActivity 中转，由 ResultProxyManager 分发回调。
 */
public class ResultProxyRouter {

    private ResultProxyRouter() {
    }

    /**
     * 启动一个 Activity 并等待结果返回。
     *
     * @param context       上下文
     * @param requestCode   requestCode（用于区分请求）
     * @param id            任务 id（用于匹配回调）
     * @param componentName 目标 Activity 的 ComponentName
     * @param extras        传递给目标 Activity 的额外参数
     * @param callback      结果回调
     */
    public static void launchForResult(
            Context context,
            int requestCode,
            int id,
            ComponentName componentName,
            Bundle extras,
            ResultProxyCallback callback) {
        launchForResult(context, requestCode, id, null, componentName, extras, callback);
    }

    /**
     * 通过 action 启动一个 Activity 并等待结果返回。
     * 用于隐式 Intent（如系统授权页）无法提供 ComponentName 的场景。
     *
     * @param context       上下文
     * @param requestCode   requestCode（用于区分请求）
     * @param id            任务 id（用于匹配回调）
     * @param action        目标 Activity 的 action
     * @param extras        传递给目标 Activity 的额外参数
     * @param callback      结果回调
     */
    public static void launchForResult(
            Context context,
            int requestCode,
            int id,
            String action,
            Bundle extras,
            ResultProxyCallback callback) {
        launchForResult(context, requestCode, id, action, null, extras, callback);
    }

    /**
     * 启动一个 Activity 并等待结果返回，支持 action 与 ComponentName 二选一。
     */
    private static void launchForResult(
            Context context,
            int requestCode,
            int id,
            String action,
            ComponentName componentName,
            Bundle extras,
            ResultProxyCallback callback) {
        ResultProxyEntry entry = new ResultProxyEntry(id, requestCode, componentName, extras, callback);
        ResultProxyManager.getInstance().register(id, entry);

        Intent intent = new Intent(context, ResultProxyActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(KEY_ID, id);
        intent.putExtra(KEY_REQUEST_CODE, requestCode);
        if (action != null) {
            intent.putExtra(KEY_ACTION, action);
        }
        if (componentName != null) {
            intent.putExtra(KEY_COMPONENT_NAME, componentName);
        }
        if (extras != null) {
            intent.putExtra(KEY_EXTRAS, extras);
        }
        context.startActivity(intent);
    }

    private static final String KEY_ID = "result_proxy_id";
    private static final String KEY_REQUEST_CODE = "result_proxy_request_code";
    private static final String KEY_COMPONENT_NAME = "result_proxy_component_name";
    private static final String KEY_ACTION = "result_proxy_action";
    private static final String KEY_EXTRAS = "result_proxy_extras";
}
