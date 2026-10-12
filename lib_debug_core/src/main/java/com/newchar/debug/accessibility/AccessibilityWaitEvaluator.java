package com.newchar.debug.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** 使用无障碍节点、前台事件和截图轮询 LQITS waitState。 */
final class AccessibilityWaitEvaluator {

    private final ScriptAccessibilityService mService;
    private final AccessibilityNodeResolver mNodes = new AccessibilityNodeResolver();
    private final int mSourceWidth;
    private final int mSourceHeight;

    /** 保存服务与录制分辨率。 */
    AccessibilityWaitEvaluator(ScriptAccessibilityService service, int sourceWidth,
            int sourceHeight) {
        mService = service;
        mSourceWidth = sourceWidth;
        mSourceHeight = sourceHeight;
    }

    /** 持续采样到条件稳定命中或 waitMs 超时。 */
    Result await(JSONObject step) throws InterruptedException {
        JSONObject state = step.optJSONObject("waitState");
        long deadline = SystemClock.elapsedRealtime() + Math.max(1L, step.optLong("waitMs"));
        int consecutive = 0;
        int required = Math.max(1, state.optInt("consecutiveMatches", 1));
        String observation = "尚未采样";
        do {
            RoundSnapshot snapshot = new RoundSnapshot();
            ConditionResult result;
            try {
                result = evaluateConditions(state, snapshot);
            } finally {
                snapshot.close();
            }
            observation = result.message;
            consecutive = result.matched ? consecutive + 1 : 0;
            if (consecutive >= required) return Result.success("条件满足：" + observation);
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L) break;
            SystemClock.sleep(Math.min(remaining,
                    Math.max(50L, state.optLong("pollIntervalMs", 250L))));
        } while (SystemClock.elapsedRealtime() <= deadline);
        return Result.failure("条件等待超时：" + observation);
    }

    /** 按 all 或 any 汇总一轮全部条件。 */
    private ConditionResult evaluateConditions(JSONObject state, RoundSnapshot snapshot)
            throws InterruptedException {
        JSONArray conditions = state.optJSONArray("conditions");
        boolean all = "all".equals(state.optString("match", "all"));
        boolean matched = all;
        List<String> messages = new ArrayList<>();
        for (int index = 0; conditions != null && index < conditions.length(); index++) {
            ConditionResult result = evaluate(conditions.optJSONObject(index), snapshot);
            messages.add((index + 1) + ":" + result.message);
            matched = all ? matched && result.matched : matched || result.matched;
        }
        return new ConditionResult(matched, join(messages));
    }

    /** 按 kind 选择一条等待条件的采集方式。 */
    private ConditionResult evaluate(JSONObject condition, RoundSnapshot snapshot)
            throws InterruptedException {
        String kind = condition.optString("kind");
        if ("ui_exists".equals(kind)) return evaluateExists(condition, snapshot.root());
        if ("ui_attribute".equals(kind)) return evaluateAttribute(condition, snapshot.root());
        if ("foreground".equals(kind)) return evaluateForeground(condition, snapshot.root());
        if ("region_color".equals(kind)) return evaluateColor(condition, snapshot.screen());
        return new ConditionResult(false, "未知条件 " + kind);
    }

    /** 判断选择器对应节点是否按预期存在。 */
    private ConditionResult evaluateExists(JSONObject condition, AccessibilityNodeInfo root) {
        AccessibilityNodeInfo node = mNodes.find(root, condition.optJSONObject("selector"));
        boolean found = node != null;
        if (node != null) node.recycle();
        boolean expected = condition.optBoolean("expected", true);
        return new ConditionResult(found == expected,
                "ui_exists 当前=" + found + " 目标=" + expected);
    }

    /** 判断首个匹配节点的指定属性。 */
    private ConditionResult evaluateAttribute(JSONObject condition, AccessibilityNodeInfo root) {
        AccessibilityNodeInfo node = mNodes.find(root, condition.optJSONObject("selector"));
        if (node == null) return new ConditionResult(false, "ui_attribute 未找到节点");
        String actual = mNodes.attribute(node, condition.optString("attribute"));
        node.recycle();
        return new ConditionResult(compare(actual, condition),
                "ui_attribute 当前=" + actual + " 目标=" + condition.optString("value"));
    }

    /** 判断活动窗口包名及可选 Activity。 */
    private ConditionResult evaluateForeground(JSONObject condition, AccessibilityNodeInfo root) {
        String packageName = root != null && root.getPackageName() != null
                ? root.getPackageName().toString() : mService.foregroundPackage();
        String activityName = mService.foregroundActivity();
        boolean matched = packageName.equalsIgnoreCase(condition.optString("packageName"));
        String expectedActivity = condition.optString("activityName");
        if (!expectedActivity.isEmpty()) {
            matched = matched && activityName.equalsIgnoreCase(expectedActivity);
        }
        return new ConditionResult(matched,
                "foreground 当前=" + packageName + "/" + activityName);
    }

    /** 判断截图区域内目标颜色采样占比。 */
    private ConditionResult evaluateColor(JSONObject condition, Bitmap bitmap) {
        if (bitmap == null) return new ConditionResult(false, "无障碍截图不可用");
        Rect area = colorArea(bitmap, condition);
        int target = Color.parseColor(condition.optString("color"));
        long[] counts = colorCounts(bitmap, area, target,
                condition.optInt("tolerance", 12),
                Math.max(1, condition.optInt("sampleStride", 2)));
        float ratio = counts[1] == 0L ? 0f : (float) counts[0] / counts[1];
        boolean matched = ratio >= condition.optDouble("minMatchRatio", 0.65d);
        return new ConditionResult(matched, "region_color 占比=" + ratio);
    }

    /** 按脚本坐标模式计算截图采样区域。 */
    private Rect colorArea(Bitmap bitmap, JSONObject condition) {
        String mode = condition.optString("coordinateMode", "normalized");
        float[] first = mapRegion(condition.optDouble("left"),
                condition.optDouble("top"), mode, bitmap);
        float[] last = mapRegion(condition.optDouble("right"),
                condition.optDouble("bottom"), mode, bitmap);
        return new Rect(clamp((int) first[0], 0, bitmap.getWidth() - 1),
                clamp((int) first[1], 0, bitmap.getHeight() - 1),
                clamp((int) last[0], 1, bitmap.getWidth()),
                clamp((int) last[1], 1, bitmap.getHeight()));
    }

    /** 将一个区域坐标映射到截图像素。 */
    private float[] mapRegion(double x, double y, String mode, Bitmap bitmap) {
        if ("normalized".equals(mode)) {
            return new float[]{(float) x * bitmap.getWidth(), (float) y * bitmap.getHeight()};
        }
        if ("source_pixel".equals(mode)) {
            return new float[]{(float) x * bitmap.getWidth() / Math.max(1, mSourceWidth),
                    (float) y * bitmap.getHeight() / Math.max(1, mSourceHeight)};
        }
        return new float[]{(float) x, (float) y};
    }

    /** 统计区域内颜色接近目标值的采样点。 */
    private long[] colorCounts(Bitmap bitmap, Rect area, int target, int tolerance, int stride) {
        long matched = 0L;
        long total = 0L;
        for (int y = area.top; y < area.bottom; y += stride) {
            for (int x = area.left; x < area.right; x += stride) {
                if (colorNear(bitmap.getPixel(x, y), target, tolerance)) matched++;
                total++;
            }
        }
        return new long[]{matched, total};
    }

    /** 判断两个 RGB 颜色的各通道是否均在容差内。 */
    private boolean colorNear(int actual, int target, int tolerance) {
        return Math.abs(Color.red(actual) - Color.red(target)) <= tolerance
                && Math.abs(Color.green(actual) - Color.green(target)) <= tolerance
                && Math.abs(Color.blue(actual) - Color.blue(target)) <= tolerance;
    }

    /** 按 equals、not_equals、contains 或 regex 比较属性。 */
    private boolean compare(String actual, JSONObject condition) {
        String expected = condition.optString("value");
        boolean ignoreCase = condition.optBoolean("ignoreCase", true);
        String left = ignoreCase ? actual.toLowerCase(Locale.ROOT) : actual;
        String right = ignoreCase ? expected.toLowerCase(Locale.ROOT) : expected;
        String operator = condition.optString("operator", "equals");
        if ("not_equals".equals(operator)) return !left.equals(right);
        if ("contains".equals(operator)) return left.contains(right);
        if ("regex".equals(operator)) {
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
            return Pattern.compile(expected, flags).matcher(actual).find();
        }
        return left.equals(right);
    }

    /** 使用系统无障碍截图接口同步取得可读 Bitmap。 */
    private Bitmap captureScreen() throws InterruptedException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Bitmap> output = new AtomicReference<>();
        ScreenshotCallback callback = new ScreenshotCallback(latch, output);
        Handler handler = new Handler(Looper.getMainLooper());
        mService.takeScreenshot(Display.DEFAULT_DISPLAY, handler::post, callback);
        latch.await(4000L, TimeUnit.MILLISECONDS);
        return output.get();
    }

    /** 将消息列表拼接为单行观测结果。 */
    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append("；");
            result.append(value);
        }
        return result.toString();
    }

    /** 将整数限制在闭区间内。 */
    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** 缓存同一轮的节点根和截图。 */
    private final class RoundSnapshot {
        private AccessibilityNodeInfo root;
        private Bitmap screen;
        private boolean screenCaptured;

        /** 返回本轮唯一活动窗口根节点。 */
        AccessibilityNodeInfo root() {
            if (root == null) root = mService.getRootInActiveWindow();
            return root;
        }

        /** 返回本轮唯一屏幕截图。 */
        Bitmap screen() throws InterruptedException {
            if (!screenCaptured) {
                screenCaptured = true;
                screen = captureScreen();
            }
            return screen;
        }

        /** 回收本轮持有的系统对象。 */
        void close() {
            if (root != null) root.recycle();
            if (screen != null && !screen.isRecycled()) screen.recycle();
        }
    }

    /** 将无障碍 ScreenshotResult 转换为软件 Bitmap。 */
    private static final class ScreenshotCallback implements AccessibilityService.TakeScreenshotCallback {
        private final CountDownLatch latch;
        private final AtomicReference<Bitmap> output;

        /** 保存等待信号和截图输出。 */
        ScreenshotCallback(CountDownLatch latch, AtomicReference<Bitmap> output) {
            this.latch = latch;
            this.output = output;
        }

        /** 转换硬件缓冲区并唤醒等待线程。 */
        @Override
        public void onSuccess(AccessibilityService.ScreenshotResult result) {
            HardwareBuffer buffer = result.getHardwareBuffer();
            Bitmap hardware = null;
            try {
                ColorSpace colorSpace = result.getColorSpace();
                hardware = Bitmap.wrapHardwareBuffer(buffer, colorSpace);
                if (hardware != null) output.set(hardware.copy(Bitmap.Config.ARGB_8888, false));
            } catch (Throwable ignored) {
                // 截图缓冲区不可读时按本轮未取得截图处理。
            } finally {
                if (hardware != null) hardware.recycle();
                buffer.close();
                latch.countDown();
            }
        }

        /** 截图失败时仅唤醒等待线程并返回空结果。 */
        @Override
        public void onFailure(int errorCode) {
            latch.countDown();
        }
    }

    /** 一轮条件检查结果。 */
    private static final class ConditionResult {
        final boolean matched;
        final String message;

        /** 保存是否命中及可读观测信息。 */
        ConditionResult(boolean matched, String message) {
            this.matched = matched;
            this.message = message;
        }
    }

    /** 一次 waitState 执行结果。 */
    static final class Result {
        final boolean success;
        final String message;

        /** 保存等待结果。 */
        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        /** 创建成功等待结果。 */
        static Result success(String message) {
            return new Result(true, message);
        }

        /** 创建失败等待结果。 */
        static Result failure(String message) {
            return new Result(false, message);
        }
    }
}
