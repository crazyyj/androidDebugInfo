package com.newchar.debug.touch;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/**
 * 透明 Activity，用于在 Service 无法直接 requestPermissions 的场景下申请相机权限。
 *
 * <p>Service 运行在后台且没有 Activity 上下文，不能直接调用 {@link Activity#requestPermissions}。
 * 这里借一个不显示任何 UI 的透明 Activity 走系统权限对话框：
 * <ul>
 *   <li>用户授予 → 重新 {@link ScreenRecordService#startCamera(android.content.Context)}，
 *       此时权限已就绪，Service 直接进入真正的开相机流程；</li>
 *   <li>用户拒绝 → {@link ScreenRecordService#stopCamera(android.content.Context)}，
 *       Service 退出前台并 stopSelf，避免留下孤儿前台通知。</li>
 * </ul>
 */
public class CameraPermissionActivity extends Activity {

    private static final String TAG = "CameraPermissionActivity";
    private static final int REQUEST_CAMERA = 0xA03;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        // 6.0 以下无需运行时权限，或权限已授予：直接让 Service 继续
        ScreenRecordService.startCamera(this);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA) {
            finish();
            return;
        }
        boolean granted = grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            Log.i(TAG, "相机权限已授予，继续启动相机");
            ScreenRecordService.startCamera(this);
        } else {
            Log.w(TAG, "相机权限被拒绝，停止相机服务");
            ScreenRecordService.stopCamera(this);
        }
        finish();
    }
}
