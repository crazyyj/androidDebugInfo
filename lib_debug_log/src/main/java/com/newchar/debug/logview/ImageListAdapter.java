package com.newchar.debug.logview;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.newchar.debug.common.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author newChar
 * date 2023/4/21
 * @since 列表中的九宫格图片
 * @since 迭代版本，（以及描述）
 */
class ImageListAdapter extends BaseAdapter {

    public static final int ID_VIEW_IMAGE = View.generateViewId();

    // 最多九张图片。
    private final List<Bitmap> mMultiImage;

    public ImageListAdapter() {
        mMultiImage = new ArrayList<>(9);
    }

    @Override
    public int getCount() {
        return mMultiImage.size();
    }

    @Override
    public Object getItem(int position) {
        return mMultiImage.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ImageListViewHolder viewHolder;
        if (convertView == null) {
            convertView = genItemChildImageView(parent.getContext());
            viewHolder = new ImageListViewHolder(convertView);
        } else {
            viewHolder = (ImageListViewHolder) convertView.getTag();
        }
        viewHolder.updateData(mMultiImage.get(position));
        return convertView;
    }

    public void notifyDataSetChanged(List<Bitmap> data) {
        mMultiImage.clear();
        mMultiImage.addAll(data);
        super.notifyDataSetChanged();
    }

    private static final class ImageListViewHolder {

        private final ImageView mImageView;

        private ImageListViewHolder(View itemView) {
            mImageView = itemView.findViewById(ID_VIEW_IMAGE);
            itemView.setTag(this);
        }

        void updateData(Bitmap bitmap) {
            if (bitmap!= null && !bitmap.isRecycled()) {
                mImageView.setImageBitmap(bitmap);
            }
        }

    }

    private static ImageView genItemChildImageView(Context context) {
        ImageView itemImageView = new ImageView(context);
        itemImageView.setId(ImageListAdapter.ID_VIEW_IMAGE);
        itemImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.getViewContainerWidth() / 4);
        itemImageView.setLayoutParams(layoutParams);
        return itemImageView;
    }

}
