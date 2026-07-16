package com.newchar.debug.net;

import android.net.VpnService;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * VPN 流量转发器。只关注实际数据传输（忽略 TCP 握手细节）。
 *
 * 工作流：
 * 1. UDP：转发到真实网络，响应写回 TUN，输出 DNS 事件
 * 2. TCP（带 payload）：通过 protect() Socket 代理转发
 *    - 成功：服务器响应写回 TUN，输出 [OK] 事件
 *    - 失败：连接失败/超时，输出 [FAIL] 事件
 */
final class PacketForwarder {

    private static final int RESPONSE_TIMEOUT_MS = 8000;
    private static final int UDP_BUFFER_SIZE = 4096;
    private static final int TCP_BUFFER_SIZE = 4096;

    private final VpnService vpnService;
    private final ExecutorService executor = Executors.newCachedThreadPool(
            r -> {
                Thread t = new Thread(r, "PacketForwarder");
                t.setDaemon(true);
                return t;
            });

    private final ConcurrentHashMap<String, HttpForwardSession> sessions = new ConcurrentHashMap<>();

    PacketForwarder(VpnService vpnService) {
        this.vpnService = vpnService;
    }

    /** 转发 UDP 包到真实网络，并将响应写回 TUN。 */
    DebugNetEvent forwardUdp(RawPacket raw, byte[] packet) {
        byte[] payload = extractPayload(raw, packet);
        if (payload == null || payload.length == 0) {
            return null;
        }
        InetAddress destAddr;
        try {
            destAddr = InetAddress.getByName(raw.getDestinationAddress());
        } catch (UnknownHostException e) {
            return null;
        }
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            if (vpnService != null) {
                vpnService.protect(socket);
            }
            socket.setSoTimeout(RESPONSE_TIMEOUT_MS);
            DatagramPacket outPacket = new DatagramPacket(payload, payload.length, destAddr, raw.getDestinationPort());
            socket.send(outPacket);
            DatagramPacket inPacket = new DatagramPacket(new byte[UDP_BUFFER_SIZE], UDP_BUFFER_SIZE);
            socket.receive(inPacket);
            InetAddress respSrc = inPacket.getAddress();
            int respSrcPort = inPacket.getPort();
            InetAddress respDst = InetAddress.getByName("10.88.0.2");
            int respDstPort = raw.getDestinationPort();
            byte[] responsePacket = buildUdpResponsePacket(respSrc, respSrcPort, respDst, respDstPort,
                    inPacket.getData(), 0, inPacket.getLength());
            if (responsePacket != null) {
                VpnServiceHolder.writeToTun(responsePacket);
            }
            // DNS 事件
            if (raw.getDestinationPort() == 53 || raw.getSourcePort() == 53) {
                return DnsPacketParser.parse(payload, raw);
            }
            return null;
        } catch (SocketTimeoutException e) {
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    /**
     * 处理 TCP 包。只处理带 payload 的数据包（忽略纯握手包）。
     */
    DebugNetEvent handleTcp(RawPacket raw, byte[] packet) {
        // 只处理带 payload 的数据包
        if (!raw.hasPayload()) {
            return null;
        }
        String key = raw.getSourceAddress() + ':' + raw.getSourcePort() + '|'
                + raw.getDestinationAddress() + ':' + raw.getDestinationPort();
        HttpForwardSession session = sessions.get(key);
        if (session == null) {
            session = new HttpForwardSession(raw.getSourceAddress(), raw.getSourcePort(),
                    raw.getDestinationAddress(), raw.getDestinationPort());
            sessions.put(key, session);
        }
        return session.forward(raw, packet);
    }

    void shutdown() {
        for (HttpForwardSession s : sessions.values()) {
            s.close();
        }
        sessions.clear();
        executor.shutdownNow();
    }

    private static byte[] extractPayload(RawPacket raw, byte[] packet) {
        int offset = raw.getPayloadOffset();
        int length = raw.getPayloadLength();
        if (offset < 0 || length <= 0 || offset + length > packet.length) {
            return null;
        }
        byte[] out = new byte[length];
        System.arraycopy(packet, offset, out, 0, length);
        return out;
    }

    /** 构造 UDP 响应包（IP+UDP+payload） */
    private static byte[] buildUdpResponsePacket(InetAddress src, int srcPort,
            InetAddress dst, int dstPort, byte[] payload, int offset, int length) {
        try {
            byte[] srcBytes = src.getAddress();
            byte[] dstBytes = dst.getAddress();
            boolean ipv6 = srcBytes.length == 16;
            if (ipv6) {
                return buildIpv6UdpPacket(srcBytes, dstBytes, srcPort, dstPort, payload, offset, length);
            }
            return buildIpv4UdpPacket(srcBytes, dstBytes, srcPort, dstPort, payload, offset, length);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] buildIpv4UdpPacket(byte[] srcBytes, byte[] dstBytes,
            int srcPort, int dstPort, byte[] payload, int offset, int length) {
        int ipHdrLen = 20;
        int udpHdrLen = 8;
        int totalLen = ipHdrLen + udpHdrLen + length;
        byte[] packet = new byte[totalLen];
        packet[0] = (byte) 0x45;
        packet[1] = 0x00;
        short totalLenShort = (short) totalLen;
        packet[2] = (byte) (totalLenShort >> 8);
        packet[3] = (byte) (totalLenShort & 0xFF);
        packet[4] = (byte) 0x00;
        packet[5] = (byte) 0x00;
        packet[6] = (byte) 0x40;
        packet[7] = (byte) 0x00;
        packet[8] = 0x40;
        packet[9] = 0x11;
        System.arraycopy(srcBytes, 0, packet, 12, 4);
        System.arraycopy(dstBytes, 0, packet, 16, 4);
        int udpOffset = ipHdrLen;
        packet[udpOffset] = (byte) (srcPort >> 8);
        packet[udpOffset + 1] = (byte) (srcPort & 0xFF);
        packet[udpOffset + 2] = (byte) (dstPort >> 8);
        packet[udpOffset + 3] = (byte) (dstPort & 0xFF);
        int udpLen = udpHdrLen + length;
        packet[udpOffset + 4] = (byte) (udpLen >> 8);
        packet[udpOffset + 5] = (byte) (udpLen & 0xFF);
        packet[udpOffset + 6] = 0x00;
        packet[udpOffset + 7] = 0x00;
        if (length > 0) {
            System.arraycopy(payload, offset, packet, udpHdrLen + ipHdrLen, length);
        }
        computeIpChecksum(packet, ipHdrLen);
        return packet;
    }

    private static byte[] buildIpv6UdpPacket(byte[] srcBytes, byte[] dstBytes,
            int srcPort, int dstPort, byte[] payload, int offset, int length) {
        int ipHdrLen = 40;
        int udpHdrLen = 8;
        int totalLen = ipHdrLen + udpHdrLen + length;
        byte[] packet = new byte[totalLen];
        packet[0] = (byte) 0x60;
        packet[1] = 0;
        packet[2] = (byte) ((udpHdrLen + length) >> 8);
        packet[3] = (byte) (udpHdrLen + length);
        packet[4] = 0;
        packet[5] = 0;
        packet[6] = 0x11;
        packet[7] = 0x40;
        System.arraycopy(srcBytes, 0, packet, 8, 16);
        System.arraycopy(dstBytes, 0, packet, 24, 16);
        int udpOffset = ipHdrLen;
        packet[udpOffset] = (byte) (srcPort >> 8);
        packet[udpOffset + 1] = (byte) (srcPort & 0xFF);
        packet[udpOffset + 2] = (byte) (dstPort >> 8);
        packet[udpOffset + 3] = (byte) (dstPort & 0xFF);
        int udpLen = udpHdrLen + length;
        packet[udpOffset + 4] = (byte) (udpLen >> 8);
        packet[udpOffset + 5] = (byte) (udpLen & 0xFF);
        packet[udpOffset + 6] = 0x00;
        packet[udpOffset + 7] = 0x00;
        if (length > 0) {
            System.arraycopy(payload, offset, packet, udpHdrLen + ipHdrLen, length);
        }
        return packet;
    }

    private static void computeIpChecksum(byte[] header, int headerLength) {
        int sum = 0;
        for (int i = 0; i < headerLength - 2; i += 2) {
            sum += (header[i] & 0xFF) << 8 | (header[i + 1] & 0xFF);
        }
        sum = (sum >>> 16) + (sum & 0xFFFF);
        sum += sum >>> 16;
        short chk = (short) ~sum;
        header[10] = (byte) (chk & 0xFF);
        header[11] = (byte) ((chk >> 8) & 0xFF);
    }

    /** 构造 TCP 响应包（IP+TCP+payload），用于将服务器响应写回 TUN。 */
    static byte[] buildIpv4TcpPacket(byte[] srcBytes, byte[] dstBytes,
            int srcPort, int dstPort, long tcpSeq, long tcpAck,
            byte[] payload, int offset, int length, int tcpFlags) {
        int ipHdrLen = 20;
        int tcpHdrLen = 20;
        int totalLen = ipHdrLen + tcpHdrLen + length;
        byte[] packet = new byte[totalLen];
        packet[0] = (byte) 0x45;
        packet[1] = 0x00;
        short totalLenShort = (short) totalLen;
        packet[2] = (byte) (totalLenShort >> 8);
        packet[3] = (byte) (totalLenShort & 0xFF);
        packet[4] = (byte) 0x00;
        packet[5] = (byte) 0x00;
        packet[6] = (byte) 0x40;
        packet[7] = (byte) 0x00;
        packet[8] = 0x40;
        packet[9] = 0x06;
        System.arraycopy(srcBytes, 0, packet, 12, 4);
        System.arraycopy(dstBytes, 0, packet, 16, 4);
        int tcpOffset = ipHdrLen;
        packet[tcpOffset] = (byte) (srcPort >> 8);
        packet[tcpOffset + 1] = (byte) (srcPort & 0xFF);
        packet[tcpOffset + 2] = (byte) (dstPort >> 8);
        packet[tcpOffset + 3] = (byte) (dstPort & 0xFF);
        packet[tcpOffset + 4] = (byte) ((tcpSeq >> 24) & 0xFF);
        packet[tcpOffset + 5] = (byte) ((tcpSeq >> 16) & 0xFF);
        packet[tcpOffset + 6] = (byte) ((tcpSeq >> 8) & 0xFF);
        packet[tcpOffset + 7] = (byte) (tcpSeq & 0xFF);
        packet[tcpOffset + 8] = (byte) ((tcpAck >> 24) & 0xFF);
        packet[tcpOffset + 9] = (byte) ((tcpAck >> 16) & 0xFF);
        packet[tcpOffset + 10] = (byte) ((tcpAck >> 8) & 0xFF);
        packet[tcpOffset + 11] = (byte) (tcpAck & 0xFF);
        packet[tcpOffset + 12] = (byte) 0x50;
        packet[tcpOffset + 13] = (byte) tcpFlags;
        packet[tcpOffset + 14] = (byte) 0x40;
        packet[tcpOffset + 15] = (byte) 0x00;
        packet[tcpOffset + 16] = 0x00;
        packet[tcpOffset + 17] = 0x00;
        packet[tcpOffset + 18] = 0x00;
        packet[tcpOffset + 19] = 0x00;
        if (length > 0) {
            System.arraycopy(payload, offset, packet, tcpHdrLen + ipHdrLen, length);
        }
        computeIpChecksum(packet, ipHdrLen);
        computeTcpChecksum(packet, ipHdrLen, tcpHdrLen, length, srcBytes, dstBytes);
        return packet;
    }

    private static void computeTcpChecksum(byte[] packet, int ipHdrLen,
            int tcpHdrLen, int payloadLen, byte[] srcIp, byte[] dstIp) {
        int sum = 0;
        int tcpStart = ipHdrLen;
        int tcpEnd = ipHdrLen + tcpHdrLen + payloadLen;
        for (int i = tcpStart; i < tcpEnd; i += 2) {
            int word;
            if (i + 1 < tcpEnd) {
                word = (packet[i] & 0xFF) << 8 | (packet[i + 1] & 0xFF);
            } else {
                word = (packet[i] & 0xFF) << 8;
            }
            sum += word;
        }
        sum += (srcIp[0] & 0xFF) << 8 | (srcIp[1] & 0xFF);
        sum += (srcIp[2] & 0xFF) << 8 | (srcIp[3] & 0xFF);
        sum += (dstIp[0] & 0xFF) << 8 | (dstIp[1] & 0xFF);
        sum += (dstIp[2] & 0xFF) << 8 | (dstIp[3] & 0xFF);
        sum += 0x0006;
        sum += tcpHdrLen + payloadLen;
        sum = (sum >>> 16) + (sum & 0xFFFF);
        sum += sum >>> 16;
        short chk = (short) ~sum;
        int checksumOffset = tcpStart + 16;
        packet[checksumOffset] = (byte) (chk & 0xFF);
        packet[checksumOffset + 1] = (byte) ((chk >> 8) & 0xFF);
    }

    /** 单次 HTTP 数据转发会话。 */
    private final class HttpForwardSession {
        private final String srcAddr;
        private final int srcPort;
        private final String dstAddr;
        private final int dstPort;
        private volatile Socket socket;
        private volatile boolean closed;

        HttpForwardSession(String srcAddr, int srcPort, String dstAddr, int dstPort) {
            this.srcAddr = srcAddr;
            this.srcPort = srcPort;
            this.dstAddr = dstAddr;
            this.dstPort = dstPort;
        }

        DebugNetEvent forward(RawPacket raw, byte[] packet) {
            if (closed) {
                return null;
            }
            byte[] payload = extractPayload(raw, packet);
            if (payload == null || payload.length == 0) {
                return null;
            }
            // 同步返回一个数据事件（不等待服务器响应）
            DebugNetEvent event = new DebugNetEvent(raw.getDirection(), "TCP",
                    srcAddr, srcPort, dstAddr, dstPort, payload.length);
            event.setSummaryText("[DATA] " + dstAddr + ':' + dstPort + ' ' + payload.length + 'B');
            event.setDisplayText("[DATA] " + dstAddr + ':' + dstPort + ' ' + payload.length + 'B');
            VpnServiceHolder.dispatchEvent(event);

            // 异步转发到服务器
            executor.execute(() -> {
                try {
                    Socket s = socket;
                    if (s == null) {
                        try {
                            s = new Socket();
                            if (vpnService != null) {
                                vpnService.protect(s);
                            }
                            s.setSoTimeout(RESPONSE_TIMEOUT_MS);
                            s.connect(new java.net.InetSocketAddress(dstAddr, dstPort), RESPONSE_TIMEOUT_MS);
                            socket = s;
                        } catch (Exception e) {
                            // 连接失败
                            DebugNetEvent failEvent = new DebugNetEvent(
                                    TrafficDirection.DOWNLOAD, "TCP",
                                    dstAddr, dstPort, srcAddr, srcPort, 0);
                            failEvent.setSummaryText("[FAIL] 连接失败: " + dstAddr + ':' + dstPort);
                            failEvent.setDisplayText("[FAIL] 连接失败: " + dstAddr + ':' + dstPort);
                            VpnServiceHolder.dispatchEvent(failEvent);
                            return;
                        }
                    }
                    if (s.isClosed() || !s.isConnected()) {
                        socket = null;
                        // 连接断开
                        DebugNetEvent failEvent = new DebugNetEvent(
                                TrafficDirection.DOWNLOAD, "TCP",
                                dstAddr, dstPort, srcAddr, srcPort, 0);
                        failEvent.setSummaryText("[FAIL] 连接已断开: " + dstAddr + ':' + dstPort);
                        failEvent.setDisplayText("[FAIL] 连接已断开: " + dstAddr + ':' + dstPort);
                        VpnServiceHolder.dispatchEvent(failEvent);
                        return;
                    }
                    // 发送 payload 到服务器
                    java.io.OutputStream out = s.getOutputStream();
                    out.write(payload);
                    out.flush();
                    // 读取服务器响应
                    byte[] respBuffer = new byte[TCP_BUFFER_SIZE];
                    java.io.InputStream in = s.getInputStream();
                    int bytesRead = in.read(respBuffer);
                    if (bytesRead <= 0) {
                        // 超时或无响应
                        DebugNetEvent failEvent = new DebugNetEvent(
                                TrafficDirection.DOWNLOAD, "TCP",
                                dstAddr, dstPort, srcAddr, srcPort, 0);
                        failEvent.setSummaryText("[FAIL] 无响应: " + dstAddr + ':' + dstPort);
                        failEvent.setDisplayText("[FAIL] 无响应: " + dstAddr + ':' + dstPort);
                        VpnServiceHolder.dispatchEvent(failEvent);
                        return;
                    }
                    // 成功：构造响应包写回 TUN
                    byte[] response = new byte[bytesRead];
                    System.arraycopy(respBuffer, 0, response, 0, bytesRead);
                    InetAddress respSrc = InetAddress.getByName(dstAddr);
                    InetAddress respDst = InetAddress.getByName("10.88.0.2");
                    byte[] responsePacket = PacketForwarder.buildIpv4TcpPacket(
                            respSrc.getAddress(), respDst.getAddress(),
                            dstPort, srcPort, 0, payload.length,
                            response, 0, bytesRead,
                            IpPacketParser.TCP_ACK | IpPacketParser.TCP_PSH);
                    if (responsePacket != null) {
                        VpnServiceHolder.writeToTun(responsePacket);
                    }
                    // 成功事件
                    DebugNetEvent okEvent = new DebugNetEvent(
                            TrafficDirection.DOWNLOAD, "TCP",
                            dstAddr, dstPort, "10.88.0.2", srcPort, bytesRead);
                    okEvent.setSummaryText("[OK] " + bytesRead + 'B' + " from " + dstAddr + ':' + dstPort);
                    okEvent.setDisplayText("[OK] " + bytesRead + 'B' + " from " + dstAddr + ':' + dstPort);
                    VpnServiceHolder.dispatchEvent(okEvent);
                } catch (IOException e) {
                    // 连接失败或已关闭
                    DebugNetEvent failEvent = new DebugNetEvent(
                            TrafficDirection.DOWNLOAD, "TCP",
                            dstAddr, dstPort, srcAddr, srcPort, 0);
                    failEvent.setSummaryText("[FAIL] " + e.getClass().getSimpleName());
                    failEvent.setDisplayText("[FAIL] " + e.getClass().getSimpleName());
                    VpnServiceHolder.dispatchEvent(failEvent);
                } finally {
                    if (socket != null && !socket.isClosed()) {
                        try {
                            socket.close();
                        } catch (IOException ignored) {
                        }
                        socket = null;
                    }
                }
            });

            return event;
        }

        void close() {
            closed = true;
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                socket = null;
            }
        }
    }
}
