package com.newchar.debug.init;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.newchar.debug.DebugManager;
import com.newchar.debug.utils.DebugUtils;

/**
 * @author newChar
 * date 2024/12/21
 * @since 调试版本初始化使用.
 * @since 用于同步多进程 Activity 等组件生命周期
 */
public class InitContainer extends ContentProvider {

    @Override
    public boolean onCreate() {
        Application app = DebugUtils.getProviderApp(this);
        if (app == null) {
            app = DebugUtils.hookActivityThreadApp();
        }

        DebugManager.getInstance().initialize(app);
        return app != null;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }



}
