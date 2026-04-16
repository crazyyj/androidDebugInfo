package com.newchar.debug.monitor.field;

import java.beans.PropertyChangeListener;

/**
 * @author newChar
 * date 2022/12/13
 * @since Debug 字段调试工具
 * @since 迭代版本，（以及描述）
 */
interface MonitorHandler {

    /**
     * @param fieldNames 需要监控的字段
     */
    void addMonitorField(String... fieldNames);

    /**
     * 删除这个监控的字段
     *
     * @param fieldNames 字段
     */
    void removeMonitorField(String... fieldNames);

    /**
     * 设置监听回调
     * @param changeListener 回调
     */
    void setChangeListener(PropertyChangeListener changeListener);

    /**
     * 回收资源
     */
    void release();
}
