package com.newchar.debug.device;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.newchar.debug.device.bean.PermissionItem;
import com.newchar.debug.device.view.PermissionListView;
import com.newchar.debug.utils.ViewUtils;

import java.lang.ref.WeakReference;
import java.util.List;

public class DevicesInfoView extends ScrollView {

    public static final int ID_VIEW_SCROLL = generateViewId();
    public static final int ID_VIEW_SCROLL_CHILD = generateViewId();
    public static final int ID_VIEW_CPU = generateViewId();
    public static final int ID_VIEW_MEMORY = generateViewId();
    public static final int ID_VIEW_DEVICES = generateViewId();
    public static final int ID_VIEW_SCREEN = generateViewId();
    public static final int ID_VIEW_PERMISSION = generateViewId();

    private static final float TITLE_TEXT_SIZE_SP = 16f;

    private LinearLayout mLinearChildView;
    private PermissionListView mPermissionListView;
    private PermissionAdapter mPermissionAdapter;

    public DevicesInfoView(Context context) {
        this(context, null);
    }

    public DevicesInfoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DevicesInfoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    private void initialize(Context context) {
        setId(ID_VIEW_SCROLL);
    }

    public void setCpuInfo(CharSequence cpuInfo) {
        if (mLinearChildView != null) {
            setTitleText(mLinearChildView.findViewById(ID_VIEW_CPU), cpuInfo);
        }
    }

    public void setMemoryInfo(CharSequence memoryInfo) {
        if (mLinearChildView != null) {
            setTitleText(mLinearChildView.findViewById(ID_VIEW_MEMORY), memoryInfo);
        }
    }

    public void setDevicesInfo(CharSequence devicesInfo) {
        if (mLinearChildView != null) {
            setTitleText(mLinearChildView.findViewById(ID_VIEW_DEVICES), devicesInfo);
        }
    }

    public void setScreenInfo(CharSequence screenInfo) {
        if (mLinearChildView != null) {
            setTitleText(mLinearChildView.findViewById(ID_VIEW_SCREEN), screenInfo);
        }
    }

    public void setPermissionInfo(List<PermissionItem> permissions) {
        if (mLinearChildView == null) {
            return;
        }
        if (mPermissionListView == null) {
            addPermissionInfoView(mLinearChildView);
        }
        if (mPermissionAdapter == null) {
            mPermissionAdapter = new PermissionAdapter();
            mPermissionListView.setAdapter(mPermissionAdapter);
        }
        mPermissionAdapter.refreshData(permissions);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mLinearChildView == null) {
            mLinearChildView = new LinearLayout(getContext());
            mLinearChildView.setId(ID_VIEW_SCROLL_CHILD);
            mLinearChildView.setOrientation(LinearLayout.VERTICAL);
        }
        if (!(getChildCount() > 0 && getChildAt(0).getId() == ID_VIEW_SCROLL_CHILD)) {
            addView(mLinearChildView, generateDefaultLayoutParams());
        }
        if (mLinearChildView.getChildCount() < 1) {
            mLinearChildView.post(new addDevicesView(mLinearChildView));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeAllViews();
        if (mLinearChildView != null) {
            mLinearChildView = null;
        }
    }

    private void setTitleText(View view, CharSequence text) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) view;
        CharSequence current = textView.getText();
        if (current != null && current.toString().equals(String.valueOf(text))) {
            return;
        }
        if (TextUtils.isEmpty(text)) {
            textView.setText(text);
            return;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        int end = TextUtils.indexOf(builder, '\n');
        if (end < 0) {
            end = builder.length();
        }
        if (end > 0) {
            builder.setSpan(new BackgroundColorSpan(Color.GRAY), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ForegroundColorSpan(Color.WHITE), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new AbsoluteSizeSpan((int) TITLE_TEXT_SIZE_SP, true), 0, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        textView.setText(builder);
    }

    private final class addDevicesView implements Runnable {

        private final WeakReference<ViewGroup> mContainerRef;

        public addDevicesView(ViewGroup linearLayout) {
            mContainerRef = new WeakReference<>(linearLayout);
        }

        @Override
        public void run() {
            final ViewGroup container = mContainerRef.get();
            if (container == null) {
                return;
            }
            addCpuInfoView(container);
            addScreenInfoView(container);
            addMemoryInfoView(container);
            addDevicesInfoView(container);
            addPermissionInfoView(container);
            mContainerRef.clear();
        }

        private void addScreenInfoView(ViewGroup parent) {
            TextView screenInfoView = new TextView(parent.getContext());
            screenInfoView.setId(ID_VIEW_SCREEN);
            screenInfoView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            screenInfoView.setTextColor(Color.DKGRAY);
            parent.addView(screenInfoView);
        }

        private void addDevicesInfoView(ViewGroup parent) {
            TextView devicesInfoView = new TextView(parent.getContext());
            devicesInfoView.setId(ID_VIEW_DEVICES);
            devicesInfoView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            devicesInfoView.setTextColor(Color.DKGRAY);
            parent.addView(devicesInfoView);
        }

        private void addMemoryInfoView(ViewGroup parent) {
            TextView memoryInfoView = new TextView(parent.getContext());
            memoryInfoView.setId(ID_VIEW_MEMORY);
            memoryInfoView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            memoryInfoView.setTextColor(Color.DKGRAY);
            parent.addView(memoryInfoView);
        }

        private void addCpuInfoView(ViewGroup parent) {
            TextView cpuInfoView = new TextView(parent.getContext());
            cpuInfoView.setId(ID_VIEW_CPU);
            cpuInfoView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cpuInfoView.setTextColor(Color.DKGRAY);
            parent.addView(cpuInfoView);
        }

    }

    private void addPermissionInfoView(ViewGroup parent) {
        Context context = parent.getContext();

        TextView titleView = new TextView(context);
        titleView.setId(ID_VIEW_PERMISSION);
        titleView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleView.setTextColor(Color.DKGRAY);
        parent.addView(titleView);
        setTitleText(titleView, "权限列表");

        mPermissionListView = new PermissionListView(context);
        mPermissionListView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mPermissionListView.setDivider(null);
        parent.addView(mPermissionListView);
    }

}
