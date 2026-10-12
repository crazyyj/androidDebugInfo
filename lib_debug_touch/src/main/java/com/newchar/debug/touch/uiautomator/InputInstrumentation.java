package com.newchar.debug.touch.uiautomator;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Base64;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通过 instrumentation 的 UiAutomation 为任意前台应用执行单条输入指令。
 *
 * <p>本类原为独立 APK（:app_debug_uiautomator）的入口，现已合并进 {@code lib_debug_touch}，
 * 由宿主 App 的 Manifest 合并后直接携带该插桩（见 lib_debug_touch 的 AndroidManifest.xml）。
 * 这样 PC 端无需再 {@code adb install} 单独的 uiautomator APK，UiAutomation 即成为一种
 * “操作设备的方式”——由 PC 脚本通道驱动、随宿主 App 一同安装。</p>
 */
public final class InputInstrumentation extends Instrumentation {

    private static final long MATCH_DELAY_MS = 600L;
    private static final Pattern COMPONENT_PATTERN = Pattern.compile(
            "([A-Za-z][A-Za-z0-9_.$]*)/([A-Za-z0-9_.$]+)");
    private UiAutomation automation;
    private Bundle arguments;
    private int sourceWidth;
    private int sourceHeight;

    /** 接收单步骤参数并启动 instrumentation 主线程。 */
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? new Bundle() : arguments;
        start();
    }

    /** 执行单步骤并通过 instrumentation result 返回结构化结果。 */
    @Override
    public void onStart() {
        Result result;
        try {
            automation = getUiAutomation();
            configureAutomation();
            JSONObject payload = decodeObject(arguments.getString("payload", ""));
            sourceWidth = payload.optInt("sourceWidth");
            sourceHeight = payload.optInt("sourceHeight");
            result = execute(payload.getJSONObject("step"));
        } catch (Throwable throwable) {
            result = Result.failure(errorMessage(throwable));
        }
        finish(Activity.RESULT_OK, resultBundle(result));
    }

    /** 开启资源 ID 和交互窗口读取能力。 */
    private void configureAutomation() {
        AccessibilityServiceInfo info = automation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation.setServiceInfo(info);
    }

    /** 按步骤类型分发 UiAutomation 能力。 */
    private Result execute(JSONObject step) throws Exception {
        String type = step.optString("type");
        if ("tap".equals(type)) return tap(step, Math.max(1L, step.optLong("durationMs", 100L)));
        if ("long_press".equals(type)) return tap(step, Math.max(500L, step.optLong("durationMs", 500L)));
        if ("gesture".equals(type)) return gesture(step);
        if ("multi_touch".equals(type)) return multiTouch(step);
        if ("key".equals(type)) return key(step.optInt("keyCode"));
        if ("wait".equals(type)) return waitStep(step);
        if ("launch_app".equals(type)) return launch(step.optString("packageName"));
        if ("node_tap".equals(type)) return nodeTap(step);
        return Result.failure("instrumentation 不支持 " + type);
    }

    /** 注入点击或长按。 */
    private Result tap(JSONObject step, long durationMs) {
        float[] point = mapPoint(step.optDouble("x"), step.optDouble("y"), step.optString("coordinateMode", "source_pixel"));
        long downTime = SystemClock.uptimeMillis();
        boolean down = injectMotion(downTime, downTime, MotionEvent.ACTION_DOWN, point[0], point[1]);
        SystemClock.sleep(durationMs);
        boolean up = injectMotion(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, point[0], point[1]);
        return down && up ? Result.success("输入完成") : Result.failure("UiAutomation 点击失败");
    }

    /** 注入单指轨迹。 */
    private Result gesture(JSONObject step) {
        JSONArray points = step.optJSONArray("points");
        if (points == null || points.length() < 2) return Result.failure("轨迹点不足");
        long downTime = SystemClock.uptimeMillis();
        boolean success = true;
        for (int index = 0; index < points.length(); index++) {
            JSONObject value = points.optJSONObject(index);
            float[] point = mapPoint(value.optDouble("x"), value.optDouble("y"), step.optString("coordinateMode", "source_pixel"));
            int action = index == 0 ? MotionEvent.ACTION_DOWN : index == points.length() - 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            long eventTime = downTime + Math.max(0L, value.optLong("timeMs"));
            sleepUntil(eventTime);
            success = injectMotion(downTime, eventTime, action, point[0], point[1]) && success;
        }
        return success ? Result.success("轨迹完成") : Result.failure("UiAutomation 轨迹失败");
    }

    /** 注入完整多指事件帧。 */
    private Result multiTouch(JSONObject step) {
        JSONArray frames = step.optJSONArray("frames");
        if (frames == null || frames.length() == 0) return Result.failure("多指事件帧为空");
        long downTime = SystemClock.uptimeMillis();
        boolean success = true;
        for (int index = 0; index < frames.length(); index++) {
            JSONObject frame = frames.optJSONObject(index);
            long eventTime = downTime + Math.max(0L, frame.optLong("timeMs"));
            sleepUntil(eventTime);
            MotionEvent event = frameEvent(step, frame, downTime, eventTime);
            success = event != null && automation.injectInputEvent(event, true) && success;
            if (event != null) event.recycle();
        }
        return success ? Result.success("多指手势完成") : Result.failure("UiAutomation 多指失败");
    }

    /** 将一帧 JSON 转换为 MotionEvent。 */
    private MotionEvent frameEvent(JSONObject step, JSONObject frame, long downTime, long eventTime) {
        JSONArray pointers = frame.optJSONArray("pointers");
        if (pointers == null || pointers.length() == 0) return null;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.length()];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[pointers.length()];
        for (int index = 0; index < pointers.length(); index++) {
            JSONObject pointer = pointers.optJSONObject(index);
            properties[index] = pointerProperties(pointer.optInt("id", index));
            coordinates[index] = pointerCoordinates(step, pointer);
        }
        return MotionEvent.obtain(downTime, eventTime, frame.optInt("action"), pointers.length(),
                properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    /** 创建一条触点属性。 */
    private MotionEvent.PointerProperties pointerProperties(int id) {
        MotionEvent.PointerProperties value = new MotionEvent.PointerProperties();
        value.id = id;
        value.toolType = MotionEvent.TOOL_TYPE_FINGER;
        return value;
    }

    /** 创建一条映射后的触点坐标。 */
    private MotionEvent.PointerCoords pointerCoordinates(JSONObject step, JSONObject pointer) {
        float[] point = mapPoint(pointer.optDouble("x"), pointer.optDouble("y"), step.optString("coordinateMode", "source_pixel"));
        MotionEvent.PointerCoords value = new MotionEvent.PointerCoords();
        value.x = point[0];
        value.y = point[1];
        value.pressure = (float) pointer.optDouble("pressure", 1d);
        value.size = (float) pointer.optDouble("size", 1d);
        return value;
    }

    /** 注入按键的 down/up 事件。 */
    private Result key(int keyCode) {
        long time = SystemClock.uptimeMillis();
        boolean down = automation.injectInputEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0), true);
        boolean up = automation.injectInputEvent(new KeyEvent(time, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0), true);
        return down && up ? Result.success("按键完成") : Result.failure("UiAutomation 按键失败");
    }

    /** 执行固定等待或条件等待。 */
    private Result waitStep(JSONObject step) throws Exception {
        JSONObject state = step.optJSONObject("waitState");
        if (state == null) {
            SystemClock.sleep(Math.max(0L, step.optLong("waitMs")));
            return Result.success("等待完成");
        }
        Result result = awaitState(step, state);
        if (result.success) SystemClock.sleep(MATCH_DELAY_MS);
        return result;
    }

    /** 轮询条件直到满足或达到 waitMs。 */
    private Result awaitState(JSONObject step, JSONObject state) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + Math.max(1L, step.optLong("waitMs"));
        int consecutive = 0;
        int required = Math.max(1, state.optInt("consecutiveMatches", 1));
        String observation = "尚未采样";
        do {
            ConditionSummary summary = evaluateConditions(state);
            observation = summary.message;
            consecutive = summary.matched ? consecutive + 1 : 0;
            if (consecutive >= required) return Result.success("条件满足：" + observation);
            SystemClock.sleep(Math.max(50L, state.optLong("pollIntervalMs", 250L)));
        } while (SystemClock.elapsedRealtime() <= deadline);
        return Result.failure("条件等待超时：" + observation);
    }

    /** 按 all/any 汇总一轮条件判断。 */
    private ConditionSummary evaluateConditions(JSONObject state) throws Exception {
        JSONArray conditions = state.optJSONArray("conditions");
        boolean all = "all".equals(state.optString("match", "all"));
        boolean matched = all;
        List<String> messages = new ArrayList<>();
        for (int index = 0; conditions != null && index < conditions.length(); index++) {
            ConditionSummary result = evaluateCondition(conditions.optJSONObject(index));
            messages.add((index + 1) + ":" + result.message);
            matched = all ? matched && result.matched : matched || result.matched;
        }
        return new ConditionSummary(matched, join(messages));
    }

    /** 判断单条 UI、前台或颜色条件。 */
    private ConditionSummary evaluateCondition(JSONObject condition) throws Exception {
        String kind = condition.optString("kind");
        if ("ui_exists".equals(kind)) return evaluateExists(condition);
        if ("ui_attribute".equals(kind)) return evaluateAttribute(condition);
        if ("foreground".equals(kind)) return evaluateForeground(condition);
        if ("region_color".equals(kind)) return evaluateColor(condition);
        return new ConditionSummary(false, "未知条件 " + kind);
    }

    /** 判断匹配节点是否按预期存在。 */
    private ConditionSummary evaluateExists(JSONObject condition) {
        AccessibilityNodeInfo node = findNode(automation.getRootInActiveWindow(), condition.optJSONObject("selector"));
        boolean found = node != null;
        if (node != null) node.recycle();
        boolean expected = condition.optBoolean("expected", true);
        return new ConditionSummary(found == expected, "ui_exists 当前=" + found + " 目标=" + expected);
    }

    /** 判断匹配节点的指定属性。 */
    private ConditionSummary evaluateAttribute(JSONObject condition) {
        AccessibilityNodeInfo node = findNode(automation.getRootInActiveWindow(), condition.optJSONObject("selector"));
        if (node == null) return new ConditionSummary(false, "未找到节点");
        String actual = nodeAttribute(node, condition.optString("attribute"));
        node.recycle();
        boolean matched = compare(actual, condition);
        return new ConditionSummary(matched, "ui_attribute 当前=" + actual);
    }

    /** 判断当前前台包名和可选 Activity。 */
    private ConditionSummary evaluateForeground(JSONObject condition) throws Exception {
        String output = executeShell("dumpsys activity activities");
        String packageName = "";
        String activityName = "";
        for (String line : output.split("\\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (!lower.contains("resumedactivity") && !lower.contains("topresumedactivity")) continue;
            Matcher matcher = COMPONENT_PATTERN.matcher(line);
            if (!matcher.find()) continue;
            packageName = matcher.group(1);
            activityName = normalizeActivity(packageName, matcher.group(2));
            break;
        }
        boolean matched = equalsIgnoreCase(packageName, condition.optString("packageName"));
        String expectedActivity = condition.optString("activityName");
        if (!expectedActivity.isEmpty()) matched = matched && equalsIgnoreCase(activityName, expectedActivity);
        return new ConditionSummary(matched, "foreground 当前=" + packageName + "/" + activityName);
    }

    /** 判断截图区域的目标颜色占比。 */
    private ConditionSummary evaluateColor(JSONObject condition) {
        Bitmap bitmap = automation.takeScreenshot();
        if (bitmap == null) return new ConditionSummary(false, "截图失败");
        Rect area = colorArea(bitmap, condition);
        int target = android.graphics.Color.parseColor(condition.optString("color"));
        int tolerance = condition.optInt("tolerance", 12);
        int stride = Math.max(1, condition.optInt("sampleStride", 2));
        long[] counts = colorCounts(bitmap, area, target, tolerance, stride);
        bitmap.recycle();
        float ratio = counts[1] == 0L ? 0f : (float) counts[0] / counts[1];
        return new ConditionSummary(ratio >= condition.optDouble("minMatchRatio", 0.65d), "region_color 占比=" + ratio);
    }

    /** 计算截图颜色条件的实际像素区域。 */
    private Rect colorArea(Bitmap bitmap, JSONObject condition) {
        String mode = condition.optString("coordinateMode", "normalized");
        float[] first = mapRegionPoint(condition.optDouble("left"), condition.optDouble("top"), mode, bitmap);
        float[] last = mapRegionPoint(condition.optDouble("right"), condition.optDouble("bottom"), mode, bitmap);
        return new Rect(clamp((int) first[0], 0, bitmap.getWidth() - 1), clamp((int) first[1], 0, bitmap.getHeight() - 1),
                clamp((int) last[0], 1, bitmap.getWidth()), clamp((int) last[1], 1, bitmap.getHeight()));
    }

    /** 统计区域内符合目标颜色的采样点数量。 */
    private long[] colorCounts(Bitmap bitmap, Rect area, int target, int tolerance, int stride) {
        long matched = 0L;
        long total = 0L;
        for (int y = area.top; y < area.bottom; y += stride) {
            for (int x = area.left; x < area.right; x += stride) {
                int color = bitmap.getPixel(x, y);
                if (channelNear(color, target, tolerance)) matched++;
                total++;
            }
        }
        return new long[]{matched, total};
    }

    /** 判断 RGB 三通道是否均在容差内。 */
    private boolean channelNear(int actual, int target, int tolerance) {
        return Math.abs(android.graphics.Color.red(actual) - android.graphics.Color.red(target)) <= tolerance
                && Math.abs(android.graphics.Color.green(actual) - android.graphics.Color.green(target)) <= tolerance
                && Math.abs(android.graphics.Color.blue(actual) - android.graphics.Color.blue(target)) <= tolerance;
    }

    /** 启动指定包名的桌面入口。 */
    private Result launch(String packageName) {
        Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) return Result.failure("未找到应用入口 " + packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        return Result.success("应用已启动");
    }

    /** 定位节点中心并注入点击。 */
    private Result nodeTap(JSONObject step) {
        AccessibilityNodeInfo node = findNode(automation.getRootInActiveWindow(), step.optJSONObject("selector"));
        if (node == null) return Result.failure("未找到匹配节点");
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        node.recycle();
        JSONObject point = new JSONObject();
        try {
            point.put("x", bounds.exactCenterX());
            point.put("y", bounds.exactCenterY());
            point.put("coordinateMode", "pixel");
            point.put("durationMs", step.optLong("durationMs", 100L));
        } catch (Exception error) {
            return Result.failure(errorMessage(error));
        }
        return tap(point, Math.max(1L, step.optLong("durationMs", 100L)));
    }

    /** 深度优先查找第一个满足选择器的节点。 */
    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo root, JSONObject selector) {
        if (root == null || selector == null) return null;
        if (matchesSelector(root, selector)) return AccessibilityNodeInfo.obtain(root);
        for (int index = 0; index < root.getChildCount(); index++) {
            AccessibilityNodeInfo child = root.getChild(index);
            AccessibilityNodeInfo found = findNode(child, selector);
            if (child != null) child.recycle();
            if (found != null) return found;
        }
        return null;
    }

    /** 判断节点文本、描述、ID 和类名是否全部匹配。 */
    private boolean matchesSelector(AccessibilityNodeInfo node, JSONObject selector) {
        return matchesValue(selector.optString("resourceId"), node.getViewIdResourceName())
                && matchesValue(selector.optString("text"), text(node.getText()))
                && matchesValue(selector.optString("contentDescription"), text(node.getContentDescription()))
                && matchesValue(selector.optString("className"), text(node.getClassName()));
    }

    /** 返回节点指定属性的文本值。 */
    private String nodeAttribute(AccessibilityNodeInfo node, String attribute) {
        if ("text".equals(attribute)) return text(node.getText());
        if ("contentDescription".equals(attribute)) return text(node.getContentDescription());
        if ("selected".equals(attribute)) return Boolean.toString(node.isSelected());
        if ("checked".equals(attribute)) return Boolean.toString(node.isChecked());
        if ("enabled".equals(attribute)) return Boolean.toString(node.isEnabled());
        if ("focused".equals(attribute)) return Boolean.toString(node.isFocused());
        return "";
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
        if ("regex".equals(operator)) return Pattern.compile(expected, ignoreCase ? Pattern.CASE_INSENSITIVE : 0).matcher(actual).find();
        return left.equals(right);
    }

    /** 注入一条单指 MotionEvent。 */
    private boolean injectMotion(long downTime, long eventTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        boolean success = automation.injectInputEvent(event, true);
        event.recycle();
        return success;
    }

    /** 将脚本坐标映射到当前显示尺寸。 */
    private float[] mapPoint(double x, double y, String mode) {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        WindowManager manager = (WindowManager) getContext().getSystemService(android.content.Context.WINDOW_SERVICE);
        manager.getDefaultDisplay().getRealMetrics(metrics);
        if ("normalized".equals(mode)) return new float[]{(float) x * metrics.widthPixels, (float) y * metrics.heightPixels};
        if ("source_pixel".equals(mode)) return new float[]{(float) x * metrics.widthPixels / Math.max(1, sourceWidth),
                (float) y * metrics.heightPixels / Math.max(1, sourceHeight)};
        return new float[]{(float) x, (float) y};
    }

    /** 将颜色区域坐标映射到截图尺寸。 */
    private float[] mapRegionPoint(double x, double y, String mode, Bitmap bitmap) {
        if ("normalized".equals(mode)) return new float[]{(float) x * bitmap.getWidth(), (float) y * bitmap.getHeight()};
        if ("source_pixel".equals(mode)) return new float[]{(float) x * bitmap.getWidth() / Math.max(1, sourceWidth),
                (float) y * bitmap.getHeight() / Math.max(1, sourceHeight)};
        return new float[]{(float) x, (float) y};
    }

    /** 通过 UiAutomation shell 通道读取命令输出。 */
    private String executeShell(String command) throws Exception {
        ParcelFileDescriptor descriptor = automation.executeShellCommand(command);
        try (FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[4096];
            StringBuilder output = new StringBuilder();
            int count;
            while ((count = input.read(buffer)) >= 0) output.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
            return output.toString();
        } finally {
            descriptor.close();
        }
    }

    /** 将 URL-safe Base64 参数解析为 JSON。 */
    private JSONObject decodeObject(String encoded) throws Exception {
        byte[] bytes = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }

    /** 构造 instrumentation 可解析的结果 Bundle。 */
    private Bundle resultBundle(Result result) {
        Bundle bundle = new Bundle();
        JSONObject value = new JSONObject();
        try {
            value.put("success", result.success);
            value.put("message", result.message);
        } catch (Exception ignored) {
            // 固定字段不会产生 JSON 异常。
        }
        String encoded = Base64.encodeToString(value.toString().getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        bundle.putString("lqits", encoded);
        return bundle;
    }

    /** 等待到轨迹帧的目标时间。 */
    private void sleepUntil(long eventTime) {
        long waitMs = eventTime - SystemClock.uptimeMillis();
        if (waitMs > 0L) SystemClock.sleep(waitMs);
    }

    /** 规范化缩写 Activity。 */
    private String normalizeActivity(String packageName, String activityName) {
        return activityName.startsWith(".") ? packageName + activityName : activityName;
    }

    /** 判断非空目标文本是否忽略大小写相等。 */
    private boolean equalsIgnoreCase(String actual, String expected) {
        return expected != null && !expected.isEmpty() && expected.equalsIgnoreCase(actual);
    }

    /** 空目标视为匹配，否则要求文本完全一致。 */
    private boolean matchesValue(String expected, String actual) {
        return expected == null || expected.isEmpty() || expected.equals(actual == null ? "" : actual);
    }

    /** 安全转换 CharSequence。 */
    private String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    /** 将消息集合连接为单行文本。 */
    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append("；");
            result.append(value);
        }
        return result.toString();
    }

    /** 将整数约束到闭区间。 */
    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** 提取异常链中的可读消息。 */
    private String errorMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** 单次执行结果。 */
    private static final class Result {
        final boolean success;
        final String message;

        /** 保存执行结果。 */
        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        /** 创建成功结果。 */
        static Result success(String message) {
            return new Result(true, message);
        }

        /** 创建失败结果。 */
        static Result failure(String message) {
            return new Result(false, message);
        }
    }

    /** 一轮条件匹配结果。 */
    private static final class ConditionSummary {
        final boolean matched;
        final String message;

        /** 保存条件结果。 */
        ConditionSummary(boolean matched, String message) {
            this.matched = matched;
            this.message = message;
        }
    }
}
