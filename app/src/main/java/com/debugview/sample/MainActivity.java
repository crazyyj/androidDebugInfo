package com.debugview.sample;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.newchar.debug.logview.LogViewPlugin;
import com.newchar.debugview.annotation.DebugUiContext;
import com.newchar.debugview.DebugManager;

/**
 * @author newChar
 * 2023/6/1
 * @since sampleActivity
 * @since 迭代版本，（以及描述）
 */
@DebugUiContext
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_main);

        if (savedInstanceState == null) {
            showFragment(new RedFragment());
        }

        findViewById(R.id.tv).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogViewPlugin logView = DebugManager.getInstance().getPlugin(LogViewPlugin.class);
                if (logView != null) {
                    logView.e("ADSA");
                }
            }
        });

        findViewById(R.id.btn_show_red).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ColorFragmentsActivity.class));
            }
        });

        findViewById(R.id.btn_show_blue).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFragment(new BlueFragment());
            }
        });
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
