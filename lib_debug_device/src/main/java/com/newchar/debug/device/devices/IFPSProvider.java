package com.newchar.debug.device.devices;

/**
 * @author newChar
 * date 2023/5/25
 * @since 获取fps值，得知UI卡顿问题
 * @since 迭代版本，（以及描述）
 */
public interface IFPSProvider {

    void getFps(FpsFreshListener l);

    interface FpsFreshListener {
        void onFresh(float fps);
    }
}
