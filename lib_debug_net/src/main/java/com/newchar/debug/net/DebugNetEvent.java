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
    private String displayText;
    private int textColor;

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
        this.textColor = direction == TrafficDirection.DOWNLOAD ? Color.rgb(0, 96, 160) : Color.rgb(80, 80, 80);
        this.displayText = buildDefaultDisplayText();
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

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        if (displayText != null && !displayText.isEmpty()) {
            this.displayText = displayText;
        }
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    private String buildDefaultDisplayText() {
        StringBuilder builder = new StringBuilder();
        SimpleDateFormat format = TIME_FORMAT.get();
        builder.append(format == null ? String.valueOf(timeMillis) : format.format(new Date(timeMillis)));
        builder.append(' ');
        builder.append(direction == TrafficDirection.DOWNLOAD ? "DOWN" : "UP");
        builder.append(' ');
        builder.append(protocol);
        builder.append(' ');
        builder.append(formatEndpoint(sourceAddress, sourcePort));
        builder.append(" -> ");
        builder.append(formatEndpoint(destinationAddress, destinationPort));
        builder.append(' ');
        builder.append(byteCount);
        builder.append('B');
        return builder.toString();
    }

    private static String formatEndpoint(String address, int port) {
        if (port <= 0) {
            return address == null ? "" : address;
        }
        return (address == null ? "" : address) + ':' + port;
    }
}
