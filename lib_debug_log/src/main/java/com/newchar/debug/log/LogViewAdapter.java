package com.newchar.debug.log;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.newchar.debug.utils.UIUtils;
import com.newchar.debug.utils.ViewUtils;
import com.newchar.debug.utils.DebugUtils;
//import com.newchar.debug.utils.DebugUtils;
//import com.newchar.debug.view.DebugView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author newChar
 * date 2022/6/15
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class LogViewAdapter extends BaseAdapter implements AbsListView.OnScrollListener {

    private final List<LogItem> mLogListData;
    /**
     * 是否折叠，0 不折叠
     */
    private int mShowLimit = 0;

    //    public static final int VIEW_ID_COPY_ITEM = View.generateViewId();
    public static final int VIEW_ID_IMAGE_ITEM = View.generateViewId();
    public static final int VIEW_ID_DESC_ITEM = View.generateViewId();
    public static final int VIEW_ID_FOLD_ITEM = View.generateViewId();
    public static final int VIEW_ID_TEXT_CONTENT = View.generateViewId();

    /**
     * 第一个可见的item索引
     */
    private int mFirstVisibleIndex = 0;

    /**
     * 一共有多少条可见。
     */
    private int mVisibleItemCount = 0;

    public LogViewAdapter() {
        this.mLogListData = new ArrayList<>();
    }

    @Override
    public int getCount() {
        return isFold() ? Math.min(mShowLimit, mLogListData.size()) : mLogListData.size();
    }

    @Override
    public Object getItem(int position) {
        if (isFold()) {
            if (mLogListData.size() >= mShowLimit) {
                position = mLogListData.size() - mShowLimit + position;
            }
        }
        return mLogListData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        BaseLogViewHolder logViewHolder;

        if (isFold()) {
            if (mLogListData.size() >= mShowLimit) {
                position = mLogListData.size() - mShowLimit + position;
            }
        }
        LogItem realItemData = mLogListData.get(position);

        if (convertView == null) {
            convertView = generateItemView(parent.getContext());
            logViewHolder = new LogItemViewHolder(convertView);
        } else {
            logViewHolder = (BaseLogViewHolder) convertView.getTag();
        }

        logViewHolder.updateData(realItemData);
        return convertView;
    }

    public void notifyDataAddChanged(LogItem logItem) {
        mLogListData.add(logItem);
        super.notifyDataSetChanged();
    }

    public void notifyDataSetChanged(List<LogItem> logItem) {
        mLogListData.clear();
        mLogListData.addAll(logItem);
        super.notifyDataSetChanged();
    }

    public void clearListData() {
        if (DebugUtils.hasData(mLogListData)) {
            mLogListData.clear();
            super.notifyDataSetChanged();
        }
    }

    private View generateItemView(Context context) {
        LinearLayout itemView = new LinearLayout(context);
        itemView.setOrientation(LinearLayout.VERTICAL);
        itemView.setPadding(
                12, 12,
                12, 12);
//
        TextView logTextView = initLogTextView(context);
        LinearLayout.LayoutParams thumbnailDescParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        itemView.addView(logTextView, thumbnailDescParams);
//
        GridView imageContainerView = genItemImageContainerView(context);
        imageContainerView.setId(VIEW_ID_IMAGE_ITEM);
        imageContainerView.setAdapter(new ImageListAdapter());
        itemView.addView(imageContainerView);
        return itemView;
    }

    /**
     * 生成图片容器ItemView
     */
    private GridView genItemImageContainerView(Context viewAttachContext) {
        GridView imageGridView = new ItemLogImageView(viewAttachContext);
        imageGridView.setVerticalSpacing(ItemLogImageView.VERTICAL_SPACING);
        imageGridView.setHorizontalSpacing(ItemLogImageView.HORIZONTAL_SPACING);
        return imageGridView;
    }


    private TextView initLogTextView(Context context) {
        TextView logTextView = new TextView(context);
        logTextView.setId(VIEW_ID_TEXT_CONTENT);
        logTextView.setTextSize(16);
        return logTextView;
    }

    private TextView initItemFoldIcon(Context context) {
        TextView foldIconView = new TextView(context);
        foldIconView.setId(VIEW_ID_FOLD_ITEM);
        return foldIconView;
    }

    private TextView initDescItemChild(Context context) {
        TextView imageViewDesc = new TextView(context);
        imageViewDesc.setId(VIEW_ID_DESC_ITEM);
        return imageViewDesc;
    }

    /**
     * 当前Item是否是折叠的
     *
     * @return true 是折叠的
     */
    public boolean isFold() {
        return mShowLimit > 0;
    }

    public int getShowLimit() {
        return mShowLimit;
    }

    public void setShowLimit(int mShowLimit) {
        this.mShowLimit = mShowLimit;
        if (mLogListData.size() >= mShowLimit) {
            notifyDataSetChanged();
        }
    }

    // 添加item改为异步， 有图片加载
    public void addLogItem(LogItem.LogUIConfig config, String log, Bitmap... image) {
        final LogItem logItem = new LogItem();
        if (config != null) {
            logItem.setLogUIConfig(config);
        }

        if (!TextUtils.isEmpty(log)) {
            logItem.setLogText(log);
        } else {
            Log.e("LogView", "此次添加LogItem，无文本信息 ");
        }

        if (image != null && image.length > 0) {
            logItem.setLogImage(image);
        } else {
            Log.e("LogView", "此次添加LogItem，无图片信息, 日志信息: "+ log);
        }

        notifyDataAddChanged(logItem);
    }

    public void addLogItem(Bitmap image) {
        addLogItem(null, null, image);
    }

    public void addLogItem(String logText) {
        addLogItem(null, logText);
    }

    public void saveLogListViewAllLog(String path) {
        if (mLogListData.isEmpty()) {
            return;
        }
        @SuppressLint("SimpleDateFormat") final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        File logFilePath = new File(path, "screenLog" + File.separator
                + simpleDateFormat.format(new Date()));
        if (logFilePath.exists() || logFilePath.mkdirs()) {
            File logFile = new File(logFilePath, System.currentTimeMillis() + ".log");
            try (Writer writer = new BufferedWriter(new FileWriter(logFile))) {
                // mLogListData copy 一份数据，进行本地序列化
                for (LogItem logListData : mLogListData) {
                    writer.write(logListData.getLogTimeStamp());
                    writer.write(' ');
                    writer.write(logListData.getLogText());
                    writer.write(System.lineSeparator());
                }
                writer.flush();
            } catch (Exception e) {
                Log.e("DebugView", logFile.getAbsolutePath(), e);
            }
        }
    }

    /**
     * 复制 某条数据中的文本
     *
     * @param context  context
     * @param position 要复制数据是第几条的；
     */
    public void copyLogItemLogText(Context context, int position) {
        if (mLogListData.isEmpty()) {
            Toast.makeText(context, "复制失败，没有Log可供复制" + System.lineSeparator()
                    , Toast.LENGTH_SHORT).show();
            return;
        }
        if (mLogListData.size() > position) {
            if (DebugUtils.copy(context, mLogListData.get(position).getLogText())) {
                Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void copyLogListViewAllLog(Context context) {
        if (mLogListData.isEmpty()) {
            Toast.makeText(context, "复制失败，没有Log可供复制"
                    , Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder stringBuilder = new StringBuilder();
        LogItem logListDatum;
        for (int i = 0; i < mLogListData.size(); i++) {
            logListDatum = mLogListData.get(i);
            stringBuilder.append(logListDatum.getLogTimeStamp()).append(':');
            stringBuilder.append(logListDatum.getLogText());
            stringBuilder.append(System.lineSeparator());
        }

        String copyContent = stringBuilder.toString();
        if (!copyContent.isEmpty() && DebugUtils.copy(context, copyContent)) {
            Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
//        isViewScroll = scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
        switch (scrollState) {
            case AbsListView.OnScrollListener.SCROLL_STATE_IDLE:
                postLoadImage(view, mLogListData);
                break;
//            case AbsListView.OnScrollListener.SCROLL_STATE_FLING:
//            case AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
//                break;
//            default:
//                break;
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        mFirstVisibleIndex = firstVisibleItem;
        mVisibleItemCount = visibleItemCount;
    }

    /*
     *
     * @param view 列表View
     * @param listData  列表数据
     */
    private void postLoadImage(AbsListView view, List<LogItem> listData) {
        for (int i = 0; i < mVisibleItemCount; i++) {
            int operateItemIndex = mFirstVisibleIndex + i;
            View visibleChild = view.getChildAt(operateItemIndex);
            // 找到需要更新的View，去更新View，以及bean数据。（一般这时会检测bean数据，选择性质更新）
            LogItem logItem = listData.get(operateItemIndex);
            if (visibleChild == null || logItem == null) {
                return;
            }
            if (DebugUtils.hasData(logItem.getThumbnailImage())) {
                Object viewHolder = visibleChild.getTag();
                if (viewHolder instanceof BaseLogViewHolder) {
                    ((BaseLogViewHolder) viewHolder).updateData(logItem);
                }
            }
        }
    }

    private static final class LogItemViewHolder extends BaseLogViewHolder {

        private final TextView mLogTextView;
        //        private final TextView mFoldIconView;
        private final GridView mImageContainerView;
        private final ImageListAdapter mImageListAdapter;

        private LogItemViewHolder(View itemView) {
            super(itemView);
            mLogTextView = itemView.findViewById(VIEW_ID_TEXT_CONTENT);
//            mCopyItemView = mItemView.findViewById(VIEW_ID_COPY_ITEM);
//            mFoldIconView = itemView.findViewById(VIEW_ID_FOLD_ITEM);
            mImageContainerView = itemView.findViewById(VIEW_ID_IMAGE_ITEM);
            mImageListAdapter = (ImageListAdapter) mImageContainerView.getAdapter();

//            mCopyItemView.setText("复");
//            mCopyItemView.setGravity(Gravity.CENTER_VERTICAL);
//            mCopyItemView.setOnClickListener(copyItemView -> {
////              复制
//                Context context = copyItemView.getContext();
//                String copyContent = mLogTextView.getText().toString();
//                if (copy(context, copyContent)) {
//                    Toast.makeText(context, "复制成功" + System.lineSeparator()
//                            + copyContent, Toast.LENGTH_SHORT).show();
//                } else {
//                    Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show();
//                }
//            });
//            mFoldIconView.setGravity(Gravity.CENTER_HORIZONTAL);
//            mFoldIconView.setOnClickListener(foldItemView -> {
//                 获取Item数据，设置折叠属性，并刷新UI
//            });
        }

        @Override
        protected void updateData(LogItem logItem) {
            LogItem.LogUIConfig logUIConfig = logItem.getLogUIConfig();
            getItemView().setBackgroundColor(logUIConfig.itemBgColor);
            if (logUIConfig.isFolded) {
//                mFoldIconView.setText("∨");
                mLogTextView.setMaxLines(logUIConfig.getFoldLimit());
            } else {
//                mFoldIconView.setText("∧");
                mLogTextView.setMaxLines(Integer.MAX_VALUE);
            }
            mLogTextView.setText(logItem.getLogText());
            mLogTextView.setTextColor(logUIConfig.itemTextColor);

            final List<Bitmap> thumbnailImage = logItem.getThumbnailImage();
            if (thumbnailImage != null && !thumbnailImage.isEmpty()) {
                final int numColumns = adjustGridViewColumns(thumbnailImage.size());
                mImageContainerView.setNumColumns(numColumns);

                if (numColumns < 3) {
                    ViewUtils.setWidthHeight(mImageContainerView,
                            UIUtils.getScreenWidth() / 3 * 2 - ItemLogImageView.HORIZONTAL_SPACING * 2,
                            AbsListView.LayoutParams.WRAP_CONTENT);
                } else {
                    ViewUtils.setWidthHeight(mImageContainerView,
                            AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT);
                }
                mImageListAdapter.notifyDataSetChanged(thumbnailImage);
                ViewUtils.setVisibility(mImageContainerView, View.VISIBLE);
            } else {
                ViewUtils.setVisibility(mImageContainerView, View.GONE);
            }
        }

        private static int adjustGridViewColumns(int imageSize) {
            final int numColumns;
            if (imageSize > 4 || imageSize == 3) {
                numColumns = 3;
            } else if (imageSize == 2 || imageSize == 4) {
                numColumns = 2;
            } else {
                numColumns = 1;
            }
            return numColumns;
        }

    }

    private static abstract class BaseLogViewHolder {

        private final View mItemView;

        public BaseLogViewHolder(View itemView) {
            mItemView = itemView;
            itemView.setTag(this);
        }

        public View getItemView() {
            return mItemView;
        }

        abstract void updateData(LogItem itemData);

    }

}
