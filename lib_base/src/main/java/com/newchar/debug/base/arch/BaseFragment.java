package com.newchar.debug.base.arch;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


/**
 * @author NewLq
 * Fragment 基类 配合Viewpager使用， 懒加载数据 在第一次进入 或用户可见时， 加载最新数据
 */
public abstract class BaseFragment extends Fragment {

    protected View mView;
    private boolean isViewInit;

    public BaseFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        isViewInit = false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initWidgetsBefore();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (mView == null) {
            int layoutId = getContentViewId();
            if (0 < layoutId) {
                mView = inflater.inflate(layoutId, null);
            } else {
                mView = super.onCreateView(inflater, container, savedInstanceState);
            }
        }
        return mView;
    }

    @Override
    public void onViewCreated(View view,  Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initWidgets(view);
        isViewInit = true;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initData(savedInstanceState);
        initListener();
    }

    //	事务切换Fragment，3.0Bug 需要重写方法
    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
        View fragView = getView();
        if (fragView != null) {
            fragView.setVisibility(menuVisible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isViewInit) {
            if (isVisibleToUser) {
                onVisibility();
            } else {
                onInVisibility();
            }
        } /*else {
            //View未初始化时, Fragment第一次进入, 可以初始创建一些目录或者文件
        }*/
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (isViewInit) {
            if (!hidden) {
                onVisibility();
            } else {
                onInVisibility();
            }
        } /*else {
            //View未初始化时, Fragment第一次进入, 可以初始创建一些目录或者文件
        }*/
    }


    @Override
    public void onPause() {
        super.onPause();
        try {
            if ((getActivity()).isFinishing()) {
                onReleaseRes();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 后台不可见，并且Activity被关闭时 释放少量资源
     */
    protected void onReleaseRes() {

    }

    public void initWidgetsBefore() {
    }

    protected void onInVisibility() {
    }

    protected abstract void onVisibility();

    protected abstract void initWidgets(View frgView);

    protected abstract void initData(Bundle savedInstanceState);

    protected abstract void initListener();

    protected abstract int getContentViewId();

    protected abstract BaseFragment getInstance(Bundle bundle);

}
