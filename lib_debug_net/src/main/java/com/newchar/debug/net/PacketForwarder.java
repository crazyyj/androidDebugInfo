package com.newchar.debug.net;

import android.net.VpnService;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * VPN 流量转发器。捕获 TUN 中的 IP 包，转发到真实网络，并将响应写回 TUN。
 *
 * 核心机制：
 * 1. UDP（含 DNS）：DatagramSocket 发请求 → 真实网络 → 构造 IP+UDP 响应包写回 TUN
 * 2. TCP：先处理 SYN/SYN-ACK 握手（写回 TUN），然后用 protect() 排除的 Socket 代理转发 payload
 *
 * 注意：转发用的 Socket 必须用 VpnService.protect() 排除出 VPN，否则会被重定向回 TUN 造成死循环。
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

    private final ConcurrentHashMap<String, TcpProxySession> tcpSessions = new ConcurrentHashMap<>();

    PacketForwarder(VpnService vpnService) {
        this.vpnService = vpnService;
    }

    /**
     * 转发 UDP 包到真实网络，并将响应写回 TUN。
     */
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
            // 关键：用 protect() 排除出 VPN，否则被重定向回 TUN 造成死循环
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
            // 返回 DNS 事件（如果这是 DNS 流量）
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
     * 处理 TCP 包。区分握手阶段和 payload 阶段。
     */
    DebugNetEvent handleTcp(RawPacket raw, byte[] packet) {
        int flags = raw.getTcpFlags();
        String key = raw.getSourceAddress() + ':' + raw.getSourcePort() + '|'
                + raw.getDestinationAddress() + ':' + raw.getDestinationPort();

        if ((flags & IpPacketParser.TCP_RST) != 0) {
            tcpSessions.remove(key);
            return null;
        }

        // 1. 处理 SYN（无 payload，需要回复 SYN-ACK 建立连接）
        if (!raw.hasPayload() && (flags & IpPacketParser.TCP_SYN) != 0 && (flags & IpPacketParser.TCP_ACK) == 0) {
            // 尝试解析 DNS 获取目标主机名（如果有的话）
            // 这里先只记录连接尝试
            DebugNetEvent event = new DebugNetEvent(raw.getDirection(), "TCP",
                    raw.getSourceAddress(), raw.getSourcePort(),
                    raw.getDestinationAddress(), raw.getDestinationPort(),
                    raw.getTotalLength());
            event.setSummaryText("[SYN] " + raw.getDestinationAddress() + ':' + raw.getDestinationPort());
            event.setDisplayText("[SYN] " + raw.getDestinationAddress() + ':' + raw.getDestinationPort());
            VpnServiceHolder.dispatchEvent(event);

            // 回复 SYN-ACK（写回 TUN）
            writeSynAck(raw.getSourceAddress(), raw.getSourcePort(),
                    raw.getDestinationAddress(), raw.getDestinationPort(), raw.getTcpSequence());
            return event;
        }

        // 2. 处理 ACK（客户端确认 SYN-ACK 的连接确认包）
        if (!raw.hasPayload() && (flags & IpPacketParser.TCP_ACK) != 0 && (flags & IpPacketParser.TCP_SYN) == 0) {
            // 连接建立成功，创建会话
            TcpProxySession session = tcpSessions.get(key);
            if (session == null) {
                session = new TcpProxySession(raw.getSourceAddress(), raw.getSourcePort(),
                        raw.getDestinationAddress(), raw.getDestinationPort());
                tcpSessions.put(key, session);
            }
            // 派发连接建立事件
            DebugNetEvent event = new DebugNetEvent(raw.getDirection(), "TCP",
                    raw.getSourceAddress(), raw.getSourcePort(),
                    raw.getDestinationAddress(), raw.getDestinationPort(), 0);
            event.setSummaryText("[CONN] " + raw.getDestinationAddress() + ':' + raw.getDestinationPort());
            event.setDisplayText("[CONN] " + raw.getDestinationAddress() + ':' + raw.getDestinationPort());
            VpnServiceHolder.dispatchEvent(event);
            return event;
        }

        // 3. 处理带 payload 的包（HTTP 请求等）
        if (raw.hasPayload()) {
            TcpProxySession session = tcpSessions.get(key);
            if (session == null) {
                session = new TcpProxySession(raw.getSourceAddress(), raw.getSourcePort(),
                        raw.getDestinationAddress(), raw.getDestinationPort());
                tcpSessions.put(key, session);
            }
            return session.forward(raw, packet);
        }

        // 4. 其他情况（FIN 等）
        if ((flags & IpPacketParser.TCP_FIN) != 0) {
            tcpSessions.remove(key);
            return null;
        }

        return null;
    }

    /** 构造并写回 TUN 的 SYN-ACK 包 */
    private void writeSynAck(String srcAddr, int srcPort, String dstAddr, int dstPort, long clientSeq) {
        try {
            InetAddress src = InetAddress.getByName(dstAddr);
            InetAddress dst = InetAddress.getByName("10.88.0.2");
            long serverSeq = 0; // 简化：服务端 seq 从 0 开始
            int tcpFlags = IpPacketParser.TCP_SYN | IpPacketParser.TCP_ACK;
            byte[] packet = PacketForwarder.buildIpv4TcpPacket(
                    src.getAddress(), dst.getAddress(),
                    dstPort, srcPort, serverSeq, clientSeq + 1,
                    new byte[0], 0, 0, tcpFlags);
            if (packet != null) {
                VpnServiceHolder.writeToTun(packet);
            }
        } catch (Throwable t) {
            // 构造失败忽略
        }
    }

    void shutdown() {
        for (TcpProxySession s : tcpSessions.values()) {
            s.close();
        }
        tcpSessions.clear();
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

    /** 构造 TCP 包（IP+TCP+payload） */
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

    /** 单次 TCP 代理会话。用 protect() 排除的 Socket 连接真实服务器。 */
    private final class TcpProxySession {
        private final String srcAddr;
        private final int srcPort;
        private final String dstAddr;
        private final int dstPort;
        private volatile Socket socket;
        private volatile boolean closed;
        private long clientSeq;

        TcpProxySession(String srcAddr, int srcPort, String dstAddr, int dstPort) {
            this.srcAddr = srcAddr;
            this.srcPort = srcPort;
            this.dstAddr = dstAddr;
            this.dstPort = dstPort;
        }

        DebugNetEvent forward(RawPacket raw, byte[] packet) {
            if (closed) {
                return null;
            }
            clientSeq = raw.getTcpSequence();
            byte[] payload = extractPayload(raw, packet);
            if (payload == null || payload.length == 0) {
                return null;
            }
            executor.execute(() -> {
                try {
                    Socket s = socket;
                    if (s == null) {
                        try {
                            s = new Socket();
                            // 关键：用 protect() 排除出 VPN，否则被重定向回 TUN 造成死循环
                            if (vpnService != null) {
                                vpnService.protect(s);
                            }
                            s.setSoTimeout(RESPONSE_TIMEOUT_MS);
                            s.connect(new java.net.InetSocketAddress(dstAddr, dstPort), RESPONSE_TIMEOUT_MS);
                            socket = s;
                        } catch (Exception e) {
                            // 连接失败（DNS 解析失败、端口不可达等）
                            return;
                        }
                    }
                    if (s.isClosed() || !s.isConnected()) {
                        socket = null;
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
                        return;
                    }
                    byte[] response = new byte[bytesRead];
                    System.arraycopy(respBuffer, 0, response, 0, bytesRead);
                    // 构造响应包（服务器 → 10.88.0.2）
                    InetAddress respSrc = InetAddress.getByName(dstAddr);
                    InetAddress respDst = InetAddress.getByName("10.88.0.2");
                    long respSeq = clientSeq + payload.length;
                    long respAck = clientSeq + payload.length;
                    int tcpFlags = IpPacketParser.TCP_ACK;
                    if (bytesRead > 0) {
                        tcpFlags |= IpPacketParser.TCP_PSH;
                    }
                    byte[] responsePacket = buildIpv4TcpPacket(
                            respSrc.getAddress(), respDst.getAddress(),
                            dstPort, srcPort, respSeq, respAck,
                            response, 0, bytesRead, tcpFlags);
                    if (responsePacket != null) {
                        VpnServiceHolder.writeToTun(responsePacket);
                    }
                    // 派发响应事件给监控
                    DebugNetEvent event = new DebugNetEvent(
                            TrafficDirection.DOWNLOAD,
                            "TCP",
                            dstAddr, dstPort, "10.88.0.2", srcPort,
                            responsePacket != null ? responsePacket.length : bytesRead);
                    event.setSummaryText("[FWD] " + dstAddr + ':' + dstPort + ' ' + bytesRead + 'B');
                    event.setDisplayText("[FWD] " + dstAddr + ':' + dstPort + ' ' + bytesRead + 'B');
                    VpnServiceHolder.dispatchEvent(event);
                } catch (IOException e) {
                    // 连接失败或已关闭
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
            // 同步返回一个占位事件用于监控回显
            DebugNetEvent event = new DebugNetEvent(raw.getDirection(), "TCP",
                    srcAddr, srcPort, dstAddr, dstPort, payload.length);
            event.setSummaryText("[REQ] " + dstAddr + ':' + dstPort + ' ' + payload.length + 'B');
            event.setDisplayText("[REQ] " + dstAddr + ':' + dstPort + ' ' + payload.length + 'B');
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
