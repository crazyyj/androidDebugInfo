package com.newchar.deviceview.devices;

import java.util.List;

/**
 * @author newChar
 * date 2023/2/12
 * @since 获取CPU信息的消息类。
 * @since 迭代版本，（以及描述）
 */
public interface ICPUProvider {

    int CPU_STATUS_ONLINE = 1;
    int CPU_STATUS_OFFLINE = 0;

    /**
     * 获取cpu的在线的索引集合
     *
     * @return 全部CPU的在线索引集合
     */
    List<Integer> getAllCPUOnlineStatus();

    /**
     * 系统支持的CPU核心数。
     *
     * @return 核心cpu索引
     */
    List<Integer> getDevicesSupportCPUCoreNum();

    /*
     *（写入无权限，待后续调研）
     * 设置cpu的在线离线状态
     *
     * @param index  第几个
     * @param status {#CPU_STATUS_ONLINE} 在线，0 离线
     * @return 是否符合预期(在线修改为在线状态，会返回true)
     */
//    boolean setCPUOnlineStatus(int index, int status);

    /**
     * 获取cpu的最高 频率
     *
     * @param index 第几个cpu
     * @return cpu的频率
     */
    String getCPUMaxRate(int index);

    /**
     * 获取cpu的 最低频率
     *
     * @param index 第几个cpu
     * @return cpu的频率
     */
    String getCPUMinRate(int index);

    /**
     * 获取cpu的 当前频率
     *
     * @param index 第几个cpu
     * @return cpu的频率
     */
    String getCPUCurRate(int index);

    /**
     * 获取App进程的使用率
     *
     * @param pid 进程id，一般传入 android.so.Process#myPid(); 获取当前应用进程
     * @return 当前应用的使用率
     */
    String getCPUUsage(int pid);

    float getCpuUsage(int pid);

}
