package com.newchar.debug.base.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

/**
 * @author newChar
 * date 2023/4/21
 * @since 当前版本，（以及描述）
 * @since 迭代版本，（以及描述）
 */
public final class ViewUtils {
    
    public static void setVisibility(View view, int visibility) {
        if (view != null && view.getVisibility() != visibility) {
            if (UIUtils.isMainThread()) {
                view.setVisibility(visibility);
            } else {
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        Context context = view.getContext();
                        if (context instanceof Activity) {
                            if (!((Activity) context).isFinishing() && !((Activity) context).isDestroyed()) {
                                view.setVisibility(visibility);
                            }
                        } else {
                            view.setVisibility(visibility);
                        }
                    }
                });
            }
        }
    }

    public static void setText(View view, CharSequence text) {
        if (view instanceof TextView) {
            ((TextView) view).setText(text);
        }
    }

    public static void setWidthHeight(View view, int width, int height) {
        view.post(() -> {
            if (view.getWidth() != width || view.getHeight() != height) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.width = width;
                layoutParams.height = height;
                view.setLayoutParams(layoutParams);
            }
        });
    }

    public static void removeSelf(View host) {
        if (host != null) {
            try {
                ViewParent viewParent = host.getParent();
                if (viewParent instanceof ViewGroup) {
                    if (UIUtils.isMainThread()) {
                        ((ViewGroup) viewParent).removeView(host);
                    } else {
                        if (viewParent == null) {
                            return;
                        }
                        host.post(new Runnable() {
                            @Override
                            public void run() {
                                Context context = host.getContext();
                                if (context instanceof Activity) {
                                    if (!((Activity) context).isFinishing() && !((Activity) context).isDestroyed()) {
                                        ((ViewGroup) viewParent).removeView(host);
                                    }
                                } else {
                                    ((ViewGroup) viewParent).removeView(host);
                                }
                            }
                        });
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static void addView(ViewGroup container, View self) {
        if (container != null && self != null) {
            if (self.getParent() != null) {
                removeSelf(self);
            }
            if (UIUtils.isMainThread()){
                container.addView(self);
            } else {
                self.post(() -> {
                    Context context = self.getContext();
                    if (context instanceof Activity) {
                        if (!((Activity) context).isFinishing() && !((Activity) context).isDestroyed()) {
                            container.addView(self);
                        }
                    } else {
                        container.addView(self);
                    }
                });
            }
        }
    }

}
