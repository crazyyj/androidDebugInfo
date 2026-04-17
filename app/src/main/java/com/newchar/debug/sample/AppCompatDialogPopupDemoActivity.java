package com.newchar.debug.sample;

import android.app.AlertDialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * AppCompat Activity 弹窗演示页。
 */
public class AppCompatDialogPopupDemoActivity extends AppCompatActivity {

    private AlertDialog mCurrentDialog;
    private PopupWindow mCurrentPopupWindow;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_dialog_popup_demo);
        initActions();
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
        findViewById(R.id.btn_alert_basic).setOnClickListener(v -> showBasicDialog());
        findViewById(R.id.btn_alert_single_choice).setOnClickListener(v -> showSingleChoiceDialog());
        findViewById(R.id.btn_alert_multi_choice).setOnClickListener(v -> showMultiChoiceDialog());
        findViewById(R.id.btn_alert_list).setOnClickListener(v -> showListDialog());
        findViewById(R.id.btn_alert_custom).setOnClickListener(v -> showCustomDialog());
        findViewById(R.id.btn_popup_dropdown_show).setOnClickListener(v -> showPopupAsDropDown());
        findViewById(R.id.btn_popup_at_location_show).setOnClickListener(v -> showPopupAtLocation());
        findViewById(R.id.btn_popup_dismiss).setOnClickListener(v -> dismissCurrentPopup());
    }

    private void showBasicDialog() {
        dismissCurrentDialog();
        AlertDialog dialog = DemoAlertDialog.createBasic(
                this,
                "基础提示",
                "这是 AppCompat 页面中的基础 AlertDialog。"
        );
        showDialog(dialog);
    }

    private void showSingleChoiceDialog() {
        dismissCurrentDialog();
        String[] items = new String[]{"Alpha", "Beta", "Gamma", "Delta"};
        AlertDialog dialog = DemoAlertDialog.createSingleChoice(this, "单选", items, 1);
        showDialog(dialog);
    }

    private void showMultiChoiceDialog() {
        dismissCurrentDialog();
        String[] items = new String[]{"缓存", "网络", "磁盘", "传感器"};
        boolean[] checked = new boolean[]{true, false, false, true};
        AlertDialog dialog = DemoAlertDialog.createMultiChoice(this, "多选", items, checked);
        showDialog(dialog);
    }

    private void showListDialog() {
        dismissCurrentDialog();
        String[] items = new String[]{"列表项 1", "列表项 2", "列表项 3", "列表项 4"};
        AlertDialog dialog = DemoAlertDialog.createList(this, "列表", items, "关闭");
        showDialog(dialog);
    }

    private void showCustomDialog() {
        dismissCurrentDialog();
        View customView = LayoutInflater.from(this).inflate(R.layout.layout_popup_content, null, false);
        TextView title = customView.findViewById(R.id.tv_popup_title);
        Button close = customView.findViewById(R.id.btn_popup_inner_dismiss);
        title.setText("自定义 AlertDialog");
        AlertDialog dialog = DemoAlertDialog.createCustom(this, "自定义", customView);
        close.setText("关闭 Dialog");
        close.setOnClickListener(v -> dialog.dismiss());
        showDialog(dialog);
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

    private void showPopupAsDropDown() {
        dismissCurrentPopup();
        View anchor = findViewById(R.id.btn_popup_anchor);
        PopupWindow popupWindow = createPopupWindow("showAsDropDown");
        mCurrentPopupWindow = popupWindow;
        popupWindow.showAsDropDown(anchor);
    }

    private void showPopupAtLocation() {
        dismissCurrentPopup();
        View root = findViewById(R.id.dialog_popup_demo_root);
        PopupWindow popupWindow = createPopupWindow("showAtLocation");
        mCurrentPopupWindow = popupWindow;
        popupWindow.showAtLocation(root, Gravity.CENTER, 0, 0);
    }

    private PopupWindow createPopupWindow(String source) {
        View contentView = LayoutInflater.from(this).inflate(R.layout.layout_popup_content, null, false);
        TextView title = contentView.findViewById(R.id.tv_popup_title);
        Button dismissButton = contentView.findViewById(R.id.btn_popup_inner_dismiss);
        title.setText("AppCompat Popup: " + source);
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

    private void dismissCurrentDialog() {
        if (mCurrentDialog != null) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
    }

    private void dismissCurrentPopup() {
        if (mCurrentPopupWindow != null) {
            mCurrentPopupWindow.dismiss();
            mCurrentPopupWindow = null;
        }
    }
}
