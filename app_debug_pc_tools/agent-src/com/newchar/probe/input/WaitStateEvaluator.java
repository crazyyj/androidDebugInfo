package com.newchar.probe.input;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 在 dex agent 内轮询并判断 waitState 条件。 */
final class WaitStateEvaluator {

    private static final long COMMAND_TIMEOUT_MS = 5000L;
    private final AgentScript script;
    private final ShellBridge shell;
    private final PlaybackControl control;
    private long startedNanos;
    private long pausedAtStartNanos;
    private long currentWaitMs;

    /** 创建一次条件等待求值器。 */
    WaitStateEvaluator(AgentScript script, ShellBridge shell, PlaybackControl control) {
        this.script = script;
        this.shell = shell;
        this.control = control;
    }

    /** 立即采样并持续轮询到条件满足、取消或超时。 */
    Result await(AgentScript.Step step) throws InterruptedException {
        startedNanos = System.nanoTime();
        pausedAtStartNanos = control.pausedDurationNanos();
        currentWaitMs = step.waitMs;
        int consecutive = 0;
        int rounds = 0;
        String observation = "尚未采样";
        while (true) {
            if (!control.awaitRunnable()) return Result.cancelled(observation);
            if (rounds > 0 && remainingMs(step.waitMs) <= 0L) return Result.timeout(rounds, observation);
            RoundSnapshot snapshot = new RoundSnapshot(this);
            ConditionSummary summary;
            try {
                summary = evaluateConditions(step.waitState, snapshot);
            } finally {
                snapshot.close();
            }
            rounds++;
            observation = summary.observation;
            if (remainingMs(step.waitMs) <= 0L) return Result.timeout(rounds, observation);
            consecutive = summary.matched ? consecutive + 1 : 0;
            if (consecutive >= step.waitState.consecutiveMatches) {
                return Result.matched(rounds, elapsedMs(), observation);
            }
            long sleepMs = Math.min(step.waitState.pollIntervalMs, remainingMs(step.waitMs));
            if (sleepMs <= 0L) return Result.timeout(rounds, observation);
            if (!control.sleep(sleepMs)) return Result.cancelled(observation);
        }
    }

    /** 对同一轮快照计算全部条件并汇总 all/any。 */
    private ConditionSummary evaluateConditions(AgentScript.WaitState state, RoundSnapshot snapshot) {
        List<String> observations = new ArrayList<>();
        boolean matched = "all".equals(state.match);
        for (int index = 0; index < state.conditions.size(); index++) {
            ConditionResult result = evaluateCondition(state.conditions.get(index), snapshot);
            observations.add((index + 1) + ":" + result.message);
            matched = "all".equals(state.match) ? matched && result.matched : matched || result.matched;
        }
        return new ConditionSummary(matched, join(observations));
    }

    /** 按条件类型执行具体判断。 */
    private ConditionResult evaluateCondition(AgentScript.Condition condition, RoundSnapshot snapshot) {
        if ("ui_exists".equals(condition.kind)) return evaluateUiExists(condition, snapshot.ui());
        if ("ui_attribute".equals(condition.kind)) return evaluateUiAttribute(condition, snapshot.ui());
        if ("foreground".equals(condition.kind)) return evaluateForeground(condition, snapshot.component());
        if ("region_color".equals(condition.kind)) return evaluateColor(condition, snapshot.screen());
        return ConditionResult.failure("未知条件 " + condition.kind);
    }

    /** 判断匹配节点是否按预期出现或消失。 */
    private ConditionResult evaluateUiExists(AgentScript.Condition condition, ShellBridge.UiSnapshot snapshot) {
        if (!snapshot.success) return ConditionResult.failure("UI采集失败=" + snapshot.message);
        boolean found = findNode(snapshot.nodes, condition.selector) != null;
        boolean matched = found == condition.expected;
        return new ConditionResult(matched, "ui_exists 当前=" + found + " 目标=" + condition.expected);
    }

    /** 判断首个匹配节点的指定属性。 */
    private ConditionResult evaluateUiAttribute(AgentScript.Condition condition, ShellBridge.UiSnapshot snapshot) {
        if (!snapshot.success) return ConditionResult.failure("UI采集失败=" + snapshot.message);
        ShellBridge.UiNode node = findNode(snapshot.nodes, condition.selector);
        if (node == null) return ConditionResult.failure("ui_attribute 未找到节点");
        String actual = node.attribute(condition.attribute);
        boolean matched = compare(actual, condition);
        return new ConditionResult(matched, condition.attribute + " 当前=" + actual + " 目标=" + condition.value);
    }

    /** 返回第一个满足选择器的 UI 节点。 */
    private ShellBridge.UiNode findNode(List<ShellBridge.UiNode> nodes, AgentScript.Selector selector) {
        for (ShellBridge.UiNode node : nodes) {
            if (node.matches(selector)) return node;
        }
        return null;
    }

    /** 根据大小写配置执行文本或正则比较。 */
    private boolean compare(String actual, AgentScript.Condition condition) {
        String left = condition.ignoreCase ? actual.toLowerCase(Locale.ROOT) : actual;
        String right = condition.ignoreCase ? condition.value.toLowerCase(Locale.ROOT) : condition.value;
        if ("equals".equals(condition.operator)) return left.equals(right);
        if ("not_equals".equals(condition.operator)) return !left.equals(right);
        if ("contains".equals(condition.operator)) return left.contains(right);
        return condition.regexPattern != null && condition.regexPattern.matcher(actual).find();
    }

    /** 判断当前前台包名及可选 Activity。 */
    private ConditionResult evaluateForeground(AgentScript.Condition condition,
            ShellBridge.ComponentSnapshot snapshot) {
        if (!snapshot.success) return ConditionResult.failure("前台采集失败=" + snapshot.message);
        boolean packageMatched = condition.packageName.equalsIgnoreCase(snapshot.packageName);
        boolean activityMatched = condition.activityName.isEmpty()
                || condition.activityName.equalsIgnoreCase(snapshot.activityName);
        return new ConditionResult(packageMatched && activityMatched,
                "foreground 当前=" + snapshot.message + " 目标=" + condition.packageName
                        + (condition.activityName.isEmpty() ? "" : "/" + condition.activityName));
    }

    /** 判断截图区域内目标颜色采样像素占比。 */
    private ConditionResult evaluateColor(AgentScript.Condition condition, ShellBridge.ScreenSnapshot snapshot) {
        if (snapshot.bitmap == null) return ConditionResult.failure("截图失败=" + snapshot.message);
        int[] rect = mapRect(condition, snapshot.bitmap);
        if (rect == null) return ConditionResult.failure("region_color 区域越界");
        int target = Integer.parseInt(condition.color.substring(1), 16);
        long[] counts = countMatchingPixels(snapshot.bitmap, rect, target,
                condition.tolerance, condition.sampleStride);
        float ratio = counts[1] == 0L ? 0f : (float) counts[0] / (float) counts[1];
        boolean matched = ratio >= condition.minMatchRatio;
        return new ConditionResult(matched, String.format(Locale.ROOT,
                "target_ratio 当前=%.4f 目标>=%.4f", ratio, condition.minMatchRatio));
    }

    /** 将脚本坐标矩形映射并裁剪到截图范围。 */
    private int[] mapRect(AgentScript.Condition condition, Bitmap bitmap) {
        float scaleX = 1f;
        float scaleY = 1f;
        if ("normalized".equals(condition.coordinateMode)) {
            scaleX = bitmap.getWidth();
            scaleY = bitmap.getHeight();
        } else if ("source_pixel".equals(condition.coordinateMode)) {
            if (script.sourceWidth <= 0 || script.sourceHeight <= 0) return null;
            scaleX = (float) bitmap.getWidth() / script.sourceWidth;
            scaleY = (float) bitmap.getHeight() / script.sourceHeight;
        }
        int left = clamp(Math.round(condition.left * scaleX), 0, bitmap.getWidth());
        int top = clamp(Math.round(condition.top * scaleY), 0, bitmap.getHeight());
        int right = clamp(Math.round(condition.right * scaleX), 0, bitmap.getWidth());
        int bottom = clamp(Math.round(condition.bottom * scaleY), 0, bitmap.getHeight());
        return right > left && bottom > top ? new int[]{left, top, right, bottom} : null;
    }

    /** 按采样步长统计目标色近似像素数和总像素数。 */
    private long[] countMatchingPixels(Bitmap bitmap, int[] rect, int target, int tolerance, int stride) {
        long matched = 0L;
        long total = 0L;
        for (int y = rect[1]; y < rect[3]; y += stride) {
            for (int x = rect[0]; x < rect[2]; x += stride) {
                if (colorNear(bitmap.getPixel(x, y), target, tolerance)) matched++;
                total++;
            }
        }
        return new long[]{matched, total};
    }

    /** 判断两个 RGB 颜色的各通道差是否都在容差内。 */
    private boolean colorNear(int actual, int target, int tolerance) {
        return Math.abs(((actual >> 16) & 255) - ((target >> 16) & 255)) <= tolerance
                && Math.abs(((actual >> 8) & 255) - ((target >> 8) & 255)) <= tolerance
                && Math.abs((actual & 255) - (target & 255)) <= tolerance;
    }

    /** 返回不超过当前 waitMs 剩余量的状态命令时限。 */
    private long commandTimeoutMs() {
        return Math.max(1L, Math.min(COMMAND_TIMEOUT_MS, remainingMs(currentWaitMs)));
    }

    /** 计算扣除暂停时长后的剩余等待毫秒数。 */
    private long remainingMs(long waitMs) {
        return Math.max(0L, waitMs - elapsedMs());
    }

    /** 计算本次等待实际运行的毫秒数。 */
    private long elapsedMs() {
        long paused = control.pausedDurationNanos() - pausedAtStartNanos;
        return Math.max(0L, (System.nanoTime() - startedNanos - paused) / 1000000L);
    }

    /** 将数值限制到闭区间。 */
    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** 使用分号拼接本轮全部条件观测值。 */
    private String join(List<String> values) {
        StringBuilder output = new StringBuilder();
        for (String value : values) {
            if (output.length() > 0) output.append("; ");
            output.append(value);
        }
        return output.toString();
    }

    /** 缓存单轮最多一次 UI、前台和截图采样。 */
    private static final class RoundSnapshot {
        private final WaitStateEvaluator evaluator;
        private ShellBridge.UiSnapshot ui;
        private ShellBridge.ComponentSnapshot component;
        private ShellBridge.ScreenSnapshot screen;

        /** 保存本轮状态采集所需的求值器。 */
        RoundSnapshot(WaitStateEvaluator evaluator) {
            this.evaluator = evaluator;
        }

        /** 返回本轮唯一 UI 快照。 */
        ShellBridge.UiSnapshot ui() {
            if (ui == null) ui = evaluator.shell.dumpUiNodes(evaluator.commandTimeoutMs());
            return ui;
        }

        /** 返回本轮唯一前台组件快照。 */
        ShellBridge.ComponentSnapshot component() {
            if (component == null) {
                component = evaluator.shell.foregroundComponent(evaluator.commandTimeoutMs());
            }
            return component;
        }

        /** 返回本轮唯一截图。 */
        ShellBridge.ScreenSnapshot screen() {
            if (screen == null) screen = evaluator.shell.captureScreen(evaluator.commandTimeoutMs());
            return screen;
        }

        /** 回收本轮截图，降低全屏 Bitmap 内存峰值。 */
        void close() {
            if (screen != null && screen.bitmap != null && !screen.bitmap.isRecycled()) {
                screen.bitmap.recycle();
            }
        }
    }

    /** 本轮所有条件的组合结果。 */
    private static final class ConditionSummary {
        final boolean matched;
        final String observation;

        /** 保存组合匹配结果和观测说明。 */
        ConditionSummary(boolean matched, String observation) {
            this.matched = matched;
            this.observation = observation;
        }
    }

    /** 单条条件的匹配结果。 */
    private static final class ConditionResult {
        final boolean matched;
        final String message;

        /** 保存单条条件结果。 */
        ConditionResult(boolean matched, String message) {
            this.matched = matched;
            this.message = message;
        }

        /** 创建未满足条件的结果。 */
        static ConditionResult failure(String message) {
            return new ConditionResult(false, message);
        }
    }

    /** 一次条件等待的最终结果。 */
    static final class Result {
        final boolean matched;
        final boolean cancelled;
        final String message;

        /** 保存等待结果。 */
        private Result(boolean matched, boolean cancelled, String message) {
            this.matched = matched;
            this.cancelled = cancelled;
            this.message = message;
        }

        /** 创建满足条件的结果。 */
        static Result matched(int rounds, long elapsedMs, String observation) {
            return new Result(true, false, "条件满足，轮询=" + rounds + "，耗时=" + elapsedMs
                    + "ms，最后状态=" + observation);
        }

        /** 创建超时结果。 */
        static Result timeout(int rounds, String observation) {
            return new Result(false, false, "条件等待超时，轮询=" + rounds + "，最后状态=" + observation);
        }

        /** 创建取消结果。 */
        static Result cancelled(String observation) {
            return new Result(false, true, "条件等待已取消，最后状态=" + observation);
        }
    }
}