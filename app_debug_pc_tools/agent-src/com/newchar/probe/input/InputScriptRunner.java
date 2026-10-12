package com.newchar.probe.input;

import com.newchar.probe.input.InputInjector.CoordinateMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 顺序执行脚本步骤并上报可由 PC 监控的状态。 */
final class InputScriptRunner implements Runnable {

    private static final int MAX_STEP_EXECUTIONS = 100;
    private static final long WAIT_STATE_MATCH_DELAY_MS = 600L;

    interface Reporter {
        /** 上报当前运行状态。 */
        void report(String runId, String state, int index, int total, String backend, String message);
    }

    interface ExternalStepExecutor {
        /** 请求 PC 经 App 通信通道执行辅助服务兜底。 */
        ShellBridge.BackendResult execute(int index, AgentScript.Step step, String message)
                throws InterruptedException;
    }

    private final AgentScript script;
    private final InputInjector injector;
    private final ShellBridge shell;
    private final PlaybackControl control;
    private final Reporter reporter;
    private final ExternalStepExecutor externalExecutor;
    private volatile int currentIndex;
    private int failureCount;
    private int stepExecutionCount;
    private final Map<String, Integer> waitTimeoutCounts = new LinkedHashMap<>();

    /** 创建一次独立脚本运行。 */
    InputScriptRunner(AgentScript script, InputInjector injector, ShellBridge shell,
            PlaybackControl control, Reporter reporter, ExternalStepExecutor externalExecutor) {
        this.script = script;
        this.injector = injector;
        this.shell = shell;
        this.control = control;
        this.reporter = reporter;
        this.externalExecutor = externalExecutor;
    }

    /** 执行全部步骤，遇到 stop 策略失败时终止。 */
    @Override
    public void run() {
        int total = script.steps.size();
        report("RUNNING", 0, "agent", "脚本开始执行");
        try {
            currentIndex = 0;
            while (currentIndex < total) {
                if (!allowNextExecution()) return;
                int nextIndex = executeCurrentStep();
                if (nextIndex < 0) return;
                currentIndex = nextIndex;
            }
            reportFinalState(total);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            report("CANCELLED", currentIndex, "agent", "脚本线程已中断");
        } catch (Throwable throwable) {
            report("FAILED", currentIndex, "agent", errorMessage(throwable));
        }
    }

    /** 在全脚本步骤执行上限内允许下一步开始。 */
    private boolean allowNextExecution() {
        int nextCount = stepExecutionCount + 1;
        if (nextCount <= MAX_STEP_EXECUTIONS) {
            stepExecutionCount = nextCount;
            return true;
        }
        AgentScript.Step step = script.steps.get(currentIndex);
        report("FAILED", currentIndex, "agent", "步骤执行次数超过 100，当前计数=" + nextCount
                + "，stepId=" + step.id + "；请检查等待条件、jump 和整体流程合理性");
        return false;
    }

    /** 执行当前步骤并返回下一执行位置，负数表示终止。 */
    private int executeCurrentStep() throws Exception {
        AgentScript.Step step = script.steps.get(currentIndex);
        if (!control.sleep(step.delayBeforeMs)) return cancelledIndex();
        String label = stepLabel(step);
        report("STEP_STARTED", currentIndex, "agent", label);
        StepResult result = executeWithRetry(step);
        if (control.isCancelled()) return cancelledIndex();
        report(result.success ? "STEP_OK" : "STEP_FAILED", currentIndex + 1,
                result.backend, label + "：" + result.message);
        if (result.stopScript) {
            report("FAILED", currentIndex + 1, result.backend, label + "：" + result.message);
            return -1;
        }
        if (!result.success) failureCount++;
        if (result.jumpTargetId != null) return AgentScript.findStep(script, result.jumpTargetId);
        return currentIndex + 1;
    }

    /** 在配置次数内重试失败步骤。 */
    private StepResult executeWithRetry(AgentScript.Step step) throws Exception {
        StepResult result = StepResult.failure("agent", "未执行");
        for (int attempt = 0; attempt <= step.retryCount; attempt++) {
            if (!control.awaitRunnable()) return StepResult.failure("agent", "已取消");
            try {
                result = executeRoutedStep(step);
            } catch (InterruptedException interrupted) {
                throw interrupted;
            } catch (Throwable throwable) {
                result = StepResult.failure("agent_exception", errorMessage(throwable));
            }
            if (result.success || control.isCancelled()) return result;
        }
        return result;
    }

    /** 按 app_process、uiautomator、adb、辅助服务顺序执行单条指令。 */
    private StepResult executeRoutedStep(AgentScript.Step step) throws Exception {
        if (step.waitState != null) return executeRoutedWaitState(step);
        StepResult primary = executeStep(step);
        if (primary.success || control.isCancelled()) return primary;
        StepResult instrumentation = executeInstrumentation(step, primary.message);
        if (instrumentation.success || control.isCancelled()) return instrumentation;
        StepResult adb = executeAdbStep(step);
        if (adb.success || control.isCancelled()) return adb;
        return executeExternal(step, failureChain(primary, instrumentation, adb));
    }

    /** 条件等待依次使用四个环境，全部失败后才应用超时策略。 */
    private StepResult executeRoutedWaitState(AgentScript.Step step) throws Exception {
        StepResult primary = evaluateWaitState(step, "wait_state_app_process");
        if (primary.success || control.isCancelled()) return primary;
        StepResult instrumentation = executeInstrumentation(step, primary.message);
        if (instrumentation.success || control.isCancelled()) return instrumentation;
        StepResult adb = evaluateWaitState(step, "wait_state_adb");
        if (adb.success || control.isCancelled()) return adb;
        StepResult external = executeExternal(step, failureChain(primary, instrumentation, adb));
        return external.success ? finishMatchedWait("accessibility", external.message)
                : applyWaitTimeout(step, external.message);
    }

    /** 按步骤类型分发具体执行逻辑。 */
    private StepResult executeStep(AgentScript.Step step) throws Exception {
        if ("tap".equals(step.type)) return press(step, Math.max(1L, step.durationMs));
        if ("long_press".equals(step.type)) return press(step, Math.max(500L, step.durationMs));
        if ("gesture".equals(step.type)) return gesture(step);
        if ("multi_touch".equals(step.type)) return multiTouch(step);
        if ("key".equals(step.type)) return key(step);
        if ("wait".equals(step.type)) return waitStep(step);
        if ("launch_app".equals(step.type) || "node_tap".equals(step.type)) {
            return StepResult.failure("app_process", "该指令交由下一执行通道");
        }
        return StepResult.failure("app_process", "不支持的步骤类型 " + step.type);
    }

    /** 使用 app_process 隐藏 API 注入点击。 */
    private StepResult press(AgentScript.Step step, long durationMs) throws Exception {
        CoordinateMapper mapper = mapper(step.coordinateMode);
        float[] point = mapper.map(step.x, step.y);
        if (injector.injectPress(point[0], point[1], durationMs, control)) {
            return StepResult.success("input_manager", "输入完成");
        }
        return StepResult.failure("input_manager", control.isCancelled() ? "已取消" : "输入注入失败");
    }

    /** 使用 app_process 隐藏 API 注入单指轨迹。 */
    private StepResult gesture(AgentScript.Step step) throws Exception {
        CoordinateMapper mapper = mapper(step.coordinateMode);
        if (injector.injectPath(step.points, mapper, control)) {
            return StepResult.success("input_manager", "轨迹完成");
        }
        return StepResult.failure("input_manager", control.isCancelled() ? "已取消" : "轨迹注入失败");
    }

    /** 注入完整多指事件帧；系统 input 命令不支持该能力。 */
    private StepResult multiTouch(AgentScript.Step step) throws Exception {
        CoordinateMapper mapper = mapper(step.coordinateMode);
        if (injector.injectFrames(step.frames, mapper, control)) {
            return StepResult.success("input_manager", "多指手势完成");
        }
        return StepResult.failure("input_manager", control.isCancelled() ? "已取消" : "多指注入失败");
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

    /** 使用 app_process 隐藏 API 注入按键。 */
    private StepResult key(AgentScript.Step step) {
        if (injector.injectKey(step.keyCode)) return StepResult.success("input_manager", "按键完成");
        return StepResult.failure("input_manager", "按键注入失败");
    }

    /** 执行固定等待或 waitState 条件等待。 */
    private StepResult waitStep(AgentScript.Step step) throws InterruptedException {
        boolean success = control.sleep(Math.max(0L, step.waitMs));
        return fallbackResult(success, "agent", success ? "等待完成" : "等待已取消");
    }

    /** 在指定执行环境中完成一次条件等待。 */
    private StepResult evaluateWaitState(AgentScript.Step step, String backend) throws InterruptedException {
        WaitStateEvaluator.Result result = new WaitStateEvaluator(script, shell, control).await(step);
        if (result.cancelled) return StepResult.failure(backend, result.message);
        if (result.matched) return finishMatchedWait(backend, result.message);
        return StepResult.failure(backend, result.message);
    }

    /** 所有执行环境均超时后应用计数和 stop/jump 策略。 */
    private StepResult applyWaitTimeout(AgentScript.Step step, String message) {
        int count = waitTimeoutCounts.containsKey(step.id) ? waitTimeoutCounts.get(step.id) + 1 : 1;
        waitTimeoutCounts.put(step.id, count);
        String countedMessage = message + "，超时计数=" + count + "/" + step.waitState.maxTimeoutCount;
        if (count > step.waitState.maxTimeoutCount) {
            return StepResult.stop("wait_state", countedMessage + "；已超过单步骤上限，请检查等待条件、jump 和整体流程合理性");
        }
        if ("jump".equals(step.timeoutPolicy)) {
            return StepResult.jump("wait_state", countedMessage + "，跳转到 " + step.jumpTargetId,
                    step.jumpTargetId);
        }
        return StepResult.stop("wait_state", countedMessage + "，按 stop 策略终止");
    }

    /** 条件命中后固定等待 600ms，再允许执行后续步骤。 */
    private StepResult finishMatchedWait(String backend, String message) throws InterruptedException {
        if (!control.sleep(WAIT_STATE_MATCH_DELAY_MS)) {
            return StepResult.failure(backend, "条件已满足，后续延迟期间取消");
        }
        return StepResult.success(backend, message + "，命中后已等待 600ms");
    }

    /** 调用独立 instrumentation，并附带前一通道的失败原因。 */
    private StepResult executeInstrumentation(AgentScript.Step step, String previous) {
        ShellBridge.BackendResult result = shell.runInstrumentation(script, step);
        String message = result.message + (result.success ? "" : "；上一步=" + previous);
        return result.success ? StepResult.success("uiautomator", message)
                : StepResult.failure("uiautomator", message);
    }

    /** 使用 adb shell 等价命令完成最后一个本地执行通道。 */
    private StepResult executeAdbStep(AgentScript.Step step) throws Exception {
        if ("tap".equals(step.type) || "long_press".equals(step.type)) return adbPress(step);
        if ("gesture".equals(step.type)) return adbGesture(step);
        if ("multi_touch".equals(step.type)) return fallbackMultiTouch(step, mapper(step.coordinateMode));
        if ("key".equals(step.type)) return fallbackResult(shell.inputKey(step.keyCode), "adb_input", "input keyevent 失败");
        if ("launch_app".equals(step.type)) return launch(step);
        if ("node_tap".equals(step.type)) return nodeTap(step);
        return StepResult.failure("adb_input", "adb 不支持 " + step.type);
    }

    /** 使用 adb input 执行点击或长按。 */
    private StepResult adbPress(AgentScript.Step step) {
        float[] point = mapper(step.coordinateMode).map(step.x, step.y);
        boolean success = "long_press".equals(step.type)
                ? shell.inputLongPress(point[0], point[1], Math.max(500L, step.durationMs))
                : shell.inputTap(point[0], point[1]);
        return fallbackResult(success, "adb_input", "input 点击失败");
    }

    /** 使用 adb input swipe 近似执行单指轨迹。 */
    private StepResult adbGesture(AgentScript.Step step) {
        if (step.points.size() < 2) return StepResult.failure("adb_input", "轨迹点不足");
        CoordinateMapper mapper = mapper(step.coordinateMode);
        float[] first = mapPoint(step.points.get(0), mapper);
        float[] last = mapPoint(step.points.get(step.points.size() - 1), mapper);
        boolean success = shell.inputSwipe(first[0], first[1], last[0], last[1], step.durationMs);
        return fallbackResult(success, "adb_input", "input swipe 失败");
    }

    /** 汇总三个执行环境的失败原因。 */
    private String failureChain(StepResult primary, StepResult instrumentation, StepResult adb) {
        return "app_process=" + primary.message + "；uiautomator=" + instrumentation.message
                + "；adb=" + adb.message;
    }

    /** 请求 PC 通过现有 App 通信链路执行辅助服务兜底。 */
    private StepResult executeExternal(AgentScript.Step step, String previous) throws InterruptedException {
        ShellBridge.BackendResult result = externalExecutor.execute(currentIndex, step, previous);
        return result.success ? StepResult.success("accessibility", result.message)
                : StepResult.failure("all_backends", previous + "；辅助服务=" + result.message);
    }

    /** 启动步骤指定或脚本默认的应用。 */
    private StepResult launch(AgentScript.Step step) {
        String packageName = packageName(step.packageName);
        boolean success = shell.launchPackage(packageName);
        return fallbackResult(success, "shell", "无法启动 " + packageName);
    }

    /** 使用 UI XML 节点定位后点击节点中心。 */
    private StepResult nodeTap(AgentScript.Step step) throws Exception {
        float[] center = shell.findNodeCenter(step.selector);
        if (center == null) return StepResult.failure("uiautomator", "未找到匹配节点");
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

    /** 返回优先采用名称、描述的用户可读步骤标题。 */
    private String stepLabel(AgentScript.Step step) {
        if (step.name != null && !step.name.isEmpty()) return step.name;
        if (step.description != null && !step.description.isEmpty()) return step.description;
        return readableStepType(step.type) + "（" + step.id + "）";
    }

    /** 将脚本步骤类型转换为中文显示名称。 */
    private String readableStepType(String type) {
        if ("tap".equals(type)) return "点击";
        if ("long_press".equals(type)) return "长按";
        if ("gesture".equals(type)) return "滑动手势";
        if ("multi_touch".equals(type)) return "多指手势";
        if ("key".equals(type)) return "按键";
        if ("wait".equals(type)) return "等待";
        if ("launch_app".equals(type)) return "打开应用";
        if ("node_tap".equals(type)) return "点击 UI 控件";
        return type == null || type.isEmpty() ? "未命名步骤" : type;
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

    /** 上报任务取消并返回终止循环的下标。 */
    private int cancelledIndex() {
        cancelled();
        return -1;
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
        final boolean stopScript;
        final String jumpTargetId;

        /** 保存一次步骤执行结果。 */
        StepResult(boolean success, String backend, String message, boolean stopScript,
                String jumpTargetId) {
            this.success = success;
            this.backend = backend;
            this.message = message;
            this.stopScript = stopScript;
            this.jumpTargetId = jumpTargetId;
        }

        /** 创建成功结果。 */
        static StepResult success(String backend, String message) {
            return new StepResult(true, backend, message, false, null);
        }

        /** 创建失败结果。 */
        static StepResult failure(String backend, String message) {
            return new StepResult(false, backend, message, false, null);
        }

        /** 创建需要终止整个脚本的失败结果。 */
        static StepResult stop(String backend, String message) {
            return new StepResult(false, backend, message, true, null);
        }

        /** 创建按目标 ID 继续执行的预期跳转结果。 */
        static StepResult jump(String backend, String message, String targetId) {
            return new StepResult(true, backend, message, false, targetId);
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