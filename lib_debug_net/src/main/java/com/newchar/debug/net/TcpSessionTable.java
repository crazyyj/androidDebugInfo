package com.newchar.debug.net;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 维护活跃 TCP 会话，负责把 {@link RawPacket} 路由到对应 {@link TcpSession}，
 * 并在会话可解析时产出 {@link DebugNetEvent}。
 */
final class TcpSessionTable {

    private static final int MAX_SESSIONS = 256;

    private final Map<String, TcpSession> sessions = new LinkedHashMap<>();
    private final DebugNetConfig config;
    private final EventSink sink;

    interface EventSink {
        void onEvent(DebugNetEvent event);
    }

    TcpSessionTable(DebugNetConfig config, EventSink sink) {
        this.config = config;
        this.sink = sink;
    }

    /**
     * 处理一个已解析的 IP 包。packet 为原始字节数组，raw 携带解析出的元数据。
     */
    void handle(RawPacket raw, byte[] packet) {
        if (raw == null) {
            return;
        }
        switch (raw.getProtocol()) {
            case RawPacket.PROTOCOL_TCP:
                handleTcp(raw, packet);
                break;
            case RawPacket.PROTOCOL_UDP:
                handleUdp(raw, packet);
                break;
            default:
                sink.onEvent(buildEndpointEvent(raw));
                break;
        }
        pruneExpired();
    }

    private void handleTcp(RawPacket raw, byte[] packet) {
        byte[] payload = extractPayload(raw, packet);
        int payloadLength = payload == null ? 0 : payload.length;
        String key = sessionKey(raw);
        TcpSession session = sessions.get(key);
        boolean shouldFlush;
        if (session == null || session.isFlushed()) {
            if (sessions.size() >= MAX_SESSIONS) {
                evictOldest();
            }
            session = new TcpSession(raw.getSourceAddress(), raw.getSourcePort(),
                    raw.getDestinationAddress(), raw.getDestinationPort());
            sessions.put(key, session);
            shouldFlush = session.feed(raw.getTcpSequence(), payload, payloadLength, raw.getTcpFlags());
        } else {
            shouldFlush = session.feed(raw.getTcpSequence(), payload, payloadLength, raw.getTcpFlags());
        }
        if (shouldFlush) {
            flushSession(session, raw);
        }
    }

    private void flushSession(TcpSession session, RawPacket raw) {
        if (session.isFlushed()) {
            return;
        }
        byte[] combined = session.combinedBytes();
        DebugNetEvent event = null;
        if (combined != null && combined.length > 0) {
            HttpRequestDecoder.Result result = HttpRequestDecoder.decode(
                    combined, combined.length,
                    config.isHttpDecodeEnabled(), config.getMaxPayloadBytes());
            if (result.valid) {
                event = new DebugNetEvent(raw.getDirection(), "TCP",
                        session.getSourceAddress(), session.getSourcePort(),
                        session.getDestinationAddress(), session.getDestinationPort(),
                        combined.length);
                event.setMethod(result.method);
                event.setRequestPath(result.path);
                event.setHost(result.host);
                event.setContentType(result.contentType);
                event.setRequestHeadersText(HttpRequestDecoder.headersToText(result.headers));
                if (config.isHttpDecodeEnabled()) {
                    event.setRequestBodyText(result.bodyText);
                }
                boolean https = session.getDestinationPort() == 443 || session.getSourcePort() == 443;
                event.setHttps(https);
            }
        }
        if (event == null) {
            // 非 HTTP 的 TCP 会话：仅产出端点摘要
            event = buildEndpointEvent(raw);
            boolean https = session.getDestinationPort() == 443 || session.getSourcePort() == 443;
            event.setHttps(https);
        }
        session.markFlushed();
        sink.onEvent(event);
    }

    private void handleUdp(RawPacket raw, byte[] packet) {
        int dstPort = raw.getDestinationPort();
        int srcPort = raw.getSourcePort();
        if (dstPort == 53 || srcPort == 53) {
            byte[] payload = extractPayload(raw, packet);
            DebugNetEvent dnsEvent = DnsPacketParser.parse(payload, raw);
            if (dnsEvent != null) {
                sink.onEvent(dnsEvent);
                return;
            }
        }
        sink.onEvent(buildEndpointEvent(raw));
    }

    private DebugNetEvent buildEndpointEvent(RawPacket raw) {
        DebugNetEvent event = new DebugNetEvent(raw.getDirection(),
                IpPacketParser.protocolName(raw.getProtocol()),
                raw.getSourceAddress(), raw.getSourcePort(),
                raw.getDestinationAddress(), raw.getDestinationPort(),
                raw.getTotalLength());
        boolean https = raw.getProtocol() == RawPacket.PROTOCOL_TCP
                && (raw.getDestinationPort() == 443 || raw.getSourcePort() == 443);
        event.setHttps(https);
        return event;
    }

    private byte[] extractPayload(RawPacket raw, byte[] packet) {
        if (!raw.hasPayload() || packet == null) {
            return null;
        }
        int offset = raw.getPayloadOffset();
        int length = raw.getPayloadLength();
        if (offset < 0 || length <= 0 || offset + length > packet.length) {
            return null;
        }
        byte[] out = new byte[length];
        System.arraycopy(packet, offset, out, 0, length);
        return out;
    }

    private String sessionKey(RawPacket raw) {
        return raw.getProtocol() + "|"
                + raw.getSourceAddress() + ':' + raw.getSourcePort() + '|'
                + raw.getDestinationAddress() + ':' + raw.getDestinationPort();
    }

    private void pruneExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, TcpSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            TcpSession session = it.next().getValue();
            if (session.isExpired(now)) {
                it.remove();
            }
        }
    }

    private void evictOldest() {
        TcpSession oldest = null;
        String oldestKey = null;
        for (Map.Entry<String, TcpSession> entry : sessions.entrySet()) {
            if (oldest == null || entry.getValue().getLastUpdatedAtMs() < oldest.getLastUpdatedAtMs()) {
                oldest = entry.getValue();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            sessions.remove(oldestKey);
        }
    }

    void clear() {
        sessions.clear();
    }
}
