package com.newchar.debug.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

/** 解析并执行 PC 下发的一条 LQITS 辅助服务指令。 */
final class AccessibilityScriptExecutor {

    private final ScriptAccessibilityService mService;
    private final AccessibilityNodeResolver mNodes = new AccessibilityNodeResolver();

    /** 保存当前已连接的无障碍服务。 */
    AccessibilityScriptExecutor(ScriptAccessibilityService service) {
        mService = service;
    }

    /** 执行完整单步载荷并返回 PC 通信协议结果。 */
    String execute(String payloadJson) {
        try {
            JSONObject payload = new JSONObject(payloadJson);
            JSONObject step = payload.getJSONObject("step");
            int sourceWidth = payload.optInt("sourceWidth");
            int sourceHeight = payload.optInt("sourceHeight");
            String targetPackage = payload.optString("targetPackage");
            Result result = executeStep(step, sourceWidth, sourceHeight, targetPackage);
            return (result.success ? "OK|" : "ERROR|") + result.message;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "ERROR|辅助服务执行已中断";
        } catch (Throwable throwable) {
            return "ERROR|" + errorMessage(throwable);
        }
    }

    /** 按步骤类型分发到节点、手势、全局动作或等待执行器。 */
    private Result executeStep(JSONObject step, int sourceWidth, int sourceHeight,
            String targetPackage) throws Exception {
        String type = step.optString("type");
        AccessibilityGestureDispatcher gestures = new AccessibilityGestureDispatcher(
                mService, sourceWidth, sourceHeight);
        if ("tap".equals(type)) return gestureResult(gestures.press(step,
                Math.max(1L, step.optLong("durationMs", 100L))), "点击");
        if ("long_press".equals(type)) return gestureResult(gestures.press(step,
                Math.max(500L, step.optLong("durationMs", 500L))), "长按");
        if ("gesture".equals(type)) return gestureResult(gestures.gesture(step), "滑动手势");
        if ("multi_touch".equals(type)) return gestureResult(gestures.multiTouch(step), "多指手势");
        if ("key".equals(type)) return globalKey(step.optInt("keyCode"));
        if ("wait".equals(type)) return waitStep(step, sourceWidth, sourceHeight);
        if ("launch_app".equals(type)) return launch(step, targetPackage);
        if ("node_tap".equals(type)) return nodeTap(step, gestures);
        return Result.failure("辅助服务不支持 " + type);
    }

    /** 将手势布尔结果转换为统一返回值。 */
    private Result gestureResult(boolean success, String label) {
        return success ? Result.success(label + "完成") : Result.failure(label + "失败");
    }

    /** 将可表达的 Android KeyCode 映射为无障碍全局动作。 */
    private Result globalKey(int keyCode) {
        int action;
        if (keyCode == KeyEvent.KEYCODE_BACK) action = AccessibilityService.GLOBAL_ACTION_BACK;
        else if (keyCode == KeyEvent.KEYCODE_HOME) action = AccessibilityService.GLOBAL_ACTION_HOME;
        else if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) action = AccessibilityService.GLOBAL_ACTION_RECENTS;
        else if (keyCode == KeyEvent.KEYCODE_NOTIFICATION) {
            action = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS;
        } else return Result.failure("辅助服务无法注入 KeyCode " + keyCode);
        return mService.performGlobalAction(action)
                ? Result.success("全局按键完成") : Result.failure("全局按键失败");
    }

    /** 执行固定等待或完整 waitState 条件轮询。 */
    private Result waitStep(JSONObject step, int sourceWidth, int sourceHeight)
            throws InterruptedException {
        if (step.optJSONObject("waitState") == null) {
            SystemClock.sleep(Math.max(0L, step.optLong("waitMs")));
            return Result.success("等待完成");
        }
        AccessibilityWaitEvaluator.Result result = new AccessibilityWaitEvaluator(
                mService, sourceWidth, sourceHeight).await(step);
        return result.success ? Result.success(result.message) : Result.failure(result.message);
    }

    /** 启动步骤包名或脚本默认包名对应的桌面入口。 */
    private Result launch(JSONObject step, String targetPackage) {
        String packageName = step.optString("packageName");
        if (packageName.isEmpty()) packageName = targetPackage;
        if (packageName.isEmpty()) return Result.failure("启动应用步骤缺少包名");
        Intent intent = mService.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) return Result.failure("未找到应用入口 " + packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mService.startActivity(intent);
        return Result.success("应用已启动");
    }

    /** 定位节点，优先执行节点点击，必要时点击其屏幕中心。 */
    private Result nodeTap(JSONObject step, AccessibilityGestureDispatcher gestures)
            throws InterruptedException {
        AccessibilityNodeInfo root = mService.getRootInActiveWindow();
        if (root == null) return Result.failure("活动窗口根节点不可用");
        AccessibilityNodeInfo node;
        try {
            node = mNodes.find(root, step.optJSONObject("selector"));
        } finally {
            root.recycle();
        }
        if (node == null) return Result.failure("未找到匹配节点");
        try {
            if (mNodes.click(node)) return Result.success("节点点击完成");
            float[] center = mNodes.center(node);
            JSONObject fallback = pointStep(center, step.optLong("durationMs", 100L));
            return gestureResult(gestures.press(fallback,
                    Math.max(1L, step.optLong("durationMs", 100L))), "节点中心点击");
        } finally {
            node.recycle();
        }
    }

    /** 创建像素坐标模式的节点中心点击步骤。 */
    private JSONObject pointStep(float[] center, long durationMs) {
        JSONObject step = new JSONObject();
        try {
            step.put("x", center[0]);
            step.put("y", center[1]);
            step.put("coordinateMode", "pixel");
            step.put("durationMs", durationMs);
        } catch (Exception ignored) {
            // 固定字段不会产生 JSON 异常。
        }
        return step;
    }

    /** 返回异常类型与可读消息。 */
    private String errorMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    /** 单步执行结果。 */
    private static final class Result {
        final boolean success;
        final String message;

        /** 保存成功状态和可读说明。 */
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
}
