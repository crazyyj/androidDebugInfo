package com.newchar.debug.net;

/**
 * 对已解码的网络数据进行全局后处理。
 */
public interface DebugNetPostProcessor {

    /**
     * 返回处理后的对象；返回 null 时沿用原始 payload。
     */
    DebugNetPayload process(DebugNetPayload payload);
}
