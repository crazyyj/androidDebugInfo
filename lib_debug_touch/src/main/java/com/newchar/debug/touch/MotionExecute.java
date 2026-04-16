package com.newchar.debug.touch;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcel;
import android.view.MotionEvent;

import com.newchar.debug.lifecycle.DefaultActivityCallback;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author newChar
 * date 2023/9/15
 * @since 数据回放
 * @since 迭代版本，（以及描述）
 */
class MotionExecute extends DefaultActivityCallback {

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        super.onActivityCreated(activity, savedInstanceState);
//        activity.getWindow().getDecorView().post(new Runnable() {
//            @Override
//            public void run() {
//                try (BufferedInputStream fileInputStream = new BufferedInputStream(new FileInputStream(
//                        new File(activity.getExternalCacheDir(), "motion" + File.separator + "MainActivity#0.rec")))){
//                    a(fileInputStream, activity);
//
//                    Parcel obtain = Parcel.obtain();
//                    byte[] a = new byte[fileInputStream.available()];
//                    List<MotionEvent> temp = new ArrayList<>();
//                    fileInputStream.read(a);
//                    obtain.unmarshall(a, 0, a.length);
//                    obtain.setDataPosition(0);
//                    temp.add(MotionEvent.CREATOR.createFromParcel(obtain));
//                    for (MotionEvent motionEvent : temp) {
//                        activity.getWindow().getDecorView().dispatchTouchEvent(motionEvent);
//                    }
//
//                } catch (IOException e) {
//                    Log.e("ARAR", "", e);
//                }
//            }
//        });
    }

    public void a(BufferedInputStream fileInputStream2, Activity activity) throws IOException {
        Parcel obtain1 = Parcel.obtain();
        byte[] b = new byte[fileInputStream2.available()];
        List<MotionEvent> temp1 = new ArrayList<>();
        fileInputStream2.read(b);
        obtain1.unmarshall(b, 0, b.length);
        obtain1.setDataPosition(0);
        temp1.add(MotionEvent.CREATOR.createFromParcel(obtain1));
        for (MotionEvent motionEvent : temp1) {
            activity.dispatchTouchEvent(motionEvent);
        }
    }
}
