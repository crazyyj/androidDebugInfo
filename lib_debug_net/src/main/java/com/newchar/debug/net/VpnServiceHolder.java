package com.newchar.debug.net;

import android.os.ParcelFileDescriptor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 持有 TUN 接口引用，供 PacketForwarder 写包到 TUN。
 */
public final class VpnServiceHolder {

    private static volatile ParcelFileDescriptor sTunFd;
    private static volatile DebugNetVpnService sService;

    /** 保存当前已建立 TUN 的 VPN 服务实例。 */
    static void setTunFd(ParcelFileDescriptor tunFd) {
        sTunFd = tunFd;
    }

    /** 保存用于绕过 VPN 的服务实例。 */
    static void setService(DebugNetVpnService service) {
        sService = service;
    }

    /**
     * 让指定 TCP Socket 绕过 DebugNet VPN。
     *
     * PCToolsPlugin 通过反射调用本方法，避免 lib_debug_core 反向依赖 lib_debug_net。
     */
    public static boolean protect(java.net.Socket socket) {
        DebugNetVpnService service = sService;
        return service == null || socket == null || service.protect(socket);
    }

    /** 清空服务与 TUN 引用，防止服务停止后继续使用已关闭的描述符。 */
    static void clear() {
        sTunFd = null;
        sService = null;
    }

    /** 将 VPN 代理构造的响应包写回 TUN。 */
    static void writeToTun(byte[] packet) {
        ParcelFileDescriptor fd = sTunFd;
        if (fd == null || packet == null || packet.length == 0) {
            return;
        }
        try {
            DataOutputStream out = new DataOutputStream(
                    new java.io.FileOutputStream(fd.getFileDescriptor()));
            out.write(packet);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    /** 向网络监控面板分发抓包事件。 */
    static void dispatchEvent(DebugNetEvent event) {
        if (event != null) {
            DebugNetMonitor.dispatch(event);
        }
    }
}
