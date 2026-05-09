package com.newchar.debug.net;

import android.graphics.Color;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 一次网络通信记录，允许 listener 修改展示样式。
 */
public final class DebugNetEvent {

    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        }
    };

    private final long timeMillis;
    private final TrafficDirection direction;
    private final String protocol;
    private final String sourceAddress;
    private final int sourcePort;
    private final String destinationAddress;
    private final int destinationPort;
    private final int byteCount;
    private long requestTimeMillis;
    private int statusCode = -1;
    private boolean https;
    private boolean decrypted;
    private String host = "";
    private String requestPath = "";
    private String requestHeadersText = "";
    private String responseHeadersText = "";
    private String requestBodyText = "";
    private String responseBodyText = "";
    private String failureReason = "";
    private String summaryText;
    private String displayText;
    private int textColor;
    private boolean summaryCustomized;
    private boolean displayCustomized;

    public DebugNetEvent(TrafficDirection direction, String protocol, String sourceAddress, int sourcePort,
            String destinationAddress, int destinationPort, int byteCount) {
        this.timeMillis = System.currentTimeMillis();
        this.direction = direction;
        this.protocol = protocol;
        this.sourceAddress = sourceAddress;
        this.sourcePort = sourcePort;
        this.destinationAddress = destinationAddress;
        this.destinationPort = destinationPort;
        this.byteCount = byteCount;
        this.requestTimeMillis = timeMillis;
        this.host = destinationAddress == null ? "" : destinationAddress;
        this.textColor = direction == TrafficDirection.DOWNLOAD ? Color.rgb(0, 96, 160) : Color.rgb(80, 80, 80);
        refreshTexts();
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public TrafficDirection getDirection() {
        return direction;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public String getDestinationAddress() {
        return destinationAddress;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public int getByteCount() {
        return byteCount;
    }

    public long getRequestTimeMillis() {
        return requestTimeMillis;
    }

    public void setRequestTimeMillis(long requestTimeMillis) {
        if (requestTimeMillis > 0) {
            this.requestTimeMillis = requestTimeMillis;
            refreshTexts();
        }
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
        refreshTexts();
    }

    public boolean isHttps() {
        return https;
    }

    public void setHttps(boolean https) {
        this.https = https;
        refreshTexts();
    }

    public boolean isDecrypted() {
        return decrypted;
    }

    public void setDecrypted(boolean decrypted) {
        this.decrypted = decrypted;
        refreshTexts();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host == null ? "" : host;
        refreshTexts();
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath == null ? "" : requestPath;
        refreshTexts();
    }

    public String getRequestHeadersText() {
        return requestHeadersText;
    }

    public void setRequestHeadersText(String requestHeadersText) {
        this.requestHeadersText = requestHeadersText == null ? "" : requestHeadersText;
    }

    public String getResponseHeadersText() {
        return responseHeadersText;
    }

    public void setResponseHeadersText(String responseHeadersText) {
        this.responseHeadersText = responseHeadersText == null ? "" : responseHeadersText;
    }

    public String getRequestBodyText() {
        return requestBodyText;
    }

    public void setRequestBodyText(String requestBodyText) {
        this.requestBodyText = requestBodyText == null ? "" : requestBodyText;
    }

    public String getResponseBodyText() {
        return responseBodyText;
    }

    public void setResponseBodyText(String responseBodyText) {
        this.responseBodyText = responseBodyText == null ? "" : responseBodyText;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason == null ? "" : failureReason;
        refreshTexts();
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        if (summaryText != null && !summaryText.isEmpty()) {
            this.summaryText = summaryText;
            this.summaryCustomized = true;
        }
    }

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        if (displayText != null && !displayText.isEmpty()) {
            this.displayText = displayText;
            this.displayCustomized = true;
        }
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    public void refreshTexts() {
        if (!summaryCustomized) {
            summaryText = buildSummaryText();
        }
        if (!displayCustomized) {
            displayText = buildDefaultDisplayText();
        }
    }

    private String buildSummaryText() {
        StringBuilder builder = new StringBuilder();
        builder.append(formatTime(requestTimeMillis));
        builder.append(' ');
        if (statusCode > 0) {
            builder.append(statusCode);
        } else if (failureReason != null && !failureReason.isEmpty()) {
            builder.append("ERROR");
        } else {
            builder.append("PENDING");
        }
        builder.append(' ');
        builder.append(resolveSummaryTarget());
        return builder.toString();
    }

    private String buildDefaultDisplayText() {
        StringBuilder builder = new StringBuilder();
        builder.append(summaryText == null ? buildSummaryText() : summaryText);
        builder.append(" | ");
        builder.append(direction == TrafficDirection.DOWNLOAD ? "DOWN" : "UP");
        builder.append(' ');
        builder.append(protocol);
        builder.append(https ? "/HTTPS" : "");
        builder.append(' ');
        builder.append(formatEndpoint(sourceAddress, sourcePort));
        builder.append(" -> ");
        builder.append(formatEndpoint(destinationAddress, destinationPort));
        builder.append(' ');
        builder.append(byteCount);
        builder.append('B');
        if (decrypted) {
            builder.append(" DECRYPTED");
        }
        return builder.toString();
    }

    private String resolveSummaryTarget() {
        if (requestPath != null && !requestPath.isEmpty()) {
            return requestPath;
        }
        if (host != null && !host.isEmpty()) {
            return host;
        }
        if (destinationAddress != null && !destinationAddress.isEmpty()) {
            return formatEndpoint(destinationAddress, destinationPort);
        }
        return formatEndpoint(sourceAddress, sourcePort);
    }

    private static String formatTime(long timestamp) {
        SimpleDateFormat format = TIME_FORMAT.get();
        return format == null ? String.valueOf(timestamp) : format.format(new Date(timestamp));
    }

    private static String formatEndpoint(String address, int port) {
        if (port <= 0) {
            return address == null ? "" : address;
        }
        return (address == null ? "" : address) + ':' + port;
    }
}
