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

/** 接收屏幕 H264 流；仅接受完成 token 握手的 v2 会话。 */
internal class AppStreamServer(private val preferredPort: Int = APP_STREAM_PORT) {
    private val closed = AtomicBoolean(false)
    private val serverSocket = AtomicReference<ServerSocket?>(null)
    private val clientSocket = AtomicReference<Socket?>(null)
    private val decoder = H264FrameDecoder()
    private var expectedHello: ExpectedStreamHello? = null

    /** 启动 listener 并返回实际端口，调用方须在下发 ADB 配置前调用。 */
    fun start(expected: ExpectedStreamHello? = null): Int {
        expectedHello = expected
        return serverSocket.get()?.localPort ?: openStreamServer(preferredPort).also(serverSocket::set).localPort
    }

    /** 等待设备通过 reverse 或 LAN direct 建连并持续解码。 */
    suspend fun collectFrames(
        onFrame: (ImageBitmap) -> Unit,
        onTransportChanged: (StreamTransportKind) -> Unit = {},
        onFrameReceived: () -> Unit = {},
        onDisconnected: (StreamTransportKind) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        closed.set(false)
        val server = serverSocket.get() ?: run { start(); serverSocket.get() ?: return@withContext }
        try {
            while (!closed.get()) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                clientSocket.getAndSet(client)?.close()
                try {
                    val transport = runCatching {
                        receiveClient(client, onFrame, onTransportChanged, onFrameReceived)
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
        decoder.close()
        clientSocket.getAndSet(null)?.close()
        serverSocket.getAndSet(null)?.close()
    }

    /** 验证首包并将合法 H264 帧连续写入 FFmpeg 解码管道。 */
    private fun receiveClient(
        client: Socket,
        onFrame: (ImageBitmap) -> Unit,
        onTransportChanged: (StreamTransportKind) -> Unit,
        onFrameReceived: () -> Unit,
    ): StreamTransportKind? {
        client.soTimeout = HELLO_TIMEOUT_MS
        DataInputStream(client.getInputStream()).use { source ->
            val firstFrame = readHelloOrFrame(source) ?: return null
            client.soTimeout = 0
            onTransportChanged(firstFrame.transport)
            decodeFrames(source, firstFrame, onFrame, onFrameReceived)
            return firstFrame.transport
        }
    }

    /** 读取并验证 v2 hello；非 v2 首包一律关闭，避免旧协议绕过会话鉴权。 */
    private fun readHelloOrFrame(source: DataInputStream): FirstFrame? {
        if (source.readInt() != APP_STREAM_MAGIC) return null
        val second = source.readUnsignedShort()
        if (second != STREAM_PROTOCOL_VERSION) return null
        val kind = source.readUnsignedByte().toStreamKind() ?: return null
        val transport = source.readUnsignedByte()
        val token = ByteArray(source.readUnsignedShort())
        source.readFully(token)
        val expected = expectedHello ?: return null
        val transportKind = transport.toStreamTransportKind() ?: return null
        return if (kind == StreamKind.SCREEN && token.contentEquals(expected.token)) {
            FirstFrame(FRAME_AFTER_HELLO, true, transportKind)
        } else null
    }

    /** 从第一帧开始将 H264 payload 输入连续解码器。 */
    private fun decodeFrames(
        source: DataInputStream,
        firstFrame: FirstFrame,
        onFrame: (ImageBitmap) -> Unit,
        onFrameReceived: () -> Unit,
    ) {
        PipedOutputStream().use { output ->
            PipedInputStream(output, PIPE_BUFFER_SIZE).use { input ->
                val thread = Thread({ decoder.decode(input, onFrame) }, "AppH264Decoder").apply { start() }
                try {
                    var frame = firstFrame
                    while (!closed.get()) {
                        if (frame.length == FRAME_AFTER_HELLO) frame = FirstFrame(source.readInt(), true, frame.transport)
                        if (!readAndWriteFrame(source, output, frame)) break
                        onFrameReceived()
                        if (source.readInt() != APP_STREAM_MAGIC) break
                        frame = FirstFrame(source.readInt(), frame.isV2, frame.transport)
                    }
                } finally {
                    output.close()
                    thread.join(DECODE_JOIN_MS)
                }
            }
        }
    }

    /** 读取一帧 v2 数据；rotation 与 flags 对屏幕解码仅作协议对齐。 */
    private fun readAndWriteFrame(source: DataInputStream, output: PipedOutputStream, frame: FirstFrame): Boolean {
        source.readLong()
        if (frame.isV2) {
            source.readInt()
            source.readInt()
        }
        if (frame.length !in 1..MAX_FRAME_BYTES) return false
        val payload = ByteArray(frame.length)
        source.readFully(payload)
        output.write(payload)
        output.flush()
        return true
    }

    private data class FirstFrame(val length: Int, val isV2: Boolean, val transport: StreamTransportKind)

    private companion object {
        const val HELLO_TIMEOUT_MS = 2_000
        const val FRAME_AFTER_HELLO = -1
        const val PIPE_BUFFER_SIZE = 1024 * 1024
        const val MAX_FRAME_BYTES = 2 * 1024 * 1024
        const val DECODE_JOIN_MS = 1_000L
    }
}

internal const val APP_STREAM_PORT = 6667
internal const val APP_STREAM_MAGIC = 0x4E435348