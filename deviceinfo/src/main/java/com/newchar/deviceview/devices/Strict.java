package com.newchar.deviceview.devices;

import android.os.StrictMode;

import com.newchar.debugview.utils.DebugUtils;

/**
 * @author newChar
 * date 2024/11/12
 * @since 严格模式.
 * @since 迭代版本，（以及描述）
 */
public class Strict {

    private static boolean sEnabled;

    /**
     * 开启全部严格模式策略。
     */
    public static void startAll() {
        thread(true);
        vm(true);
        sEnabled = true;
    }

    /**
     * 关闭全部严格模式策略。
     */
    public static void stopAll() {
        thread(false);
        vm(false);
        sEnabled = false;
    }

    /**
     * 切换严格模式开关状态。
     *
     * @return 切换后的状态
     */
    public static boolean toggleAll() {
        if (sEnabled) {
            stopAll();
        } else {
            startAll();
        }
        return sEnabled;
    }

    /**
     * 判断严格模式是否启用。
     *
     * @return true 代表当前处于开启状态
     */
    public static boolean isEnabled() {
        return sEnabled;
    }

    /**
     * 配置线程严格模式策略。
     *
     * @param start 是否开启
     */
    public static void thread(boolean start) {
        if (!DebugUtils.isDebuggable()) {
            return;
        }
        if (start) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectCustomSlowCalls() //API等级11，使用StrictMode.noteSlowCode
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()        // or .detectAll() for all detectable problems
                    .penaltyDialog()        //弹出违规提示对话框
                    .penaltyLog()           //在Logcat 中打印违规异常信息
                    .penaltyFlashScreen()
//                    .penaltyListener(null, getViolationListener()) // api 28
                    .build());
        } else {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().build());
        }

    }

    /**
     * 配置虚拟机严格模式策略。
     *
     * @param start 是否开启
     */
    public static void vm(boolean start) {
        if (!DebugUtils.isDebuggable()) {
            return;
        }
        if (start) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects() //API等级11
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        } else {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        }
    }

    /**
     * 构建线程违规监听器。
     *
     * @return 监听器
     */
    private static StrictMode.OnThreadViolationListener getViolationListener() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return violation -> {

            };
        }
        return null;
    }

}
