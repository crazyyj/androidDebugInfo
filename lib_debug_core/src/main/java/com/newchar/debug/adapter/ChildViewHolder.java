package com.newchar.debug.adapter;

import java.util.List;
import java.util.Map;

import android.view.View;

/**
 *
 * 固定Integer 条目 待指哪个条目
 * @param <V> 数据类型 Bean对象
 */
public abstract class ChildViewHolder<V> {
	
	public abstract void initWidgets(View childView);
	public abstract void setData(Map<Integer, List<V>> childData, int groupPosition, int childPosition);

}