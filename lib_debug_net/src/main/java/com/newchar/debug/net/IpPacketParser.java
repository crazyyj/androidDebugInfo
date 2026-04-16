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
        return new DebugNetEvent(direction, "IP" + version, "unknown", 0, "unknown", 0, length);
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
        return new DebugNetEvent(direction, protocolName(protocol), source, sourcePort, destination, destinationPort,
                length);
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
        return new DebugNetEvent(direction, protocolName(protocol), source, sourcePort, destination, destinationPort,
                length);
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
}
