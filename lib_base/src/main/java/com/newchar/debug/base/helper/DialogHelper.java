package com.newchar.debug.base.helper;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;

import com.newchar.debug.base.utils.DateUtils;


public class DialogHelper {

    private static volatile DialogHelper dHelper;

    private AlertDialog mAlertDialog;

    private ProgressDialog mProgressDialog;

    private TimePickerDialog mTimePickerDialog;

    private DatePickerDialog mDatePickerDialog;

    private static final String ProgressMsg = "正在加载...";

    private DialogHelper() {}


    public static DialogHelper getHelper() {
        if (dHelper == null) {
            synchronized (DialogHelper.class) {
                dHelper = (dHelper == null) ? new DialogHelper() : dHelper;
            }
        }
        return dHelper;
    }

    public AlertDialog getAlertDialog(Context c) {
        mAlertDialog = new AlertDialog(c) {
        };
        mAlertDialog.setCancelable(true);
        mAlertDialog.setCanceledOnTouchOutside(false);
        return mAlertDialog;
    }

    /**
     * 展示提示框
     *
     * @param title 提示框的标题
     * @param Msg   提示框提示的信息
     * @param l     提示框确定按钮的点击事件
     * @param osl   展示时回调的方法
     */
    public void showAlertDialog(String title, String Msg,
                                DialogInterface.OnClickListener l, OnShowListener osl) {
        if (mAlertDialog.isShowing())
            mAlertDialog.dismiss();
        DialogInterface.OnClickListener negativeListener = null;
        mAlertDialog.setTitle(title);
        mAlertDialog.setMessage(Msg);
        mAlertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "确定", l);
        mAlertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "取消", negativeListener);
        mAlertDialog.setOnShowListener(osl);
        mAlertDialog.show();
    }

    /**
     * 隐藏AlertDialog
     *
     * @param listener OnDismissListener 可以传空, null
     */
    public void dismissAlertDialog(Dialog.OnDismissListener listener) {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.setOnDismissListener(listener);
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
    }

    public ProgressDialog getProgress(Context c) {
        mProgressDialog = new ProgressDialog(c);
        mProgressDialog.setCancelable(true);
        mProgressDialog.setCanceledOnTouchOutside(false);
        return mProgressDialog;
    }

    /**
     * 展示等待框
     *
     * @param msg
     * @param showListener
     */
    public void showProgress(String msg, OnShowListener showListener) {
        if (mProgressDialog.isShowing())
            mProgressDialog.dismiss();
        mProgressDialog.setMessage(msg == null || "".equalsIgnoreCase(msg) ? ProgressMsg : msg);
        mProgressDialog.setOnShowListener(showListener);
        mProgressDialog.show();
    }

    /**
     * 隐藏等待框
     *
     * @param listener 隐藏回调
     */
    public void dismissProgress(DialogInterface.OnDismissListener listener) {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.setOnDismissListener(listener);
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }


    public DatePickerDialog getDatePicker(Context c, DatePickerDialog.OnDateSetListener listener) {
        mDatePickerDialog = new DatePickerDialog(c
                , DatePickerDialog.THEME_HOLO_LIGHT, listener
                , DateUtils.getDay()[0]
                , DateUtils.getDay()[1]
                , DateUtils.getDay()[2]);
        mDatePickerDialog.setCancelable(true);
        mDatePickerDialog.setCanceledOnTouchOutside(false);
        return mDatePickerDialog;
    }

    /**
     * 展示日期提示框
     *
     * @param title        日期标题
     * @param showListener 显示监听回调, 大部分情况为null
     * @param tag          View、tag 区分谁触发的日期提示框
     */
    public void showDateDialog(String title, Object tag, OnShowListener showListener) {
        if (mDatePickerDialog.isShowing())
            mDatePickerDialog.dismiss();
        mDatePickerDialog.setTitle(title);
        mDatePickerDialog.setOnShowListener(showListener);
        mDatePickerDialog.getDatePicker().setTag(tag);
        mDatePickerDialog.show();
    }

    public void dismissDateDialog(DialogInterface.OnDismissListener l) {
        if (mDatePickerDialog != null && mDatePickerDialog.isShowing()) {
            mDatePickerDialog.setOnDismissListener(l);
            mDatePickerDialog.dismiss();
            mDatePickerDialog = null;
        }
    }

    public TimePickerDialog getTimePicker(Context c, TimePickerDialog.OnTimeSetListener listener) {
        if (mTimePickerDialog == null) {
            synchronized (DialogHelper.class) {
                mTimePickerDialog = new TimePickerDialog(c
                        , TimePickerDialog.THEME_HOLO_LIGHT
                        , listener
                        , DateUtils.getTime()[0]
                        , DateUtils.getTime()[1]
                        , true);
            }
        }
        mTimePickerDialog.setCancelable(true);
        mTimePickerDialog.setCanceledOnTouchOutside(false);
        return mTimePickerDialog;
    }

    public void showTimeDialog(String title, OnShowListener showListener) {
        if (mTimePickerDialog.isShowing())
            mTimePickerDialog.dismiss();
        mTimePickerDialog.setTitle(title);
        mTimePickerDialog.setOnShowListener(showListener);
        mTimePickerDialog.show();
    }

    public void dismissTimeDialog(DialogInterface.OnDismissListener l) {
        if (mTimePickerDialog != null && mTimePickerDialog.isShowing()) {
            mTimePickerDialog.setOnDismissListener(l);
            mTimePickerDialog.dismiss();
            mTimePickerDialog = null;
        }
    }

}
