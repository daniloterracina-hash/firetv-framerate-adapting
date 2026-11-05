package com.firetv.streamdeck.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firetv.streamdeck.data.FireTvDeckRepository
import com.firetv.streamdeck.data.FireTvKeycodes
import com.firetv.streamdeck.data.VideoPreset4K
import kotlinx.coroutines.launch

// Color Palette OLED Dark
val DarkBackground = Color(0xFF070B14)
val DarkSurface = Color(0xFF0F172A)
val DarkCard = Color(0xFF1E293B)
val AmberAccent = Color(0xFFF59E0B)
val SkyAccent = Color(0xFF38BDF8)
val EmeraldAccent = Color(0xFF10B981)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FireTvDeckScreen() {
    val repository = remember { FireTvDeckRepository() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var host by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("5555") }
    var connectionStatus by remember { mutableStateOf("Da connettere") }
    var isConnected by remember { mutableStateOf(false) }
    var latencyMs by remember { mutableStateOf<Long?>(null) }

    var isExecutingMacro by remember { mutableStateOf(false) }
    var macroRemainingMs by remember { mutableStateOf<Long?>(null) }
    var activePreset by remember { mutableStateOf(VideoPreset4K.UHD_60HZ) }
    var lastCommandLog by remember { mutableStateOf("Nessun comando inviato") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Fire TV Stream & Resolution Deck",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface),
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            connectionStatus = "Ping in corso..."
                            val result = repository.pingSocket(host, port.toIntOrNull() ?: 5555)
                            result.onSuccess { lat ->
                                isConnected = true
                                latencyMs = lat
                                connectionStatus = "Connesso (${lat}ms)"
                            }.onFailure { err ->
                                isConnected = false
                                connectionStatus = "Errore: ${err.localizedMessage ?: "Offline"}"
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Test Connessione", tint = AmberAccent)
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SCHERMATA A: CONFIGURAZIONE DI RETE (SETTINGS)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Configurazione di Rete (ADB TCP/IP)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("IP Fire TV Stick") },
                            modifier = Modifier.weight(2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AmberAccent
                            )
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("Porta") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AmberAccent
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stato: $connectionStatus",
                            fontSize = 12.sp,
                            color = if (isConnected) EmeraldAccent else Color.Gray,
                            fontWeight = FontWeight.Medium
                        )

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    connectionStatus = "Connessione..."
                                    repository.pingSocket(host, port.toIntOrNull() ?: 5555)
                                        .onSuccess {
                                            isConnected = true
                                            latencyMs = it
                                            connectionStatus = "Connesso (${it}ms)"
                                        }
                                        .onFailure {
                                            isConnected = false
                                            connectionStatus = "Disconnesso"
                                        }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)
                        ) {
                            Text("Salva & Connetti", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // SCHERMATA B - SEZIONE 1: MACRO DIAGNOSTICA STREAM (OVERLAY INFO)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sezione 1: Macro Diagnostica Stream", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Richiama il pannello nativo System X-Ray per verificare codec, risoluzione reale e framerate.", color = Color.Gray, fontSize = 11.sp)

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isExecutingMacro = true
                            scope.launch {
                                repository.executeStreamInfoMacro(host, port.toIntOrNull() ?: 5555) { remaining ->
                                    macroRemainingMs = remaining
                                }.onSuccess {
                                    lastCommandLog = "Macro completata con successo! Overlay attivato."
                                }.onFailure { err ->
                                    lastCommandLog = "Errore Macro: ${err.localizedMessage}"
                                }
                                isExecutingMacro = false
                                macroRemainingMs = null
                            }
                        },
                        enabled = !isExecutingMacro,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SkyAccent)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isExecutingMacro) "Esecuzione Macro in corso..." else "Info Stream (Debug Overlay)",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isExecutingMacro) "Pausa transizione: ${macroRemainingMs ?: 1200}ms" else "DPAD_CENTER (23) + DPAD_DOWN (20) → 1.2s → MENU (82)",
                                fontSize = 10.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }

            // SCHERMATA B - SEZIONE 2: PRESET RISOLUZIONE & FRAME RATE (4K FISSI)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sezione 2: Preset Risoluzione & Frame Rate (Fissi a 4K)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Risoluzione fissa 2160p con gestione 1:1 dei fotogrammi per azzerare judder e microscatti.", color = AmberAccent, fontSize = 11.sp)

                    VideoPreset4K.values().forEach { preset ->
                        val isSelected = activePreset == preset
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                activePreset = preset
                                scope.launch {
                                    repository.applyVideoPreset(host, port.toIntOrNull() ?: 5555, preset)
                                        .onSuccess { lastCommandLog = "Impostato display: ${preset.label}" }
                                        .onFailure { lastCommandLog = "Errore: ${it.localizedMessage}" }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isSelected) AmberAccent else Color.DarkGray
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) AmberAccent.copy(alpha = 0.15f) else Color.Transparent
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(preset.label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    Text(preset.description, color = Color.LightGray, fontSize = 10.sp)
                                }
                                if (isSelected) {
                                    Text("ATTIVO", color = AmberAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // SEZIONE 3: CONTROLLO TELECOMANDO ERGONOMICO D-PAD
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Telecomando & Navigazione Rapida", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)

                    // D-Pad Controller Circolare
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(CircleShape)
                            .background(DarkBackground)
                            .border(1.dp, Color.DarkGray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // UP
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { repository.sendKey(host, port.toIntOrNull() ?: 5555, FireTvKeycodes.KEYCODE_DPAD_UP) }
                            },
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Su", tint = Color.White)
                        }

                        // DOWN
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { repository.sendKey(host, port.toIntOrNull() ?: 5555, FireTvKeycodes.KEYCODE_DPAD_DOWN) }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Giù", tint = Color.White)
                        }

                        // LEFT
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { repository.sendKey(host, port.toIntOrNull() ?: 5555, FireTvKeycodes.KEYCODE_DPAD_LEFT) }
                            },
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Sinistra", tint = Color.White)
                        }

                        // RIGHT
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { repository.sendKey(host, port.toIntOrNull() ?: 5555, FireTvKeycodes.KEYCODE_DPAD_RIGHT) }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Destra", tint = Color.White)
                        }

                        // CENTER (OK)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AmberAccent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch { repository.sendKey(host, port.toIntOrNull() ?: 5555, FireTvKeycodes.KEYCODE_DPAD_CENTER) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("OK", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }
                    }

                    // Tasti di Navigazione: Back, Home, Menu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { repository.sendKey(host, port.toIntOrNull() ?: 5555, FireTvKeycodes.KEYCODE_BACK) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { repository.sendKey(host, port.toIntOrNull() ?: 5555, FireTvKeycodes.KEYCODE_HOME) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Home", tint = AmberAccent)
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { repository.sendKey(host, port.toIntOrNull() ?: 5555, FireTvKeycodes.KEYCODE_MENU) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu 82", tint = Color.White)
                        }
                    }
                }
            }

            // FEEDBACK LOG STATUS
            Text(
                text = "Ultimo comando: $lastCommandLog",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
