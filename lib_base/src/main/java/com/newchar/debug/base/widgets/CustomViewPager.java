package com.newchar.debug.base.widgets;

import android.content.Context;

import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * 不滑动
 * @author dell
 */
public class CustomViewPager extends androidx.viewpager.widget.ViewPager {
	
	private boolean isCanScroll = false;

	
	public CustomViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CustomViewPager(Context context) {
		super(context);
	}

	public void setCanScroll(boolean isCanScroll) {
		this.isCanScroll = isCanScroll;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (isCanScroll) {
			return super.onTouchEvent(event);
		} else {
			return false;
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		if (isCanScroll) {
			return super.onInterceptTouchEvent(event);
		} else {
			return false;
		}
	}

	//	在直接切换pager页时， 不会出现滚动的中间时间
	//	boolean smoothScroll 表示 是否直接切换，切换是否需要时间
	/**
	 *  但是如果点击标签横切多个page页面是，会出现闪烁，影响用户体验，又要改， 
	 *  辛苦的搜索了一通，还好，很快又有了结果：
	 *  在上面的代码里加入：smoothScroll
	 */
	@Override
	public void setCurrentItem(int item, boolean smoothScroll) {
		super.setCurrentItem(item, smoothScroll);
	}

	/**
	 * 其中，super.setCurrentItem(item, false);，表示切换的时候，不需要切换时间。
	 */
	@Override
	public void setCurrentItem(int item) {
		super.setCurrentItem(item, false);
	}

	public boolean isCanScroll() {
		return isCanScroll;
	}
	
}
