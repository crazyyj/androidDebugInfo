package com.newchar.debug.pc.device.preview

import androidx.compose.ui.graphics.ImageBitmap
import com.newchar.debug.pc.device.stream.StreamKind
import com.newchar.debug.pc.device.stream.StreamTransportKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

/** 接收相机 H264；仅接受持有当前会话 token 的 v2 连接。 */
internal class CameraStreamServer(private val preferredPort: Int = CAMERA_STREAM_PORT) {
    private val log = Logger.getLogger(CameraStreamServer::class.java.name)
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val serverSocket = AtomicReference<ServerSocket?>(null)
    private val clientSocket = AtomicReference<Socket?>(null)
    private val decoder = H264FrameDecoder()
    private var expectedHello: ExpectedStreamHello? = null

    /** 绑定 listener 并返回实际端口，端口冲突时自动选择备用端口。 */
    fun start(expected: ExpectedStreamHello? = null): Int {
        expectedHello = expected
        if (!started.compareAndSet(false, true)) return serverSocket.get()?.localPort ?: 0
        val server = runCatching { openStreamServer(preferredPort) }.getOrElse { throwable ->
            started.set(false)
            throw IllegalStateException("相机流监听失败: ${throwable.message}", throwable)
        }
        serverSocket.set(server)
        return server.localPort
    }

    /** 等待设备经 reverse 或 LAN direct 连接并消费实时 H264 帧。 */
    suspend fun collectFrames(
        onFrame: (ImageBitmap) -> Unit,
        onError: (String) -> Unit = {},
        onConnected: () -> Unit = {},
        onRotationChanged: (Int) -> Unit = {},
        onTransportChanged: (StreamTransportKind) -> Unit = {},
        onFrameReceived: () -> Unit = {},
        onDisconnected: (StreamTransportKind) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val server = serverSocket.get() ?: throw IllegalStateException("相机流服务尚未启动")
        closed.set(false)
        try {
            while (!closed.get()) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                clientSocket.getAndSet(client)?.close()
                try {
                    val transport = runCatching {
                        receiveClient(client, onFrame, onError, onConnected, onRotationChanged, onTransportChanged, onFrameReceived)
                    }.getOrNull()
                    if (transport != null && !closed.get()) onDisconnected(transport)
                } finally {
                    clientSocket.compareAndSet(client, null)
                    client.close()
                }
            }
        } finally {
            clientSocket.getAndSet(null)?.close()
        }
    }

    /** 停止 listener、设备连接与解码器。 */
    fun close() {
        closed.set(true)
        started.set(false)
        decoder.close()
        clientSocket.getAndSet(null)?.close()
        serverSocket.getAndSet(null)?.close()
    }

    /** 完成 v2 hello 校验后将帧交给解码线程。 */
    private fun receiveClient(
        client: Socket,
        onFrame: (ImageBitmap) -> Unit,
        onError: (String) -> Unit,
        onConnected: () -> Unit,
        onRotationChanged: (Int) -> Unit,
        onTransportChanged: (StreamTransportKind) -> Unit,
        onFrameReceived: () -> Unit,
    ): StreamTransportKind? {
        client.soTimeout = HELLO_TIMEOUT_MS
        DataInputStream(client.getInputStream()).use { source ->
            val first = readHelloOrFrame(source) ?: return null
            client.soTimeout = 0
            onConnected()
            onTransportChanged(first.transport)
            decodeFrames(source, first, onFrame, onError, onRotationChanged, onFrameReceived)
            return first.transport
        }
    }

    /** 读取 v2 token hello；旧版裸帧协议一律拒绝。 */
    private fun readHelloOrFrame(source: DataInputStream): FirstFrame? {
        val magic = source.readInt()
        if (magic != CAMERA_STREAM_MAGIC) return null
        val second = source.readUnsignedShort()
        if (second != STREAM_PROTOCOL_VERSION) return null
        val kind = source.readUnsignedByte().toStreamKind() ?: return null
        val transport = source.readUnsignedByte()
        val token = ByteArray(source.readUnsignedShort())
        source.readFully(token)
        val expected = expectedHello ?: return null
        val transportKind = transport.toStreamTransportKind() ?: return null
        return if (kind == StreamKind.CAMERA && token.contentEquals(expected.token)) {
            FirstFrame(0, -1, true, transportKind)
        } else null
    }

    /** 连续读取帧，并把当前方向回调给 Compose 层。 */
    private fun decodeFrames(
        source: DataInputStream,
        first: FirstFrame,
        onFrame: (ImageBitmap) -> Unit,
        onError: (String) -> Unit,
        onRotationChanged: (Int) -> Unit,
        onFrameReceived: () -> Unit,
    ) {
        PipedOutputStream().use { output ->
            PipedInputStream(output, PIPE_BUFFER_SIZE).use { input ->
                val thread = Thread({ decoder.decode(input, onFrame, onError) }, "CameraH264Decoder").apply { start() }
                try {
                    var rotation = -1
                    while (!closed.get()) {
                        // v2 hello 后每轮都从当前帧的 magic、length 开始读取。
                        // 不可在上一帧末尾预读下一帧帧头，否则下一轮会把 pts 当作帧头导致流错位。
                        val frame = FirstFrame(source.readInt(), source.readInt(), true, first.transport)
                        val nextRotation = readAndWriteFrame(source, output, frame) ?: break
                        onFrameReceived()
                        if (nextRotation != rotation) {
                            rotation = nextRotation
                            onRotationChanged(rotation)
                        }
                    }
                } catch (error: Exception) {
                    if (!closed.get()) log.warning("相机流接收异常: ${error.message}")
                } finally {
                    output.close()
                    thread.join(DECODE_JOIN_MS)
                }
            }
        }
    }

    /** 读取一帧 v2 payload；非法长度直接断开连接。 */
    private fun readAndWriteFrame(source: DataInputStream, output: PipedOutputStream, frame: FirstFrame): Int? {
        if (frame.magic != CAMERA_STREAM_MAGIC) return null
        source.readLong()
        val rotation = source.readInt().toPreviewRotation()
        if (frame.isV2) source.readInt()
        if (frame.length !in 1..MAX_FRAME_BYTES) return null
        val payload = ByteArray(frame.length)
        source.readFully(payload)
        output.write(payload)
        output.flush()
        return rotation
    }

    private data class FirstFrame(
        val magic: Int,
        val length: Int,
        val isV2: Boolean,
        val transport: StreamTransportKind,
    )

    private companion object {
        const val HELLO_TIMEOUT_MS = 2_000
        const val PIPE_BUFFER_SIZE = 1024 * 1024
        const val MAX_FRAME_BYTES = 2 * 1024 * 1024
        const val DECODE_JOIN_MS = 1_000L
    }
}

internal const val CAMERA_STREAM_PORT = 6668
internal const val CAMERA_STREAM_MAGIC = 0x4E434332
/** 将非标准方向统一降级为自然方向，避免异常协议数据影响 UI。 */
private fun Int.toPreviewRotation(): Int = when (this) {
    0, 90, 180, 270 -> this
    else -> 0
}
