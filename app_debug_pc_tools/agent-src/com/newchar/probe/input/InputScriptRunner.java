package com.newchar.probe.input;

import com.newchar.probe.input.InputInjector.CoordinateMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 顺序执行脚本步骤并上报可由 PC 监控的状态。 */
final class InputScriptRunner implements Runnable {

    interface Reporter {
        /** 上报当前运行状态。 */
        void report(String runId, String state, int index, int total, String backend, String message);
    }

    private final AgentScript script;
    private final InputInjector injector;
    private final ShellBridge shell;
    private final PlaybackControl control;
    private final Reporter reporter;
    private volatile int currentIndex;
    private int failureCount;

    /** 创建一次独立脚本运行。 */
    InputScriptRunner(AgentScript script, InputInjector injector, ShellBridge shell,
            PlaybackControl control, Reporter reporter) {
        this.script = script;
        this.injector = injector;
        this.shell = shell;
        this.control = control;
        this.reporter = reporter;
    }

    /** 执行全部步骤，遇到 stop 策略失败时终止。 */
    @Override
    public void run() {
        int total = script.steps.size();
        report("RUNNING", 0, "agent", "脚本开始执行");
        try {
            for (currentIndex = 0; currentIndex < total; currentIndex++) {
                if (!executeCurrentStep()) return;
            }
            reportFinalState(total);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            report("CANCELLED", currentIndex, "agent", "脚本线程已中断");
        } catch (Throwable throwable) {
            report("FAILED", currentIndex, "agent", errorMessage(throwable));
        }
    }

    /** 执行当前步骤并应用重试与失败策略。 */
    private boolean executeCurrentStep() throws Exception {
        AgentScript.Step step = script.steps.get(currentIndex);
        if (!control.sleep(step.delayBeforeMs)) return cancelled();
        report("STEP_STARTED", currentIndex, "agent", step.id + ": " + step.type);
        StepResult result = executeWithRetry(step);
        if (control.isCancelled()) return cancelled();
        report(result.success ? "STEP_OK" : "STEP_FAILED", currentIndex + 1,
                result.backend, step.id + ": " + result.message);
        if (!result.success) failureCount++;
        return true;
    }

    /** 在配置次数内重试失败步骤。 */
    private StepResult executeWithRetry(AgentScript.Step step) throws Exception {
        StepResult result = StepResult.failure("agent", "未执行");
        for (int attempt = 0; attempt <= step.retryCount; attempt++) {
            if (!control.awaitRunnable()) return StepResult.failure("agent", "已取消");
            try {
                result = executeStep(step);
            } catch (InterruptedException interrupted) {
                throw interrupted;
            } catch (Throwable throwable) {
                result = StepResult.failure("agent_exception", errorMessage(throwable));
            }
            if (result.success || control.isCancelled()) return result;
        }
        return result;
    }

    /** 按步骤类型分发具体执行逻辑。 */
    private StepResult executeStep(AgentScript.Step step) throws Exception {
        if ("tap".equals(step.type)) return press(step, Math.max(1L, step.durationMs));
        if ("long_press".equals(step.type)) return press(step, Math.max(500L, step.durationMs));
        if ("gesture".equals(step.type)) return gesture(step);
        if ("multi_touch".equals(step.type)) return multiTouch(step);
        if ("key".equals(step.type)) return key(step);
        if ("wait".equals(step.type)) return waitStep(step);
        if ("launch_app".equals(step.type)) return launch(step);
        if ("package_check".equals(step.type)) return packageCheck(step);
        if ("node_tap".equals(step.type)) return nodeTap(step);
        return StepResult.failure("agent", "不支持的步骤类型 " + step.type);
    }

    /** 使用隐藏 API 点击，失败时切换 input 命令。 */
    private StepResult press(AgentScript.Step step, long durationMs) throws Exception {
        CoordinateMapper mapper = mapper(step.coordinateMode);
        float[] point = mapper.map(step.x, step.y);
        if (injector.injectPress(point[0], point[1], durationMs, control)) {
            return StepResult.success("input_manager", "输入完成");
        }
        if (control.isCancelled()) return StepResult.failure("input_manager", "已取消");
        boolean success = durationMs >= 500L
                ? shell.inputLongPress(point[0], point[1], durationMs)
                : shell.inputTap(point[0], point[1]);
        return fallbackResult(success, "adb_input", "input 点击失败");
    }

    /** 注入单指轨迹，失败时降级为首尾直线 swipe。 */
    private StepResult gesture(AgentScript.Step step) throws Exception {
        CoordinateMapper mapper = mapper(step.coordinateMode);
        if (injector.injectPath(step.points, mapper, control)) {
            return StepResult.success("input_manager", "轨迹完成");
        }
        if (control.isCancelled()) return StepResult.failure("input_manager", "已取消");
        if (step.points.size() < 2) return StepResult.failure("adb_input", "轨迹点不足");
        float[] first = mapPoint(step.points.get(0), mapper);
        float[] last = mapPoint(step.points.get(step.points.size() - 1), mapper);
        boolean success = shell.inputSwipe(first[0], first[1], last[0], last[1], step.durationMs);
        return fallbackResult(success, "adb_input", "input swipe 失败");
    }

    /** 注入完整多指事件帧；系统 input 命令不支持该能力。 */
    private StepResult multiTouch(AgentScript.Step step) throws Exception {
        CoordinateMapper mapper = mapper(step.coordinateMode);
        if (injector.injectFrames(step.frames, mapper, control)) {
            return StepResult.success("input_manager", "多指手势完成");
        }
        if (control.isCancelled()) return StepResult.failure("input_manager", "已取消");
        return fallbackMultiTouch(step, mapper);
    }

    /** 将多指轨迹降级为逐指执行的直线 swipe。 */
    private StepResult fallbackMultiTouch(AgentScript.Step step, CoordinateMapper mapper) {
        Map<Integer, PointerSpan> spans = pointerSpans(step.frames);
        boolean success = !spans.isEmpty();
        for (PointerSpan span : spans.values()) {
            float[] first = mapPoint(span.first, mapper);
            float[] last = mapPoint(span.last, mapper);
            success = shell.inputSwipe(first[0], first[1], last[0], last[1],
                    Math.max(1L, step.durationMs)) && success;
        }
        return success
                ? StepResult.success("adb_input_approx", "多指已按逐指轨迹近似执行")
                : StepResult.failure("adb_input_approx", "多指逐指兜底失败");
    }

    /** 汇总每个 pointerId 的首尾触点。 */
    private Map<Integer, PointerSpan> pointerSpans(List<AgentScript.Frame> frames) {
        Map<Integer, PointerSpan> spans = new LinkedHashMap<>();
        for (AgentScript.Frame frame : frames) {
            for (AgentScript.Point point : frame.pointers) {
                PointerSpan span = spans.get(point.id);
                if (span == null) {
                    span = new PointerSpan(point);
                    spans.put(point.id, span);
                }
                span.last = point;
            }
        }
        return spans;
    }

    /** 注入按键，隐藏 API 失败时使用 input keyevent。 */
    private StepResult key(AgentScript.Step step) {
        if (injector.injectKey(step.keyCode)) return StepResult.success("input_manager", "按键完成");
        return fallbackResult(shell.inputKey(step.keyCode), "adb_input", "input keyevent 失败");
    }

    /** 执行可暂停和取消的等待步骤。 */
    private StepResult waitStep(AgentScript.Step step) throws InterruptedException {
        boolean success = control.sleep(Math.max(0L, step.waitMs));
        return fallbackResult(success, "agent", success ? "等待完成" : "等待已取消");
    }

    /** 启动步骤指定或脚本默认的应用。 */
    private StepResult launch(AgentScript.Step step) {
        String packageName = packageName(step.packageName);
        boolean success = shell.launchPackage(packageName);
        return fallbackResult(success, "shell", "无法启动 " + packageName);
    }

    /** 在超时时间内等待前台包名符合预期。 */
    private StepResult packageCheck(AgentScript.Step step) throws InterruptedException {
        String expected = packageName(step.expectedPackage);
        long deadline = System.currentTimeMillis() + Math.max(0L, step.timeoutMs);
        do {
            String current = shell.foregroundPackage();
            if (expected.equals(current)) return StepResult.success("shell", "前台应用匹配");
            if (!control.sleep(100L)) return StepResult.failure("shell", "已取消");
        } while (System.currentTimeMillis() <= deadline);
        return StepResult.failure("shell", "前台应用不是 " + expected);
    }

    /** 使用 UI XML 节点定位后点击节点中心。 */
    private StepResult nodeTap(AgentScript.Step step) throws Exception {
        float[] center = shell.findNodeCenter(step.selector);
        if (center == null) return StepResult.failure("uiautomator", "未找到匹配节点");
        if (injector.injectPress(center[0], center[1], Math.max(1L, step.durationMs), control)) {
            return StepResult.success("uiautomator+input_manager", "节点点击完成");
        }
        boolean success = shell.inputTap(center[0], center[1]);
        return fallbackResult(success, "uiautomator+adb_input", "节点 input 点击失败");
    }

    /** 创建当前步骤的坐标映射器。 */
    private CoordinateMapper mapper(String mode) {
        return new CoordinateMapper(script.sourceWidth, script.sourceHeight, shell.displaySize(), mode);
    }

    /** 映射一个脚本触点。 */
    private float[] mapPoint(AgentScript.Point point, CoordinateMapper mapper) {
        return mapper.map(point.x, point.y);
    }

    /** 返回步骤包名，空值时回退到脚本目标包名。 */
    private String packageName(String value) {
        return value == null || value.isEmpty() ? script.targetPackage : value;
    }

    /** 将布尔执行结果转换为步骤结果。 */
    private StepResult fallbackResult(boolean success, String backend, String failure) {
        return success ? StepResult.success(backend, "操作完成") : StepResult.failure(backend, failure);
    }

    /** 上报任务取消并终止循环。 */
    private boolean cancelled() {
        report("CANCELLED", currentIndex, "agent", "脚本已取消");
        return false;
    }

    /** 根据失败计数上报最终状态，但保证全部步骤都已尝试。 */
    private void reportFinalState(int total) {
        if (control.isCancelled()) {
            report("CANCELLED", total, "agent", "脚本已取消");
        } else if (failureCount > 0) {
            report("COMPLETED_WITH_ERRORS", total, "agent",
                    "全部步骤已尝试，失败 " + failureCount + " 步");
        } else {
            report("COMPLETED", total, "agent", "脚本执行完成");
        }
    }

    /** 上报带当前脚本标识和总步骤数的状态。 */
    private void report(String state, int index, String backend, String message) {
        reporter.report(script.scriptId, state, index, script.steps.size(), backend, message);
    }

    /** 提取异常的可读信息。 */
    private String errorMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** 步骤执行结果。 */
    private static final class StepResult {
        final boolean success;
        final String backend;
        final String message;

        /** 保存一次步骤执行结果。 */
        StepResult(boolean success, String backend, String message) {
            this.success = success;
            this.backend = backend;
            this.message = message;
        }

        /** 创建成功结果。 */
        static StepResult success(String backend, String message) {
            return new StepResult(true, backend, message);
        }

        /** 创建失败结果。 */
        static StepResult failure(String backend, String message) {
            return new StepResult(false, backend, message);
        }
    }

    /** 一个指针轨迹的首尾触点。 */
    private static final class PointerSpan {
        final AgentScript.Point first;
        AgentScript.Point last;

        /** 使用首个触点初始化轨迹范围。 */
        PointerSpan(AgentScript.Point point) {
            first = point;
            last = point;
        }
    }
}