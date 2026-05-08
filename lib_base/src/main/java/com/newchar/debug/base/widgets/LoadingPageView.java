package com.newchar.debug.base.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.widget.ViewFlipper;



/**
 * Created by newlq on 2016/12/9.
 */

public abstract class LoadingPageView extends ViewFlipper {


    private final int STATUS_PAGE_LODING = 0;
    private final int STATUS_PAGE_LODING_SUCCESS = 1;
    private final int STATUS_PAGE_LODING_FAIL = 2;
    private final int STATUS_PAGE_LOADING_ERROR = 3;

    private int STATUS_PAGE_LODING_DEFAULT = STATUS_PAGE_LODING;

    public int TIME_OUT_MS = 5000;

    private final SparseArray<View> views = new SparseArray<>(4);

    public LoadingPageView(Context context) {
        this(context, null);
    }

    public LoadingPageView(Context context, AttributeSet attrs) {
        super(context, attrs);

    }

    protected View inflate(int layoutId){
        View view = views.get(layoutId);
        if (view == null) {
            view = View.inflate(getContext(), layoutId, null);
            views.put(layoutId, view);
        }
        return view;
    }

    protected View getNetErrorView(){
//        return inflate(R.layout.default_error_view);
        return null;
    }

    protected View getEmptyView(){
        return null;
    }

    protected View getLoadingView(){
        return null;
    }

    protected abstract View getView();


    public abstract static class PageStatusViewListener{
        protected abstract void onLoadingEnd();
        protected abstract void onLoadingFail();
        protected abstract void onLoadingStart();
        protected abstract void onLoadingError();
        protected abstract void onLoadingSuccess();
    }

}
