package com.newchar.debug.adapter;

import java.util.List;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public abstract class GroupViewHolder<V> {
	
	public abstract void initWidgets(View groupView);
	public abstract void setData(List<V> groupData, int groupPosition);

}