package com.newchar.debugview.utils;

import android.view.MotionEvent;
import android.view.View;

/**
 * @author newChar
 * date 2025/6/6
 * @since 简单跟随移动TouchListener
 * @since 迭代版本，（以及描述）
 */
public class MoveTouchListener implements View.OnTouchListener {

    /**
     * 移动回调，用于外部同步窗口坐标。
     */
    public interface MoveHandler {

        /**
         * 处理本次移动偏移。
         *
         * @param control 被控制的视图
         * @param deltaX X方向偏移
         * @param deltaY Y方向偏移
         */
        void onMove(View control, float deltaX, float deltaY);
    }

    private View mControl;
    private MoveHandler mMoveHandler;
    private float mLastRawX;
    private float mLastRawY;

    public MoveTouchListener() {
    }

    public MoveTouchListener(View control) {
        this(control, null);
    }

    public MoveTouchListener(View control, MoveHandler moveHandler) {
        mControl = control;
        mMoveHandler = moveHandler;
    }

    /**
     * 设置移动处理器。
     *
     * @param moveHandler 移动处理器
     */
    public void setMoveHandler(MoveHandler moveHandler) {
        mMoveHandler = moveHandler;
    }

    /**
     * 处理拖拽手势。
     *
     * @param v 触发手势的视图
     * @param event 手势事件
     * @return true 代表已消费
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mControl == null) {
            mControl = v;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLastRawX = event.getRawX();
                mLastRawY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getRawX() - mLastRawX;
                float deltaY = event.getRawY() - mLastRawY;
                mLastRawX = event.getRawX();
                mLastRawY = event.getRawY();
                applyMove(deltaX, deltaY);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return false;
        }
    }

    /**
     * 应用拖动偏移。
     *
     * @param deltaX X方向偏移
     * @param deltaY Y方向偏移
     */
    private void applyMove(float deltaX, float deltaY) {
        if (mMoveHandler != null) {
            mMoveHandler.onMove(mControl, deltaX, deltaY);
            return;
        }
        mControl.setTranslationX(mControl.getTranslationX() + deltaX);
        mControl.setTranslationY(mControl.getTranslationY() + deltaY);
    }
}
