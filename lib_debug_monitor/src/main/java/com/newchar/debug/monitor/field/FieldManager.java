package com.newchar.debug.monitor.field;

import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author newChar
 * date 2025/3/28
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
final class FieldManager {

    // 如果没有监视的对象，则添加。a
    private final Map<Object, FieldMonitorHandler> mMonitorHandlers = new HashMap<>();


    /**
     * 需要实现接口的对象，调用接口方法进行监听
     *
     * @param fieldHostObj   字段的宿主对象
     * @param changeListener 字段值变化的回调
     * @param fieldNames     字段
     * @return 得到调用方法的接口对象
     */
    public Object monitorField(Object fieldHostObj, PropertyChangeListener changeListener, String... fieldNames) {
        FieldMonitorHandler fieldMonitorHandler;
        if (mMonitorHandlers.containsKey(fieldHostObj)) {
            fieldMonitorHandler = mMonitorHandlers.get(fieldHostObj);
            if (fieldMonitorHandler != null) {
                fieldMonitorHandler.addMonitorField(fieldNames);
            }
        } else {
            fieldMonitorHandler = new FieldMonitorHandler(fieldHostObj, fieldNames);
            fieldMonitorHandler.setChangeListener(changeListener);
            mMonitorHandlers.put(fieldHostObj, fieldMonitorHandler);
        }
        return fieldMonitorHandler != null ? fieldMonitorHandler.newProxy() : null;
    }

    public void destroy() {

        if (mMonitorHandlers != null) {
            Collection<FieldMonitorHandler> values = mMonitorHandlers.values();
            for (FieldMonitorHandler value : values) {
                value.release();
            }
            mMonitorHandlers.clear();
        }
    }

}
