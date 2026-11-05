package com.firetv.streamdeck.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Client TCP Socket a basso livello per la comunicazione con il demone ADB
 * in esecuzione sulla porta 5555 di Amazon Fire TV Stick.
 */
class AdbTcpClient(
    private val host: String,
    private val port: Int = 5555,
    private val timeoutMs: Int = 3000
) {

    /**
     * Esegue un probe di connessione TCP per verificare lo stato e la latenza.
     */
    suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            val latency = System.currentTimeMillis() - startTime
            Result.success(latency)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Invia un comando shell via socket ADB TCP/IP.
     * Implementa la sequenza di handshake (CNXN -> OPEN shell:<cmd> -> OKAY -> CLSE).
     */
    suspend fun executeShellCommand(shellCommand: String): Result<String> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(host, port), timeoutMs)

            val outputStream: OutputStream = socket.getOutputStream()
            val inputStream: InputStream = socket.getInputStream()

            // 1. Invio pacchetto CNXN (Connect)
            val systemId = "host::FireTvDeck\u0000".toByteArray(Charsets.US_ASCII)
            val cnxnPacket = createAdbPacket("CNXN", 0x01000000, 4096, systemId)
            outputStream.write(cnxnPacket)
            outputStream.flush()

            // 2. Lettura risposta CNXN da Fire TV
            val headerBuffer = ByteArray(24)
            readFully(inputStream, headerBuffer)

            // 3. Invio comando shell (OPEN shell:<cmd>)
            val cmdPayload = "shell:$shellCommand\u0000".toByteArray(Charsets.US_ASCII)
            val openPacket = createAdbPacket("OPEN", 1, 0, cmdPayload)
            outputStream.write(openPacket)
            outputStream.flush()

            // 4. Lettura output (OKAY e WRTE con buffer)
            val responseBuilder = StringBuilder()
            val dataBuffer = ByteArray(1024)
            var bytesRead: Int

            try {
                while (inputStream.read(dataBuffer).also { bytesRead = it } != -1) {
                    if (bytesRead >= 24) {
                        val cmdTag = String(dataBuffer, 0, 4, Charsets.US_ASCII)
                        if (cmdTag == "WRTE" && bytesRead > 24) {
                            responseBuilder.append(String(dataBuffer, 24, bytesRead - 24, Charsets.UTF_8))
                            // Invia conferma OKAY
                            val okayPacket = createAdbPacket("OKAY", 1, 1, ByteArray(0))
                            outputStream.write(okayPacket)
                            outputStream.flush()
                        } else if (cmdTag == "CLSE") {
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // Timeout di lettura atteso se lo stream di output termina
            }

            Result.success(responseBuilder.toString().ifBlank { "Comando inviato con successo" })
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Costruisce il frame binario di un pacchetto ADB secondo le specifiche del protocollo TCP:
     * - command (4 byte ASCII: CNXN, OPEN, OKAY, CLSE, WRTE)
     * - arg0 (uint32 LE)
     * - arg1 (uint32 LE)
     * - data_length (uint32 LE)
     * - data_crc32 (uint32 LE)
     * - magic (uint32 LE = command XOR 0xFFFFFFFF)
     * - payload data
     */
    private fun createAdbPacket(cmd: String, arg0: Int, arg1: Int, payload: ByteArray): ByteArray {
        val cmdBytes = cmd.toByteArray(Charsets.US_ASCII)
        var crc = 0L
        for (b in payload) {
            crc = (crc + (b.toInt() and 0xFF)) and 0xFFFFFFFFL
        }

        val magic = ByteBuffer.wrap(cmdBytes).order(ByteOrder.LITTLE_ENDIAN).int xor -1

        val buffer = ByteBuffer.allocate(24 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(cmdBytes)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(payload.size)
        buffer.putInt(crc.toInt())
        buffer.putInt(magic)
        buffer.put(payload)

        return buffer.array()
    }

    private fun readFully(inputStream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = inputStream.read(buffer, offset, buffer.size - offset)
            if (count == -1) break
            offset += count
        }
    }
}
