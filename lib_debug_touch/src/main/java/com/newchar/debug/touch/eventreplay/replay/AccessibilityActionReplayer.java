package com.newchar.debug.touch.eventreplay.replay;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * 以 accesshelper 风格（无障碍服务手势）复现动作流。
 *
 * <p>这就是“事件复现的另一种形式”：同一个被录制下来的事件，
 * 不再以原始 MotionEvent 重放，而是先被 {@link TouchToActionConverter} 转换为
 * 语义动作流，再由本类经由 {@link GesturePerformer} 通过无障碍服务模拟执行。</p>
 */
public final class AccessibilityActionReplayer {

    private final GesturePerformer mPerformer = new GesturePerformer();

    public boolean replay(AccessibilityService service, ActionStream stream) {
        if (service == null || stream == null) {
            return false;
        }
        boolean ok = true;
        for (Action action : stream.actions) {
            if (action == null || action.actionOutput == null) {
                continue;
            }
            ActionOutput out = action.actionOutput;
            if (out.type == 10 && !out.gesturePath.isEmpty()) {
                // 手势 / 滑动
                if (!mPerformer.perform(service, out)) {
                    ok = false;
                }
            } else if (out.type == 0 && action.actionLocal != null && action.actionLocal.type == 3) {
                // 点击：取坐标，构造一个极短的点按手势
                PointF pt = parseLocation(action.actionLocal.actionLocation);
                if (pt != null) {
                    List<PointF> tap = new ArrayList<>();
                    tap.add(pt);
                    tap.add(new PointF(pt.x, pt.y));
                    if (!mPerformer.perform(service, tap, 1L)) {
                        ok = false;
                    }
                }
            }
        }
        return ok;
    }

    private static PointF parseLocation(String location) {
        if (location == null || location.indexOf(',') < 0) {
            return null;
        }
        String[] parts = location.split(",");
        try {
            return new PointF(Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
