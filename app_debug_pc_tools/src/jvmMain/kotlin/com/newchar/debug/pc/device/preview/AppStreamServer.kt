package com.newchar.debug.pc.device.preview

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** 接收设备端 MediaCodec 推送的 [magic][length][pts][H264] 二进制流并解码。 */
internal class AppStreamServer(private val port: Int = APP_STREAM_PORT) {
    private val closed = AtomicBoolean(false)
    private val serverSocket = AtomicReference<ServerSocket?>(null)
    private val clientSocket = AtomicReference<Socket?>(null)
    private val decoder = H264FrameDecoder()

    /** 等待设备通过 adb reverse 连接并消费实时 H264 帧。 */
    suspend fun collectFrames(onFrame: (ImageBitmap) -> Unit) = withContext(Dispatchers.IO) {
        closed.set(false)
        val server = ServerSocket(port)
        serverSocket.set(server)
        try {
            while (!closed.get()) {
                val client = server.accept()
                clientSocket.set(client)
                try {
                    receiveClient(client, onFrame)
                } finally {
                    clientSocket.compareAndSet(client, null)
                    client.close()
                }
            }
        } finally {
            clientSocket.getAndSet(null)?.close()
            serverSocket.compareAndSet(server, null)
            server.close()
        }
    }

    /** 停止监听、断开设备连接并解除 FFmpeg 的阻塞读取。 */
    fun close() {
        closed.set(true)
        decoder.close()
        clientSocket.getAndSet(null)?.close()
        serverSocket.getAndSet(null)?.close()
    }

    /** 将有长度边界的 H264 帧写入管道，交由 FFmpeg 连续解码。 */
    private fun receiveClient(client: Socket, onFrame: (ImageBitmap) -> Unit) {
        PipedOutputStream().use { output ->
            PipedInputStream(output, PIPE_BUFFER_SIZE).use { input ->
                val decodeThread = Thread({ decoder.decode(input, onFrame) }, "AppH264Decoder").apply { start() }
                try {
                    DataInputStream(client.getInputStream()).use { source ->
                        while (!closed.get()) {
                            if (source.readInt() != APP_STREAM_MAGIC) break
                            val length = source.readInt()
                            source.readLong() // 时间戳只作协议对齐，FFmpeg 使用自身帧节奏。
                            if (length !in 1..MAX_FRAME_BYTES) break
                            val payload = ByteArray(length)
                            source.readFully(payload)
                            output.write(payload)
                            output.flush()
                        }
                    }
                } finally {
                    output.close()
                    decodeThread.join(DECODE_JOIN_MS)
                }
            }
        }
    }

    private companion object {
        const val PIPE_BUFFER_SIZE = 1024 * 1024
        const val MAX_FRAME_BYTES = 2 * 1024 * 1024
        const val DECODE_JOIN_MS = 1_000L
    }
}

internal const val APP_STREAM_PORT = 6667
internal const val APP_STREAM_MAGIC = 0x4E435348
