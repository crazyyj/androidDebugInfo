package com.newchar.debugview.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author newChar
 * date 2026/2/9
 * @since 标记可触发调试悬浮窗策略的 Activity。
 * @since 迭代版本，（以及描述）
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DebugUiContext {
}
