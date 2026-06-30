package com.newchar.debug.view;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.inputmethod.InputMethodManager;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import android.widget.Toast;

import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.PluginManager;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.utils.MoveTouchListener;
import com.newchar.debug.utils.UIUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author newChar
 * date 2022/6/15
 * @since 调试View的顶层View。
 * @since 迭代版本，（以及描述）
 */
public class DebugView extends LinearLayout {

    private static final int VIEW_ID_ROOT_VIEW = View.generateViewId();
    private TextView mCopyView;
    private static final int VIEW_ID_COPY_VIEW = View.generateViewId();

    private TextView mFoldView;
    private static final int VIEW_ID_FOLD_VIEW = View.generateViewId();

    private TextView mClearView;
    private static final int VIEW_ID_CLEAR_VIEW = View.generateViewId();

    private TextView mSwitchModeView;
    private static final int VIEW_ID_SWITCH_MODE_VIEW = View.generateViewId();

    public static final int BUTTON_PADDING_TOP_BOTTOM = 12;
    public static final int BUTTON_PADDING_LEFT_RIGHT = 10;

    public static final String TEXT_COPY = "复制";
    public static final String TEXT_PAUSE = "停止";
    public static final String TEXT_FRESH = "刷新";
    public static final String TEXT_RESUME = "恢复";
    public static final String TEXT_CLEAR = "清空";
    public static final String TEXT_SWITCH = "模式";
    public static final String TEXT_SIZE_FULL = "尺寸";
    public static final String TEXT_SIZE_INSET = "内嵌";
    public static final String TEXT_SIZE_COLLAPSED = "折叠";

    private static final int SIZE_MODE_FULL = ScreenDisplayPlugin.UI_MODE_FULL;
    private static final int SIZE_MODE_INSET = ScreenDisplayPlugin.UI_MODE_INSET;
    private static final int SIZE_MODE_COLLAPSED = ScreenDisplayPlugin.UI_MODE_COLLAPSED;

    private int mSizeMode = SIZE_MODE_INSET;
    private int mMaxHeight = 0;

    private final PluginContext mPluginContext = new PluginContext();
    private ScreenDisplayPlugin mCurrentPlugin = null;
    private final List<ScreenDisplayPlugin> mPagePlugin = new ArrayList<>();
    private final List<ScreenDisplayPlugin> mPendingPlugins = new ArrayList<>();
    private final Set<String> mBlacklistedPluginClasses = new HashSet<>();
    private MoveTouchListener.MoveHandler mMoveHandler;
    private ListPopupWindow mPluginSelectorPopup;

    public DebugView(Context context) {
        this(context, null);
    }

    public DebugView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DebugView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    private void initialize(Context context) {
        setId(VIEW_ID_ROOT_VIEW);
        setOrientation(LinearLayout.VERTICAL);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void generateTitleController() {
        Context context = getContext();
        LinearLayout titleController = new LinearLayout(context);
        titleController.setGravity(Gravity.END);
        titleController.setOnTouchListener(new MoveTouchListener(this, mMoveHandler));

        initCopyView(context);
        initFoldView(context);
        initClearView(context);
        initSwitchModeView(context);

        titleController.addView(mCopyView);
        titleController.addView(mFoldView);
        titleController.addView(mClearView);
        titleController.addView(mSwitchModeView);

        addView(titleController);
    }

    private void initClearView(Context context) {
        mClearView = genTextView(context, VIEW_ID_CLEAR_VIEW);
        mClearView.setText(TEXT_CLEAR);
        mClearView.setOnClickListener(clearView -> {
            if (mCurrentPlugin != null) {
                mCurrentPlugin.onClear();
            }
        });
        mClearView.setOnLongClickListener(clearView -> true);
    }

    private void initFoldView(Context context) {
        mFoldView = genTextView(context, VIEW_ID_FOLD_VIEW);
        mFoldView.setText(TEXT_SIZE_INSET);
        mFoldView.setOnClickListener(foldView -> cycleSizeMode());
        mFoldView.setOnLongClickListener(foldView -> true);
    }

    private void initCopyView(Context context) {
        mCopyView = genTextView(context, VIEW_ID_COPY_VIEW);
        mCopyView.setText(TEXT_COPY);
        mCopyView.setOnClickListener(copyView -> {
            if (mCurrentPlugin == null) {
                return;
            }
            CharSequence text = mCurrentPlugin.onCopyText();
            if (text == null || text.length() == 0) {
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("debug", text));
                Toast.makeText(getContext(), "已复制", Toast.LENGTH_SHORT).show();
            }
        });
        mCopyView.setOnLongClickListener(copyView -> {
            if (mCurrentPlugin == null) {
                return false;
            }
            Intent shareIntent = mCurrentPlugin.onCreateShareIntent();
            if (shareIntent == null) {
                return false;
            }
            try {
                getContext().startActivity(Intent.createChooser(shareIntent, "分享"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        });
    }

    private void initSwitchModeView(Context context) {
        mSwitchModeView = genTextView(context, VIEW_ID_SWITCH_MODE_VIEW);
        mSwitchModeView.setText(TEXT_SWITCH);
        mSwitchModeView.setOnClickListener(switchMode -> {
            switchNextPlugin();
        });
        mSwitchModeView.setOnLongClickListener(switchMode -> {
            showPluginSelectorPopup();
            return true;
        });
    }

    private void cycleSizeMode() {
        mSizeMode = (mSizeMode + 1) % 3;
        applySizeMode();
    }

    private void applySizeMode() {
        Context ctx = getContext();
        int screenWidth = UIUtils.getScreenWidth();
        int screenHeight = UIUtils.getScreenHeight();
        int baseEdge = Math.min(screenWidth, screenHeight);
        int inset = UIUtils.getStatusBarHeight(ctx) + UIUtils.getNavigationBarHeight(ctx);
        int insetSize = baseEdge - inset - dp2px(ctx, 8);
        int fullWidth = screenWidth - dp2px(ctx, 8);
        int fullHeight = screenHeight - dp2px(ctx, 8);

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) {
            return;
        }

        String label;
        switch (mSizeMode) {
            case SIZE_MODE_INSET: {
                lp.width = insetSize;
                lp.height = insetSize;
                mMaxHeight = 0;
                setPluginAreaVisible(true);
                label = TEXT_SIZE_INSET;
                break;
            }
            case SIZE_MODE_COLLAPSED: {
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mMaxHeight = insetSize;
                setPluginAreaVisible(false);
                label = TEXT_SIZE_COLLAPSED;
                break;
            }
            case SIZE_MODE_FULL:
            default: {
                lp.width = fullWidth;
                lp.height = fullHeight;
                mMaxHeight = 0;
                setPluginAreaVisible(true);
                label = TEXT_SIZE_FULL;
                break;
            }
        }
        if (mFoldView != null) {
            mFoldView.setText(label);
        }
        setLayoutParams(lp);
        if (mLayoutUpdateHandler != null) {
            mLayoutUpdateHandler.onUpdateLayout(this, lp);
        }
        if (mCurrentPlugin != null) {
            mCurrentPlugin.onSizeModeChanged(mSizeMode);
        }
    }

    private void setPluginAreaVisible(boolean visible) {
        if (visible) {
            if (mCurrentPlugin != null) {
                mCurrentPlugin.onShow();
            }
        } else {
            int childCount = getChildCount();
            for (int i = 1; i < childCount; i++) {
                View child = getChildAt(i);
                child.setVisibility(View.GONE);
            }
        }
    }

    private TextView genTextView(Context context, int id) {
        TextView textView = new TextView(context);
        textView.setPadding(
                BUTTON_PADDING_LEFT_RIGHT, BUTTON_PADDING_TOP_BOTTOM,
                BUTTON_PADDING_LEFT_RIGHT, BUTTON_PADDING_TOP_BOTTOM);
        textView.setId(id);
        textView.setTextSize(16);
        textView.setTextColor(Color.BLACK);
        return textView;
    }

    public void setMoveHandler(MoveTouchListener.MoveHandler moveHandler) {
        mMoveHandler = moveHandler;
    }

    /**
     * Layout 变更回调，用于外层（如 WindowManager 悬浮窗）同步 updateViewLayout。
     */
    public interface LayoutUpdateHandler {
        void onUpdateLayout(View view, ViewGroup.LayoutParams lp);
    }

    private LayoutUpdateHandler mLayoutUpdateHandler;

    public void setLayoutUpdateHandler(LayoutUpdateHandler handler) {
        mLayoutUpdateHandler = handler;
    }

    /**
     * 焦点切换回调，用于外层（WindowManager 悬浮窗）在 EditText 获取/失去焦点时
     * 切换 FLAG_NOT_FOCUSABLE，使软键盘可弹出。
     */
    public interface FocusToggleHandler {
        void onToggleFocusable(View view, boolean focusable);
    }

    private FocusToggleHandler mFocusToggleHandler;
    private ViewTreeObserver.OnGlobalFocusChangeListener mFocusChangeListener;

    public void setFocusToggleHandler(FocusToggleHandler handler) {
        mFocusToggleHandler = handler;
    }

    private void registerFocusChangeListener() {
        if (mFocusChangeListener != null) {
            return;
        }
        mFocusChangeListener = (oldFocus, newFocus) -> {
            boolean needsFocus = newFocus instanceof android.widget.EditText;
            if (mFocusToggleHandler != null) {
                mFocusToggleHandler.onToggleFocusable(this, needsFocus);
            }
            if (needsFocus) {
                newFocus.requestFocus();
                InputMethodManager imm = (InputMethodManager) getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(newFocus, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        };
        getViewTreeObserver().addOnGlobalFocusChangeListener(mFocusChangeListener);
    }

    private void unregisterFocusChangeListener() {
        if (mFocusChangeListener != null && getViewTreeObserver().isAlive()) {
            getViewTreeObserver().removeOnGlobalFocusChangeListener(mFocusChangeListener);
        }
        mFocusChangeListener = null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mMaxHeight > 0) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            int capped = Math.min(size, mMaxHeight);
            int newMode = (mode == MeasureSpec.EXACTLY) ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST;
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(capped, newMode);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        DebugViewStore.attach(this);
        try {
            mPluginContext.mApp = getContext().getApplicationContext();
            generateTitleController();
            applySizeMode();
        } catch (Exception e) {
            e.printStackTrace();
        }
        registerFocusChangeListener();
        loadPlugin();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregisterFocusChangeListener();
        dismissPluginSelectorPopup();
        DebugViewStore.detach(this);
        removeAllViews();
        unloadPlugin();
    }

    public <T extends ScreenDisplayPlugin> T getPlugin(Class<T> clazz){
        for (ScreenDisplayPlugin plugin : mPagePlugin) {
            if (clazz.isInstance(plugin)) {
                return (T) plugin;
            }
        }
        return null;
    }

    private void loadPlugin() {
        Collection<Class<ScreenDisplayPlugin>> allPlugin = PluginManager.getInstance().getAllPlugin();
        for (final Class<ScreenDisplayPlugin> screenDisplayPlugin : allPlugin) {
            if (mBlacklistedPluginClasses.contains(screenDisplayPlugin.getName())) {
                continue;
            }
            post(() -> {
                try {
                    ScreenDisplayPlugin plugin = screenDisplayPlugin.newInstance();
                    plugin.onLoad(mPluginContext, DebugView.this);
                    mPagePlugin.add(plugin);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        post(() -> {
            if (!mPagePlugin.isEmpty()) {
                applyPlugin(mPagePlugin.get(0));
            }
        });
    }

    private void unloadPlugin() {
        mCurrentPlugin = null;
        for (ScreenDisplayPlugin plugin : mPagePlugin) {
            try {
                plugin.onUnload();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mPagePlugin.clear();
    }

    private void applyPlugin(ScreenDisplayPlugin plugin) {
        if (plugin == null) {
            return;
        }
        ScreenDisplayPlugin olcCurrentPlugin = mCurrentPlugin;
        if (olcCurrentPlugin != null) {
            olcCurrentPlugin.onHide();
        }
        plugin.onShow();
        bringToFront();
        mCurrentPlugin = plugin;
        mCurrentPlugin.onSizeModeChanged(mSizeMode);
    }

    private void switchNextPlugin() {
        if (!mPagePlugin.isEmpty()) {
            if (mCurrentPlugin == null) {
                applyPlugin(mPagePlugin.get(0));
            } else {
                int curPosition = mPagePlugin.indexOf(mCurrentPlugin);
                int nextPosition = 0;
                if (curPosition < mPagePlugin.size() - 1){
                    nextPosition = Math.min(curPosition + 1, mPagePlugin.size() - 1);
                }
                applyPlugin(mPagePlugin.get(nextPosition));
            }
        }
    }

    private void showPluginSelectorPopup() {
        if (mSwitchModeView == null) {
            return;
        }
        dismissPluginSelectorPopup();
        syncPendingPlugins();
        List<ScreenDisplayPlugin> displayList = new ArrayList<>();
        displayList.addAll(mPagePlugin);
        displayList.addAll(mPendingPlugins);
        if (displayList.isEmpty()) {
            return;
        }
        Context context = getContext();
        ListPopupWindow popup = new ListPopupWindow(context);
        PluginSelectorAdapter adapter = new PluginSelectorAdapter(displayList, this::onPluginRowAction);
        int contentWidth = measurePopupContentWidth(context, adapter);
        popup.setAnchorView(mSwitchModeView);
        popup.setAdapter(adapter);
        popup.setContentWidth(contentWidth);
        popup.setHorizontalOffset(mSwitchModeView.getWidth() - contentWidth);
        popup.setBackgroundDrawable(createPopupBackground(context));
        popup.setModal(true);
        popup.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < displayList.size()) {
                ScreenDisplayPlugin plugin = displayList.get(position);
                if (mPendingPlugins.contains(plugin)) {
                    loadPendingPlugin(plugin);
                }
                applyPlugin(plugin);
            }
            popup.dismiss();
        });
        popup.setOnDismissListener(() -> mPluginSelectorPopup = null);
        popup.show();
        mPluginSelectorPopup = popup;
    }

    private void syncPendingPlugins() {
        Collection<Class<ScreenDisplayPlugin>> all = PluginManager.getInstance().getAllPlugin();
        for (Class<ScreenDisplayPlugin> clazz : all) {
            if (mBlacklistedPluginClasses.contains(clazz.getName())) {
                continue;
            }
            if (containsPluginOfClass(mPagePlugin, clazz) || containsPluginOfClass(mPendingPlugins, clazz)) {
                continue;
            }
            try {
                mPendingPlugins.add(clazz.newInstance());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean containsPluginOfClass(List<ScreenDisplayPlugin> list, Class<?> clazz) {
        for (ScreenDisplayPlugin p : list) {
            if (p.getClass() == clazz) {
                return true;
            }
        }
        return false;
    }

    private void loadPendingPlugin(ScreenDisplayPlugin plugin) {
        try {
            plugin.onLoad(mPluginContext, DebugView.this);
            mPagePlugin.add(plugin);
            mPendingPlugins.remove(plugin);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unloadSinglePlugin(ScreenDisplayPlugin plugin) {
        if (plugin == null) {
            return;
        }
        mBlacklistedPluginClasses.add(plugin.getClass().getName());
        mPendingPlugins.remove(plugin);
        mPagePlugin.remove(plugin);
        if (mCurrentPlugin == plugin) {
            mCurrentPlugin = null;
            if (!mPagePlugin.isEmpty()) {
                applyPlugin(mPagePlugin.get(0));
            }
        }
        try {
            plugin.onUnload();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onPluginRowAction(ScreenDisplayPlugin plugin, int action) {
        if (action != PluginSelectorAdapter.ACTION_UNLOAD) {
            return;
        }
        if (mPendingPlugins.contains(plugin)) {
            mBlacklistedPluginClasses.add(plugin.getClass().getName());
            mPendingPlugins.remove(plugin);
        } else {
            unloadSinglePlugin(plugin);
        }
        if (mPluginSelectorPopup != null) {
            showPluginSelectorPopup();
        }
    }

    private GradientDrawable createPopupBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp2px(context, 12));
        drawable.setStroke(1, Color.parseColor("#E0E0E0"));
        return drawable;
    }

    private int measurePopupContentWidth(Context context, PluginSelectorAdapter adapter) {
        int maxWidth = dp2px(context, 180);
        int padding = dp2px(context, 12);
        TextView nameView = new TextView(context);
        nameView.setPadding(0, padding, padding, padding);
        nameView.setTextSize(16);
        nameView.setTextColor(Color.parseColor("#212121"));
        nameView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView closeView = new TextView(context);
        closeView.setText("\u2715");
        closeView.setTextSize(16);
        closeView.setPadding(dp2px(context, 8), 0, padding, 0);
        closeView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < adapter.getCount(); i++) {
            ScreenDisplayPlugin plugin = (ScreenDisplayPlugin) adapter.getItem(i);
            nameView.setText(plugin != null && plugin.getName() != null ? plugin.getName() : "");
            nameView.measure(widthMeasureSpec, heightMeasureSpec);
            closeView.measure(widthMeasureSpec, heightMeasureSpec);
            int rowWidth = padding + nameView.getMeasuredWidth() + closeView.getMeasuredWidth();
            maxWidth = Math.max(maxWidth, rowWidth);
        }
        return maxWidth;
    }

    private static int dp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dp, context.getResources().getDisplayMetrics());
    }

    private void dismissPluginSelectorPopup() {
        if (mPluginSelectorPopup != null) {
            mPluginSelectorPopup.dismiss();
            mPluginSelectorPopup = null;
        }
    }

    private static final class PluginSelectorAdapter extends BaseAdapter {

        static final int ACTION_UNLOAD = 0;

        interface OnPluginRowAction {
            void onAction(ScreenDisplayPlugin plugin, int action);
        }

        private final List<ScreenDisplayPlugin> mPlugins;
        private final OnPluginRowAction mActionCallback;

        PluginSelectorAdapter(List<ScreenDisplayPlugin> plugins, OnPluginRowAction callback) {
            mPlugins = plugins;
            mActionCallback = callback;
        }

        @Override
        public int getCount() {
            return mPlugins.size();
        }

        @Override
        public Object getItem(int position) {
            return mPlugins.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context ctx = parent.getContext();
            LinearLayout row;
            TextView nameView;
            TextView closeView;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                nameView = (TextView) row.getChildAt(0);
                closeView = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = DebugView.dp2px(ctx, 12);
                row.setPadding(pad, pad, pad, pad);

                nameView = new TextView(ctx);
                nameView.setTextSize(16);
                nameView.setTextColor(Color.parseColor("#212121"));
                nameView.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                closeView = new TextView(ctx);
                closeView.setText("\u2715");
                closeView.setTextSize(16);
                closeView.setTextColor(Color.parseColor("#F44336"));
                closeView.setPadding(DebugView.dp2px(ctx, 8), 0, 0, 0);
                closeView.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                row.addView(nameView);
                row.addView(closeView);
            }
            ScreenDisplayPlugin plugin = mPlugins.get(position);
            nameView.setText(plugin != null && plugin.getName() != null ? plugin.getName() : "");
            ScreenDisplayPlugin target = plugin;
            closeView.setOnClickListener(v -> {
                if (mActionCallback != null) {
                    mActionCallback.onAction(target, ACTION_UNLOAD);
                }
            });
            return row;
        }
    }

}
