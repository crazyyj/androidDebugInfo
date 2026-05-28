package com.newchar.debug.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.widget.Toast;

import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.PluginManager;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.utils.MoveTouchListener;
import com.newchar.debug.utils.UIUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    private TextView mSizeView;
    private static final int VIEW_ID_SIZE_VIEW = View.generateViewId();

    public static final int BUTTON_PADDING_TOP_BOTTOM = 12;
    public static final int BUTTON_PADDING_LEFT_RIGHT = 10;

    public static final String TEXT_FOLD = "折叠";
    public static final String TEXT_UNFOLD = "展开";
    public static final String TEXT_COPY = "复制";
    public static final String TEXT_PAUSE = "停止";
    public static final String TEXT_FRESH = "刷新";
    public static final String TEXT_RESUME = "恢复";
    public static final String TEXT_CLEAR = "清空";
    public static final String TEXT_SWITCH = "模式";

    private static final int SIZE_MODE_DEFAULT = 0;
    private static final int SIZE_MODE_90_PERCENT = 1;
    private static final int SIZE_MODE_MINI = 2;

    private int mSizeMode = SIZE_MODE_DEFAULT;

    private final PluginContext mPluginContext = new PluginContext();
    private ScreenDisplayPlugin mCurrentPlugin = null;
    private final List<ScreenDisplayPlugin> mPagePlugin = new ArrayList<>();
    private MoveTouchListener.MoveHandler mMoveHandler;

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
        initSizeView(context);

        titleController.addView(mCopyView);
        titleController.addView(mFoldView);
        titleController.addView(mClearView);
        titleController.addView(mSwitchModeView);
        titleController.addView(mSizeView);

        addView(titleController);
    }

    private void initClearView(Context context) {
        mClearView = genTextView(context, VIEW_ID_CLEAR_VIEW);
        mClearView.setText(TEXT_CLEAR);
        mClearView.setOnClickListener(clearView -> {
            Toast.makeText(getContext(), "弹出了", Toast.LENGTH_LONG).show();
        });
        mClearView.setOnLongClickListener(clearView -> {
            return true;
        });
    }

    private void initFoldView(Context context) {
        mFoldView = genTextView(context, VIEW_ID_FOLD_VIEW);
        mFoldView.setText(TEXT_FOLD);
        mFoldView.setOnClickListener(foldView -> {

        });
        mFoldView.setOnLongClickListener(foldView -> {
            return true;
        });
    }

    private void initCopyView(Context context) {
        mCopyView = genTextView(context, VIEW_ID_COPY_VIEW);
        mCopyView.setText(TEXT_COPY);
        mCopyView.setOnClickListener(copyView ->
                copyLogListViewAllLog()
        );
        mCopyView.setOnLongClickListener(copyView -> {
            saveLogListViewAllLog();
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
            return true;
        });
    }

    private void initSizeView(Context context) {
        mSizeView = genTextView(context, VIEW_ID_SIZE_VIEW);
        mSizeView.setText("尺寸");
        mSizeView.setOnClickListener(v -> cycleSizeMode());
        mSizeView.setOnLongClickListener(v -> {
            return true;
        });
    }

    private void cycleSizeMode() {
        mSizeMode = (mSizeMode + 1) % 3;
        applySizeMode();
    }

    private void applySizeMode() {
        if (mSizeView != null) {
            switch (mSizeMode) {
                case SIZE_MODE_DEFAULT:
                    mSizeView.setText("尺寸");
                    break;
                case SIZE_MODE_90_PERCENT:
                    mSizeView.setText("90%");
                    break;
                case SIZE_MODE_MINI:
                    mSizeView.setText("迷你");
                    break;
            }
        }

        int screenWidth = UIUtils.getScreenWidth();
        int screenHeight = UIUtils.getScreenHeight();

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) {
            return;
        }

        switch (mSizeMode) {
            case SIZE_MODE_DEFAULT:
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                setPluginAreaVisible(true);
                break;
            case SIZE_MODE_90_PERCENT:
                lp.width = (int) (screenWidth * 0.9);
                lp.height = (int) (screenHeight * 0.9);
                setPluginAreaVisible(true);
                break;
            case SIZE_MODE_MINI:
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                setPluginAreaVisible(false);
                break;
        }

        setLayoutParams(lp);
    }

    private void setPluginAreaVisible(boolean visible) {
        int childCount = getChildCount();
        for (int i = 1; i < childCount; i++) {
            View child = getChildAt(i);
            child.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void saveLogListViewAllLog() {

    }

    private void copyLogListViewAllLog() {

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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        DebugViewStore.attach(this);
        try {
            mPluginContext.mApp = getContext().getApplicationContext();
            generateTitleController();
        } catch (Exception e) {
            e.printStackTrace();
        }
        loadPlugin();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
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

}
