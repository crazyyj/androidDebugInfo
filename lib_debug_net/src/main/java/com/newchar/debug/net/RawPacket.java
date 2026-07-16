package com.newchar.debug.net;

/**
 * 从 TUN 读出的单个 IP 包解析结果，仅承载展示与重组需要的可拷贝字段。
 * 不持有原始字节数组引用，负载通过 offset/length 在原始 packet 上读取。
 */
final class RawPacket {

    static final int PROTOCOL_ICMP = 1;
    static final int PROTOCOL_TCP = 6;
    static final int PROTOCOL_UDP = 17;
    static final int PROTOCOL_ICMP_V6 = 58;

    private final int version;
    private final int protocol;
    private final String sourceAddress;
    private final int sourcePort;
    private final String destinationAddress;
    private final int destinationPort;
    private final long tcpSequence;
    private final int tcpFlags;
    private final int payloadOffset;
    private final int payloadLength;
    private final int totalLength;
    private final TrafficDirection direction;

    RawPacket(int version, int protocol, String sourceAddress, int sourcePort,
            String destinationAddress, int destinationPort, long tcpSequence, int tcpFlags,
            int payloadOffset, int payloadLength, int totalLength, TrafficDirection direction) {
        this.version = version;
        this.protocol = protocol;
        this.sourceAddress = sourceAddress;
        this.sourcePort = sourcePort;
        this.destinationAddress = destinationAddress;
        this.destinationPort = destinationPort;
        this.tcpSequence = tcpSequence;
        this.tcpFlags = tcpFlags;
        this.payloadOffset = payloadOffset;
        this.payloadLength = payloadLength;
        this.totalLength = totalLength;
        this.direction = direction;
    }

    int getVersion() {
        return version;
    }

    int getProtocol() {
        return protocol;
    }

    String getSourceAddress() {
        return sourceAddress;
    }

    int getSourcePort() {
        return sourcePort;
    }

    String getDestinationAddress() {
        return destinationAddress;
    }

    int getDestinationPort() {
        return destinationPort;
    }

    long getTcpSequence() {
        return tcpSequence;
    }

    int getTcpFlags() {
        return tcpFlags;
    }

    int getPayloadOffset() {
        return payloadOffset;
    }

    int getPayloadLength() {
        return payloadLength;
    }

    int getTotalLength() {
        return totalLength;
    }

    TrafficDirection getDirection() {
        return direction;
    }

    boolean hasPayload() {
        return payloadOffset > 0 && payloadLength > 0;
    }
}
