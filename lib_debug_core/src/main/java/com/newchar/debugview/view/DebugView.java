package com.newchar.debugview.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.newchar.debug.common.utils.Prompt;
import com.newchar.debugview.api.PluginContext;
import com.newchar.debugview.api.PluginManager;
import com.newchar.debugview.api.ScreenDisplayPlugin;
import com.newchar.debugview.utils.MoveTouchListener;

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

    /**
     * 当前View的id
     */
    private static final int VIEW_ID_ROOT_VIEW = View.generateViewId();
    /**
     * 复制全部
     */
    private TextView mCopyView;
    private static final int VIEW_ID_COPY_VIEW = View.generateViewId();

    /**
     * 折叠
     */
    private TextView mFoldView;
    private static final int VIEW_ID_FOLD_VIEW = View.generateViewId();

    /*
     * 停止（停止继续收集Log/日志等信息）
     */
//    private TextView mResumePauseView;
//    private static final int VIEW_ID_STOP_VIEW = View.generateViewId();

    /**
     * 清空
     */
    private TextView mClearView;
    private static final int VIEW_ID_CLEAR_VIEW = View.generateViewId();

    /**
     * 切换日志/系统数据模式
     */
    private TextView mSwitchModeView;
    private static final int VIEW_ID_SWITCH_MODE_VIEW = View.generateViewId();

//    private LinearLayout mTitleController;
    public static final int BUTTON_PADDING_TOP_BOTTOM = 12;
    public static final int BUTTON_PADDING_LEFT_RIGHT = 10;

    public static final String TEXT_FOLD = "折叠";
    public static final String TEXT_UNFOLD = "展开";
    public static final String TEXT_COPY = "复制";
    public static final String TEXT_PAUSE = "停止";
    public static final String TEXT_FRESH = "刷新"; // 强制刷新监控情况
    public static final String TEXT_RESUME = "恢复";
    public static final String TEXT_CLEAR = "清空";
    public static final String TEXT_SWITCH = "模式";

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

        titleController.addView(mCopyView);
        titleController.addView(mFoldView);
        titleController.addView(mClearView);
        titleController.addView(mSwitchModeView);

        addView(titleController);
    }

    /**
     * 清空全部日志, 刷新全部数据
     *
     * @param context Context
     */
    private void initClearView(Context context) {
        mClearView = genTextView(context, VIEW_ID_CLEAR_VIEW);
        mClearView.setText(TEXT_CLEAR);
        mClearView.setOnClickListener(clearView -> {
            Prompt.show_long("弹出了");
        });
        mClearView.setOnLongClickListener(clearView -> {
            //
            return true;
        });
    }

    /**
     * 初始化折叠view
     *
     * @param context context
     */
    private void initFoldView(Context context) {
        mFoldView = genTextView(context, VIEW_ID_FOLD_VIEW);
        mFoldView.setText(TEXT_FOLD);
        mFoldView.setOnClickListener(foldView -> {

        });
        mFoldView.setOnLongClickListener(foldView -> {
//
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



    /**
     * 保存全部Log到cache目录
     */
    private void saveLogListViewAllLog() {

    }

    /**
     * 复制全部数据
     */
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

    /**
     * 设置拖动处理器。
     *
     * @param moveHandler 拖动处理器
     */
    public void setMoveHandler(MoveTouchListener.MoveHandler moveHandler) {
        mMoveHandler = moveHandler;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        DebugViewStore.attach(this);
        try {
            mPluginContext.mApp = getContext().getApplicationContext();
            // TitleView区域回调.
            generateTitleController();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 内容区域回调.
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
        // 是触发事件向下传递, 还是下边提前准备好, 反向设置上来.
        // 文本样式,还是需要底部提供,以及不同插件 设置的功能 个数, 需要提前准备好, 去下层取. 以及不显示 的使用某些插件的逻辑.
        // 可以使用 简单的任务处理 做到不阻塞.更快处理. 具体 View 是否是全局数据, 使用插件内部自行实现.
        for (Class<ScreenDisplayPlugin> screenDisplayPlugin : allPlugin) {
            try {
                ScreenDisplayPlugin plugin = screenDisplayPlugin.newInstance();
                plugin.onLoad(mPluginContext, this);
                mPagePlugin.add(plugin);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (!mPagePlugin.isEmpty()) {
            applyPlugin(mPagePlugin.get(0));
        }
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

    /**
     * 应用指定插件
     *
     * @param plugin 插件
     */
    private void applyPlugin(ScreenDisplayPlugin plugin) {
        if (plugin == null) {
            return;
        }
        ScreenDisplayPlugin olcCurrentPlugin = mCurrentPlugin;
        if (olcCurrentPlugin != null) {
            olcCurrentPlugin.onHide();
        }
        // 切换插件
        plugin.onShow();
        // 防止被遮挡
        bringToFront();
//        setElevation();
        mCurrentPlugin = plugin;
    }

    /**
     * 展示下一个插件
     */
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
