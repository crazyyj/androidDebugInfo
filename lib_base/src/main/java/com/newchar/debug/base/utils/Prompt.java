package com.newchar.debug.base.utils;

import android.view.Gravity;
import android.widget.Toast;

/**
 * Tip 工具类， 更符合项目要求的系统Toast功能
 */
public class Prompt {

    private Prompt() {
    }

    private static Toast t;

    public static void showTips(String msg) {
        if (t != null) {
            t.cancel();
            t = null;
        }
        t = Toast.makeText(UIUtils.getContext(), msg, Toast.LENGTH_SHORT);
        t.setText(msg);
        t.show();
    }

    public static void show_long(String msg) {
        if (t == null) {
            t = Toast.makeText(UIUtils.getContext(), "", Toast.LENGTH_LONG);
        }
        t.setDuration(Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.setText(msg);
        t.show();
    }
    
}
