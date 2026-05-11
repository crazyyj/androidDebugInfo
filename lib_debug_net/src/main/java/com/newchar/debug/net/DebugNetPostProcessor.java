package com.newchar.debug.net;

public interface DebugNetPostProcessor {
    DebugNetPayload process(DebugNetPayload payload);
}
