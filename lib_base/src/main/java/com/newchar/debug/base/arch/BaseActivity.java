package com.newchar.debug.base.arch;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;

import com.newchar.debug.base.utils.CommonUtils;
import com.newchar.debug.base.utils.TextUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Activity 开发模板
 *
 * @author Newlq
 */
public abstract class BaseActivity extends Activity implements View.OnClickListener {


    private final AtomicBoolean resReleased = new AtomicBoolean();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int layoutId = getContentViewId();
        if (0 >= layoutId) {
            throw new IllegalArgumentException("Activity布局文件有问题");
        }
        setContentView(layoutId);

        initWidgets();
        handlerIntent(getIntent());
        initProjectFromAsync(savedInstanceState);

    }

    /**
     * 初始化界面控件View
     * 初始化监听器
     */
    protected abstract void initWidgets();

    /**
     * 处理被打开的Intent
     * 处理Intent 附带过来的数据
     */
    protected void handlerIntent(Intent otherIntent) {

    }

    /**
     * 初始化当前页数据
     *
     * @param savedInstanceState 保存状态数据的Bundle对象
     */
    protected abstract void initData(Bundle savedInstanceState);

    /**
     * 设置当前Activity布局文件
     * 执行在setContentView();之前
     *
     * @return 布局的资源Xml文件ID
     */
    protected abstract int getContentViewId();

    /**
     * 填充数据时机优化, 显示第一帧 填充数据
     */
    private void initProjectFromAsync(final Bundle savedInstanceState) {
        getWindow().getDecorView().post(new Runnable() {
            @Override
            public void run() {
                initData(savedInstanceState);
            }
        });
    }

    protected void showUI(View view) {

    }
    protected void hideUI(View view) {

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handlerIntent(intent);
        setIntent(intent);
    }

    protected void goAct(Class<?> clazz) {
        goActWithData(clazz, null);
    }

    protected void goActWithData(Class<?> clazz, Bundle bundle) {
        Intent intent = new Intent(getApplicationContext(), clazz);
        if (bundle != null && !bundle.isEmpty()) intent.putExtras(bundle);
        startActivity(intent);
    }

    /**
     * 隐式意图打开其他
     *
     * @param action  startActivity .#startService();
     */
    protected void gotoOther(String action) {
        if (!TextUtils.isEmpty(action)) {
            Intent intent = new Intent(action);
            if (!(getPackageManager().queryIntentActivities(intent, 0).isEmpty())) {
                startActivity(intent);
            } else if (!(getPackageManager().queryIntentServices(intent, 0).isEmpty())) {
                startService(intent);
            }
        }
    }

    public void finish(int animIn, int animOut) {
        finish();
        this.overridePendingTransition(animIn, animOut);
    }

    /**
     * @return 当前Activity 的根View
     */
    protected View getRootView() {
        return ((ViewGroup) getWindow().getDecorView()
                .findViewById(Window.ID_ANDROID_CONTENT)).getChildAt(0);
    }

    /**
     * 控制点击EditText以外的部分 收回软键盘
     *
     * @param ev
     * @return
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (isShouldHideInput(v, ev)) {
                CommonUtils.closeKeybord(getWindow().getCurrentFocus(), this);
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isShouldHideInput(View v, MotionEvent event) {
        if (v instanceof EditText) {
            int[] leftTop = {0, 0};
            // 获取输入框当前的location位置
            v.getLocationInWindow(leftTop);
            Rect r = new Rect();
            int left = leftTop[0];
            int top = leftTop[1];
            int bottom = top + v.getHeight();
            int right = left + v.getWidth();
            if (event.getX() > left && event.getX() < right
                    && event.getY() > top && event.getY() < bottom) {
                // 点击的是输入框区域，保留点击EditText的事件
                return false;
            }
        }
        return true;
    }

    /**
     * Title 回退按钮的 点击事件, 可能会被弃用
     *
     * @param v 响应回退点击的View
     */
    protected void goBack(View v) {
        onBackPressed();
    }

    /**
     * 释放资源
     */
    protected void onReleaseRes() {

    }


    @Override
    public void onClick(View v) {

    }

    @Override
    protected void onStop() {
        super.onStop();
        if ((isFinishing() || isDestroyed()) && resReleased.compareAndSet(false, true)) {
            onReleaseRes();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 采用callback替换
        if (resReleased.compareAndSet(false, true)) {
            onReleaseRes();
        }
    }

}
