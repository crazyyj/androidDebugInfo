package com.newchar.debug.base.click.intercept;

import com.newchar.debug.base.click.Wrapper;

/**
 * @author newchar
 * date            2020/4/23
 * @since 拦截目标类型，View 拦截器，Int类型拦截器等。
 * @since 迭代版本描述
 */
public class InterceptTargetType<T> implements Wrapper<T> {

    private T mTarget;

    public InterceptTargetType() {
        super();
    }

    public InterceptTargetType(T target) {
        super();
        this.mTarget = target;
    }

    @Override
    public void set(T target) {
        mTarget = target;
    }

    @Override
    public T get() {
        return mTarget;
    }

}
