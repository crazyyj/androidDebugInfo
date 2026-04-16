package com.newchar.debug.net.plugin;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.newchar.debug.common.utils.ViewUtils;
import com.newchar.debug.net.DebugNetEvent;
import com.newchar.debug.net.DebugNetMonitor;
import com.newchar.debug.net.DebugNetTrafficListener;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.utils.HandleWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用 VPN 捕获当前 App 网络包，并用列表展示每次通信记录。
 */
public class DebugNetPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "DEBUG_NET";

    private static final int MAX_EVENT_COUNT = 300;
    private static final int MAX_FLUSH_BATCH = 60;

    private final List<DebugNetEvent> mEvents = new ArrayList<>();
    private final ConcurrentLinkedQueue<DebugNetEvent> mPendingEvents = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean mFlushScheduled = new AtomicBoolean(false);

    private LinearLayout mRootView;
    private TextView mStatusView;
    private TrafficAdapter mAdapter;

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        initView(pluginContainerView.getContext());
        pluginContainerView.addView(mRootView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        DebugNetMonitor.addListener(mTrafficListener);
        updateStatus();
        ViewUtils.setVisibility(mRootView, View.GONE);
    }

    @Override
    public void onShow() {
        updateStatus();
        ViewUtils.setVisibility(mRootView, View.VISIBLE);
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(mRootView, View.GONE);
    }

    @Override
    public void onUnload() {
        DebugNetMonitor.removeListener(mTrafficListener);
        HandleWrapper.getMainHandler().removeCallbacks(mFlushTask);
        mPendingEvents.clear();
        mFlushScheduled.set(false);
        mEvents.clear();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        mRootView = null;
        mStatusView = null;
        mAdapter = null;
    }

    private void initView(Context context) {
        if (mRootView != null) {
            return;
        }
        mRootView = new LinearLayout(context);
        mRootView.setOrientation(LinearLayout.VERTICAL);
        mRootView.setBackgroundColor(0x4D808080);

        LinearLayout actionBar = new LinearLayout(context);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);

        Button startButton = new Button(context);
        startButton.setText("启动VPN监听");
        startButton.setOnClickListener(v -> {
            boolean started = DebugNetMonitor.start(context);
            mStatusView.setText(started ? "VPN监听启动中" : "需要授权VPN，授权后再次点击启动");
        });

        Button stopButton = new Button(context);
        stopButton.setText("停止");
        stopButton.setOnClickListener(v -> {
            DebugNetMonitor.stop(context);
            updateStatus();
        });

        Button clearButton = new Button(context);
        clearButton.setText("清空");
        clearButton.setOnClickListener(v -> {
            mEvents.clear();
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        });

        mStatusView = new TextView(context);
        mStatusView.setTextColor(Color.DKGRAY);
        mStatusView.setPadding(12, 0, 12, 0);

        actionBar.addView(startButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actionBar.addView(stopButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actionBar.addView(clearButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ListView listView = new ListView(context);
        mAdapter = new TrafficAdapter(context, mEvents);
        listView.setAdapter(mAdapter);

        mRootView.addView(actionBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mRootView.addView(mStatusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mRootView.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));
    }

    private void updateStatus() {
        if (mStatusView == null) {
            return;
        }
        mStatusView.setText(DebugNetMonitor.isRunning()
                ? "VPN监听中：记录当前 App TUN 包头"
                : "VPN未启动");
    }

    private void enqueueEvent(DebugNetEvent event) {
        if (event == null) {
            return;
        }
        mPendingEvents.offer(event);
        if (mFlushScheduled.compareAndSet(false, true)) {
            HandleWrapper.getMainHandler().post(mFlushTask);
        }
    }

    private final Runnable mFlushTask = new Runnable() {
        @Override
        public void run() {
            int count = 0;
            DebugNetEvent event;
            while (count < MAX_FLUSH_BATCH && (event = mPendingEvents.poll()) != null) {
                mEvents.add(0, event);
                count++;
            }
            while (mEvents.size() > MAX_EVENT_COUNT) {
                mEvents.remove(mEvents.size() - 1);
            }
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
            updateStatus();
            if (!mPendingEvents.isEmpty()) {
                HandleWrapper.getMainHandler().post(this);
                return;
            }
            mFlushScheduled.set(false);
            if (!mPendingEvents.isEmpty() && mFlushScheduled.compareAndSet(false, true)) {
                HandleWrapper.getMainHandler().post(this);
            }
        }
    };

    private final DebugNetTrafficListener mTrafficListener = new DebugNetTrafficListener() {
        @Override
        public boolean onTrafficEvent(DebugNetEvent event) {
            enqueueEvent(event);
            return true;
        }
    };

    private static final class TrafficAdapter extends ArrayAdapter<DebugNetEvent> {

        TrafficAdapter(Context context, List<DebugNetEvent> events) {
            super(context, android.R.layout.simple_list_item_1, events);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            DebugNetEvent event = getItem(position);
            if (view instanceof TextView && event != null) {
                TextView textView = (TextView) view;
                textView.setText(event.getDisplayText());
                textView.setTextColor(event.getTextColor());
            }
            return view;
        }
    }
}
