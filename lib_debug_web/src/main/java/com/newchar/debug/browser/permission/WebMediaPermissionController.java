package com.newchar.debug.browser.permission;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.webkit.PermissionRequest;
import android.widget.Toast;

import com.newchar.debug.lifecycle.AppLifecycleManager;
import com.newchar.debug.view.DebugViewStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 协调 WebView 媒体请求、Android 系统权限和用户确认过的 origin 授权。
 */
public final class WebMediaPermissionController {

    private static final int PROMPT_DIALOG = 1;
    private static final int PROMPT_OVERLAY = 2;

    private static WebMediaPermissionController instance;

    private final Context appContext;
    private final WebMediaPermissionStore store;
    private final Map<PermissionRequest, Entry> entries = new IdentityHashMap<>();
    private final Map<String, PendingOrigin> pendingOrigins = new HashMap<>();
    private final Map<Object, Map<String, Integer>> grantedOwnerResources = new IdentityHashMap<>();
    private final Map<Object, Map<String, Integer>> reportedOwnerResources = new IdentityHashMap<>();

    /**
     * 返回进程共享控制器，所有状态只保存到应用私有存储。
     */
    public static synchronized WebMediaPermissionController getInstance(Context context) {
        if (instance == null) {
            instance = new WebMediaPermissionController(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 供 DialogFragment 回传选择；进程重建后不存在的 token 无需处理。
     */
    static synchronized void resolvePrompt(String token, boolean allowed) {
        if (instance != null) {
            instance.resolve(token, allowed);
        }
    }

    /**
     * 创建控制器并保存应用上下文，避免持有 Activity。
     */
    private WebMediaPermissionController(Context context) {
        appContext = context;
        store = new WebMediaPermissionStore(context);
    }

    /**
     * 处理一次内核媒体请求；只能在主线程调用以保持 Dialog 与请求生命周期一致。
     */
    public void request(Context context, Object owner, PermissionRequest request) {
        if (request == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            request.deny();
            return;
        }
        String origin = WebMediaPermissionOrigin.from(request.getOrigin());
        int requested = requestedResources(request);
        if (origin == null || requested == 0) {
            request.deny();
            return;
        }
        int systemAllowed = systemGranted(requested);
        int storedAllowed = store.getGranted(origin) & systemAllowed & requested;
        int needsConfirmation = requested & systemAllowed & ~storedAllowed;
        if (needsConfirmation == 0) {
            grantAndReport(owner, origin, request, storedAllowed, storedAllowed != 0);
            return;
        }
        Activity activity = findAvailableActivity(context);
        int promptType = activity == null ? PROMPT_OVERLAY : PROMPT_DIALOG;
        if (promptType == PROMPT_OVERLAY && !WebMediaPermissionOverlay.canShow(appContext)) {
            grantAndReport(owner, origin, request, storedAllowed, storedAllowed != 0);
            return;
        }
        addPending(activity, promptType, owner, request, origin, storedAllowed, needsConfirmation);
    }

    /**
     * 内核取消请求时移除对应等待项，避免对失效请求调用 grant 或 deny。
     */
    public void cancel(PermissionRequest request) {
        if (request == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }
        removeEntry(request);
    }

    /**
     * WebView 被销毁时拒绝该实例尚未回答的所有网站请求并关闭空提示框。
     */
    public void release(Object owner) {
        if (owner == null || Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }
        List<PermissionRequest> requests = new ArrayList<>();
        for (Map.Entry<PermissionRequest, Entry> item : entries.entrySet()) {
            if (item.getValue().owner == owner) {
                requests.add(item.getKey());
            }
        }
        for (PermissionRequest request : requests) {
            Entry entry = entries.get(request);
            removeEntry(request);
            if (entry != null) {
                request.deny();
            }
        }
        if (grantedOwnerResources.remove(owner) != null || reportedOwnerResources.remove(owner) != null) {
            updateDebugViewMediaStatus();
        }
    }

    /**
     * 顶层页面开始导航时移除旧页面的媒体状态，避免顶部提示遗留。
     */
    public void clearMediaCaptureStatus(Object owner) {
        if (owner != null && Looper.myLooper() == Looper.getMainLooper()
                && (grantedOwnerResources.remove(owner) != null || reportedOwnerResources.remove(owner) != null)) {
            updateDebugViewMediaStatus();
        }
    }

    /**
     * 接收页面脚本回传的实时状态，仅接受该 WebView 已获准的 origin。
     */
    public void reportMediaCaptureState(Object owner, String origin, boolean audioCapturing,
                                        boolean videoCapturing) {
        if (owner == null || origin == null || Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }
        Map<String, Integer> granted = grantedOwnerResources.get(owner);
        if (granted == null || !granted.containsKey(origin)) {
            return;
        }
        int resources = (audioCapturing ? WebMediaPermissionStore.AUDIO : 0)
                | (videoCapturing ? WebMediaPermissionStore.VIDEO : 0);
        Map<String, Integer> reported = reportedOwnerResources.get(owner);
        if (reported == null) {
            reported = new HashMap<>();
            reportedOwnerResources.put(owner, reported);
        }
        reported.put(origin, resources & granted.get(origin));
        updateDebugViewMediaStatus();
    }

    /**
     * 将同 origin 的并发请求合并为一个确认框，并同步更新提示资源范围。
     */
    private void addPending(Activity activity, int promptType, Object owner, PermissionRequest request, String origin,
                            int storedAllowed, int needsConfirmation) {
        PendingOrigin pending = pendingOrigins.get(origin);
        if (pending == null) {
            pending = new PendingOrigin(origin, UUID.randomUUID().toString(), activity, promptType);
            pendingOrigins.put(origin, pending);
        }
        Entry entry = new Entry(owner, request, pending, storedAllowed, needsConfirmation);
        pending.entries.add(entry);
        pending.resources |= needsConfirmation;
        entries.put(request, entry);
        if (!pending.shown) {
            pending.shown = true;
            if (!showPrompt(pending)) {
                resolve(pending.token, false);
            }
        } else {
            updatePrompt(pending);
        }
    }

    /**
     * 将用户确认结果应用到仍存活的请求，系统权限在授予前再次检查。
     */
    private void resolve(String token, boolean allowed) {
        PendingOrigin pending = findPending(token);
        if (pending == null) {
            return;
        }
        pendingOrigins.remove(pending.origin);
        dismissPrompt(pending);
        if (allowed) {
            store.grant(pending.origin, pending.resources);
        }
        List<Entry> current = new ArrayList<>(pending.entries);
        pending.entries.clear();
        for (Entry entry : current) {
            entries.remove(entry.request);
            int granted = entry.storedAllowed;
            if (allowed) {
                granted |= entry.needsConfirmation;
            }
            grantAndReport(entry.owner, pending.origin, entry.request, granted & systemGranted(
                    entry.storedAllowed | entry.needsConfirmation), false);
        }
    }

    /**
     * 从已取消的请求中解除关联；没有等待项时关闭对应提示宿主。
     */
    private void removeEntry(PermissionRequest request) {
        Entry entry = entries.remove(request);
        if (entry == null) {
            return;
        }
        PendingOrigin pending = entry.pending;
        pending.entries.remove(entry);
        if (!pending.entries.isEmpty()) {
            return;
        }
        pendingOrigins.remove(pending.origin);
        dismissPrompt(pending);
    }

    /**
     * 根据 token 查找当前仍在等待用户确认的 origin。
     */
    private PendingOrigin findPending(String token) {
        if (token == null) {
            return null;
        }
        for (PendingOrigin pending : pendingOrigins.values()) {
            if (token.equals(pending.token)) {
                return pending;
            }
        }
        return null;
    }

    /**
     * 仅接受本次请求中明确要求的音频与视频资源。
     */
    private int requestedResources(PermissionRequest request) {
        int resources = 0;
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                resources |= WebMediaPermissionStore.AUDIO;
            } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                resources |= WebMediaPermissionStore.VIDEO;
            }
        }
        return resources;
    }

    /**
     * 读取 Android 录音和相机的当前系统授权，不能由网页请求自动拉起系统权限框。
     */
    private int systemGranted(int requested) {
        int granted = 0;
        if ((requested & WebMediaPermissionStore.AUDIO) != 0
                && hasPermission(Manifest.permission.RECORD_AUDIO)) {
            granted |= WebMediaPermissionStore.AUDIO;
        }
        if ((requested & WebMediaPermissionStore.VIDEO) != 0
                && hasPermission(Manifest.permission.CAMERA)) {
            granted |= WebMediaPermissionStore.VIDEO;
        }
        return granted;
    }

    /**
     * 检查应用是否已经拥有一个危险权限。
     */
    private boolean hasPermission(String permission) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || appContext.checkSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 仅向内核授予请求过且通过系统及本地策略检查的资源。
     */
    private void grantOrDeny(PermissionRequest request, int resources) {
        List<String> granted = new ArrayList<>();
        if ((resources & WebMediaPermissionStore.AUDIO) != 0) {
            granted.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
        }
        if ((resources & WebMediaPermissionStore.VIDEO) != 0) {
            granted.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        }
        if (granted.isEmpty()) {
            request.deny();
        } else {
            request.grant(granted.toArray(new String[0]));
        }
    }

    /**
     * 向内核回传权限，并同步记录该 WebView 获准的媒体资源。
     */
    private void grantAndReport(Object owner, String origin, PermissionRequest request, int resources,
                                boolean showHistoryToast) {
        grantOrDeny(request, resources);
        if (resources == 0 || owner == null || origin == null) {
            return;
        }
        int previous = ownerOriginResources(grantedOwnerResources, owner, origin);
        ownerResources(grantedOwnerResources, owner).put(origin, previous | resources);
        removeReportedOrigin(owner, origin);
        updateDebugViewMediaStatus();
        if (showHistoryToast && (previous & resources) != resources) {
            Toast.makeText(appContext, mediaCaptureMessage(resources), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 将所有存活 WebView 的媒体资源聚合后回调到 DebugView 顶部区域。
     */
    private void updateDebugViewMediaStatus() {
        int resources = 0;
        for (Map.Entry<Object, Map<String, Integer>> entry : grantedOwnerResources.entrySet()) {
            Map<String, Integer> reported = reportedOwnerResources.get(entry.getKey());
            for (Map.Entry<String, Integer> origin : entry.getValue().entrySet()) {
                resources |= reported != null && reported.containsKey(origin.getKey())
                        ? reported.get(origin.getKey()) : origin.getValue();
            }
        }
        DebugViewStore.updateWebMediaCaptureStatus(
                (resources & WebMediaPermissionStore.AUDIO) != 0,
                (resources & WebMediaPermissionStore.VIDEO) != 0);
    }

    /**
     * 生成人类可读的历史授权媒体提示。
     */
    private String mediaCaptureMessage(int resources) {
        if ((resources & WebMediaPermissionStore.AUDIO) != 0
                && (resources & WebMediaPermissionStore.VIDEO) != 0) {
            return "正在录音/拍摄中";
        }
        return (resources & WebMediaPermissionStore.AUDIO) != 0 ? "正在录音中" : "正在拍摄中";
    }

    /**
     * 读取一个 WebView 在指定 origin 上最近获准的媒体资源。
     */
    private int ownerOriginResources(Map<Object, Map<String, Integer>> values, Object owner, String origin) {
        Map<String, Integer> origins = values.get(owner);
        Integer resources = origins == null ? null : origins.get(origin);
        return resources == null ? 0 : resources;
    }

    /**
     * 返回一个 WebView 的按 origin 媒体资源记录，缺失时按需创建。
     */
    private Map<String, Integer> ownerResources(Map<Object, Map<String, Integer>> values, Object owner) {
        Map<String, Integer> origins = values.get(owner);
        if (origins == null) {
            origins = new HashMap<>();
            values.put(owner, origins);
        }
        return origins;
    }

    /**
     * 新授权会产生新的流，因此移除同 origin 的过期脚本状态。
     */
    private void removeReportedOrigin(Object owner, String origin) {
        Map<String, Integer> reported = reportedOwnerResources.get(owner);
        if (reported == null) {
            return;
        }
        reported.remove(origin);
        if (reported.isEmpty()) {
            reportedOwnerResources.remove(owner);
        }
    }

    /**
     * 优先选取生命周期管理器记录的前台 Activity，未接入管理器时回退到传入上下文。
     */
    private Activity findAvailableActivity(Context context) {
        Activity activity = AppLifecycleManager.getInstance().getLastActivity();
        if (isUsableActivity(activity)) {
            return activity;
        }
        activity = findActivity(context);
        return isUsableActivity(activity) ? activity : null;
    }

    /**
     * 判断 Activity 是否仍可作为 DialogFragment 的安全宿主。
     */
    private boolean isUsableActivity(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    /**
     * 从 MutableContextWrapper 等包装上下文中取得关联的 Activity。
     */
    private Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            Context next = ((ContextWrapper) current).getBaseContext();
            if (next == current) {
                break;
            }
            current = next;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    /**
     * 按已选宿主展示确认提示，展示失败将由调用方拒绝请求。
     */
    private boolean showPrompt(PendingOrigin pending) {
        if (pending.promptType == PROMPT_DIALOG) {
            return WebMediaPermissionDialogFragment.show(pending.activity, pending.token,
                    pending.origin, pending.resources);
        }
        return WebMediaPermissionOverlay.show(appContext, pending.token, pending.origin, pending.resources);
    }

    /**
     * 更新同一 origin 合并请求后的提示内容。
     */
    private void updatePrompt(PendingOrigin pending) {
        if (pending.promptType == PROMPT_DIALOG) {
            WebMediaPermissionDialogFragment.update(pending.activity, pending.token, pending.resources);
        } else {
            WebMediaPermissionOverlay.update(pending.token, pending.origin, pending.resources);
        }
    }

    /**
     * 关闭指定宿主中的提示，避免用户已完成或请求已取消后遗留窗口。
     */
    private void dismissPrompt(PendingOrigin pending) {
        if (pending.promptType == PROMPT_DIALOG) {
            WebMediaPermissionDialogFragment.dismiss(pending.activity, pending.token);
        } else {
            WebMediaPermissionOverlay.dismiss(pending.token);
        }
    }

    /**
     * 单个 PermissionRequest 在当前确认流程中的状态。
     */
    private static final class Entry {
        final Object owner;
        final PermissionRequest request;
        final PendingOrigin pending;
        final int storedAllowed;
        final int needsConfirmation;

        /**
         * 创建一次请求状态，所有字段都来自请求发生时的不可变快照。
         */
        Entry(Object owner, PermissionRequest request, PendingOrigin pending, int storedAllowed,
              int needsConfirmation) {
            this.owner = owner;
            this.request = request;
            this.pending = pending;
            this.storedAllowed = storedAllowed;
            this.needsConfirmation = needsConfirmation;
        }
    }

    /**
     * 合并相同 origin 的并发请求，避免连续弹出多个相同网站提示框。
     */
    private static final class PendingOrigin {
        final String origin;
        final String token;
        final Activity activity;
        final int promptType;
        final List<Entry> entries = new ArrayList<>();
        int resources;
        boolean shown;

        /**
         * 记录一个当前可展示确认框的 origin。
         */
        PendingOrigin(String origin, String token, Activity activity, int promptType) {
            this.origin = origin;
            this.token = token;
            this.activity = activity;
            this.promptType = promptType;
        }
    }
}