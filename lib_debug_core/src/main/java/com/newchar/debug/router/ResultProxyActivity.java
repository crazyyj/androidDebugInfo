package com.newchar.debug.router;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * @author newChar
 * @since 结果代理 Activity。透明启动目标 Activity，返回时将结果透传给插件回调。
 * 解决插件无法直接使用 startActivityForResult 的问题。
 */
public class ResultProxyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        int id = intent.getIntExtra(KEY_ID, -1);
        int requestCode = intent.getIntExtra(KEY_REQUEST_CODE, -1);
        ComponentName componentName = intent.getParcelableExtra(KEY_COMPONENT_NAME);
        String action = intent.getStringExtra(KEY_ACTION);
        Bundle extras = intent.getBundleExtra(KEY_EXTRAS);
        if (componentName == null && action == null) {
            finish();
            return;
        }
        Intent targetIntent = new Intent();
        if (action != null) {
            targetIntent.setAction(action);
        }
        if (componentName != null) {
            targetIntent.setComponent(componentName);
        }
        if (extras != null) {
            targetIntent.putExtras(extras);
        }
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityForResult(targetIntent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Intent resultIntent = getIntent();
        int id = resultIntent != null ? resultIntent.getIntExtra(KEY_ID, -1) : -1;
        ResultProxyManager.getInstance().onResult(id, requestCode, resultCode, data);
        finish();
    }

    private static final String KEY_ID = "result_proxy_id";
    private static final String KEY_REQUEST_CODE = "result_proxy_request_code";
    private static final String KEY_COMPONENT_NAME = "result_proxy_component_name";
    private static final String KEY_ACTION = "result_proxy_action";
    private static final String KEY_EXTRAS = "result_proxy_extras";
}
