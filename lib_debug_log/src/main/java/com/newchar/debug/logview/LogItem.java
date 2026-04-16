package com.newchar.debug.logview;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.newchar.debug.utils.DebugUtils;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

/**
 * @author newChar
 * date 2022/6/15
 * @since Log实体数据类
 * @since 迭代版本，（以及描述）
 */
public final class LogItem {

    public static final int LEVEL_LOG_INFO = 1;
    public static final int LEVEL_LOG_WARN = 2;
    public static final int LEVEL_LOG_ERROR = 3;
    public static final int LEVEL_LOG_DEFAULT = LEVEL_LOG_ERROR;

    public static final LogUIConfig UI_CONFIG_ERROR_LOG = new LogUIConfig(Color.RED, Color.WHITE);
    public static final LogUIConfig UI_CONFIG_WARNING_LOG = new LogUIConfig(Color.YELLOW, Color.BLACK);
    public static final LogUIConfig UI_CONFIG_INFO_LOG = new LogUIConfig(Color.GREEN, Color.WHITE);

    //    private int mLogLevel = LEVEL_LOG_DEFAULT;
    private LogUIConfig mLogUIConfig = UI_CONFIG_ERROR_LOG;

    private List<Bitmap> mThumbnailImage;
    private WeakHashMap<Bitmap, Long> mImageLogRef = null;
    private String mLogText = "";

//    private byte mLogType = TYPE_LOG_TEXT;

    private long mLogSystemStamp = System.currentTimeMillis();
    private final SimpleDateFormat mDataFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public LogItem() {
    }

//    public LogItem(byte logType) {
//        this.mLogType = logType;
//    }

    public String getLogText() {
        return mLogText;
    }

    public void setLogText(String logText) {
        this.mLogText = logText;
//        setLogType(TYPE_LOG_TEXT);
    }

//    public byte getLogType() {
//        return mLogType;
//    }
//
//    public void setLogType(byte logType) {
//        this.mLogType = logType;
//    }

    public List<Bitmap> getThumbnailImage() {
        return mThumbnailImage;
    }

    public void setThumbnailImage(Bitmap... thumbnailImage) {
        if (mThumbnailImage == null) {
            this.mThumbnailImage = Arrays.asList(thumbnailImage);
        } else {
            mThumbnailImage.clear();
            mThumbnailImage.addAll(Arrays.asList(thumbnailImage));
        }
    }

    public LogUIConfig getLogUIConfig() {
        return mLogUIConfig;
    }

    public void setLogUIConfig(LogUIConfig logUIConfig) {
        this.mLogUIConfig = logUIConfig;
    }

    public String getLogTimeStamp() {
        return mDataFormat.format(new Date(mLogSystemStamp));
    }

    public void setLogTimeStamp(long logSystemStamp) {
        this.mLogSystemStamp = logSystemStamp;
    }

    public void setLogImage(Bitmap... image) {
        final WeakHashMap<Bitmap, Long> tempOldImages = mImageLogRef;
        mImageLogRef = new WeakHashMap<>(image.length);
        for (int i = 0; i < image.length; i++) {
            mImageLogRef.put(image[i], mLogSystemStamp + i);
        }
        if (tempOldImages != null) {
            tempOldImages.clear();
        }

        // 复用对象，缩略图对象处理
        final List<Bitmap> thumbnailImage = getThumbnailImage();
        if (DebugUtils.hasData(thumbnailImage)) {
            Iterator<Bitmap> iterator = thumbnailImage.iterator();
            while (iterator.hasNext()) {
                try {
                    Bitmap next = iterator.next();
                    if (!next.isRecycled()) {
                        next.recycle();
                    }
                    iterator.remove();
                } catch (Throwable ignored) {
                }
            }
        }
        Bitmap[] a = new Bitmap[image.length];
        for (int i = 0; i < image.length; i++) {
            Bitmap Image = DebugUtils.compressImage(image[i], 200, 100);
            a[i] = Image;
        }
        setThumbnailImage(a);
    }

    public Collection<Bitmap> getLogImage() {
        return mImageLogRef.keySet();
    }

    public static final class LogUIConfig {

        int itemBgColor = Color.RED;
        int itemTextColor = Color.WHITE;
        int foldLimit = 1;
        boolean isFolded = false;
        boolean isCopyEnable = true;

        public LogUIConfig(int itemBgColor, int itemTextColor) {
            this.itemBgColor = itemBgColor;
            this.itemTextColor = itemTextColor;
        }

        public int getItemBgColor() {
            return itemBgColor;
        }

        public void setItemBgColor(int itemBgColor) {
            this.itemBgColor = itemBgColor;
        }

        public int getItemTextColor() {
            return itemTextColor;
        }

        public void setItemTextColor(int itemTextColor) {
            this.itemTextColor = itemTextColor;
        }

        public int getFoldLimit() {
            return foldLimit;
        }

        /**
         * 设置文本折叠几行
         *
         * @param foldLimit 折叠行数
         */
        public void setFoldLimit(int foldLimit) {
            this.foldLimit = foldLimit;
        }

        public boolean isFolded() {
            return isFolded;
        }

        public void setFolded(boolean folded) {
            isFolded = folded;
        }

        public boolean isCopyEnable() {
            return isCopyEnable;
        }

        public void setCopyEnable(boolean copyEnable) {
            isCopyEnable = copyEnable;
        }
    }

}