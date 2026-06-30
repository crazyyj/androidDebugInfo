package com.newchar.debug.net;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;

/**
 * 透明授权页面，用于从悬浮窗/Service 上下文请求 VPN 权限。
 * 绕过 Android 10+ 后台启动 Activity 限制。
 */
public class DebugNetPermissionActivity extends Activity {

    private static final int REQUEST_VPN = 30002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestVpnPermissionIfNeeded();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            startVpnService();
        }
        finish();
    }

    private void requestVpnPermissionIfNeeded() {
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, REQUEST_VPN);
        } else {
            startVpnService();
            finish();
        }
    }

    private void startVpnService() {
        Intent intent = new Intent(this, DebugNetVpnService.class);
        intent.setAction(DebugNetVpnService.ACTION_START);
        startService(intent);
    }
}
