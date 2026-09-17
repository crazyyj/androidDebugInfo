package com.newchar.debug.pc.device

/**
 * 设备连接状态的状态模式抽象。
 *
 * 状态只描述连接能力与目标选择，不直接执行 ADB 命令；命令执行仍由上层注入的
 * [com.newchar.debug.pc.executor.CommandExecutor] 完成，避免 commonMain 依赖 JVM 实现。
 */
sealed class DeviceConnectionState {

    /** 当前连接不可用时可切换的备用连接状态。 */
    abstract val nextState: DeviceConnectionState?

    /** 面向用户展示的连接方式名称。 */
    abstract val displayName: String

    /** 当前状态是否允许执行设备管理类 ADB 操作。 */
    abstract val canManageDevice: Boolean

    /** 根据设备快照判断当前是否通过无线 ADB 通信。 */
    abstract fun isWireless(device: DeviceConnectionSnapshot): Boolean

    /** 根据设备快照返回应传给 ADB -s 的目标。 */
    abstract fun adbTarget(device: DeviceConnectionSnapshot): String

    /** 根据设备快照判断是否可以发起连接或重连。 */
    abstract fun canReconnect(device: DeviceConnectionSnapshot): Boolean

    /** 根据一次新的设备观测结果迁移状态；当前连接失效时优先启用可用的备用连接。 */
    fun transitionTo(observation: DeviceConnectionSnapshot): DeviceConnectionState {
        val observedState = from(observation)
        return nextState?.takeIf { !observedState.canManageDevice && it.canManageDevice }
            ?: observedState.withNextState(nextState)
    }

    /** USB ADB 已授权且在线。 */
    data class AdbConnected(
        override val nextState: DeviceConnectionState? = null,
    ) : DeviceConnectionState() {
        override val displayName: String = "USB ADB"
        override val canManageDevice: Boolean = true

        override fun isWireless(device: DeviceConnectionSnapshot): Boolean = false

        override fun adbTarget(device: DeviceConnectionSnapshot): String = device.usbSerial.ifBlank { device.id }

        override fun canReconnect(device: DeviceConnectionSnapshot): Boolean = false

        override fun withNextState(nextState: DeviceConnectionState?): DeviceConnectionState = copy(nextState = nextState)
    }

    /** Wi-Fi/TCP ADB 已授权且在线。 */
    data class WifiConnected(
        override val nextState: DeviceConnectionState? = null,
    ) : DeviceConnectionState() {
        override val displayName: String = "Wi-Fi ADB"
        override val canManageDevice: Boolean = true

        override fun isWireless(device: DeviceConnectionSnapshot): Boolean = true

        override fun adbTarget(device: DeviceConnectionSnapshot): String = device.wirelessEndpoint.ifBlank { device.id }

        override fun canReconnect(device: DeviceConnectionSnapshot): Boolean = false

        override fun withNextState(nextState: DeviceConnectionState?): DeviceConnectionState = copy(nextState = nextState)
    }

    /** ADB 已发现设备，但尚未获得设备授权。 */
    data class Unauthorized(
        override val nextState: DeviceConnectionState? = null,
    ) : DeviceConnectionState() {
        override val displayName: String = "未授权"
        override val canManageDevice: Boolean = false

        override fun isWireless(device: DeviceConnectionSnapshot): Boolean = device.id.contains(':')

        override fun adbTarget(device: DeviceConnectionSnapshot): String = device.wirelessEndpoint.ifBlank { device.id }

        override fun canReconnect(device: DeviceConnectionSnapshot): Boolean = device.id.contains(':') ||
            device.wirelessEndpoint.isNotBlank()

        override fun withNextState(nextState: DeviceConnectionState?): DeviceConnectionState = copy(nextState = nextState)
    }

    /** 未连接、离线、恢复模式或用户手动断开的状态。 */
    data class Disconnected(
        val reason: DeviceDisconnectReason,
        override val nextState: DeviceConnectionState? = null,
    ) : DeviceConnectionState() {
        override val displayName: String = reason.displayName
        override val canManageDevice: Boolean = false

        override fun isWireless(device: DeviceConnectionSnapshot): Boolean =
            device.id.contains(':') || device.wirelessEndpoint.isNotBlank()

        override fun adbTarget(device: DeviceConnectionSnapshot): String =
            device.wirelessEndpoint.ifBlank { device.id }

        override fun canReconnect(device: DeviceConnectionSnapshot): Boolean =
            device.id.contains(':') || device.wirelessEndpoint.isNotBlank()

        override fun withNextState(nextState: DeviceConnectionState?): DeviceConnectionState = copy(nextState = nextState)
    }

    /** 为当前状态设置备用状态，避免状态对象在切换时共享可变引用。 */
    abstract fun withNextState(nextState: DeviceConnectionState?): DeviceConnectionState

    companion object {
        /** 将 ADB 原始观测数据转换为对应的连接状态。 */
        fun from(observation: DeviceConnectionSnapshot): DeviceConnectionState = when {
            observation.isManuallyDisconnected -> Disconnected(DeviceDisconnectReason.MANUALLY_DISCONNECTED)
            observation.isRetainedOffline -> Disconnected(DeviceDisconnectReason.RETAINED_OFFLINE)
            observation.status == STATUS_DEVICE && observation.id.contains(':') -> WifiConnected()
            observation.status == STATUS_DEVICE -> AdbConnected()
            observation.status == STATUS_UNAUTHORIZED -> Unauthorized()
            else -> Disconnected(DeviceDisconnectReason.fromAdbStatus(observation.status))
        }
    }
}

/** 连接状态迁移所需的、与设备描述字段解耦的最小快照。 */
data class DeviceConnectionSnapshot(
    val id: String,
    val usbSerial: String,
    val status: String,
    val wirelessEndpoint: String,
    val isRetainedOffline: Boolean,
    val isManuallyDisconnected: Boolean,
)

/** 未连接状态的具体原因。 */
enum class DeviceDisconnectReason(val displayName: String) {
    MANUALLY_DISCONNECTED("已手动断开"),
    RETAINED_OFFLINE("等待无线重连"),
    OFFLINE("离线"),
    RECOVERY("恢复模式"),
    SIDELOAD("Sideload 模式"),
    AUTHORIZING("正在授权"),
    UNKNOWN("未连接");

    companion object {
        /** 将 ADB 原始状态映射为未连接原因。 */
        fun fromAdbStatus(status: String): DeviceDisconnectReason = when (status) {
            STATUS_OFFLINE -> OFFLINE
            STATUS_RECOVERY -> RECOVERY
            STATUS_SIDELOAD -> SIDELOAD
            STATUS_AUTHORIZING -> AUTHORIZING
            else -> UNKNOWN
        }
    }
}

internal const val STATUS_DEVICE = "device"
internal const val STATUS_UNAUTHORIZED = "unauthorized"
internal const val STATUS_OFFLINE = "offline"
internal const val STATUS_RECOVERY = "recovery"
internal const val STATUS_SIDELOAD = "sideload"
internal const val STATUS_AUTHORIZING = "authorizing"
