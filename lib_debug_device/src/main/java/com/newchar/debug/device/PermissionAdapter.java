package com.newchar.debug.device;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.newchar.debug.device.bean.PermissionItem;
import com.newchar.debug.lifecycle.AppLifecycleManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限列表适配器。
 */
public final class PermissionAdapter extends BaseAdapter {

    private static final int COLOR_GRANTED = Color.parseColor("#006400");
    private static final int COLOR_DENIED = Color.parseColor("#FF00FF");
    private static final int COLOR_PERMANENTLY_DENIED = Color.parseColor("#FF0000");

    private static final int REQUEST_CODE_PERMISSION = 10001;
    private static final long REFRESH_DELAY_MILLIS = 500L;

    private final List<PermissionItem> mData = new ArrayList<>();

    public void refreshData(List<PermissionItem> data) {
        mData.clear();
        if (data != null) {
            mData.addAll(data);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = createItemView(parent);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.bind(mData.get(position));
        return convertView;
    }

    private static View createItemView(ViewGroup parent) {
        Context context = parent.getContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));

        TextView nameView = new TextView(context);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nameView.setTextColor(Color.WHITE);
        nameView.setId(View.generateViewId());
        layout.addView(nameView);

        TextView statusView = new TextView(context);
        statusView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        statusView.setId(View.generateViewId());
        layout.addView(statusView);

        return layout;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class ViewHolder {

        private final TextView nameView;
        private final TextView statusView;

        ViewHolder(View itemView) {
            LinearLayout layout = (LinearLayout) itemView;
            nameView = layout.findViewById(layout.getChildAt(0).getId());
            statusView = layout.findViewById(layout.getChildAt(1).getId());
        }

        void bind(PermissionItem item) {
            nameView.setText(item.getName());
            statusView.setTag(item);
            switch (item.getStatus()) {
                case PermissionItem.STATUS_GRANTED:
                    statusView.setText("已授权");
                    statusView.setTextColor(COLOR_GRANTED);
                    statusView.setOnClickListener(null);
                    statusView.setClickable(false);
                    break;
                case PermissionItem.STATUS_PERMANENTLY_DENIED:
                    statusView.setText("永久拒绝");
                    statusView.setTextColor(COLOR_PERMANENTLY_DENIED);
                    statusView.setOnClickListener(mRequestClickListener);
                    break;
                case PermissionItem.STATUS_DENIED:
                default:
                    statusView.setText("未授权");
                    statusView.setTextColor(COLOR_DENIED);
                    statusView.setOnClickListener(mRequestClickListener);
                    break;
            }
        }

    }

    private static final View.OnClickListener mRequestClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Object tag = v.getTag();
            if (!(tag instanceof PermissionItem)) {
                return;
            }
            requestPermission((PermissionItem) tag, v);
        }
    };

    private static void requestPermission(PermissionItem item, View anchor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        Activity activity = AppLifecycleManager.getInstance().getLastActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        activity.requestPermissions(new String[]{item.getPermission()}, REQUEST_CODE_PERMISSION);
        if (anchor != null) {
            anchor.postDelayed(() -> {
                DeviceMonitor monitor = DeviceMonitor.getInstance();
                if (monitor != null) {
                    monitor.updatePermissionInfo();
                }
            }, REFRESH_DELAY_MILLIS);
        }
    }

}
