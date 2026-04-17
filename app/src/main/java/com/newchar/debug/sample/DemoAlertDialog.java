package com.newchar.debug.sample;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/**
 * 演示用 AlertDialog 子类，确保运行时类名为自定义类型。
 */
public final class DemoAlertDialog extends AlertDialog {

    private static final int MODE_MESSAGE = 1;
    private static final int MODE_SINGLE = 2;
    private static final int MODE_MULTI = 3;
    private static final int MODE_LIST = 4;
    private static final int MODE_CUSTOM = 5;

    private final Config mConfig;

    private DemoAlertDialog(Context context, Config config) {
        super(context);
        mConfig = config;
        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }

    public static DemoAlertDialog createBasic(Context context, String title, String message) {
        Config config = new Config(MODE_MESSAGE);
        config.title = title;
        config.message = message;
        config.positiveText = "确定";
        config.negativeText = "取消";
        return new DemoAlertDialog(context, config);
    }

    public static DemoAlertDialog createSingleChoice(Context context, String title, String[] items, int checkedIndex) {
        Config config = new Config(MODE_SINGLE);
        config.title = title;
        config.items = items;
        config.checkedIndex = checkedIndex;
        config.positiveText = "确定";
        config.negativeText = "取消";
        return new DemoAlertDialog(context, config);
    }

    public static DemoAlertDialog createMultiChoice(Context context, String title, String[] items, boolean[] checked) {
        Config config = new Config(MODE_MULTI);
        config.title = title;
        config.items = items;
        config.checkedItems = checked == null ? null : checked.clone();
        config.positiveText = "确定";
        config.negativeText = "取消";
        return new DemoAlertDialog(context, config);
    }

    public static DemoAlertDialog createList(Context context, String title, String[] items, String negativeText) {
        Config config = new Config(MODE_LIST);
        config.title = title;
        config.items = items;
        config.negativeText = TextUtils.isEmpty(negativeText) ? "关闭" : negativeText;
        return new DemoAlertDialog(context, config);
    }

    public static DemoAlertDialog createCustom(Context context, String title, View customView) {
        Config config = new Config(MODE_CUSTOM);
        config.title = title;
        config.customView = customView;
        return new DemoAlertDialog(context, config);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
    }

    private View buildContentView() {
        Context context = getContext();
        int padding = dip(context, 20);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if (!TextUtils.isEmpty(mConfig.title)) {
            TextView titleView = new TextView(context);
            titleView.setText(mConfig.title);
            titleView.setTextSize(18f);
            titleView.setPadding(0, 0, 0, dip(context, 12));
            root.addView(titleView);
        }

        if (!TextUtils.isEmpty(mConfig.message)) {
            TextView messageView = new TextView(context);
            messageView.setText(mConfig.message);
            messageView.setPadding(0, 0, 0, dip(context, 16));
            root.addView(messageView);
        }

        if (mConfig.mode == MODE_CUSTOM && mConfig.customView != null) {
            attachDetachedView(mConfig.customView);
            root.addView(mConfig.customView);
        } else if (mConfig.items != null && mConfig.items.length > 0) {
            root.addView(buildListView(context));
        }

        View buttonBar = buildButtonBar(context);
        if (buttonBar != null) {
            root.addView(buttonBar);
        }
        return root;
    }

    private View buildListView(Context context) {
        ListView listView = new ListView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dip(context, 220)
        );
        listView.setLayoutParams(params);
        switch (mConfig.mode) {
            case MODE_SINGLE:
                listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                listView.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_list_item_single_choice, mConfig.items));
                if (mConfig.checkedIndex >= 0 && mConfig.checkedIndex < mConfig.items.length) {
                    listView.setItemChecked(mConfig.checkedIndex, true);
                }
                listView.setOnItemClickListener((parent, view, position, id) -> mConfig.checkedIndex = position);
                break;
            case MODE_MULTI:
                listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                listView.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_list_item_multiple_choice, mConfig.items));
                if (mConfig.checkedItems != null) {
                    for (int i = 0; i < Math.min(mConfig.checkedItems.length, mConfig.items.length); i++) {
                        listView.setItemChecked(i, mConfig.checkedItems[i]);
                    }
                }
                listView.setOnItemClickListener((parent, view, position, id) -> {
                    if (mConfig.checkedItems != null && position < mConfig.checkedItems.length) {
                        mConfig.checkedItems[position] = listView.isItemChecked(position);
                    }
                });
                break;
            case MODE_LIST:
            default:
                listView.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_list_item_1, mConfig.items));
                break;
        }
        return listView;
    }

    private View buildButtonBar(Context context) {
        boolean hasPositive = !TextUtils.isEmpty(mConfig.positiveText);
        boolean hasNegative = !TextUtils.isEmpty(mConfig.negativeText);
        if (!hasPositive && !hasNegative) {
            return null;
        }
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dip(context, 16), 0, 0);
        if (hasNegative) {
            Button negative = new Button(context);
            negative.setText(mConfig.negativeText);
            negative.setOnClickListener(v -> dismiss());
            row.addView(negative);
        }
        if (hasPositive) {
            Button positive = new Button(context);
            positive.setText(mConfig.positiveText);
            positive.setOnClickListener(v -> dismiss());
            row.addView(positive);
        }
        return row;
    }

    private static void attachDetachedView(View view) {
        if (view == null) {
            return;
        }
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private static int dip(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private static final class Config {
        final int mode;
        String title;
        String message;
        String positiveText;
        String negativeText;
        String[] items;
        int checkedIndex = -1;
        boolean[] checkedItems;
        View customView;

        Config(int mode) {
            this.mode = mode;
        }
    }
}
