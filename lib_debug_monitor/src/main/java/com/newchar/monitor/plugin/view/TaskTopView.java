package com.newchar.monitor.plugin.view;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.newchar.debug.common.utils.ViewUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author newChar
 * date 2025/7/1
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class TaskTopView extends FrameLayout {

//    public static final int VIEW_ID = generateViewId();
    public static final int VIEW_ID_1 = generateViewId();

    private ExpandableListView mExpandableListView;
    private final Map<Activity, String> mActivityGroupLabels = new WeakHashMap<>();

    public TaskTopView(Context context) {
        this(context, null);
    }

    public TaskTopView( Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskTopView( Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
//        setId(VIEW_ID);
        initContainerView(getContext());
    }

    private void initContainerView(Context context) {
        if (mExpandableListView == null) {
            mExpandableListView = new ExpandableListView(context);
            mExpandableListView.setId(VIEW_ID_1);
            mExpandableListView.setGroupIndicator(new ColorDrawable());
            mExpandableListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        }
        ExpandableListAdapter expandableListAdapter = mExpandableListView.getExpandableListAdapter();
        if (expandableListAdapter == null) {
            PageTaskTopAdapter expandableAdapter = new PageTaskTopAdapter(new ArrayList<>(), new HashMap<>());
            mExpandableListView.setAdapter(expandableAdapter);
        }
        if (mExpandableListView.getParent() instanceof ViewGroup) {
            ViewUtils.removeSelf(mExpandableListView);
        }
        mExpandableListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                if (parent.isGroupExpanded(groupPosition)) {
                    parent.collapseGroup(groupPosition);
                } else {
                    parent.expandGroup(groupPosition);
                }
                return true;
            }
        });

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        addView(mExpandableListView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeView(mExpandableListView);
    }

    public void addActivity(Activity activity) {
        if (activity == null || mExpandableListView == null) {
            return;
        }
        ExpandableListAdapter expandableListAdapter = mExpandableListView.getExpandableListAdapter();
        if (!(expandableListAdapter instanceof PageTaskTopAdapter)) {
            return;
        }
        PageTaskTopAdapter adapter = (PageTaskTopAdapter) expandableListAdapter;
        String groupLabel = buildGroupLabel(activity);
        if (adapter.indexOfGroup(groupLabel) < 0) {
            adapter.addGroupData(groupLabel);
            mActivityGroupLabels.put(activity, groupLabel);
            int groupPosition = adapter.indexOfGroup(groupLabel);
            if (groupPosition >= 0) {
                mExpandableListView.expandGroup(groupPosition);
            }
        } else {
            mActivityGroupLabels.put(activity, groupLabel);
        }
    }

    public void removeActivity(Activity activity) {
        if (activity == null || mExpandableListView == null) {
            return;
        }
        ExpandableListAdapter expandableListAdapter = mExpandableListView.getExpandableListAdapter();
        if (expandableListAdapter instanceof PageTaskTopAdapter) {
            PageTaskTopAdapter adapter = (PageTaskTopAdapter) expandableListAdapter;
            String groupLabel = mActivityGroupLabels.remove(activity);
            if (groupLabel == null) {
                groupLabel = buildGroupLabel(activity);
            }
            adapter.removeGroup(groupLabel);
        }
    }

    public void addDialog(Context context, String childText) {
        addChildForContext(context, childText);
    }

    public void removeDialog(Context context, String childText) {
        removeChildForContext(context, childText);
    }

    public void addPopup(Context context, String childText) {
        addChildForContext(context, childText);
    }

    public void removePopup(Context context, String childText) {
        removeChildForContext(context, childText);
    }

    public void addFragment(Context context, String childText) {
        addChildForContext(context, childText);
    }

    public void removeFragment(Context context, String childText) {
        removeChildForContext(context, childText);
    }

    private void addChildForContext(Context context, String childText) {
        if (mExpandableListView == null) {
            return;
        }
        ExpandableListAdapter expandableListAdapter = mExpandableListView.getExpandableListAdapter();
        if (!(expandableListAdapter instanceof PageTaskTopAdapter)) {
            return;
        }
        Activity hostActivity = resolveActivity(context);
        if (hostActivity == null) {
            return;
        }
        PageTaskTopAdapter adapter = (PageTaskTopAdapter) expandableListAdapter;
        String groupLabel = ensureGroupLabel(hostActivity, adapter);
        if (groupLabel != null) {
            adapter.addChildData(groupLabel, childText);
            int groupPosition = adapter.indexOfGroup(groupLabel);
            if (groupPosition >= 0) {
                mExpandableListView.expandGroup(groupPosition);
            }
        }
    }

    private void removeChildForContext(Context context, String childText) {
        if (mExpandableListView == null) {
            return;
        }
        ExpandableListAdapter expandableListAdapter = mExpandableListView.getExpandableListAdapter();
        if (!(expandableListAdapter instanceof PageTaskTopAdapter)) {
            return;
        }
        Activity hostActivity = resolveActivity(context);
        if (hostActivity == null) {
            return;
        }
        PageTaskTopAdapter adapter = (PageTaskTopAdapter) expandableListAdapter;
        String groupLabel = ensureGroupLabel(hostActivity, adapter);
        if (groupLabel != null) {
            adapter.removeChildData(groupLabel, childText);
        }
    }

    private String ensureGroupLabel(Activity activity, PageTaskTopAdapter adapter) {
        String groupLabel = mActivityGroupLabels.get(activity);
        if (groupLabel == null) {
            groupLabel = buildGroupLabel(activity);
        }
        if (adapter.indexOfGroup(groupLabel) < 0) {
            adapter.addGroupData(groupLabel);
        }
        mActivityGroupLabels.put(activity, groupLabel);
        return groupLabel;
    }

    private Activity resolveActivity(Context context) {
        if (context == null) {
            return null;
        }
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        if (current instanceof Activity) {
            return (Activity) current;
        }
        return null;
    }

    private String buildGroupLabel(Activity activity) {
        return activity.getClass().getCanonicalName() + "\n#" + activity.hashCode();
    }

}
