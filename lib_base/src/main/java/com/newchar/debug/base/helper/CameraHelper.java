package com.newchar.debug.base.helper;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;

import com.newchar.debug.base.utils.IOUtils;

public class CameraHelper {

    public final static int CROP_IMAGE_REQUESTCODE = 10;
    public final static int OPEN_GALLER_REQUESTCODE = 20;
    public final static int OPEN_CAPTURE_REQUESTCODE = 30;

    private CameraHelper() {
    }

    /**
     * 调用系统裁剪图片, 一般用于头像上传前
     * <li>requestCode = 20</li>
     *
     * @param uri 图片资源
     */
    private static Intent getCropImageIntent(Uri uri) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(uri, IOUtils.FileType.IMAGE);
        intent.putExtra("crop", "true");
        intent.putExtra("return-data", true);
        intent.putExtra("noFaceDetection", true);
        intent.putExtra("outputX", 256);
        intent.putExtra("outputY", 256);
        intent.putExtra("aspectY", 1);
        intent.putExtra("aspectX", 1);
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        return intent;
    }

    /**
     * 无权限，尝试直接打开。
     *
     * @param act 发返回拿到数据的Activity
     * @param uri 图片数据
     */
    public static void openCropImage(Activity act, Uri uri) {
        Intent cropImageIntent = getCropImageIntent(uri);
        if (cropImageIntent.resolveActivity(act.getPackageManager()) != null) {
            act.startActivityForResult(cropImageIntent, CROP_IMAGE_REQUESTCODE);
        }
    }

    public static void openCapture(Activity act, String FileName) {
        Intent intentForCapture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE_SECURE); // --打开相机
        intentForCapture.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(IOUtils.PROJECT_DIR, FileName)));
        act.startActivityForResult(intentForCapture, OPEN_CAPTURE_REQUESTCODE);
    }

    public static void openGaller(Activity act) {
        Intent intentFromGallery = new Intent(Intent.ACTION_PICK);    //--相册Action
        intentFromGallery.setType(IOUtils.FileType.IMAGE);                        // 设置文件类型
        act.startActivityForResult(intentFromGallery, OPEN_GALLER_REQUESTCODE);
    }


}
