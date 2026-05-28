package com.newchar.debug.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;

public abstract class EasyExpandableAdapter<K, V> extends BaseExpandableListAdapter {

    protected List<K> parentData;
    protected Map<Integer, List<V>> childData;

    public EasyExpandableAdapter(List<K> parentData, Map<Integer, List<V>> childData) {
        this.childData = childData;
        this.parentData = parentData;
    }

    @Override
    public int getGroupCount() {
        return parentData == null ? 0 : parentData.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return childData == null ? 0 :
                childData.get(groupPosition) == null ? 0 : childData.get(groupPosition).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return parentData.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return childData.get(groupPosition).get(childPosition);
    }

    public void addGroupData(K data) {
        parentData.add(data);
        notifyDataSetChanged();
    }

    public void removeGroupData(K data) {
        parentData.remove(data);
        notifyDataSetChanged();
    }

    public void setGroupData(List<K> data) {
        parentData.clear();
        parentData.addAll(data);
        notifyDataSetChanged();
    }

    public void addChildData(int groupPosition, V childText) {
        List<V> vs = childData.get(groupPosition);
        if (vs == null) {
            vs = new ArrayList<>();
            childData.put(groupPosition, vs);
        }
        vs.add(childText);
        notifyDataSetChanged();
    }

    public void removeChildData(int groupPosition, V childText) {
        List<V> vs = childData.get(groupPosition);
        if (vs != null) {
            vs.remove(childText);
            notifyDataSetChanged();
        }
    }


    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition * 100;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return groupPosition * 100 + childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
                             View convertView, ViewGroup parent) {
        GroupViewHolder<K> holder = null;
        if (convertView == null) {
            holder = getGroupHolder();
            convertView = getGroupView(parent.getContext());
            holder.initWidgets(convertView);
            convertView.setTag(holder);
        } else {
            holder = (GroupViewHolder<K>) convertView.getTag();
        }
        holder.setData(parentData, groupPosition);
        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {
        ChildViewHolder<V> holder;
        if (convertView == null) {
            holder = getChildHolder();
            convertView = getChildView(parent.getContext());
            holder.initWidgets(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ChildViewHolder<V>) convertView.getTag();
        }
        holder.setData(childData, groupPosition, childPosition);
        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    protected abstract View getGroupView(Context context);

    protected abstract View getChildView(Context context);

    protected abstract GroupViewHolder<K> getGroupHolder();

    protected abstract ChildViewHolder<V> getChildHolder();

}