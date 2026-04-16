package com.debugview.sample;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * @author newChar
 * date 2025/6/18
 * @since 红色展示页
 * @since 迭代版本，（以及描述）
 */
public class RedFragment extends Fragment {

    /**
     * 创建红色页面视图。
     *
     * @param inflater  布局加载器
     * @param container 容器
     * @param savedInstanceState 状态
     * @return 视图
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        FrameLayout root = new FrameLayout(context);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.RED);
        TextView title = buildLabel(context, "Red Fragment");
        root.addView(title);
        return root;
    }

    /**
     * 构建页面标题。
     *
     * @param context 上下文
     * @param text    文本
     * @return 文本视图
     */
    private TextView buildLabel(Context context, String text) {
        TextView textView = new TextView(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        textView.setLayoutParams(params);
        textView.setText(text);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(24);
        return textView;
    }
}
