package com.newchar.debug.monitor;

import android.app.Activity;

/**
 * @author newChar
 * date 2025/6/30
 * @since 工具类 提供一些通用的方法
 * @since 迭代版本，（以及描述）
 */
public class Utils {

    public static final String DIALOG_FRAGMENT_FLAG = "androidx.fragment.app.DialogFragment";

    public static boolean isAndroidXEnv(Class<? extends Activity> actClazz) {
        try {
            Class<?> compatActivity = Class.forName("androidx.core.app.ComponentActivity",
                    false, Thread.currentThread().getContextClassLoader());
            return isSubclass(compatActivity, actClazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isAndroidXDialog() {
        try {
            Class.forName("androidx.activity.ComponentDialog",
                    false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isAndroidV4Env() {
        try {
            Class.forName("android.support.v4.app.FragmentActivity",
                    false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 判断childClass是否是parentClass的子类
     * 
     * @param parentClass 父类
     * @param childClass  子类
     * @return 如果childClass是parentClass的子类则返回true，否则返回false
     */
    public static boolean isSubclass(Class<?> parentClass, Class<?> childClass) {
        if (parentClass == null || childClass == null) {
            return false;
        }
        
        // 如果两个类相同，则认为是继承关系
        if (parentClass == childClass) {
            return true;
        }
        
        // 使用isAssignableFrom方法判断继承关系
        return parentClass.isAssignableFrom(childClass);
    }

    /**
     * 返回全局常量 padding 值，将 16dp 转换为 px 值
     * @param context 上下文对象，用于获取屏幕密度
     * @return 16dp 对应的 px 值
     */
    public static int getGlobalPadding(android.content.Context context, int dp) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * scale);
    }
}
