package com.newchar.deviceview;

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

import com.newchar.debug.common.utils.ViewUtils;

import java.lang.ref.WeakReference;

/**
 * @author newChar
 * date 2023/7/30
 * @since 显示当前设备信息的滚动View
 * @since 迭代版本，（以及描述）
 */
public class DevicesInfoView extends ScrollView {

    /**
     * 自身id
     */
    public static final int ID_VIEW_SCROLL = generateViewId();

    /**
     * 内部唯一child LinearLayout id
     */
    public static final int ID_VIEW_SCROLL_CHILD = generateViewId();

    public static final int ID_VIEW_CPU = generateViewId();

    public static final int ID_VIEW_MEMORY = generateViewId();

    public static final int ID_VIEW_DEVICES = generateViewId();

    public static final int ID_VIEW_SCREEN = generateViewId();

    public static final int ID_VIEW_STORAGE = generateViewId();

    public static final int ID_VIEW_FILE = generateViewId();

    private static final float TITLE_TEXT_SIZE_SP = 16f;

    private LinearLayout mLinearChildView;

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

    public void setStorageInfo(CharSequence storageInfo) {
        if (mLinearChildView != null) {
            setTitleText(mLinearChildView.findViewById(ID_VIEW_STORAGE), storageInfo);
        }
    }

    public void setFileInfo(CharSequence fileInfo) {
        if (mLinearChildView != null) {
            ViewUtils.setText(mLinearChildView.findViewById(ID_VIEW_FILE), fileInfo);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // 添加childView
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

    private static final class addDevicesView implements Runnable {

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
            addStorageInfoView(container);
            addFileInfoView(container);
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

        private void addStorageInfoView(ViewGroup parent) {
            TextView storageInfoView = new TextView(parent.getContext());
            storageInfoView.setId(ID_VIEW_STORAGE);
            storageInfoView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            storageInfoView.setTextColor(Color.DKGRAY);
            parent.addView(storageInfoView);
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

        private void addFileInfoView(ViewGroup parent) {
            TextView fileInfoView = new TextView(parent.getContext());
            fileInfoView.setId(ID_VIEW_FILE);
            fileInfoView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            fileInfoView.setTextColor(Color.DKGRAY);
            parent.addView(fileInfoView);
        }

    }

}
