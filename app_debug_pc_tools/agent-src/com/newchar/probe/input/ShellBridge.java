package com.newchar.probe.input;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/** 封装 shell 命令、屏幕信息、节点定位与 adb input 兜底。 */
final class ShellBridge {

    private static final String UI_XML = "/data/local/tmp/newchar_input_ui.xml";
    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)x(\\d+)");
    private static final Pattern COMPONENT_PATTERN = Pattern.compile("([A-Za-z][A-Za-z0-9_.$]*)/");
    private static final Pattern BOUNDS_PATTERN = Pattern.compile("\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]");

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

    /** 返回当前前台应用包名。 */
    String foregroundPackage() {
        String output = run("dumpsys", "activity", "activities");
        for (String line : output.split("\\n")) {
            if (!line.toLowerCase().contains("resumedactivity")) continue;
            Matcher matcher = COMPONENT_PATTERN.matcher(line);
            if (matcher.find()) return matcher.group(1);
        }
        return "";
    }

    /** 通过 launcher intent 启动应用。 */
    boolean launchPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        CommandResult result = execute("monkey", "-p", packageName,
                "-c", "android.intent.category.LAUNCHER", "1");
        return result.exitCode == 0;
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

    /** 通过 uiautomator dump 查找节点中心点。 */
    float[] findNodeCenter(AgentScript.Selector selector) {
        if (selector == null) return null;
        execute("uiautomator", "dump", "--compressed", UI_XML);
        File file = new File(UI_XML);
        try {
            return parseNodeCenter(file, selector);
        } catch (Throwable ignored) {
            return null;
        } finally {
            file.delete();
        }
    }

    /** 解析 UI XML 并返回第一个匹配节点中心点。 */
    private float[] parseNodeCenter(File file, AgentScript.Selector selector) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(input).getElementsByTagName("node");
            for (int index = 0; index < nodes.getLength(); index++) {
                Element node = (Element) nodes.item(index);
                if (matches(node, selector)) return centerOf(node.getAttribute("bounds"));
            }
            return null;
        } finally {
            input.close();
        }
    }

    /** 判断 XML 节点是否满足全部非空选择条件。 */
    private boolean matches(Element node, AgentScript.Selector selector) {
        return matchesValue(selector.resourceId, node.getAttribute("resource-id"))
                && matchesValue(selector.text, node.getAttribute("text"))
                && matchesValue(selector.contentDescription, node.getAttribute("content-desc"))
                && matchesValue(selector.className, node.getAttribute("class"));
    }

    /** 空条件视为匹配，否则要求完全相等。 */
    private boolean matchesValue(String expected, String actual) {
        return expected == null || expected.isEmpty() || expected.equals(actual);
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

    /** 启动本机 shell 子进程并同步读取结果。 */
    private CommandResult execute(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = readOutput(process);
            return new CommandResult(process.waitFor(), output);
        } catch (Throwable throwable) {
            return new CommandResult(-1, throwable.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    /** 读取子进程完整输出。 */
    private String readOutput(Process process) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) output.append(line).append('\n');
        reader.close();
        return output.toString();
    }

    /** 将坐标四舍五入为 input 命令参数。 */
    private String integer(float value) {
        return String.valueOf(Math.round(value));
    }

    /** shell 子进程执行结果。 */
    private static final class CommandResult {
        final int exitCode;
        final String output;

        /** 保存退出码和输出。 */
        CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }
}