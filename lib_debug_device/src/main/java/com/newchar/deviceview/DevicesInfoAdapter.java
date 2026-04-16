package com.newchar.deviceview;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * @author newChar
 * date 2022/8/5
 * @since 设备信息Adapter
 * @since 迭代版本，（以及描述）
 */
public class DevicesInfoAdapter extends BaseAdapter {

    private final List<DevicesInfo> mAllDevicesInfo;

    private static final int ID_TEXT_CONTENT = View.generateViewId();

    public DevicesInfoAdapter() {
        super();
        this.mAllDevicesInfo = new ArrayList<>();
        mAllDevicesInfo.add(new DevicesInfo(DevicesInfo.ITEM_TYPE_CPU));
        mAllDevicesInfo.add(new DevicesInfo(DevicesInfo.ITEM_TYPE_FRAME));
        mAllDevicesInfo.add(new DevicesInfo(DevicesInfo.ITEM_TYPE_MEMORY));
        mAllDevicesInfo.add(new DevicesInfo(DevicesInfo.ITEM_TYPE_TRAFFIC));
        mAllDevicesInfo.add(new DevicesInfo(DevicesInfo.ITEM_TYPE_STORAGE));
        mAllDevicesInfo.add(new DevicesInfo(DevicesInfo.ITEM_TYPE_DEVICES));
    }

    @Override
    public int getCount() {
        return mAllDevicesInfo.size();
    }

    @Override
    public Object getItem(int position) {
        return mAllDevicesInfo.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        DevicesInfo devicesInfo = mAllDevicesInfo.get(position);
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = generateItemView(parent.getContext());
            viewHolder = new ViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        viewHolder.setItemText(devicesInfo.text);
        return convertView;
    }

    public void notifyDataChanged(int type, String content) {
//        if (content != null && type == DevicesInfo.ITEM_TYPE_FRAME) {
//            View itemView = listView.getChildAt(DevicesInfo.ITEM_TYPE_FRAME - 1);
//            Object tag;
//            if (itemView != null && (tag = itemView.getTag()) instanceof ViewHolder) {
//                ((ViewHolder) tag).setItemText(content);
//                mAllDevicesInfo.get(DevicesInfo.ITEM_TYPE_FRAME - 1).text = content;
//            }
//        } else if (content != null && type != 0) {
            DevicesInfo devicesInfo = mAllDevicesInfo.get(type - 1);
            if (!TextUtils.equals(devicesInfo.text, content)) {
                devicesInfo.text = content;
                super.notifyDataSetChanged();
            }
//        }
    }

    private View generateItemView(Context context) {
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Color.argb(188, 0, 0, 255));
        frameLayout.setPadding(10,6, 10, 6);
        TextView itemContentView = genItemTextView(context);
        frameLayout.addView(itemContentView);
        return frameLayout;
    }

    private TextView genItemTextView(Context context) {
        TextView textView = new TextView(context);
        textView.setId(ID_TEXT_CONTENT);
        textView.setTextColor(Color.WHITE);
//        textView.setMaxLines(Integer.MAX_VALUE);
        return textView;
    }

    public final static class DevicesInfo {

        public static final int ITEM_TYPE_UN = 0;
        public static final int ITEM_TYPE_CPU = 1;
        public static final int ITEM_TYPE_FRAME = 2;
        public static final int ITEM_TYPE_MEMORY = 3;
        public static final int ITEM_TYPE_STORAGE = 4;
        public static final int ITEM_TYPE_DEVICES = 5;
        public static final int ITEM_TYPE_TRAFFIC = 6;

        public int type;
        public String text;

        public DevicesInfo(int type) {
            this.type = type;
        }
    }

    private final static class ViewHolder {

        TextView textView;

        public ViewHolder(View itemView) {
            this.textView = itemView.findViewById(ID_TEXT_CONTENT);
        }

        public void setItemText(String text) {
            if (textView != null) {
                textView.setText(text);
            }
        }

    }

}
