package com.newchar.debug.pc.device.preview

import com.newchar.debug.pc.device.stream.StreamKind
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.Base64

internal const val STREAM_PROTOCOL_VERSION = 2
internal const val STREAM_TRANSPORT_REVERSE = 1
internal const val STREAM_TRANSPORT_DIRECT = 2

/** PC 等待设备端发送的 v2 首包内容。 */
internal data class ExpectedStreamHello(
    val kind: StreamKind,
    val token: ByteArray,
)

/** 生成仅存于内存、用于单个流会话鉴权的随机 token。 */
internal fun createStreamToken(): ByteArray = ByteArray(16).also(SecureRandom()::nextBytes)

/** 将 token 转成可通过 `adb shell am` extra 传递的 URL-safe 文本。 */
internal fun encodeStreamToken(token: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(token)

/** 按默认端口优先、递增端口回退、系统分配兜底的顺序创建收流 listener。 */
internal fun openStreamServer(preferredPort: Int): ServerSocket {
    val candidates = (0..7).map { preferredPort + it }
    candidates.forEach { port ->
        runCatching { return bindStreamServer(port) }
    }
    return bindStreamServer(0)
}

/** 绑定到所有网卡，使 reverse 与 LAN direct 都能连接同一 listener。 */
private fun bindStreamServer(port: Int): ServerSocket = ServerSocket().apply {
    reuseAddress = true
    bind(java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), port))
}

/** 将二进制协议中的流类别数值映射为共享状态模型。 */
internal fun Int.toStreamKind(): StreamKind? = when (this) {
    1 -> StreamKind.SCREEN
    2 -> StreamKind.CAMERA
    else -> null
}

/** 将协议传输字段转换成内部状态，未知值由握手校验拒绝。 */
internal fun Int.toStreamTransportKind(): com.newchar.debug.pc.device.stream.StreamTransportKind? = when (this) {
    STREAM_TRANSPORT_REVERSE -> com.newchar.debug.pc.device.stream.StreamTransportKind.REVERSE
    STREAM_TRANSPORT_DIRECT -> com.newchar.debug.pc.device.stream.StreamTransportKind.DIRECT
    else -> null
}