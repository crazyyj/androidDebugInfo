package com.debugview.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * 平台 Fragment 体系下的弹窗演示页。
 */
public class PlatformFragmentDialogPopupDemoActivity extends Activity {

    private static final String TAG_RED = "platform_red";
    private static final String TAG_BLUE = "platform_blue";

    private AlertDialog mCurrentDialog;
    private PopupWindow mCurrentPopupWindow;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_platform_fragment_demo);
        initActions();
        if (savedInstanceState == null) {
            showPlatformFragment(new PlatformRedFragment(), TAG_RED);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        dismissCurrentDialog();
        dismissCurrentPopup();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissCurrentDialog();
        dismissCurrentPopup();
    }

    private void initActions() {
        findViewById(R.id.btn_show_platform_red_fragment)
                .setOnClickListener(v -> showPlatformFragment(new PlatformRedFragment(), TAG_RED));
        findViewById(R.id.btn_show_platform_blue_fragment)
                .setOnClickListener(v -> showPlatformFragment(new PlatformBlueFragment(), TAG_BLUE));

        findViewById(R.id.btn_activity_alert_basic).setOnClickListener(v -> showBasicDialog("Activity"));
        findViewById(R.id.btn_activity_alert_single_choice)
                .setOnClickListener(v -> showSingleChoiceDialog("Activity"));
        findViewById(R.id.btn_activity_alert_multi_choice)
                .setOnClickListener(v -> showMultiChoiceDialog("Activity"));
        findViewById(R.id.btn_activity_alert_list).setOnClickListener(v -> showListDialog("Activity"));
        findViewById(R.id.btn_activity_alert_custom).setOnClickListener(v -> showCustomDialog("Activity"));

        findViewById(R.id.btn_activity_popup_dropdown_show).setOnClickListener(v -> showPopupAsDropDown("Activity"));
        findViewById(R.id.btn_activity_popup_at_location_show).setOnClickListener(v -> showPopupAtLocation("Activity"));
        findViewById(R.id.btn_activity_popup_dismiss).setOnClickListener(v -> dismissCurrentPopup());
    }

    private void showPlatformFragment(Fragment fragment, String tag) {
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.platform_fragment_container, fragment, tag)
                .commit();
    }

    public void showBasicDialog(String source) {
        dismissCurrentDialog();
        AlertDialog dialog = DemoAlertDialog.createBasic(
                this,
                source + " 基础提示",
                "这是平台 Fragment 页面里的 AlertDialog。"
        );
        showDialog(dialog);
    }

    public void showSingleChoiceDialog(String source) {
        dismissCurrentDialog();
        String[] items = new String[]{"One", "Two", "Three", "Four"};
        AlertDialog dialog = DemoAlertDialog.createSingleChoice(this, source + " 单选", items, 0);
        showDialog(dialog);
    }

    public void showMultiChoiceDialog(String source) {
        dismissCurrentDialog();
        String[] items = new String[]{"A", "B", "C", "D"};
        boolean[] checked = new boolean[]{false, true, false, true};
        AlertDialog dialog = DemoAlertDialog.createMultiChoice(this, source + " 多选", items, checked);
        showDialog(dialog);
    }

    public void showListDialog(String source) {
        dismissCurrentDialog();
        String[] items = new String[]{"列表 A", "列表 B", "列表 C", "列表 D"};
        AlertDialog dialog = DemoAlertDialog.createList(this, source + " 列表", items, "关闭");
        showDialog(dialog);
    }

    public void showCustomDialog(String source) {
        dismissCurrentDialog();
        View customView = LayoutInflater.from(this).inflate(R.layout.layout_popup_content, null, false);
        TextView title = customView.findViewById(R.id.tv_popup_title);
        Button close = customView.findViewById(R.id.btn_popup_inner_dismiss);
        title.setText(source + " 自定义 AlertDialog");
        AlertDialog dialog = DemoAlertDialog.createCustom(this, "自定义", customView);
        close.setText("关闭 Dialog");
        close.setOnClickListener(v -> dialog.dismiss());
        showDialog(dialog);
    }

    public void showPopupAsDropDown(String source) {
        dismissCurrentPopup();
        View anchor = findViewById(R.id.btn_activity_popup_anchor);
        PopupWindow popupWindow = createPopupWindow(source + " showAsDropDown");
        mCurrentPopupWindow = popupWindow;
        popupWindow.showAsDropDown(anchor);
    }

    public void showPopupAtLocation(String source) {
        dismissCurrentPopup();
        View root = findViewById(R.id.platform_demo_root);
        PopupWindow popupWindow = createPopupWindow(source + " showAtLocation");
        mCurrentPopupWindow = popupWindow;
        popupWindow.showAtLocation(root, Gravity.CENTER, 0, 0);
    }

    public void dismissCurrentPopup() {
        if (mCurrentPopupWindow != null) {
            mCurrentPopupWindow.dismiss();
            mCurrentPopupWindow = null;
        }
    }

    private PopupWindow createPopupWindow(String titleText) {
        View contentView = LayoutInflater.from(this).inflate(R.layout.layout_popup_content, null, false);
        TextView title = contentView.findViewById(R.id.tv_popup_title);
        Button dismissButton = contentView.findViewById(R.id.btn_popup_inner_dismiss);
        title.setText("Platform Popup: " + titleText);
        PopupWindow popupWindow = new PopupWindow(
                contentView,
                getResources().getDisplayMetrics().widthPixels * 3 / 4,
                getResources().getDisplayMetrics().heightPixels / 4,
                true
        );
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(0xFFFFFFFF));
        popupWindow.setOnDismissListener(() -> {
            if (mCurrentPopupWindow == popupWindow) {
                mCurrentPopupWindow = null;
            }
        });
        dismissButton.setOnClickListener(v -> popupWindow.dismiss());
        return popupWindow;
    }

    private void showDialog(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        mCurrentDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (mCurrentDialog == dialog) {
                mCurrentDialog = null;
            }
        });
        dialog.show();
    }

    private void dismissCurrentDialog() {
        if (mCurrentDialog != null) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
    }
}
