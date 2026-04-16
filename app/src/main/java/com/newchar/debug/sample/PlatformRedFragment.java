package com.debugview.sample;

import android.app.AlertDialog;
import android.app.Fragment;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * 平台 Fragment 红色页面。
 */
public class PlatformRedFragment extends Fragment {

    private AlertDialog mCurrentDialog;
    private PopupWindow mCurrentPopupWindow;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.frag_platform_red, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.btn_frag_alert_basic).setOnClickListener(v -> showBasicDialog());
        view.findViewById(R.id.btn_frag_popup_dropdown_show).setOnClickListener(v -> showPopupAsDropDown(v));
        view.findViewById(R.id.btn_frag_popup_dismiss).setOnClickListener(v -> dismissCurrentPopup());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dismissCurrentDialog();
        dismissCurrentPopup();
    }

    private void showBasicDialog() {
        if (getActivity() == null) {
            return;
        }
        dismissCurrentDialog();
        AlertDialog dialog = DemoAlertDialog.createBasic(
                getActivity(),
                "Red Fragment Dialog",
                "来自 PlatformRedFragment 的 AlertDialog"
        );
        mCurrentDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (mCurrentDialog == dialog) {
                mCurrentDialog = null;
            }
        });
        dialog.show();
    }

    private void showPopupAsDropDown(View anchor) {
        if (getActivity() == null) {
            return;
        }
        dismissCurrentPopup();
        View contentView = LayoutInflater.from(getActivity()).inflate(R.layout.layout_popup_content, null, false);
        TextView title = contentView.findViewById(R.id.tv_popup_title);
        Button dismissButton = contentView.findViewById(R.id.btn_popup_inner_dismiss);
        title.setText("Red Fragment Popup");
        PopupWindow popupWindow = new PopupWindow(
                contentView,
                getResources().getDisplayMetrics().widthPixels * 2 / 3,
                getResources().getDisplayMetrics().heightPixels / 5,
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
        mCurrentPopupWindow = popupWindow;
        popupWindow.showAsDropDown(anchor);
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
