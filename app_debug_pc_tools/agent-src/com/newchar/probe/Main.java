package com.newchar.probe;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class Main {

    public static void main(String[] args) {
        try {
            android.os.Looper.prepareMainLooper();
            Context ctx = systemContext();
            PackageManager pm = ctx.getPackageManager();

            for (String arg : args) {
                String pkg = arg.trim();
                if (pkg.isEmpty()) {
                    continue;
                }
                String label = "";
                String iconB64 = "";
                try {
                    ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                    CharSequence raw = pm.getApplicationLabel(info);
                    if (raw != null) {
                        label = raw.toString();
                    }
                    Drawable drawable = pm.getApplicationIcon(pkg);
                    Bitmap bitmap = render(drawable);
                    if (bitmap != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        iconB64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                    }
                } catch (Throwable t) {
                    label = "ERR:" + t.getClass().getSimpleName();
                }
                System.out.println(pkg + "|" + label + "|" + iconB64);
                System.out.flush();
            }
        } catch (Throwable t) {
            System.out.println("FATAL|" + t.getClass().getName() + ": " + t.getMessage());
            Throwable cause = t.getCause();
            while (cause != null) {
                System.out.println("CAUSE|" + cause.getClass().getName() + ": " + cause.getMessage());
                cause = cause.getCause();
            }
            t.printStackTrace(System.out);
        }
        System.exit(0);
    }

    /** 通过反射拿到 system context，避免编译期依赖隐藏 API。 */
    private static Context systemContext() throws Exception {
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        Object at = atClass.getDeclaredMethod("systemMain").invoke(null);
        return (Context) atClass.getDeclaredMethod("getSystemContext").invoke(at);
    }

    private static Bitmap render(Drawable drawable) {
        int size = 48;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bitmap;
    }
}
