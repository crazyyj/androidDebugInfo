package com.newchar.debug.base.arch;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by newlq on 2016/10/13.
 */
public class BaseDialog extends DialogFragment {

    /**
     *
     */
    protected static final int CREATE_TYPE_DIALOG = 0;
    protected static final int CREATE_TYPE_VIEW = 1;
    protected int CREATE_TYPE_DEFAULT;

    @IntDef({CREATE_TYPE_DIALOG, CREATE_TYPE_VIEW})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CreateDialogType {
    }


    public BaseDialog() {

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (CREATE_TYPE_DEFAULT == CREATE_TYPE_VIEW) {
            int layoutId = getDialogLayoutId();
            if (layoutId > 0) {
                getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);  //去掉标题
                view = inflater.inflate(layoutId, container);
                initWidgets(view);
            }
        }
        return view;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = genBaseDialog(savedInstanceState);
        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (CREATE_TYPE_DEFAULT == CREATE_TYPE_DIALOG || dialog == null) {
            dialog = super.onCreateDialog(savedInstanceState);
        }
        return dialog;
    }

    protected int getDialogLayoutId() {
        return 0;
    }

    protected void initWidgets(View view) {
        return ;
    }

    protected Dialog genBaseDialog(Bundle savedInstanceState) {
        return new InnerDialog(getActivity(), 0);
    }

    //Tip: 内部专用提示框
    public static class InnerDialog extends AlertDialog {

        public InnerDialog(Context context, int themeResId) {
            super(context, themeResId);
            setOwnerActivity(((Activity) context));
        }

    }

}
