package com.newchar.debug.net;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条 TCP 会话的负载重组缓冲。
 * 仅处理上行（App 发出的）HTTP 请求重组；下行响应本轮不支持。
 * （因为未实现转发栈，远端响应不会回到 TUN。）
 */
final class TcpSession {

    private static final long IDLE_TIMEOUT_MS = 30_000L;
    private static final int HEADER_END_LIMIT = 64 * 1024;

    private final String sourceAddress;
    private final int sourcePort;
    private final String destinationAddress;
    private final int destinationPort;
    private final long createdAtMs = System.currentTimeMillis();
    private long lastUpdatedAtMs = createdAtMs;

    /** 按 seq 升序排布的负载分片，便于乱序到达时的拼接。 */
    private final List<Frame> frames = new ArrayList<>();
    private long expectedSeq = -1L;
    private int bufferedLength = 0;
    private boolean headerComplete = false;
    private int headerEndOffset = -1;
    private boolean flushed = false;

    TcpSession(String sourceAddress, int sourcePort, String destinationAddress, int destinationPort) {
        this.sourceAddress = sourceAddress;
        this.sourcePort = sourcePort;
        this.destinationAddress = destinationAddress;
        this.destinationPort = destinationPort;
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

    boolean isExpired(long nowMs) {
        return nowMs - lastUpdatedAtMs > IDLE_TIMEOUT_MS;
    }

    long getLastUpdatedAtMs() {
        return lastUpdatedAtMs;
    }

    /**
     * 喂入一个 TCP 负载分片。返回 true 表示该会话应被 flush（header 完整且可解析）。
     */
    boolean feed(long sequence, byte[] payload, int payloadLength, int tcpFlags) {
        lastUpdatedAtMs = System.currentTimeMillis();
        if (payloadLength > 0 && payload != null) {
            if (expectedSeq < 0) {
                expectedSeq = sequence;
            }
            if (sequence >= expectedSeq) {
                frames.add(new Frame(sequence, payload, payloadLength));
                bufferedLength += payloadLength;
                scanHeaderEnd();
            }
        }
        boolean fin = (tcpFlags & IpPacketParser.TCP_FIN) != 0;
        boolean rst = (tcpFlags & IpPacketParser.TCP_RST) != 0;
        return rst || (headerComplete && bufferedLength >= headerEndOffset + 4);
    }

    private void scanHeaderEnd() {
        if (headerComplete) {
            return;
        }
        byte[] combined = combinedBytes();
        if (combined == null) {
            return;
        }
        int scanLimit = Math.min(combined.length, HEADER_END_LIMIT);
        int idx = indexOf(combined, scanLimit, HEADER_END);
        if (idx >= 0) {
            headerComplete = true;
            headerEndOffset = idx;
        }
    }

    /**
     * 仅当检测到完整 HTTP 头时返回非 null；返回完整重组字节（含 body）。
     */
    byte[] combinedBytes() {
        if (frames.isEmpty()) {
            return null;
        }
        // 按 seq 排序拼接
        frames.sort((a, b) -> Long.compare(a.sequence, b.sequence));
        byte[] out = new byte[bufferedLength];
        int offset = 0;
        for (Frame frame : frames) {
            System.arraycopy(frame.data, 0, out, offset, frame.length);
            offset += frame.length;
        }
        return out;
    }

    boolean isFlushed() {
        return flushed;
    }

    void markFlushed() {
        flushed = true;
    }

    private static int indexOf(byte[] data, int limit, byte[] pattern) {
        int end = Math.min(limit, data.length) - pattern.length;
        outer:
        for (int i = 0; i <= end; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static final byte[] HEADER_END = new byte[]{'\r', '\n', '\r', '\n'};

    private static final class Frame {
        final long sequence;
        final byte[] data;
        final int length;

        Frame(long sequence, byte[] payload, int length) {
            this.sequence = sequence;
            this.data = payload;
            this.length = length;
        }
    }
}
