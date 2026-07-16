package com.newchar.debug.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 解析 TUN 设备中的 IP 包头，产出 {@link RawPacket}。
 * 不再直接产出 DebugNetEvent；负载偏移与 TCP 序列号交给上游重组器使用。
 */
final class IpPacketParser {

    /** TCP 标志位（标准 RFC 793 布局） */
    static final int TCP_URG = 0x01;
    static final int TCP_ACK = 0x02;
    static final int TCP_PSH = 0x04;
    static final int TCP_RST = 0x08;
    static final int TCP_SYN = 0x10;
    static final int TCP_FIN = 0x20;

    /** VPN 本地地址，用于判定上下行：源地址等于本地地址视为上行。 */
    static final String VPN_LOCAL_ADDRESS_V4 = "10.88.0.2";

    private IpPacketParser() {
    }

    /**
     * 解析单个 IP 包。返回 null 表示包太短或无法识别。
     *
     * @param packet    原始字节数组，调用方保证有效长度为 length
     * @param length    有效长度
     * @param direction 可选方向提示；为 null 时按源地址自动判定
     */
    static RawPacket parse(byte[] packet, int length, TrafficDirection direction) {
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
        return null;
    }

    private static RawPacket parseIpv4(byte[] packet, int length, TrafficDirection directionHint) {
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
        TrafficDirection direction = resolveDirection(directionHint, source);
        int sourcePort = 0;
        int destinationPort = 0;
        long tcpSeq = 0L;
        int tcpFlags = 0;
        int payloadOffset = 0;
        int payloadLength = 0;
        if ((protocol == RawPacket.PROTOCOL_TCP || protocol == RawPacket.PROTOCOL_UDP) && length >= headerLength + 4) {
            sourcePort = readUnsignedShort(packet, headerLength);
            destinationPort = readUnsignedShort(packet, headerLength + 2);
        }
        if (protocol == RawPacket.PROTOCOL_TCP && length >= headerLength + 20) {
            tcpSeq = readUnsignedInt(packet, headerLength + 4);
            tcpFlags = packet[headerLength + 13] & 0xFF;
            int dataOffset = ((packet[headerLength + 12] >> 4) & 0x0F) * 4;
            int transportHeaderEnd = headerLength + dataOffset;
            if (dataOffset >= 20 && transportHeaderEnd <= length) {
                payloadOffset = transportHeaderEnd;
                payloadLength = length - transportHeaderEnd;
            }
        } else if (protocol == RawPacket.PROTOCOL_UDP && length >= headerLength + 8) {
            int transportHeaderEnd = headerLength + 8;
            payloadOffset = transportHeaderEnd;
            payloadLength = length - transportHeaderEnd;
        }
        return new RawPacket(4, protocol, source, sourcePort, destination, destinationPort,
                tcpSeq, tcpFlags, payloadOffset, payloadLength, length, direction);
    }

    private static RawPacket parseIpv6(byte[] packet, int length, TrafficDirection directionHint) {
        if (length < 40) {
            return null;
        }
        int protocol = packet[6] & 0xFF;
        String source = ipv6ToString(packet, 8);
        String destination = ipv6ToString(packet, 24);
        TrafficDirection direction = resolveDirection(directionHint, source);
        int sourcePort = 0;
        int destinationPort = 0;
        long tcpSeq = 0L;
        int tcpFlags = 0;
        int payloadOffset = 0;
        int payloadLength = 0;
        if ((protocol == RawPacket.PROTOCOL_TCP || protocol == RawPacket.PROTOCOL_UDP) && length >= 44) {
            sourcePort = readUnsignedShort(packet, 40);
            destinationPort = readUnsignedShort(packet, 42);
        }
        if (protocol == RawPacket.PROTOCOL_TCP && length >= 60) {
            tcpSeq = readUnsignedInt(packet, 44);
            tcpFlags = packet[53] & 0xFF;
            int dataOffset = ((packet[52] >> 4) & 0x0F) * 4;
            int transportHeaderEnd = 40 + dataOffset;
            if (dataOffset >= 20 && transportHeaderEnd <= length) {
                payloadOffset = transportHeaderEnd;
                payloadLength = length - transportHeaderEnd;
            }
        } else if (protocol == RawPacket.PROTOCOL_UDP && length >= 48) {
            int transportHeaderEnd = 48;
            payloadOffset = transportHeaderEnd;
            payloadLength = length - transportHeaderEnd;
        }
        return new RawPacket(6, protocol, source, sourcePort, destination, destinationPort,
                tcpSeq, tcpFlags, payloadOffset, payloadLength, length, direction);
    }

    private static TrafficDirection resolveDirection(TrafficDirection hint, String sourceAddress) {
        if (hint != null) {
            return hint;
        }
        return VPN_LOCAL_ADDRESS_V4.equals(sourceAddress) ? TrafficDirection.UPLOAD : TrafficDirection.DOWNLOAD;
    }

    static String protocolName(int protocol) {
        switch (protocol) {
            case RawPacket.PROTOCOL_ICMP:
                return "ICMP";
            case RawPacket.PROTOCOL_TCP:
                return "TCP";
            case RawPacket.PROTOCOL_UDP:
                return "UDP";
            case RawPacket.PROTOCOL_ICMP_V6:
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

    private static long readUnsignedInt(byte[] packet, int offset) {
        if (offset < 0 || packet.length < offset + 4) {
            return 0L;
        }
        return ((long) (packet[offset] & 0xFF) << 24)
                | ((long) (packet[offset + 1] & 0xFF) << 16)
                | ((long) (packet[offset + 2] & 0xFF) << 8)
                | ((long) (packet[offset + 3] & 0xFF));
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
