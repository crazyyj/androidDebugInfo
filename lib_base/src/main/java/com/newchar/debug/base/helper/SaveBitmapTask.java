package com.newchar.debug.base.helper;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.os.AsyncTask;

import com.newchar.debug.base.utils.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author newlq
 * 将Bitmap存到本地的工具类
 */
public class SaveBitmapTask extends AsyncTask<String, Integer, Boolean> {

    //图片的质量
    private static final int Image_Quality = 100;

    //需要保存的保存的图片
    private Bitmap mBitmap;

    //文件夹路径
    private String savePath;
    private File imageFile;

    public SaveBitmapTask(Bitmap bm) {
        mBitmap = bm;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Boolean doInBackground(String... params) {
        boolean bmSaveSuccess = false;
        OutputStream stream = null;
        if (mBitmap != null && !mBitmap.isRecycled()) {
            try {
                File filePath = new File(buildTargetDir(params));
                if (filePath.exists() || filePath.mkdirs()) {
                    if (params[params.length - 1].endsWith(IOUtils.JPG)) {
                        imageFile = new File(filePath, params[params.length - 1]);
                    } else {
                        imageFile = new File(filePath, generateBMFileName());
                    }
                    stream = new FileOutputStream(imageFile);
                    bmSaveSuccess = mBitmap.compress(CompressFormat.JPEG, Image_Quality, stream) && imageFile.exists();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                IOUtils.close(stream);
            }
        }
        return bmSaveSuccess;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
//        if (result && imageFile != null && imageFile.exists()) {
        if (mSaveFinishListener != null) {
            mSaveFinishListener.onSaveFinishListener(result,
                    imageFile != null && imageFile.exists() ? imageFile.getAbsolutePath() : "");
        }
//        }
        onRecycle();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        if (mProgressUpdateListener != null)
            mProgressUpdateListener.progressUpdateListener(values);
    }

    protected void onRecycle() {
//        if (mBitmap != null && !mBitmap.isRecycled()) {
//            mBitmap.recycle();
//            mBitmap = null;
//            System.gc();
//        }
    }

    @Override
    protected void onCancelled(Boolean aBoolean) {
        super.onCancelled(aBoolean);
        onRecycle();
    }

//    @Override
//    protected void onCancelled() {
//        super.onCancelled();
//        onRecycle();
//    }

    /**
     * 构建文件夹路径字符串
     *
     * @param dirs 文件夹名称数组
     * @return
     */
    private String buildTargetDir(String[] dirs) {
        StringBuilder temp_dir = new StringBuilder();
        int length;
        if (dirs[dirs.length - 1].endsWith(IOUtils.JPG)) {
            length = dirs.length - 1;
        } else {
            length = dirs.length;
        }
        for (int i = 0; i < length; i++) {
            temp_dir.append(dirs[i])
                    .append(File.separator);
        }
        return temp_dir.toString();
    }

    private String generateBMFileName() {
        return new SimpleDateFormat("yyMMddHHmmssSS")
                .format(new Date(System.currentTimeMillis())) + IOUtils.JPG;
    }

    private SaveFinishListener mSaveFinishListener;
    private ProgressUpdateListener mProgressUpdateListener;

    public void setSaveFinishListener(SaveFinishListener saveFinishListener) {
        this.mSaveFinishListener = saveFinishListener;
    }

    public void setProgressUpdateListener(ProgressUpdateListener progressUpdateListener) {
        this.mProgressUpdateListener = progressUpdateListener;
    }

    public interface ProgressUpdateListener {
        void progressUpdateListener(Integer... values);
    }

    public interface SaveFinishListener {
        void onSaveFinishListener(boolean result, String path);
    }
}
