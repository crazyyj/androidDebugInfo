package com.newchar.debug.monitor.jvmti;

import java.lang.reflect.Method;

public interface DebugStackMotionCallback {

    /**
     * 方法进出回调。
     *
     * @param method   方法
     * @param isEnter  是否进入方法
     * @param error    异常堆栈
     */
    void onMethodVisit(Method method, boolean isEnter, Throwable error);

    /**
     * 变量访问回调。
     *
     * @param field   字段
     * @param isSet   是否为设置操作
     * @param error   异常堆栈
     */
    void onVariableVisit(java.lang.reflect.Field field, boolean isSet, Throwable error);
}
