package com.newchar.debug.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 解析 TUN 设备中的 IP 包头，只提取展示与过滤需要的可拷贝字段。
 */
final class IpPacketParser {

    private static final int PROTOCOL_ICMP = 1;
    private static final int PROTOCOL_TCP = 6;
    private static final int PROTOCOL_UDP = 17;
    private static final int PROTOCOL_ICMP_V6 = 58;

    private IpPacketParser() {
    }

    static DebugNetEvent parse(byte[] packet, int length, TrafficDirection direction) {
        if (packet == null || length <= 0) {
            return null;
        }
        int version = (packet[0] >> 4) & 0x0F;
        if (version == 4) {
            return parseIpv4(packet, length, direction);
        }
        if (version == 6) {
            return parseIpv6(packet, length, direction);
        }
        DebugNetEvent unknown = new DebugNetEvent(direction, "IP" + version, "unknown", 0, "unknown", 0, length);
        unknown.setRequestPath("/unknown");
        return unknown;
    }

    private static DebugNetEvent parseIpv4(byte[] packet, int length, TrafficDirection direction) {
        if (length < 20) {
            return null;
        }
        int headerLength = (packet[0] & 0x0F) * 4;
        if (headerLength < 20 || length < headerLength) {
            return null;
        }
        int protocol = packet[9] & 0xFF;
        String source = ipv4ToString(packet, 12);
        String destination = ipv4ToString(packet, 16);
        int sourcePort = 0;
        int destinationPort = 0;
        if ((protocol == PROTOCOL_TCP || protocol == PROTOCOL_UDP) && length >= headerLength + 4) {
            sourcePort = readUnsignedShort(packet, headerLength);
            destinationPort = readUnsignedShort(packet, headerLength + 2);
        }
        DebugNetEvent event = new DebugNetEvent(direction, protocolName(protocol), source, sourcePort, destination,
                destinationPort, length);
        // 仅做非常轻量的 path 猜测：HTTP 请求行通常以 "GET /path" 开头，但由于当前没有 TCP 重组，命中率有限。
        if (protocol == PROTOCOL_TCP) {
            String path = tryParseHttpPath(packet, headerLength);
            if (path != null) {
                event.setRequestPath(path);
            }
            if (destinationPort == 443 || sourcePort == 443) {
                event.setHttps(true);
            }
        }
        return event;
    }

    private static DebugNetEvent parseIpv6(byte[] packet, int length, TrafficDirection direction) {
        if (length < 40) {
            return null;
        }
        int protocol = packet[6] & 0xFF;
        String source = ipv6ToString(packet, 8);
        String destination = ipv6ToString(packet, 24);
        int sourcePort = 0;
        int destinationPort = 0;
        if ((protocol == PROTOCOL_TCP || protocol == PROTOCOL_UDP) && length >= 44) {
            sourcePort = readUnsignedShort(packet, 40);
            destinationPort = readUnsignedShort(packet, 42);
        }
        DebugNetEvent event = new DebugNetEvent(direction, protocolName(protocol), source, sourcePort, destination,
                destinationPort, length);
        if (protocol == PROTOCOL_TCP) {
            String path = tryParseHttpPath(packet, 40);
            if (path != null) {
                event.setRequestPath(path);
            }
            if (destinationPort == 443 || sourcePort == 443) {
                event.setHttps(true);
            }
        }
        return event;
    }

    private static String protocolName(int protocol) {
        switch (protocol) {
            case PROTOCOL_ICMP:
                return "ICMP";
            case PROTOCOL_TCP:
                return "TCP";
            case PROTOCOL_UDP:
                return "UDP";
            case PROTOCOL_ICMP_V6:
                return "ICMPv6";
            default:
                return "P" + protocol;
        }
    }

    private static int readUnsignedShort(byte[] packet, int offset) {
        if (offset < 0 || packet.length < offset + 2) {
            return 0;
        }
        return ((packet[offset] & 0xFF) << 8) | (packet[offset + 1] & 0xFF);
    }

    private static String ipv4ToString(byte[] packet, int offset) {
        if (packet.length < offset + 4) {
            return "unknown";
        }
        return (packet[offset] & 0xFF) + "."
                + (packet[offset + 1] & 0xFF) + "."
                + (packet[offset + 2] & 0xFF) + "."
                + (packet[offset + 3] & 0xFF);
    }

    private static String ipv6ToString(byte[] packet, int offset) {
        if (packet.length < offset + 16) {
            return "unknown";
        }
        byte[] address = new byte[16];
        System.arraycopy(packet, offset, address, 0, address.length);
        try {
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException ignored) {
            return "unknown";
        }
    }

    private static String tryParseHttpPath(byte[] packet, int transportHeaderOffset) {
        if (packet == null) {
            return null;
        }
        int tcpHeaderMinOffset = transportHeaderOffset + 20;
        if (packet.length < tcpHeaderMinOffset) {
            return null;
        }
        int dataOffset = ((packet[transportHeaderOffset + 12] >> 4) & 0x0F) * 4;
        if (dataOffset < 20) {
            return null;
        }
        int payloadOffset = transportHeaderOffset + dataOffset;
        if (payloadOffset < 0 || payloadOffset >= packet.length) {
            return null;
        }
        int limit = Math.min(packet.length, payloadOffset + 256);
        // Request line: METHOD SP PATH SP HTTP/1.1
        int methodEnd = indexOfByte(packet, payloadOffset, limit, (byte) ' ');
        if (methodEnd <= payloadOffset) {
            return null;
        }
        // 支持常见方法
        int methodLen = methodEnd - payloadOffset;
        if (!(methodLen == 3 || methodLen == 4 || methodLen == 5 || methodLen == 6 || methodLen == 7)) {
            return null;
        }
        int pathStart = methodEnd + 1;
        if (pathStart >= limit) {
            return null;
        }
        int pathEnd = indexOfByte(packet, pathStart, limit, (byte) ' ');
        if (pathEnd <= pathStart) {
            return null;
        }
        // 必须以 '/' 开头，避免误判
        if (packet[pathStart] != (byte) '/') {
            return null;
        }
        // HTTP request line is ASCII-compatible.
        return new String(packet, pathStart, pathEnd - pathStart, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static int indexOfByte(byte[] data, int start, int end, byte target) {
        for (int i = start; i < end; i++) {
            if (data[i] == target) {
                return i;
            }
        }
        return -1;
    }
}
