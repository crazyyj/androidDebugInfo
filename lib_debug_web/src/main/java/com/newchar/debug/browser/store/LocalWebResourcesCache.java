package com.newchar.debug.browser.store;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.newchar.debug.browser.WebFileUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author newChar
 * date 2024/3/21
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
final class LocalWebResourcesCache extends WebResourcesCache {

//    DiskLruCache diskCache = new DiskLruCache(FileSystem.SYSTEM, )

    public LocalWebResourcesCache(WebResourcesCache resources) {
        super(resources);
    }

    @Override
    public WebResources get(Uri requestResourceUri) {
        String fileName = requestResourceUri.getLastPathSegment();
        String parentDir = "";
        if (fileName != null) {
            int i = fileName.lastIndexOf('.');
            parentDir = fileName.substring(i == -1 ? 0 : i);
        }
        String hostDir = WebFileUtils.md5Decode16(requestResourceUri.getHost());
        File fileResDir = new File(WebFileUtils.getWebViewBasicCachePath(), hostDir + File.separator + parentDir);
        if (!fileResDir.exists()) {
            fileResDir.mkdirs();
        }
        WebResources webResources = null;
        File realFile = new File(fileResDir, fileName);
        SharedPreferences fileSP = WebFileUtils.appContext.getSharedPreferences(hostDir, Context.MODE_PRIVATE);
        if (realFile.exists()) {
            // 文件存在, 创建 resources 对象返回.
            try {
                JSONObject jsonObject = new JSONObject(fileSP.getString(fileName, "{}"));
                WebResources resources = new WebResources();
                attachHeader(resources, jsonObject.getJSONArray("response_header"));
                resources.setStateCode(jsonObject.getInt("state_code"));
                resources.setInputSteam(new BufferedInputStream(new FileInputStream(realFile)));
                webResources = resources;
            } catch (Exception ignored) {
                if (realFile.delete()) {
                    SharedPreferences.Editor edit = fileSP.edit();
                    edit.remove(fileName);
                    edit.apply();
                }
            }
        } else {
            webResources = getNext().get(requestResourceUri);
            // 异步把流写到本地,
            try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(realFile))){
//                BufferedInputStream bufferedInputStream = new BufferedInputStream(webResources.getInputSteam());
                if (WebFileUtils.copyTo(webResources.getInputSteam(), bufferedOutputStream)) {
                    webResources.setInputSteam(new FileInputStream(realFile));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                // 把响应数据 序列化到本地
                SharedPreferences.Editor edit = fileSP.edit();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("response_header", parseHeaderToJson(webResources.getResponseHeader()));
                jsonObject.put("state_code", webResources.getStateCode());
                edit.putString(fileName, jsonObject.toString());
                edit.apply();
            }catch (Exception ignored) {
            }
        }
        return webResources;
    }

    private JSONArray parseHeaderToJson(Map<String, String> responseHeader) {
        JSONArray header = new JSONArray();
        Iterator<Map.Entry<String, String>> headerIterator = responseHeader.entrySet().iterator();
        while (headerIterator.hasNext()) {
            try {
                Map.Entry<String, String> headerPair = headerIterator.next();
                if ("Expires".equals(headerPair.getKey())) {
                    SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
                    String format = formatter.format(new Date(System.currentTimeMillis() + 600));
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("key", "Expires");
                    jsonObject.put("value", format);
                    header.put(jsonObject);
                    continue;
                }
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("key", headerPair.getKey());
                jsonObject.put("value", headerPair.getValue());
                header.put(jsonObject);
            } catch (Exception ignored) {
            }
        }
        try {
            header.put(new JSONObject().put("Access-Control-Allow-Origin", "*"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return header;
    }

    private void attachHeader(WebResources resources, JSONArray header) {
        for (int i = 0; i < header.length(); i++) {
            try {
                JSONObject subHeader = header.getJSONObject(i);

                resources.addHeader(subHeader.getString("key"), subHeader.getString("value"));
            } catch (JSONException ignored) {
            }
        }
//        String expires = resources.getResponseHeader().get("Expires");
//        if (expires == null) {
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            String format = formatter.format(new Date(System.currentTimeMillis() + 600));
            resources.getResponseHeader().put("Expires", format);
//        } else {
//
//        }
    }

}
