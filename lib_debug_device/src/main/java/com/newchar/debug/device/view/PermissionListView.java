package com.newchar.debug.device.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * 可完整展开的 ListView，用于嵌套在 ScrollView 中展示权限列表。
 */
public final class PermissionListView extends ListView {

    public PermissionListView(Context context) {
        super(context);
    }

    public PermissionListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PermissionListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, expandSpec);
    }

}
