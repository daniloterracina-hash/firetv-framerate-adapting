package com.firetv.streamdeck.data

import com.firetv.streamdeck.network.AdbTcpClient
import kotlinx.coroutines.delay

/**
 * Codici tasto (Keycodes) ufficiali di Android per Amazon Fire TV Stick.
 */
object FireTvKeycodes {
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_BACK = 4
    const val KEYCODE_HOME = 3
    const val KEYCODE_MENU = 82
    const val KEYCODE_PLAY_PAUSE = 85
    const val KEYCODE_REWIND = 89
    const val KEYCODE_FAST_FORWARD = 90
    const val KEYCODE_VOLUME_UP = 24
    const val KEYCODE_VOLUME_DOWN = 25
    const val KEYCODE_VOLUME_MUTE = 164
    const val KEYCODE_POWER = 26
}

enum class VideoPreset4K(
    val label: String,
    val refreshRate: Float,
    val description: String,
    val useCase: String,
    val adbCommand: String
) {
    UHD_24HZ(
        label = "2160p @ 24 Hz",
        refreshRate = 24.0f,
        description = "Cinema e contenuti nativi a 23.976 / 24 fps",
        useCase = "Film e serie TV (Netflix, Prime Video, Disney+, Apple TV+, Plex). Cadenza nativa 1:1, zero telecine judder.",
        adbCommand = "cmd display set-user-preferred-display-mode 3840 2160 24.0"
    ),
    UHD_50HZ(
        label = "2160p @ 50 Hz",
        refreshRate = 50.0f,
        description = "Standard europei, broadcast TV e sport a 25/50 fps",
        useCase = "Streaming TV europeo (DAZN, Sky Go, NOW, Eurosport, BBC, RAI). Multiplo intero esatto (2:1 / 1:1), zero microscatti.",
        adbCommand = "cmd display set-user-preferred-display-mode 3840 2160 50.0"
    ),
    UHD_60HZ(
        label = "2160p @ 60 Hz",
        refreshRate = 60.0f,
        description = "Standard digitali, YouTube e contenuti a 30/60 fps",
        useCase = "YouTube, Twitch, interfaccia nativa Fire OS e video NTSC. Massima fluidità.",
        adbCommand = "cmd display set-user-preferred-display-mode 3840 2160 60.0"
    )
}

class FireTvDeckRepository {

    /**
     * Esegue il test di ping del socket TCP sulla porta 5555.
     */
    suspend fun pingSocket(host: String, port: Int = 5555): Result<Long> {
        val client = AdbTcpClient(host, port)
        return client.testConnection()
    }

    /**
     * Invia un singolo KeyEvent via shell ADB.
     */
    suspend fun sendKey(host: String, port: Int = 5555, keycode: Int): Result<String> {
        val client = AdbTcpClient(host, port)
        return client.executeShellCommand("input keyevent $keycode")
    }

    /**
     * SEZIONE 1: Esecuzione Macro Diagnostica Stream (Overlay Info)
     * Logica con temporizzazione esatta:
     * 1. Invio combinato/stretto di KEYCODE_DPAD_CENTER (23) e KEYCODE_DPAD_DOWN (20)
     * 2. Pausa di transizione di 1.2 secondi (1200ms)
     * 3. Invio immediato del tasto KEYCODE_MENU (82) per aprire il pannello informazioni.
     */
    suspend fun executeStreamInfoMacro(
        host: String,
        port: Int = 5555,
        onProgress: (remainingMs: Long) -> Unit = {}
    ): Result<String> {
        val client = AdbTcpClient(host, port)

        // Passo 1: Invio DPAD_CENTER (23) e DPAD_DOWN (20)
        val step1 = client.executeShellCommand("input keyevent 23 20")
        if (step1.isFailure) {
            // Continua comunque con la sequenza temporizzata per tolleranza di rete
        }

        // Passo 2: Pausa di 1.2 secondi con aggiornamento progressivo
        val totalPauseMs = 1200L
        val intervalMs = 100L
        var elapsed = 0L
        while (elapsed < totalPauseMs) {
            onProgress(totalPauseMs - elapsed)
            delay(intervalMs)
            elapsed += intervalMs
        }
        onProgress(0L)

        // Passo 3: Invio immediato di KEYCODE_MENU (82)
        // Viene inoltre inviato in fallback l'intent nativo SSM Developer Tools nel caso
        // in cui la versione di Fire OS (es. Fire OS 7 o 8) richieda l'activity diretta
        return client.executeShellCommand(
            "input keyevent 82 || am start -n com.amazon.ssm/com.amazon.ssm.ControlPanelActivity"
        )
    }

    /**
     * SEZIONE 2: Preset Risoluzione & Frame Rate (Fissi a 4K - Multipli Interi)
     * Invia il comando ADB di sistema Android display manager.
     */
    suspend fun applyVideoPreset(
        host: String,
        port: Int = 5555,
        preset: VideoPreset4K
    ): Result<String> {
        val client = AdbTcpClient(host, port)
        // Esegue il comando di impostazione display mode e abilita il match content frame rate
        val command = "${preset.adbCommand} && settings put global match_content_frame_rate 1"
        return client.executeShellCommand(command)
    }

    /**
     * Iniezione di testo nei campi di ricerca di Fire TV.
     */
    suspend fun injectText(host: String, port: Int = 5555, text: String): Result<String> {
        val client = AdbTcpClient(host, port)
        val sanitized = text.replace(" ", "%s").replace("'", "")
        return client.executeShellCommand("input text \"$sanitized\"")
    }
}
