package com.newchar.debug.logview;

import android.content.Context;
import android.widget.GridView;

/**
 * @author newChar
 * date 2023/4/21
 * @since 被GridView嵌套的 九宫格图片
 * @since 迭代版本，（以及描述）
 */
class ItemLogImageView extends GridView {

    /**
     * 竖向间隙
     */
    public static final int VERTICAL_SPACING = 6;

    /**
     * 横向间隙
     */
    public static final int HORIZONTAL_SPACING = 6;

    public ItemLogImageView(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

}
