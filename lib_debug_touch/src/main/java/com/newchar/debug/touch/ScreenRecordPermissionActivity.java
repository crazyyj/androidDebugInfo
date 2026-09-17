package com.newchar.debug.touch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

/**
 * 透明授权页面，用于请求 MediaProjection 权限。
 */
public class ScreenRecordPermissionActivity extends Activity {

    private static final int REQUEST_SCREEN_CAPTURE = 30001;
    private static final String TAG = "ScreenRecordPermission";
    private boolean mRequestStarted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestProjectionIfNeed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREEN_CAPTURE && resultCode == RESULT_OK && data != null) {
            ScreenRecordService.start(this, resultCode, data);
        } else if (requestCode == REQUEST_SCREEN_CAPTURE) {
            Log.w(TAG, "用户未授予 MediaProjection 权限，无法启动屏幕推流");
            Toast.makeText(getApplicationContext(), "未授予屏幕录制权限，无法开始推流", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void requestProjectionIfNeed() {
        if (mRequestStarted) {
            return;
        }
        mRequestStarted = true;
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            finish();
            return;
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE);
    }
}