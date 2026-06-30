package com.newchar.debug.device.devices;

/**
 * @author newChar
 * date 2023/7/30
 * @since 设备信息 Provider 工厂
 */
public final class DevicesInfoFactory {

    private static final ICPUProvider CPU_INSTANCE = new CPUProviderImpl();
    private static final IGPUProvider GPU_INSTANCE = new GPUProviderImpl();
    private static final IFPSProvider FPS_INSTANCE = new FPSProviderDisplayImpl();
    private static final IPermissionProvider PERMISSION_INSTANCE = new PermissionProviderImpl();

    private DevicesInfoFactory() {
    }

    public static ICPUProvider getCpuInfo() {
        return CPU_INSTANCE;
    }

    public static IGPUProvider getGpuInfo() {
        return GPU_INSTANCE;
    }

    public static IFPSProvider getFpsInfo() {
        return FPS_INSTANCE;
    }

    public static IPermissionProvider getPermissionProvider() {
        return PERMISSION_INSTANCE;
    }

}
