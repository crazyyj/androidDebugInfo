package com.newchar.debug.browser;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author newChar
 * date 2024/3/18
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public class WebFileUtils {

    /**
     * App
     */
    public static Context appContext;

    private static File mWebViewAppCachePath;

    public static File getWebViewBasicCachePath() {
        if (mWebViewAppCachePath == null) {
            mWebViewAppCachePath = new File(appContext.getExternalCacheDir(), "webview_app_cache");
            mWebViewAppCachePath.mkdirs();
        }
        return mWebViewAppCachePath;
    }

    public static File getJsPath(Uri requestUri) {
        File jsBasicCachePath = new File(WebFileUtils.getWebViewBasicCachePath(), WebFileUtils.md5Decode16(requestUri.getHost()) + File.separator + "js");
        if (!jsBasicCachePath.exists()) {
            jsBasicCachePath.mkdirs();
        }
        return jsBasicCachePath;
    }

    public static File getCSSPath(Uri requestUri) {
        File jsBasicCachePath = new File(WebFileUtils.getWebViewBasicCachePath(), WebFileUtils.md5Decode16(requestUri.getHost()) + File.separator + "css");
        if (!jsBasicCachePath.exists()) {
            jsBasicCachePath.mkdirs();
        }
        return jsBasicCachePath;
    }

    /**
     * 32位MD5加密
     *
     * @param content -- 待加密内容
     * @return md5摘要值
     */
    public static String md5(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            //对生成的16字节数组进行补零操作
            final StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                if ((b & 0xFF) < 0x10) {
                    hex.append("0");
                }
                hex.append(Integer.toHexString(b & 0xFF));
            }
            return hex.toString();
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 16位MD5加密
     * 实际是截取的32位加密结果的中间部分(8-24位)
     *
     * @param content md5原始
     * @return 16位的 md5
     */
    public static String md5Decode16(String content) {
        String md5_32 = md5(content);
        if (md5_32 != null) {
            return md5_32.substring(8, 24);
        }
        return null;
    }

    public static boolean copyTo(InputStream inputStream, OutputStream outputStream) {
        byte[] buffer = new byte[4096];
        int readSize = -1;
        try {
            while ((readSize = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, readSize);
                outputStream.flush();
            }
            return true;
        } catch (IOException ignored) {
        }
        return false;
    }

}
