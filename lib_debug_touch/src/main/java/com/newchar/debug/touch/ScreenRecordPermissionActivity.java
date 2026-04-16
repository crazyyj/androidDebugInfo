package com.newchar.debug.touch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

import androidx.annotation.Nullable;

/**
 * 透明授权页面，用于请求 MediaProjection 权限。
 */
public class ScreenRecordPermissionActivity extends Activity {

    private static final int REQUEST_SCREEN_CAPTURE = 30001;
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
