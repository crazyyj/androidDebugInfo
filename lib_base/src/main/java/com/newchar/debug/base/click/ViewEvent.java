package com.newchar.debug.base.click;

import android.view.View;

/**
 * @author  newchar
 * date            2019-06-20
 * @since 入口类，
 * @since 迭代版本描述
 */
public class ViewEvent {

    private Intercept mIntercept;
    private ActionListener mActionListener = new ActionDefaultListener();

    public ViewEvent(Intercept intercept) {
        super();
        mIntercept = intercept;
    }

    public ViewEvent() {
        super();
        mIntercept = new BaseIntercept() {
            @Override
            public boolean onUserIntercept(View view) {
                if (mActionListener != null) {
                    return mActionListener.onActionBefore(view);
                }
                return false;
            }
        };
    }

    public void setActionListener(ActionListener mActionListener) {
        this.mActionListener = mActionListener;
    }

    /**
     * 用户的调用方法
     *
     * @param view 触发事件的View
     */
    public void clickCompat(View view) {
        if (!mIntercept.onDefaultIntercept(view) && !mIntercept.onUserIntercept(view)) {
            if (mActionListener != null) {
                mActionListener.onAction(view);
            }
        }
    }

    public void destroy() {
        mIntercept.destroy();
    }

}
