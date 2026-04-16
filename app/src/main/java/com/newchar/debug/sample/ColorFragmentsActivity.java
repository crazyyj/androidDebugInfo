package com.debugview.sample;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

/**
 * @author newChar
 * date 2025/6/18
 * @since 红蓝页切换示例
 * @since 迭代版本，（以及描述）
 */
public class ColorFragmentsActivity extends AppCompatActivity {

    private static final int ID_CONTAINER = 0x2211;

    /**
     * 初始化颜色切换页面。
     *
     * @param savedInstanceState 状态
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildRoot());
        if (savedInstanceState == null) {
            showFragment(new RedFragment());
        }
    }

    /**
     * 构建页面根布局。
     *
     * @return 根视图
     */
    private LinearLayout buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button redButton = new Button(this);
        redButton.setText("Red");
        redButton.setOnClickListener(view -> showFragment(new RedFragment()));

        Button blueButton = new Button(this);
        blueButton.setText("Blue");
        blueButton.setOnClickListener(view -> showFragment(new BlueFragment()));

        actions.addView(redButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        actions.addView(blueButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout container = new FrameLayout(this);
        container.setId(ID_CONTAINER);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(actions);
        root.addView(container);
        return root;
    }

    /**
     * 切换展示的 Fragment。
     *
     * @param fragment 目标页面
     */
    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(ID_CONTAINER, fragment)
                .commit();
    }
}
