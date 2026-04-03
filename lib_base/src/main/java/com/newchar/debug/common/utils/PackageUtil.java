package com.newchar.debug.common.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import java.util.List;

public class PackageUtil {

    /**
     * 获取ApplicationInfo 或 Activity 节点下的的MetaData数据
     *
     * @param clazz 对应Activity Class 字节码对象, 传null 获取ApplicationInfo下mata信息
     * @return MetaData 全部数据
     */
    public static Bundle getMetaData(Class<?> clazz) {
        Bundle metaData = null;
        try {
            PackageManager packageManager = UIUtils.getContext().getPackageManager();
            if (clazz == null) {
                ApplicationInfo applicationInfo = packageManager.getApplicationInfo(
                        UIUtils.getContext().getPackageName(), PackageManager.GET_META_DATA);
                metaData = applicationInfo.metaData;
            } else {
                ComponentName component = new ComponentName(UIUtils.getContext(), clazz);
                ActivityInfo activityInfo = packageManager.getActivityInfo(component, PackageManager.GET_META_DATA);
                metaData = activityInfo.metaData;
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return metaData;
    }

    /**
     * 获取ApplicationInfo 或 Activity 节点下的的MetaData数据
     *
     * @param clazz 对应Activity Class 字节码对象, 传null 则获取Application下的meta
     * @param key   要获取对应数据的键
     * @return 你想要的值
     */
    public static String getMetaDataValue(Class<?> clazz, String key) {
        String str = getMetaData(clazz).getString(key);
        return str;
    }

    /**
     * 检查这个隐式意图，是否在本机上是否可用， 下面的代码貌似功能类似
     * if (intent.resolveActivity(getPackageManager()) != null) {
     * startActivity(chooser);
     * }
     *
     * @param intent 带有隐式Action的Intent
     * @return 是否可用 true 可用，
     */
    public static boolean queryIntentEnable(Intent intent) {
        PackageManager packageManager = UIUtils.getContext().getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(
                intent, PackageManager.MATCH_DEFAULT_ONLY);
        return !activities.isEmpty();
    }

    /**
     * 检查当前设备是否有摄像头
     *
     * @return
     * @see PackageManager#FEATURE_CAMERA  特性常量
     */
    public static boolean hardwareEnable(String feature) {
        return UIUtils.getContext().getPackageManager().hasSystemFeature(feature);
    }

    /**
     * 获取应用程序名称
     */
    public static String getAppName() {
        try {
            Context context = UIUtils.getContext();
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            int labelRes = packageInfo.applicationInfo.labelRes;
            return context.getResources().getString(labelRes);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * [获取应用程序版本名称信息]
     *
     * @return 当前应用的版本号
     */
    public static int getVersionCode() {
        try {
            PackageManager packageManager = UIUtils.getContext().getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    UIUtils.getContext().getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return 0;
    }


}
