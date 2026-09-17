package com.newchar.debug.plugin;

import android.content.Context;
import android.Manifest;
import android.app.ActivityManager;
import android.content.pm.PackageManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.Camera;
import android.provider.Settings;
import android.os.Build;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import java.util.concurrent.Callable;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.newchar.debug.admin.DeviceAdminManager;
import com.newchar.debug.api.PluginContext;
import com.newchar.debug.api.ScreenDisplayPlugin;
import com.newchar.debug.monitor.strict.Strict;
import com.newchar.debug.utils.ViewUtils;

/**
 * @author newChar
 * @since 严格模式监控插件。
 * <p>
 * 严格模式独立启停，设备管理员权限单独申请和管理。
 */
public class StrictMonitorPlugin extends ScreenDisplayPlugin {

    public static final String TAG_PLUGIN = "Strict_Monitor";

    private LinearLayout mContainer;
    private ScrollView mScrollView;
    private LinearLayout mAdminControls;
    private LinearLayout mAppDataControls;
    private TextView mTitleView;
    private TextView mApplyAdminView;
    private Switch mStrictSwitch;

    private boolean mAdminActive = false;
    private String mCameraVerificationMessage = "相机状态：等待策略变更后自动验证";
    private String mAppDataClearMessage = "应用数据清理：等待操作";

    @Override
    public String id() {
        return TAG_PLUGIN;
    }

    @Override
    public String getName() {
        return "严格模式";
    }

    @Override
    public void onLoad(PluginContext ctx, ViewGroup pluginContainerView) {
        Context context = pluginContainerView.getContext();
        mContainer = new LinearLayout(context);
        mContainer.setOrientation(LinearLayout.VERTICAL);
        mContainer.setPadding(dp2px(context, 12), dp2px(context, 12), dp2px(context, 12), dp2px(context, 12));
        mContainer.setBackgroundColor(0x4D808080);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, dp2px(context, 8), 0, dp2px(context, 8));

        // 标题
        mTitleView = new TextView(context);
        mTitleView.setTextSize(16);
        mTitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        mTitleView.setText("严格模式监控");
        mContainer.addView(mTitleView, layoutParams);

        // 申请权限 TextView（admin 未授予时可见）
        mApplyAdminView = new TextView(context);
        mApplyAdminView.setTextSize(14);
        mApplyAdminView.setGravity(Gravity.CENTER);
        mApplyAdminView.setText("点击申请设备管理员权限");
        mApplyAdminView.setTextColor(Color.parseColor("#1976D2"));
        mApplyAdminView.setPadding(dp2px(context, 16), dp2px(context, 12), dp2px(context, 16), dp2px(context, 12));
        mApplyAdminView.setOnClickListener(v -> requestAdminPermission(v.getContext()));
        mContainer.addView(mApplyAdminView, layoutParams);

        mStrictSwitch = new Switch(context);
        mStrictSwitch.setTextSize(14);
        mStrictSwitch.setText("严格模式");
        mStrictSwitch.setChecked(Strict.isEnabled());
        mStrictSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            applyStrictMode(buttonView, isChecked);
        });
        mContainer.addView(mStrictSwitch, layoutParams);
        mAdminControls = new LinearLayout(context);
        mAdminControls.setOrientation(LinearLayout.VERTICAL);
        mContainer.addView(mAdminControls, layoutParams);
        mAppDataControls = new LinearLayout(context);
        mAppDataControls.setOrientation(LinearLayout.VERTICAL);
        mContainer.addView(mAppDataControls, layoutParams);

        refreshAdminState(context);
        updateUIState();

        mScrollView = new ScrollView(context);
        mScrollView.setFillViewport(true);
        mScrollView.addView(mContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pluginContainerView.addView(mScrollView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ViewUtils.setVisibility(mScrollView, View.GONE);
    }

    @Override
    public void onShow() {
        Context context = mContainer == null ? null : mContainer.getContext();
        if (context != null) {
            // 每次 onShow 都重新检查 admin 状态，处理用户从系统设置返回的情况
            refreshAdminState(context);
            updateUIState();
        }
        ViewUtils.setVisibility(mScrollView, View.VISIBLE);
    }

    @Override
    public void onHide() {
        ViewUtils.setVisibility(mScrollView, View.GONE);
    }

    @Override
    public void onUnload() {
        if (mStrictSwitch != null) {
            mStrictSwitch.setOnCheckedChangeListener(null);
        }
        mContainer = null;
        mScrollView = null;
        mAdminControls = null;
        mAppDataControls = null;
        mTitleView = null;
        mApplyAdminView = null;
        mStrictSwitch = null;
        mCameraVerificationMessage = "相机状态：等待策略变更后自动验证";
        mAppDataClearMessage = "应用数据清理：等待操作";
    }

    private void refreshAdminState(Context context) {
        mAdminActive = DeviceAdminManager.getInstance().isActive(context);
    }

    /** 更新独立的管理员授权入口与严格模式开关。 */
    private void updateUIState() {
        if (mApplyAdminView == null || mStrictSwitch == null) {
            return;
        }
        if (mAdminActive) {
            mApplyAdminView.setVisibility(View.GONE);
        } else {
            mApplyAdminView.setVisibility(View.VISIBLE);
        }
        mStrictSwitch.setVisibility(View.VISIBLE);
        mStrictSwitch.setChecked(Strict.isEnabled());
        refreshAdminControls();
        refreshApplicationDataControls(mContainer.getContext());
    }

    /** 授权后列出管理员控制项，每次刷新从系统读取当前策略。 */
    private void refreshAdminControls() {
        if (mAdminControls == null) {
            return;
        }
        mAdminControls.removeAllViews();
        mAdminControls.setVisibility(mAdminActive ? View.VISIBLE : View.GONE);
        if (!mAdminActive) {
            return;
        }
        Context context = mAdminControls.getContext();
        DevicePolicyManager manager = (DevicePolicyManager)
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (manager == null) {
            return;
        }
        ComponentName admin = DeviceAdminManager.getInstance().getAdminComponent(context);
        addPolicySwitch("禁止相机", () -> manager.getCameraDisabled(admin),
                (button, checked) -> manager.setCameraDisabled(admin, checked),
                (expected, actual) -> verifyCameraPolicy(context, expected, actual));
        addStateText(mCameraVerificationMessage);
        addKeyguardSwitch(manager, admin, "禁止锁屏相机",
                DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA);
        addKeyguardSwitch(manager, admin, "禁止锁屏通知",
                DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS);
        addKeyguardSwitch(manager, admin, "禁止指纹解锁",
                DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT);
        addPolicySwitch("最长 30 秒自动锁屏", () -> manager.getMaximumTimeToLock(admin) > 0,
                (button, checked) -> manager.setMaximumTimeToLock(admin, checked ? 30000L : 0L));
        addPasswordControls(manager, admin);
        addAdminAction("立即锁屏", () -> manager.lockNow());
        addAdminAction("撤销管理员权限", () -> revokeAdminAndRefresh(context));
    }

    /** 单独更新指定锁屏限制位，保留其他已设置的锁屏策略。 */
    private void addKeyguardSwitch(DevicePolicyManager manager, ComponentName admin,
            String title, int flag) {
        addPolicySwitch(title, () -> (manager.getKeyguardDisabledFeatures(admin) & flag) != 0,
                (button, checked) -> {
                    int current = manager.getKeyguardDisabledFeatures(admin);
                    manager.setKeyguardDisabledFeatures(admin,
                            checked ? current | flag : current & ~flag);
                });
    }

    /** 提供密码过期控制、登录失败查询和系统安全设置入口。 */
    private void addPasswordControls(DevicePolicyManager manager, ComponentName admin) {
        addPolicySwitch("密码 30 天后过期",
                () -> manager.getPasswordExpirationTimeout(admin) > 0,
                (button, checked) -> manager.setPasswordExpirationTimeout(admin,
                        checked ? 30L * 24 * 60 * 60 * 1000 : 0L));
        addStateText("登录失败次数：" + manager.getCurrentFailedPasswordAttempts());
        addStateText("存储加密状态：" + storageEncryptionStatusText(
                manager.getStorageEncryptionStatus()));
        addAdminAction("密码与安全设置", () -> {
            Context context = mAdminControls.getContext();
            context.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
    }

    /** 独立刷新应用数据清理区，使普通管理员未激活时仍可清理当前应用。 */
    private void refreshApplicationDataControls(Context context) {
        if (mAppDataControls == null) {
            return;
        }
        mAppDataControls.removeAllViews();
        DevicePolicyManager manager = (DevicePolicyManager)
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        addApplicationDataControls(context, manager);
    }

    /** 根据 DO/PO 权限与系统版本展示指定应用或当前应用的数据清理入口。 */
    private void addApplicationDataControls(Context context, DevicePolicyManager manager) {
        boolean canClearTarget = manager != null && canClearTargetApplicationData(manager, context);
        EditText packageInput = new EditText(context);
        packageInput.setHint(canClearTarget ? "输入需要清理的应用包名" : dataClearUnavailableHint(manager, context));
        packageInput.setEnabled(canClearTarget);
        packageInput.setFocusable(canClearTarget);
        mAppDataControls.addView(packageInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button clearButton = new Button(context);
        clearButton.setText(canClearTarget ? "清理指定应用数据" : "清理当前应用数据");
        clearButton.setOnClickListener(view -> clearApplicationData(manager,
                packageInput.getText().toString(), canClearTarget, view.getContext()));
        mAppDataControls.addView(clearButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addStateText(mAppDataControls, mAppDataClearMessage);
    }

    /** 只有 Android 9 及以上的 DO/PO 才可调用系统 API 清理其他应用的数据。 */
    private boolean canClearTargetApplicationData(DevicePolicyManager manager, Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && (manager.isDeviceOwnerApp(context.getPackageName())
                || manager.isProfileOwnerApp(context.getPackageName()));
    }

    /** 返回输入框不可用的原因，区分权限不足和系统版本不足。 */
    private String dataClearUnavailableHint(DevicePolicyManager manager, Context context) {
        if (manager == null) {
            return "设备管理服务不可用";
        }
        boolean isOwner = manager.isDeviceOwnerApp(context.getPackageName())
                || manager.isProfileOwnerApp(context.getPackageName());
        if (!isOwner) {
            return "没有 DO/PO 权限";
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? "当前状态不支持清理指定应用数据" : "系统版本低于 Android 9，无法清理指定应用数据";
    }

    /** 按权限路径清理指定应用或当前调试应用的数据。 */
    private void clearApplicationData(DevicePolicyManager manager, String packageName,
            boolean canClearTarget, Context context) {
        if (canClearTarget) {
            clearTargetApplicationData(manager, DeviceAdminManager.getInstance()
                    .getAdminComponent(context), packageName, context);
        } else {
            clearCurrentApplicationData(context);
        }
    }

    /** 调用 DO/PO 专属系统 API，并由异步回调更新清理结果。 */
    @android.annotation.TargetApi(Build.VERSION_CODES.P)
    private void clearTargetApplicationData(DevicePolicyManager manager, ComponentName admin,
            String packageName, Context context) {
        if (packageName.trim().isEmpty()) {
            mAppDataClearMessage = "应用数据清理：请输入应用包名";
            refreshApplicationDataControls(context);
            return;
        }
        try {
            mAppDataClearMessage = "应用数据清理：正在清理 $packageName";
            manager.clearApplicationUserData(admin, packageName.trim(), context.getMainExecutor(),
                    (clearedPackage, succeeded) -> publishDataClearResult(clearedPackage, succeeded));
            refreshApplicationDataControls(context);
        } catch (Exception error) {
            mAppDataClearMessage = "应用数据清理失败：" + error.getMessage();
            refreshApplicationDataControls(context);
        }
    }

    /** 清理当前调试应用自身的数据；系统随后会终止当前进程。 */
    private void clearCurrentApplicationData(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        boolean accepted = manager != null && manager.clearApplicationUserData();
        showMessage(context, accepted ? "已请求清理当前应用数据，应用将重启" : "无法请求清理当前应用数据");
    }

    /** 将系统异步回调的清理结论展示到当前插件 UI。 */
    private void publishDataClearResult(String packageName, boolean succeeded) {
        mAppDataClearMessage = succeeded ? "应用数据清理完成：" + packageName
                : "应用数据清理失败：" + packageName;
        if (mAppDataControls != null) {
            refreshApplicationDataControls(mAppDataControls.getContext());
        }
    }

    /** 根据系统策略初始化开关，捕获不支持的操作并恢复真实状态。 */
    private void addPolicySwitch(String title, Callable<Boolean> read,
            CompoundButton.OnCheckedChangeListener write) {
        addPolicySwitch(title, read, write, null);
    }

    /** 调用系统策略后读取真实状态，再同步开关并执行可选的功能验证。 */
    private void addPolicySwitch(String title, Callable<Boolean> read,
            CompoundButton.OnCheckedChangeListener write, PolicyVerificationCallback verification) {
        Switch control = new Switch(mAdminControls.getContext());
        control.setText(title);
        try {
            control.setChecked(read.call());
        } catch (Exception error) {
            control.setEnabled(false);
            control.setText(title + "（当前系统不支持）");
        }
        control.setOnCheckedChangeListener((button, checked) -> {
            try {
                write.onCheckedChanged(button, checked);
                boolean actual = read.call();
                if (actual != checked) {
                    showMessage(button.getContext(), "系统未应用此策略");
                }
                if (verification != null) {
                    verification.onVerified(checked, actual);
                }
            } catch (Exception error) {
                showMessage(button.getContext(), "操作失败：" + error.getMessage());
            }
            refreshAdminControls();
        });
        mAdminControls.addView(control, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 添加管理员操作按钮并处理权限撤销及系统版本限制。 */
    private void addAdminAction(String title, Runnable action) {
        Button button = new Button(mAdminControls.getContext());
        button.setText(title);
        button.setOnClickListener(view -> {
            try {
                action.run();
            } catch (Exception error) {
                showMessage(view.getContext(), "操作失败：" + error.getMessage());
            }
        });
        mAdminControls.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 显示从系统 API 读取的只读状态，不提供额外的检查入口。 */
    private void addStateText(String message) {
        addStateText(mAdminControls, message);
    }

    /** 在指定控制区域展示只读状态文本。 */
    private void addStateText(LinearLayout container, String message) {
        TextView state = new TextView(container.getContext());
        state.setText(message);
        state.setTextSize(13);
        state.setPadding(0, dp2px(state.getContext(), 4), 0, dp2px(state.getContext(), 4));
        container.addView(state, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 严格模式开关执行后立刻以 Strict 状态回写 UI，避免异常时显示错误状态。 */
    private void applyStrictMode(CompoundButton button, boolean enabled) {
        try {
            if (enabled) {
                Strict.startAll();
            } else {
                Strict.stopAll();
            }
        } catch (Exception error) {
            showMessage(button.getContext(), "严格模式切换失败：" + error.getMessage());
        }
        button.setChecked(Strict.isEnabled());
    }

    /** 仅在系统确认相机限制已关闭后，异步尝试打开相机验证实际可用性。 */
    private void verifyCameraPolicy(Context context, boolean expected, boolean actual) {
        if (expected || actual) {
            mCameraVerificationMessage = "相机状态：系统策略仍禁止访问";
            return;
        }
        if (!hasCameraPermission(context)) {
            mCameraVerificationMessage = "相机状态：缺少相机权限，无法验证";
            return;
        }
        mCameraVerificationMessage = "相机状态：正在验证";
        new Thread(() -> publishCameraVerification(context, openCameraForVerification()),
                "strict-camera-verification").start();
    }

    /** 在后台打开并释放相机，返回可显示的验证结论。 */
    private String openCameraForVerification() {
        Camera camera = null;
        try {
            camera = Camera.open();
            return camera == null ? "相机状态：未找到可用相机" : "相机状态：验证成功，可以打开";
        } catch (Exception error) {
            return "相机状态：验证失败（可能被占用或受系统限制）";
        } finally {
            if (camera != null) {
                camera.release();
            }
        }
    }

    /** 使用对应系统版本可用的 API 判断应用是否已获相机权限。 */
    private boolean hasCameraPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return context.getPackageManager().checkPermission(Manifest.permission.CAMERA,
                context.getPackageName()) == PackageManager.PERMISSION_GRANTED;
    }

    /** 将 DevicePolicyManager 的存储加密状态码转换为用户可理解的中文文本。 */
    private String storageEncryptionStatusText(int status) {
        switch (status) {
            case DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED:
                return "设备不支持存储加密";
            case DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE:
                return "存储加密未启用";
            case DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING:
                return "存储加密正在启用";
            case DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE:
                return "存储加密已启用";
            case DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY:
                return "存储已加密，但未由锁屏凭据保护";
            case DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER:
                return "存储已加密，密钥已关联当前用户";
            default:
                return "存储加密状态未知";
        }
    }

    /** 回到主线程后再次确认系统策略未恢复，再使用验证结果刷新 UI。 */
    private void publishCameraVerification(Context context, String message) {
        if (mContainer == null) {
            return;
        }
        mContainer.post(() -> {
            refreshAdminState(context);
            if (!mAdminActive) {
                return;
            }
            DevicePolicyManager manager = (DevicePolicyManager)
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = DeviceAdminManager.getInstance().getAdminComponent(context);
            mCameraVerificationMessage = manager != null && manager.getCameraDisabled(admin)
                    ? "相机状态：系统策略已重新禁止访问" : message;
            refreshAdminControls();
        });
    }

    /** 请求撤销后重新读取系统管理员状态，UI 不依赖 removeActiveAdmin 的返回值。 */
    private void revokeAdminAndRefresh(Context context) {
        DeviceAdminManager.getInstance().cancel(context.getApplicationContext());
        refreshAdminState(context);
        updateUIState();
        showMessage(context, mAdminActive ? "系统尚未撤销管理员权限" : "管理员权限已撤销");
    }

    /** 显示操作结果。 */
    private void showMessage(Context context, String message) {
        Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private void requestAdminPermission(Context context) {
        DeviceAdminManager.getInstance().apply(context, new DeviceAdminManager.AdminResultCallback() {
            @Override
            public void onResult(boolean granted, String message) {
                refreshAdminState(context);
                updateUIState();
                if (mContainer != null) {
                    Toast.makeText(mContainer.getContext(),
                            granted ? "管理员权限已授予" : "管理员权限未授予",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private int dp2px(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 策略写入后提供期望值与系统读回值，用于触发对应功能验证。 */
    private interface PolicyVerificationCallback {
        void onVerified(boolean expected, boolean actual);
    }
}
