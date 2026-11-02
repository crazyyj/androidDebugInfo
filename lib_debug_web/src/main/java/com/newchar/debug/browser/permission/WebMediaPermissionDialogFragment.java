package com.newchar.debug.browser.permission;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

/**
 * 承载网站音视频采集确认提示的平台 DialogFragment。
 */
public final class WebMediaPermissionDialogFragment extends DialogFragment {

    private static final String ARG_TOKEN = "token";
    private static final String ARG_ORIGIN = "origin";
    private static final String ARG_RESOURCES = "resources";
    private static final String TAG_PREFIX = "web_media_permission:";
    private boolean decided;

    /**
     * 创建并显示一次网站媒体采集确认框。
     */
    static boolean show(Activity activity, String token, String origin, int resources) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }
        WebMediaPermissionDialogFragment fragment = new WebMediaPermissionDialogFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_TOKEN, token);
        arguments.putString(ARG_ORIGIN, origin);
        arguments.putInt(ARG_RESOURCES, resources);
        fragment.setArguments(arguments);
        try {
            fragment.show(activity.getFragmentManager(), TAG_PREFIX + token);
            return true;
        } catch (IllegalStateException error) {
            return false;
        }
    }

    /**
     * 更新同一 origin 合并后需要告知用户的资源类型。
     */
    static void update(Activity activity, String token, int resources) {
        if (activity == null) {
            return;
        }
        android.app.Fragment current = activity.getFragmentManager()
                .findFragmentByTag(TAG_PREFIX + token);
        if (current instanceof WebMediaPermissionDialogFragment) {
            ((WebMediaPermissionDialogFragment) current).updateResources(resources);
        }
    }

    /**
     * 关闭已取消或已经处理的确认框。
     */
    static void dismiss(Activity activity, String token) {
        if (activity == null) {
            return;
        }
        android.app.Fragment current = activity.getFragmentManager()
                .findFragmentByTag(TAG_PREFIX + token);
        if (current instanceof DialogFragment) {
            ((DialogFragment) current).dismissAllowingStateLoss();
        }
    }

    /**
     * 使用 AlertDialog 构造实际网站授权说明。
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        String origin = arguments == null ? "" : arguments.getString(ARG_ORIGIN, "");
        int resources = arguments == null ? 0 : arguments.getInt(ARG_RESOURCES, 0);
        return new AlertDialog.Builder(getActivity())
                .setTitle("网站请求媒体权限")
                .setMessage(message(origin, resources))
                .setNegativeButton("拒绝", (dialog, which) -> decide(false))
                .setPositiveButton("允许", (dialog, which) -> decide(true))
                .create();
    }

    /**
     * 取消、返回或触摸外部区域均视为本次拒绝。
     */
    @Override
    public void onCancel(android.content.DialogInterface dialog) {
        super.onCancel(dialog);
        decide(false);
    }

    /**
     * 宿主页面销毁导致窗口关闭时，拒绝尚未作出的网页权限请求。
     */
    @Override
    public void onDismiss(android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        decide(false);
    }

    /**
     * 将当前决定回传给仍存活的 Web 权限请求。
     */
    private void decide(boolean allowed) {
        if (decided) {
            return;
        }
        decided = true;
        Bundle arguments = getArguments();
        WebMediaPermissionController.resolvePrompt(arguments == null ? null
                : arguments.getString(ARG_TOKEN), allowed);
    }

    /**
     * 在同一提示框等待期间有额外媒体资源请求时更新说明。
     */
    private void updateResources(int resources) {
        Bundle arguments = getArguments();
        if (arguments == null) {
            return;
        }
        arguments.putInt(ARG_RESOURCES, resources);
        Dialog dialog = getDialog();
        if (dialog instanceof AlertDialog) {
            ((AlertDialog) dialog).setMessage(message(arguments.getString(ARG_ORIGIN, ""), resources));
        }
    }

    /**
     * 生成人类可读的 origin 与录音/视频采集范围说明。
     */
    static String message(String origin, int resources) {
        String media;
        if ((resources & WebMediaPermissionStore.AUDIO) != 0
                && (resources & WebMediaPermissionStore.VIDEO) != 0) {
            media = "录音和摄像";
        } else if ((resources & WebMediaPermissionStore.VIDEO) != 0) {
            media = "摄像";
        } else {
            media = "录音";
        }
        return origin + " 请求使用本设备的" + media + "权限。\n\n"
                + "允许后，该网站在此设备上再次请求相同权限时将自动允许。";
    }
}