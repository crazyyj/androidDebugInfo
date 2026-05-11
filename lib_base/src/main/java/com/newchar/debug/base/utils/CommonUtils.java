package com.newchar.debug.base.utils;

import android.content.Context;
import android.os.Messenger;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Created by newlq on 2016/10/10.
 */

public class CommonUtils {

    public static String getText(TextView tv){
        return tv.getText().toString().trim();
    }

    /**
     * 弹出软键盘
     * @param editText
     *            输入框
     * @param context
     *            上下文
     */
    public static void openKeybord(EditText editText, Context context) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    /**
     * 关闭软键盘
     * @param v
     *            当前Activity 的焦点View ，通常为正在编辑的EditText
     * @param context
     *            上下文
     * @category
     * 			使用方法 CommonUtils.closeKeybord(this.getCurrentFocus(),this);
     */
    public static void closeKeybord(View v, Context context) {
        InputMethodManager imm = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (v != null)
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
