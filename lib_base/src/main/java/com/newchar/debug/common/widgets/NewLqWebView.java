package com.newchar.debug.common.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;

import com.newchar.debug.common.utils.UIUtils;
import com.newchar.debug.common.utils.ViewUtils;

/**
 * Created by newlq on 2017/2/8.
 */

public class NewLqWebView extends WebView {


    public NewLqWebView(Context context) {
        super(context);
    }

    public NewLqWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NewLqWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void destroy() {
        ViewUtils.removeSelf(this);
        super.destroy();
    }

}
