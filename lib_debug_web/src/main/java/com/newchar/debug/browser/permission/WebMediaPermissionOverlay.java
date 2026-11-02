package com.newchar.debug.browser.permission;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * 没有可用 Activity 时，以悬浮窗承载网站媒体权限确认的普通 View。
 */
final class WebMediaPermissionOverlay {

    private static final Map<String, WebMediaPermissionOverlay> OVERLAYS = new HashMap<>();

    private final WindowManager windowManager;
    private final View root;
    private final TextView messageView;
    private final String token;
    private boolean attached;

    /**
     * 创建一个由应用上下文管理的悬浮窗提示。
     */
    private WebMediaPermissionOverlay(Context context, String token, String origin, int resources) {
        this.token = token;
        windowManager = context.getSystemService(WindowManager.class);
        root = createRoot(context);
        messageView = createMessage(context, origin, resources);
        addCard(context, root, messageView);
    }

    /**
     * 判断当前设备是否允许本应用展示应用级悬浮窗。
     */
    static boolean canShow(Context context) {
        return context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Settings.canDrawOverlays(context);
    }

    /**
     * 展示指定 origin 的确认视图；窗口创建失败时返回 false。
     */
    static boolean show(Context context, String token, String origin, int resources) {
        if (!canShow(context) || OVERLAYS.containsKey(token)) {
            return false;
        }
        WebMediaPermissionOverlay overlay = new WebMediaPermissionOverlay(context, token, origin, resources);
        if (overlay.attach()) {
            OVERLAYS.put(token, overlay);
            return true;
        }
        return false;
    }

    /**
     * 更新合并请求后的音视频说明。
     */
    static void update(String token, String origin, int resources) {
        WebMediaPermissionOverlay overlay = OVERLAYS.get(token);
        if (overlay != null) {
            overlay.messageView.setText(WebMediaPermissionDialogFragment.message(origin, resources));
        }
    }

    /**
     * 移除与该确认请求关联的悬浮窗。
     */
    static void dismiss(String token) {
        WebMediaPermissionOverlay overlay = OVERLAYS.remove(token);
        if (overlay != null) {
            overlay.detach();
        }
    }

    /**
     * 构建遮罩根视图，点击遮罩等同于拒绝当前请求。
     */
    private View createRoot(Context context) {
        FrameLayout layout = new FrameLayout(context);
        layout.setBackgroundColor(Color.argb(102, 0, 0, 0));
        layout.setClickable(true);
        layout.setOnClickListener(view -> respond(false));
        return layout;
    }

    /**
     * 创建网站与媒体资源说明文本。
     */
    private TextView createMessage(Context context, String origin, int resources) {
        TextView view = new TextView(context);
        view.setText(WebMediaPermissionDialogFragment.message(origin, resources));
        view.setTextColor(Color.rgb(33, 33, 33));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setLineSpacing(dp(context, 4), 1f);
        return view;
    }

    /**
     * 将提示文本和取消、允许按钮装配为居中的对话框卡片。
     */
    private void addCard(Context context, View root, TextView message) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setPadding(dp(context, 24), dp(context, 20), dp(context, 24), dp(context, 12));
        card.setBackgroundColor(Color.WHITE);
        card.addView(title(context));
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(context, 12);
        card.addView(message, messageParams);
        card.addView(actions(context));
        int width = Math.min(dp(context, 360), Math.max(dp(context, 1),
                screenWidth(context) - dp(context, 32)));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(width,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        ((FrameLayout) root).addView(card, cardParams);
    }

    /**
     * 创建标题文本。
     */
    private TextView title(Context context) {
        TextView view = new TextView(context);
        view.setText("网站请求媒体权限");
        view.setTextColor(Color.rgb(33, 33, 33));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        return view;
    }

    /**
     * 创建右对齐的取消与允许按钮区域。
     */
    private View actions(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.topMargin = dp(context, 16);
        layout.setLayoutParams(layoutParams);
        Button deny = new Button(context);
        deny.setText("取消");
        deny.setOnClickListener(view -> respond(false));
        Button allow = new Button(context);
        allow.setText("允许");
        allow.setOnClickListener(view -> respond(true));
        layout.addView(deny);
        layout.addView(allow);
        return layout;
    }

    /**
     * 将普通 View 添加到全屏悬浮窗，卡片保持居中对话框尺寸。
     */
    private boolean attach() {
        if (windowManager == null) {
            return false;
        }
        try {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    windowType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.CENTER;
            windowManager.addView(root, params);
            attached = true;
            return true;
        } catch (WindowManager.BadTokenException | SecurityException error) {
            return false;
        }
    }

    /**
     * 安全移除已挂载的悬浮窗。
     */
    private void detach() {
        if (!attached || windowManager == null) {
            return;
        }
        attached = false;
        try {
            windowManager.removeView(root);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * 将用户选择回传控制器；控制器会负责持久化、重新校验并移除窗口。
     */
    private void respond(boolean allowed) {
        WebMediaPermissionController.resolvePrompt(token, allowed);
    }

    /**
     * 根据系统版本选择正确的应用级窗口类型。
     */
    private int windowType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
    }

    /**
     * 将 dp 转换为当前屏幕密度对应的像素。
     */
    private int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    /**
     * 返回显示区域的像素宽度，用于限制卡片在窄屏设备上的尺寸。
     */
    private int screenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }
}