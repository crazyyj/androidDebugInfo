package com.newchar.debug.monitor.view;


import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import com.newchar.debug.common.adapter.ChildViewHolder;
import com.newchar.debug.common.adapter.GroupViewHolder;
import com.newchar.debug.common.adapter.EasyExpandableAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author newChar
 * date 2025/7/1
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
class PageTaskTopAdapter extends EasyExpandableAdapter<String, String> {

    // group view ids
    public static final int VIEW_ID_BY_GROUP = View.generateViewId();

    // child view ids
    public static final int VIEW_ID_BY_CHILD = View.generateViewId();

    public PageTaskTopAdapter() {
        super(new ArrayList<>(), new HashMap<>());
    }

    public PageTaskTopAdapter(List<String> parentData, Map<Integer, List<String>> childData) {
        super(parentData, childData);
    }

    public int indexOfGroup(String groupLabel) {
        return parentData != null ? parentData.indexOf(groupLabel) : -1;
    }

    public void addChildData(String groupLabel, String childText) {
        int index = indexOfGroup(groupLabel);
        if (index >= 0) {
            addChildData(index, childText);
        }
    }

    public void removeChildData(String groupLabel, String childText) {
        int index = indexOfGroup(groupLabel);
        if (index >= 0) {
            removeChildData(index, childText);
        }
    }

    public void clearChildren(String groupLabel) {
        int index = indexOfGroup(groupLabel);
        if (index >= 0 && childData != null) {
            List<String> children = childData.get(index);
            if (children != null) {
                children.clear();
                notifyDataSetChanged();
            }
        }
    }

    public void removeGroup(String groupLabel) {
        int index = indexOfGroup(groupLabel);
        if (index < 0) {
            return;
        }
        if (parentData != null && parentData.size() > index) {
            parentData.remove(index);
        }
        if (childData != null) {
            if (childData.containsKey(index)) {
                childData.remove(index);
            }
            if (!childData.isEmpty()) {
                Map<Integer, List<String>> reIndexed = new HashMap<>();
                for (Map.Entry<Integer, List<String>> entry : childData.entrySet()) {
                    int key = entry.getKey();
                    if (key > index) {
                        reIndexed.put(key - 1, entry.getValue());
                    } else if (key < index) {
                        reIndexed.put(key, entry.getValue());
                    }
                }
                childData.clear();
                childData.putAll(reIndexed);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    protected View getGroupView(Context context) {
        return newGroupView(context);
    }

    @Override
    protected View getChildView(Context context) {
        return newChildView(context);
    }

    @Override
    protected GroupViewHolder<String> getGroupHolder() {
        return new GroupHolder();
    }

    @Override
    protected ChildViewHolder<String> getChildHolder() {
        return new ChildHolder();
    }

    private View newGroupView(Context context) {
        TextView textView = new TextView(context);
        textView.setId(VIEW_ID_BY_GROUP);
        textView.setPadding(10, 6, 10, 6);
        textView.setTextColor(Color.GRAY);
        return textView;
    }

    private View newChildView(Context context) {
        TextView textView = new TextView(context);
        textView.setId(VIEW_ID_BY_CHILD);
//        textView.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
//        textView.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        textView.setPadding(10, 6, 10, 6);
        textView.setTextColor(Color.GRAY);
        return textView;
    }

    @Override
    public void onGroupCollapsed(int groupPosition) {
        super.onGroupCollapsed(groupPosition);

    }

    @Override
    public void onGroupExpanded(int groupPosition) {
        super.onGroupExpanded(groupPosition);

    }

    private static class GroupHolder extends GroupViewHolder<String> {

        private View mGroupView;

        @Override
        public void initWidgets(View groupView) {
            mGroupView = groupView;
        }

        @Override
        public void setData(List<String> groupData, int groupPosition) {
            ((TextView) mGroupView).setText(groupData.get(groupPosition));
        }
    }

    private static class ChildHolder extends ChildViewHolder<String> {

        private View mChildView;

        @Override
        public void initWidgets(View childView) {
            mChildView = childView;
        }

        @Override
        public void setData(Map<Integer, List<String>> childData, int groupPosition, int childPosition) {
            ((TextView) mChildView).setText(childData.get(groupPosition).get(childPosition));
        }

    }

}
