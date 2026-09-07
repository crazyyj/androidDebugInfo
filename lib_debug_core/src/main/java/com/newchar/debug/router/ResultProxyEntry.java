package com.newchar.debug.router;

import android.content.ComponentName;
import android.os.Bundle;

/**
 * @author newChar
 * @since 结果代理条目。
 */
public class ResultProxyEntry {

    public final int id;
    public final int requestCode;
    public final ComponentName componentName;
    public final Bundle extras;
    public final ResultProxyCallback callback;

    public ResultProxyEntry(int id, int requestCode, ComponentName componentName, Bundle extras, ResultProxyCallback callback) {
        this.id = id;
        this.requestCode = requestCode;
        this.componentName = componentName;
        this.extras = extras;
        this.callback = callback;
    }
}
