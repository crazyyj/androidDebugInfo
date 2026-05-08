package com.newchar.debug.base.adapter;


import android.view.View;

import java.util.List;

/**
 * ListView 的 ViewHolder 标准
 *
 * @author NewLq1
 */
public abstract class NewLqBaseHolder<T> implements View.OnClickListener {

    public abstract int getLayoutId();

    public abstract void initWidgets(View v);

    public abstract void setData(List<T> data, int position);
}

