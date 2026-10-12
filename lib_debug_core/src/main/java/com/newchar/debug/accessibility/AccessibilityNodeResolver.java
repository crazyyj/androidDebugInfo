package com.newchar.debug.accessibility;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

/** 负责按 LQITS selector 查找和操作无障碍节点。 */
final class AccessibilityNodeResolver {

    /** 深度优先返回首个同时满足所有非空字段的节点副本。 */
    AccessibilityNodeInfo find(AccessibilityNodeInfo root, JSONObject selector) {
        if (root == null || selector == null) return null;
        if (matches(root, selector)) return AccessibilityNodeInfo.obtain(root);
        for (int index = 0; index < root.getChildCount(); index++) {
            AccessibilityNodeInfo child = root.getChild(index);
            AccessibilityNodeInfo found = find(child, selector);
            if (child != null) child.recycle();
            if (found != null) return found;
        }
        return null;
    }

    /** 优先点击目标节点，失败时逐级尝试可点击父节点。 */
    boolean click(AccessibilityNodeInfo source) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(source);
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current.isClickable()
                    && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                current.recycle();
                return true;
            }
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }
        if (current != null) current.recycle();
        return false;
    }

    /** 返回节点屏幕中心点。 */
    float[] center(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        return new float[]{bounds.exactCenterX(), bounds.exactCenterY()};
    }

    /** 返回等待条件指定的节点属性文本。 */
    String attribute(AccessibilityNodeInfo node, String attribute) {
        if ("text".equals(attribute)) return text(node.getText());
        if ("contentDescription".equals(attribute)) return text(node.getContentDescription());
        if ("selected".equals(attribute)) return Boolean.toString(node.isSelected());
        if ("checked".equals(attribute)) return Boolean.toString(node.isChecked());
        if ("enabled".equals(attribute)) return Boolean.toString(node.isEnabled());
        if ("focused".equals(attribute)) return Boolean.toString(node.isFocused());
        return "";
    }

    /** 判断节点是否满足选择器全部非空字段。 */
    private boolean matches(AccessibilityNodeInfo node, JSONObject selector) {
        return matchesValue(selector.optString("resourceId"), node.getViewIdResourceName())
                && matchesValue(selector.optString("text"), text(node.getText()))
                && matchesValue(selector.optString("contentDescription"),
                text(node.getContentDescription()))
                && matchesValue(selector.optString("className"), text(node.getClassName()));
    }

    /** 空目标视为匹配，否则要求值完全一致。 */
    private boolean matchesValue(String expected, String actual) {
        return expected == null || expected.isEmpty()
                || expected.equals(actual == null ? "" : actual);
    }

    /** 安全地将节点字符字段转换为字符串。 */
    private String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
