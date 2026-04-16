package com.newchar.deviceview.devices;

/**
 * @author newChar
 * date 2023/5/25
 * @since 获取fps值，得知UI卡顿问题
 * @since 迭代版本，（以及描述）
 */
interface IFPSProvider {

    void getFps(onFpsFreshListener l);

    interface onFpsFreshListener {
        void onFresh(float fps);
    }
}
