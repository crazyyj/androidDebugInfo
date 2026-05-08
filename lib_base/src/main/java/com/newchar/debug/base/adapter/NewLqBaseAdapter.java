package com.newchar.debug.base.adapter;

import java.util.List;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
* @author NewLq1
*/
public abstract class NewLqBaseAdapter<T> extends BaseAdapter {
	
	protected List<T> list;

	public static final int FLAG_LOAD_ADAPTER_DATA = 0;
	public static final int FLAG_MORE_ADAPTER_DATA = 1;

	public NewLqBaseAdapter(List<T> adapterData) {
		super();
		this.list = adapterData;
	}
	
	protected void notifyDataSetChanged(List<T> adapterData, int flag){
		if (flag == FLAG_LOAD_ADAPTER_DATA)
			this.list = adapterData;
		else if (flag == FLAG_MORE_ADAPTER_DATA)
			this.list.addAll(adapterData);
		this.notifyDataSetChanged();
	}

	@Override
	public int getCount() {return list == null ? 0 : list.size(); }

	@Override
	public Object getItem(int position) {return list.get(position);}

	@Override
	public long getItemId(int position) {return list == null ? 0 : position;}

	@Override
	public  View getView(int position, View convertView, ViewGroup parent){
		NewLqBaseHolder<T> holder;
		if (convertView == null) {
			holder = getBaseHolder();
			convertView = View.inflate(parent.getContext(), holder.getLayoutId(), null);
			holder.initWidgets(convertView);
			convertView.setTag(holder);
		} else {
			holder = (NewLqBaseHolder<T>) convertView.getTag();
		}
		holder.setData(list, position);
		return convertView;
	}
	
	protected abstract NewLqBaseHolder<T> getBaseHolder();
	
}
