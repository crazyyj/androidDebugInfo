package com.newchar.debug.base.click;

import android.view.View;

/**
 * @author  newchar
 * date            2019-06-21
 * @since 当前版本描述，
 * @since 迭代版本描述
 */
public interface ActionListener {

    /**
     * 在执行之前的回调
     * @param view 事件View
     */
    boolean onActionBefore(View view);

    /**
     *
     * @param view 事件View
     * @return  是否执行后续的方法
     */
    boolean onAction(View view);

}
