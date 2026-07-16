package com.newchar.debug.net;

/**
 * 解析 DNS 查询包，提取查询域名与类型。
 * 仅解析 Query 段；响应包本轮不处理（未实现转发栈，远端响应不会回到 TUN）。
 */
final class DnsPacketParser {

    private static final int TYPE_A = 1;
    private static final int TYPE_NS = 2;
    private static final int TYPE_CNAME = 5;
    private static final int TYPE_AAAA = 28;
    private static final int TYPE_MX = 15;
    private static final int TYPE_TXT = 16;
    private static final int TYPE_PTR = 12;
    private static final int TYPE_SVCB = 64;
    private static final int TYPE_HTTPS = 65;

    private DnsPacketParser() {
    }

    /**
     * @param payload DNS 报文负载（UDP 之后的字节）
     * @param raw     原始包元数据，用于构建事件
     * @return DNS 事件；非 DNS 或解析失败返回 null
     */
    static DebugNetEvent parse(byte[] payload, RawPacket raw) {
        if (payload == null || payload.length < 12) {
            return null;
        }
        int flags = ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
        boolean isResponse = (flags & 0x8000) != 0;
        int qdCount = ((payload[4] & 0xFF) << 8) | (payload[5] & 0xFF);
        if (qdCount == 0) {
            return null;
        }
        int offset = 12;
        StringBuilder name = new StringBuilder();
        int[] nameEnd = new int[1];
        if (!readName(payload, offset, name, nameEnd)) {
            return null;
        }
        offset = nameEnd[0];
        if (offset + 4 > payload.length) {
            return null;
        }
        int qType = ((payload[offset] & 0xFF) << 8) | (payload[offset + 1] & 0xFF);
        String domain = name.toString();
        if (domain.isEmpty()) {
            return null;
        }
        DebugNetEvent event = new DebugNetEvent(raw.getDirection(), "DNS",
                raw.getSourceAddress(), raw.getSourcePort(),
                raw.getDestinationAddress(), raw.getDestinationPort(),
                raw.getTotalLength());
        event.setHost(domain);
        event.setRequestPath("/DNS");
        event.setMethod(isResponse ? "DNS-R" : "DNS-Q");
        String type = typeName(qType);
        event.setSummaryText(buildDnsSummary(isResponse, domain, type));
        event.setDisplayText(buildDnsDisplay(isResponse, domain, type, raw));
        return event;
    }

    private static String buildDnsSummary(boolean isResponse, String domain, String type) {
        return (isResponse ? "DNS-R " : "DNS-Q ") + domain + ' ' + type;
    }

    private static String buildDnsDisplay(boolean isResponse, String domain, String type, RawPacket raw) {
        return buildDnsSummary(isResponse, domain, type)
                + " | " + (isResponse ? "DOWN" : "UP")
                + ' ' + raw.getDestinationAddress() + ':' + raw.getDestinationPort();
    }

    private static boolean readName(byte[] data, int offset, StringBuilder out, int[] endOut) {
        int pos = offset;
        int jumped = 0;
        int safety = 0;
        while (pos < data.length && safety++ < 128) {
            int len = data[pos] & 0xFF;
            if (len == 0) {
                pos++;
                endOut[0] = pos;
                return true;
            }
            if ((len & 0xC0) == 0xC0) {
                // 指针压缩：本轮 Query 段通常不含指针，遇到则终止
                endOut[0] = pos + 2;
                return out.length() > 0;
            }
            pos++;
            if (pos + len > data.length) {
                return false;
            }
            if (out.length() > 0) {
                out.append('.');
            }
            for (int i = 0; i < len; i++) {
                out.append((char) (data[pos + i] & 0xFF));
            }
            pos += len;
            jumped = pos;
        }
        return false;
    }

    private static String typeName(int type) {
        switch (type) {
            case TYPE_A:
                return "A";
            case TYPE_AAAA:
                return "AAAA";
            case TYPE_NS:
                return "NS";
            case TYPE_CNAME:
                return "CNAME";
            case TYPE_MX:
                return "MX";
            case TYPE_TXT:
                return "TXT";
            case TYPE_PTR:
                return "PTR";
            case TYPE_SVCB:
                return "SVCB";
            case TYPE_HTTPS:
                return "HTTPS";
            default:
                return "TYPE" + type;
        }
    }
}
