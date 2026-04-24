package com.newchar.debug.log.plugin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.newchar.debug.common.utils.ViewUtils;
import com.newchar.debug.lifecycle.AppLifecycleManager;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.log.LogItem;
import com.newchar.debug.log.LogViewAdapter;

/**
 * @author newChar
 * date 2024/11/30
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class LogViewPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "LOG_VIEW";

    private ListView mDebugLogView;
    private LogViewAdapter mLogViewAdapter;
    private boolean mLog2Logcat;

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    protected View getView(Context context) {
        generateDataView(context);
        return mDebugLogView;
    }


    private ViewGroup.LayoutParams generateLayoutParams(ViewGroup containerView) {
        return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onShow() {
        if (mLogViewAdapter != null) {
            mLogViewAdapter.notifyDataSetChanged();
        }
        ViewUtils.setVisibility(mDebugLogView, View.VISIBLE);
        Log.e(id(), "onShow " + hashCode());
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(mDebugLogView, View.INVISIBLE);
        Log.e(id(), "onHide " + hashCode());
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup containerView) {
        View view = getView(containerView.getContext());
        view.setBackgroundColor(Color.BLACK);
        containerView.addView(view, generateLayoutParams(containerView));
        Log.e(id(), "onLoad " + hashCode());
    }

    @Override
    public void onUnload() {

    }

    public void i(String log, Bitmap... bitmaps) {
        log(LogItem.UI_CONFIG_INFO_LOG, log, bitmaps);
    }

    public void w(String log, Bitmap... bitmaps) {
        log(LogItem.UI_CONFIG_WARNING_LOG, log, bitmaps);
    }

    public void e(String log, Bitmap... bitmaps) {
        log(LogItem.UI_CONFIG_ERROR_LOG, log, bitmaps);
    }

    public void log(LogItem.LogUIConfig config, String log, Bitmap... bitmaps) {
        if (mLogViewAdapter != null) {
            mLogViewAdapter.addLogItem(config, log, bitmaps);
        }
        if (mLog2Logcat) {
            Log.e(AppLifecycleManager.getInstance().getLastActivity().getClass().getSimpleName(),
                    log);
        }
    }

    public void setLog2Logcat(boolean print) {
        mLog2Logcat = print;
    }

    private void initLogItemClickListener() {
        AdapterView.OnItemClickListener onLogItemClick
                = (parent, view, position, id) -> {

        };
        AdapterView.OnItemLongClickListener onLogItemLongClick
                = (parent, view, position, id) -> {
//            copyLogListViewItemLog(position);
            return true;
        };
        mDebugLogView.setOnItemClickListener(onLogItemClick);
        mDebugLogView.setOnItemLongClickListener(onLogItemLongClick);
    }

    private void generateDataView(Context context) {
        if (mDebugLogView == null) {
            mDebugLogView = new ListView(context);
            // 新加入到不做滚动处理，一直保持当前UI，如果是最后一条 一直保持最后一条的位置。
            mDebugLogView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        }
        if (mLogViewAdapter == null) {
            mLogViewAdapter = new LogViewAdapter();
        }
        if (mDebugLogView.getAdapter() == null) {
            mDebugLogView.setAdapter(mLogViewAdapter);
        }
        initLogItemClickListener();
    }

}
