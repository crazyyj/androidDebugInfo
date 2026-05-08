package com.newchar.debug.base.click;

import android.os.Handler;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author  newchar
 * date            2019-06-21
 * @since 这是，
 * @since 迭代版本描述
 */
public abstract class BaseIntercept implements Intercept {

    /**
     * 默认间隔事件
     */
    private static final int common_click_interval = 1200;

    /**
     * 构造传入的通用间隔事件
     */
    private int click_interval;

    /**
     * 存储 target.hashCode() + 2 作为Key，
     * ，value 存储 指定View 更改的间隔时间
     */
    private Map<Integer, Integer> enableMap = new LinkedHashMap<>();

    private Handler clickEventHandler = new Handler();

    public BaseIntercept(int interval) {
        super();
        click_interval = interval;
    }

    public BaseIntercept() {
        this(common_click_interval);
    }

    @Override
    public boolean onDefaultIntercept(View target) {
//        if (target == null) {
//            return true;
//        }
        if (!clickEventHandler.hasMessages(target.hashCode() + 2)) {
            clickEventHandler.sendEmptyMessageDelayed(target.hashCode() + 2, getViewClickInterval(target));
            return false;
        }
        return true;
    }

    @Override
    public final void setDefaultInterceptEnable(View tag, int enable) {
        enableMap.put(tag.hashCode() + 2, enable);
    }

    /*
     * 获取当前触发事件的View 在其中所
     * @param view
     * @return
     */
    private Integer getViewClickInterval(View view) {
        Integer interceptTag = enableMap.get(view.hashCode() + 1);
        if (interceptTag == null) {
            return click_interval;
        } else {
            return interceptTag;
        }
    }

    @Override
    public void destroy() {
        enableMap.clear();
        clickEventHandler.removeCallbacksAndMessages(null);
    }
}
