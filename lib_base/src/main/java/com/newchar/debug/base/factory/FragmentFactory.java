package com.newchar.debug.base.factory;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.os.Bundle;
import android.text.TextUtils;

import com.newchar.debug.base.arch.BaseFragment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by newWiner on 2016/12/1.
 * Fragment工厂类
 * 传入Fragment 全路径 和参数 参数 应对于Fragment的 setArgment()方法的参数， Bundle可不传
 * 可以额外在项目中添加 一个配置文件 传入 的全路径为 一个常量 这样 就可以实现 修改常量值 打开 指定的Fragment
 */

public class FragmentFactory {

    private static final Map<Class<? extends BaseFragment>, Fragment> fragmentContainer = new ConcurrentHashMap<>();
    private static final Map<String, Fragment> fragmentAllWayContainer = new ConcurrentHashMap<>();

    private FragmentFactory(){}

    /**
     * 创建Fragment 方法
     * @param fragAllWay        Fragment 全路径
     * @param bunlde             Fragment.setArgments(Bundle b)// ****只在第一次可传 如果第二次再传的话 会报错****
     * @see android.app.Fragment#setArguments(Bundle)
     * @return Fragment 实例
     */
    public static Fragment createFragment(String fragAllWay, Bundle bunlde) {
        Fragment fragment = null;
        if (fragmentAllWayContainer.containsKey(fragAllWay)) {
            fragment = fragmentAllWayContainer.get(fragAllWay);
            if ((bunlde != null & bunlde.size() > 0) & fragment.isAdded()) {
                throw new IllegalStateException("fragment已经被添加, 不可调用setArgment(Bundle bundle)");
            }
            return fragment;
        }
        try {
            Class<Fragment> fragClazz = (Class<Fragment>) Class.forName(fragAllWay);
            Method newInstance = fragClazz.getMethod("newInstance", Bundle.class);
//            newInstance.setAccessible(true);
            fragment = (Fragment) newInstance.invoke(fragClazz, bunlde);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } finally {
            if (fragmentAllWayContainer != null && !TextUtils.isEmpty(fragAllWay)
                    && !fragmentAllWayContainer.containsKey(fragAllWay) && fragment != null)
                fragmentAllWayContainer.put(fragAllWay, fragment);
            return fragment;
        }
    }

    @SuppressLint("SuspiciousIndentation")
    public static Fragment createFragment(Class<? extends BaseFragment> fragClazz, Bundle bunlde){
        Fragment fragment = null;
        if (fragmentContainer.containsKey(fragClazz)){
            fragment = fragmentContainer.get(fragClazz);
            if ((bunlde != null & bunlde.size() > 0) & fragment.isAdded()) {
                throw new IllegalStateException("fragment已经被添加, 不可调用setArgment(Bundle b)");
            }
            return fragment;
        }
        try {
            Method newInstance = fragClazz.getMethod("newInstance", Bundle.class);
            newInstance.setAccessible(true);
            fragment = (Fragment) newInstance.invoke(fragClazz, bunlde);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } finally {
			if (fragmentContainer != null && fragClazz != null
				&& !fragmentContainer.containsKey(fragClazz) && fragment != null)
				fragmentContainer.put(fragClazz, fragment);
        return fragment;
		}
    }


}
