package com.newchar.debug.net;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将一条已重组完整的 HTTP 请求字节流解析为结构化字段。
 * 仅处理明文 HTTP；HTTPS 流量不会被解码（调用方应先判断端口）。
 */
final class HttpRequestDecoder {

    private HttpRequestDecoder() {
    }

    /**
     * 解析结果。当输入不是合法 HTTP 请求时各字段为空。
     */
    static final class Result {
        String method = "";
        String path = "";
        String version = "";
        final Map<String, String> headers = new LinkedHashMap<>();
        String host = "";
        String contentType = "";
        int contentLength = -1;
        String bodyText = "";
        boolean valid;
    }

    /**
     * @param data            重组后的请求字节（可能包含 header + body）
     * @param length          有效长度
     * @param decodeBody      是否解析 body 文本（受 httpDecodeEnabled 控制）
     * @param maxPayloadBytes body 文本截断上限
     */
    static Result decode(byte[] data, int length, boolean decodeBody, int maxPayloadBytes) {
        Result result = new Result();
        if (data == null || length <= 0) {
            return result;
        }
        int headerEnd = indexOf(data, length, HEADER_END);
        if (headerEnd < 0) {
            // 头部尚未完整
            return result;
        }
        int headerLength = headerEnd; // 不含 \r\n\r\n
        String headerText = new String(data, 0, headerLength, StandardCharsets.ISO_8859_1);
        String[] lines = headerText.split("\r\n");
        if (lines.length == 0) {
            return result;
        }
        String[] requestLine = splitFirst(lines[0], ' ');
        if (requestLine.length < 3) {
            return result;
        }
        result.method = requestLine[0];
        result.path = requestLine[1];
        result.version = requestLine[2];
        if (result.method.isEmpty() || result.path.isEmpty()) {
            return result;
        }
        // 常见方法名长度校验，避免误判二进制为 HTTP
        if (!isLikelyMethod(result.method)) {
            return result;
        }
        result.valid = true;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (name.isEmpty()) {
                continue;
            }
            result.headers.put(name, value);
            String lower = name.toLowerCase();
            if ("host".equals(lower)) {
                result.host = value;
            } else if ("content-type".equals(lower)) {
                result.contentType = value;
            } else if ("content-length".equals(lower)) {
                try {
                    result.contentLength = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (decodeBody) {
            int bodyOffset = headerEnd + HEADER_END.length;
            int bodyLength = Math.max(0, length - bodyOffset);
            int limit = maxPayloadBytes > 0 ? Math.min(bodyLength, maxPayloadBytes) : bodyLength;
            if (limit > 0) {
                result.bodyText = new String(data, bodyOffset, limit, StandardCharsets.UTF_8);
            }
        }
        return result;
    }

    static String headersToText(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        return builder.toString();
    }

    private static boolean isLikelyMethod(String method) {
        switch (method) {
            case "GET":
            case "POST":
            case "PUT":
            case "DELETE":
            case "HEAD":
            case "OPTIONS":
            case "PATCH":
            case "TRACE":
            case "CONNECT":
                return true;
            default:
                return false;
        }
    }

    private static String[] splitFirst(String value, char delimiter) {
        int idx = value.indexOf(delimiter);
        if (idx < 0) {
            return new String[]{value};
        }
        return new String[]{value.substring(0, idx), value.substring(idx + 1)};
    }

    private static int indexOf(byte[] data, int length, byte[] pattern) {
        int limit = length - pattern.length;
        outer:
        for (int i = 0; i <= limit; i++) {
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
}
