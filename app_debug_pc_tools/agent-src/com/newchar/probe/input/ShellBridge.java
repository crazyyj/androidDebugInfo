package com.newchar.probe.input;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONObject;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

/** 封装 shell 命令、屏幕信息、节点定位与 input 命令兜底。 */
final class ShellBridge {

    private static final String UI_XML = "/data/local/tmp/newchar_input_ui.xml";
    private static final String SCREEN_PNG = "/data/local/tmp/newchar_input_screen.png";
    private static final long DEFAULT_TIMEOUT_MS = 15000L;
    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)x(\\d+)");
    private static final Pattern COMPONENT_PATTERN = Pattern.compile(
            "([A-Za-z][A-Za-z0-9_.$]*)/([A-Za-z0-9_.$]+)");
    private static final Pattern BOUNDS_PATTERN = Pattern.compile(
            "\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]");

    /** 返回当前有效屏幕尺寸。 */
    int[] displaySize() {
        String output = run("wm", "size");
        Matcher matcher = SIZE_PATTERN.matcher(output);
        int[] last = new int[]{1080, 1920};
        while (matcher.find()) {
            last[0] = Integer.parseInt(matcher.group(1));
            last[1] = Integer.parseInt(matcher.group(2));
        }
        return last;
    }

    /** 在指定命令时限内读取前台包名和 Activity。 */
    ComponentSnapshot foregroundComponent(long timeoutMs) {
        CommandResult result = execute(timeoutMs, "dumpsys", "activity", "activities");
        if (!result.success()) return ComponentSnapshot.failure(result.message());
        for (String line : result.output.split("\\n")) {
            String lower = line.toLowerCase();
            if (!lower.contains("resumedactivity") && !lower.contains("topresumedactivity")) continue;
            Matcher matcher = COMPONENT_PATTERN.matcher(line);
            if (matcher.find()) return componentOf(matcher.group(1), matcher.group(2));
        }
        return ComponentSnapshot.failure("未解析到前台组件");
    }

    /** 规范化缩写 Activity 并创建前台快照。 */
    private ComponentSnapshot componentOf(String packageName, String activityName) {
        String normalized = activityName.startsWith(".") ? packageName + activityName : activityName;
        return ComponentSnapshot.success(packageName, normalized);
    }

    /** 通过 launcher intent 启动应用。 */
    boolean launchPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        CommandResult result = execute("monkey", "-p", packageName,
                "-c", "android.intent.category.LAUNCHER", "1");
        return result.exitCode == 0;
    }

    /** 调用独立 UiAutomation instrumentation 执行单条指令。 */
    BackendResult runInstrumentation(AgentScript script, AgentScript.Step step) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("sourceWidth", script.sourceWidth);
            payload.put("sourceHeight", script.sourceHeight);
            JSONObject stepValue = new JSONObject(step.source.toString());
            if ("launch_app".equals(step.type) && stepValue.optString("packageName").isEmpty()) {
                stepValue.put("packageName", script.targetPackage);
            }
            payload.put("step", stepValue);
            String encoded = Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8),
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            CommandResult result = execute(650000L, "am", "instrument", "-w", "-r", "-e",
                    "payload", encoded, "com.newchar.debug.sample/com.newchar.debug.touch.uiautomator.InputInstrumentation");
            return parseInstrumentationResult(result);
        } catch (Exception error) {
            return BackendResult.failure(error.getMessage());
        }
    }

    /** 解析 instrumentation 返回的 Base64 JSON。 */
    private BackendResult parseInstrumentationResult(CommandResult result) {
        if (!result.success()) return BackendResult.failure(result.message());
        String prefix = "INSTRUMENTATION_RESULT: lqits=";
        for (String line : result.output.split("\\n")) {
            if (!line.startsWith(prefix)) continue;
            try {
                String json = new String(Base64.decode(line.substring(prefix.length()).trim(),
                        Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING), StandardCharsets.UTF_8);
                JSONObject value = new JSONObject(json);
                return new BackendResult(value.optBoolean("success"), value.optString("message"));
            } catch (Exception error) {
                return BackendResult.failure("instrumentation 结果解析失败: " + error.getMessage());
            }
        }
        return BackendResult.failure("instrumentation 未返回结果");
    }

    /** 使用系统 input 命令点击坐标。 */
    boolean inputTap(float x, float y) {
        return execute("input", "tap", integer(x), integer(y)).exitCode == 0;
    }

    /** 使用系统 input swipe 模拟长按。 */
    boolean inputLongPress(float x, float y, long durationMs) {
        return inputSwipe(x, y, x, y, durationMs);
    }

    /** 使用系统 input swipe 执行直线滑动。 */
    boolean inputSwipe(float x1, float y1, float x2, float y2, long durationMs) {
        CommandResult result = execute("input", "swipe", integer(x1), integer(y1),
                integer(x2), integer(y2), String.valueOf(Math.max(1L, durationMs)));
        return result.exitCode == 0;
    }

    /** 使用系统 input 命令注入按键。 */
    boolean inputKey(int keyCode) {
        return execute("input", "keyevent", String.valueOf(keyCode)).exitCode == 0;
    }

    /** 通过一次 UI 快照查找节点中心点。 */
    float[] findNodeCenter(AgentScript.Selector selector) {
        if (selector == null) return null;
        UiSnapshot snapshot = dumpUiNodes(DEFAULT_TIMEOUT_MS);
        if (!snapshot.success) return null;
        for (UiNode node : snapshot.nodes) {
            if (node.matches(selector)) return centerOf(node.bounds);
        }
        return null;
    }

    /** 在指定命令时限内获取一份 UI 节点快照。 */
    UiSnapshot dumpUiNodes(long timeoutMs) {
        File file = new File(UI_XML);
        file.delete();
        CommandResult result = execute(timeoutMs, "uiautomator", "dump", "--compressed", UI_XML);
        if (!result.success() || !file.isFile()) return UiSnapshot.failure(result.message());
        try {
            return UiSnapshot.success(parseUiNodes(file));
        } catch (Throwable throwable) {
            return UiSnapshot.failure(readableMessage(throwable));
        } finally {
            file.delete();
        }
    }

    /** 解析 UI XML 为可重复查询的轻量节点集合。 */
    private List<UiNode> parseUiNodes(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(input).getElementsByTagName("node");
            List<UiNode> result = new ArrayList<>();
            for (int index = 0; index < nodes.getLength(); index++) {
                result.add(UiNode.from((Element) nodes.item(index)));
            }
            return result;
        } finally {
            input.close();
        }
    }

    /** 在指定命令时限内截图并解码为 Bitmap。 */
    ScreenSnapshot captureScreen(long timeoutMs) {
        File file = new File(SCREEN_PNG);
        file.delete();
        CommandResult result = execute(timeoutMs, "screencap", "-p", SCREEN_PNG);
        if (!result.success() || !file.isFile()) return ScreenSnapshot.failure(result.message());
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(SCREEN_PNG);
            return bitmap == null ? ScreenSnapshot.failure("截图解码失败") : ScreenSnapshot.success(bitmap);
        } catch (Throwable throwable) {
            return ScreenSnapshot.failure(readableMessage(throwable));
        } finally {
            file.delete();
        }
    }

    /** 将 bounds 属性转换为中心坐标。 */
    private float[] centerOf(String bounds) {
        Matcher matcher = BOUNDS_PATTERN.matcher(bounds == null ? "" : bounds);
        if (!matcher.matches()) return null;
        float left = Float.parseFloat(matcher.group(1));
        float top = Float.parseFloat(matcher.group(2));
        float right = Float.parseFloat(matcher.group(3));
        float bottom = Float.parseFloat(matcher.group(4));
        return new float[]{(left + right) / 2f, (top + bottom) / 2f};
    }

    /** 执行命令并返回合并后的标准输出。 */
    String run(String... command) {
        return execute(command).output;
    }

    /** 使用默认时限启动本机 shell 子进程。 */
    private CommandResult execute(String... command) {
        return execute(DEFAULT_TIMEOUT_MS, command);
    }

    /** 在指定时限内启动子进程并同步收集输出。 */
    private CommandResult execute(long timeoutMs, String... command) {
        Process process = null;
        OutputReader reader = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            reader = new OutputReader(process.getInputStream());
            reader.start();
            boolean finished = process.waitFor(Math.max(1L, timeoutMs), TimeUnit.MILLISECONDS);
            if (!finished) terminate(process);
            reader.join(300L);
            return new CommandResult(finished ? process.exitValue() : -1, reader.output(), !finished);
        } catch (Throwable throwable) {
            return new CommandResult(-1, readableMessage(throwable), false);
        } finally {
            if (process != null) process.destroy();
        }
    }

    /** 终止超时子进程，必要时执行强制终止。 */
    private void terminate(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(200L, TimeUnit.MILLISECONDS)) process.destroyForcibly();
    }

    /** 返回异常类型和可读消息。 */
    private String readableMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** 将坐标四舍五入为 input 命令参数。 */
    private String integer(float value) {
        return String.valueOf(Math.round(value));
    }

    /** 一份前台组件采样结果。 */
    static final class ComponentSnapshot {
        final boolean success;
        final String packageName;
        final String activityName;
        final String message;

        /** 保存前台组件采样结果。 */
        private ComponentSnapshot(boolean success, String packageName, String activityName, String message) {
            this.success = success;
            this.packageName = packageName;
            this.activityName = activityName;
            this.message = message;
        }

        /** 创建成功的前台组件结果。 */
        static ComponentSnapshot success(String packageName, String activityName) {
            return new ComponentSnapshot(true, packageName, activityName, packageName + "/" + activityName);
        }

        /** 创建失败的前台组件结果。 */
        static ComponentSnapshot failure(String message) {
            return new ComponentSnapshot(false, "", "", message);
        }
    }

    /** 一份 UI 树采样结果。 */
    static final class UiSnapshot {
        final boolean success;
        final List<UiNode> nodes;
        final String message;

        /** 保存 UI 树采样结果。 */
        private UiSnapshot(boolean success, List<UiNode> nodes, String message) {
            this.success = success;
            this.nodes = nodes;
            this.message = message;
        }

        /** 创建成功的 UI 树结果。 */
        static UiSnapshot success(List<UiNode> nodes) {
            return new UiSnapshot(true, nodes, "节点数=" + nodes.size());
        }

        /** 创建失败的 UI 树结果。 */
        static UiSnapshot failure(String message) {
            return new UiSnapshot(false, new ArrayList<UiNode>(), message);
        }
    }

    /** 从 UI XML 提取的节点属性。 */
    static final class UiNode {
        String resourceId;
        String text;
        String contentDescription;
        String className;
        String selected;
        String checked;
        String enabled;
        String focused;
        String bounds;

        /** 从 XML 元素创建轻量节点。 */
        static UiNode from(Element element) {
            UiNode node = new UiNode();
            node.resourceId = element.getAttribute("resource-id");
            node.text = element.getAttribute("text");
            node.contentDescription = element.getAttribute("content-desc");
            node.className = element.getAttribute("class");
            node.selected = element.getAttribute("selected");
            node.checked = element.getAttribute("checked");
            node.enabled = element.getAttribute("enabled");
            node.focused = element.getAttribute("focused");
            node.bounds = element.getAttribute("bounds");
            return node;
        }

        /** 判断节点是否满足选择器全部非空字段。 */
        boolean matches(AgentScript.Selector selector) {
            return matchesValue(selector.resourceId, resourceId)
                    && matchesValue(selector.text, text)
                    && matchesValue(selector.contentDescription, contentDescription)
                    && matchesValue(selector.className, className);
        }

        /** 返回条件等待使用的指定属性。 */
        String attribute(String name) {
            if ("text".equals(name)) return text;
            if ("contentDescription".equals(name)) return contentDescription;
            if ("selected".equals(name)) return selected;
            if ("checked".equals(name)) return checked;
            if ("enabled".equals(name)) return enabled;
            if ("focused".equals(name)) return focused;
            return "";
        }

        /** 空选择字段视为匹配，否则要求完全相等。 */
        private boolean matchesValue(String expected, String actual) {
            return expected == null || expected.isEmpty() || expected.equals(actual);
        }
    }

    /** 一份截图采样结果。 */
    static final class ScreenSnapshot {
        final Bitmap bitmap;
        final String message;

        /** 保存截图或失败原因。 */
        private ScreenSnapshot(Bitmap bitmap, String message) {
            this.bitmap = bitmap;
            this.message = message;
        }

        /** 创建成功的截图结果。 */
        static ScreenSnapshot success(Bitmap bitmap) {
            return new ScreenSnapshot(bitmap, bitmap.getWidth() + "x" + bitmap.getHeight());
        }

        /** 创建失败的截图结果。 */
        static ScreenSnapshot failure(String message) {
            return new ScreenSnapshot(null, message);
        }
    }

    /** 外部执行通道返回结果。 */
    static final class BackendResult {
        final boolean success;
        final String message;

        /** 保存执行结果。 */
        BackendResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        /** 创建失败结果。 */
        static BackendResult failure(String message) {
            return new BackendResult(false, message == null ? "执行失败" : message);
        }
    }

    /** shell 子进程执行结果。 */
    private static final class CommandResult {
        final int exitCode;
        final String output;
        final boolean timedOut;

        /** 保存退出码、输出和超时标记。 */
        CommandResult(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.timedOut = timedOut;
        }

        /** 判断命令是否正常完成。 */
        boolean success() {
            return exitCode == 0 && !timedOut;
        }

        /** 返回命令失败或超时的可读说明。 */
        String message() {
            if (timedOut) return "状态采集命令超时";
            return output.isEmpty() ? "状态采集命令失败，exit=" + exitCode : output.trim();
        }
    }

    /** 后台读取子进程输出，避免输出缓冲区阻塞进程。 */
    private static final class OutputReader extends Thread {
        private final InputStream input;
        private final StringBuilder value = new StringBuilder();

        /** 保存待读取的子进程输出流。 */
        OutputReader(InputStream input) {
            this.input = input;
            setDaemon(true);
        }

        /** 持续读取文本输出直到进程关闭输出流。 */
        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                String line;
                while ((line = reader.readLine()) != null) value.append(line).append('\n');
                reader.close();
            } catch (Throwable ignored) {
                // 进程超时被销毁时允许输出流同步关闭。
            }
        }

        /** 返回已读取的完整文本。 */
        String output() {
            return value.toString();
        }
    }
}
