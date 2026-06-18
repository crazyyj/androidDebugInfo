package com.newchar.debug.monitor.hook;

import android.app.Dialog;
import android.widget.PopupWindow;

public interface WindowHookCallback {

    void onPopupWindowShow(PopupWindow popupWindow);

    void onPopupWindowDismiss(PopupWindow popupWindow);

    void onDialogShow(Dialog dialog);

    void onDialogDismiss(Dialog dialog);
}
