package com.newchar.debug.base.click;

//import javax.inject.Provider;

/**
 * @author 
 * date            2020/4/23
 * @since 类型包装类，提供set，get方法，
 * @since 迭代版本描述
 */
public interface Wrapper<T> /*extends Provider*/ {

    void set(T t);

    T get();

}
