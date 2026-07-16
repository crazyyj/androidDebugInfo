package com.newchar.debug.net;

import android.os.ParcelFileDescriptor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 持有 TUN 接口引用，供 PacketForwarder 写包到 TUN。
 */
final class VpnServiceHolder {

    private static volatile ParcelFileDescriptor sTunFd;

    static void setTunFd(ParcelFileDescriptor tunFd) {
        sTunFd = tunFd;
    }

    static void clear() {
        sTunFd = null;
    }

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

    static void dispatchEvent(DebugNetEvent event) {
        if (event != null) {
            DebugNetMonitor.dispatch(event);
        }
    }
}
