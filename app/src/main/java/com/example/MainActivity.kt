package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.HonorSoundStudioTheme
import com.example.ui.theme.LiquidGlassBase
import com.example.ui.theme.LiquidGlassBorderLight
import com.example.ui.theme.LiquidGlassBorderTint
import com.example.ui.theme.MagicAmber
import com.example.ui.theme.MagicCyan
import com.example.ui.theme.MagicEmerald
import com.example.ui.theme.MagicRose
import com.example.ui.theme.MagicSapphire
import com.example.ui.theme.MagicViolet
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.honorsound.studio.HonorMagicDspService
import com.honorsound.studio.HonorMagicDspService.AudioOutputDevice
import com.honorsound.studio.HonorMagicDspService.DspEngineState
import com.honorsound.studio.HonorMagicDspService.HookedSessionInfo
import com.honorsound.studio.HonorMagicDspService.MagicPreset
import com.honorsound.studio.HonorMagicDspService.MicMonitorMode
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            HonorSoundStudioTheme {
                HonorSoundStudioApp()
            }
        }
    }
}

@Composable
private fun HonorSoundStudioApp() {
    val context = LocalContext.current
    var boundService by remember { mutableStateOf<HonorMagicDspService?>(null) }
    var pendingMicMode by remember { mutableStateOf<MicMonitorMode?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Refresh foreground notification once permission prompt finishes
        ContextCompat.startForegroundService(
            context,
            Intent(context, HonorMagicDspService::class.java).apply {
                action = HonorMagicDspService.ACTION_START_OR_SYNC
            }
        )
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val requested = pendingMicMode
        pendingMicMode = null
        if (granted && requested != null) {
            boundService?.setMicMonitorMode(requested)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(context) {
        val serviceIntent = Intent(context, HonorMagicDspService::class.java).apply {
            action = HonorMagicDspService.ACTION_START_OR_SYNC
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val localBinder = service as? HonorMagicDspService.LocalBinder
                boundService = localBinder?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }
        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            runCatching { context.unbindService(connection) }
            boundService = null
        }
    }

    val engineState by (boundService?.state?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(DspEngineState()) })

    HonorSoundStudioScreen(
        state = engineState,
        onToggleBypass = {
            boundService?.setMasterBypass(!engineState.masterBypass)
        },
        onRescanSessions = {
            boundService?.rescanActiveSessions()
        },
        onSelectPreset = { preset ->
            boundService?.selectPreset(preset)
        },
        onBandGainChange = { band, gain ->
            boundService?.setEqBandGain(band, gain)
        },
        onResetFlat = {
            boundService?.resetEqFlat()
        },
        onBassBoostChange = { value ->
            boundService?.setBassBoost(value)
        },
        onVoiceForwardChange = { value ->
            boundService?.setVoiceForward(value)
        },
        onSurroundSubwooferToggle = { enabled ->
            boundService?.setSurroundSubwoofer(enabled)
        },
        onSpatialFieldChange = { level ->
            boundService?.setSpatialFieldLevel(level)
        },
        onUltimateVolumeChange = { step ->
            boundService?.setUltimateVolumeStep(step)
        },
        onNightModeToggle = { enabled ->
            boundService?.setNightMode(enabled)
        },
        onPrivacyCallToggle = { enabled ->
            boundService?.setAiPrivacyCallMode(enabled)
        },
        onMicMonitorSelect = { mode ->
            if (mode == MicMonitorMode.OFF) {
                boundService?.setMicMonitorMode(MicMonitorMode.OFF)
            } else {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    boundService?.setMicMonitorMode(mode)
                } else {
                    pendingMicMode = mode
                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        onRestartService = {
            val intent = Intent(context, HonorMagicDspService::class.java).apply {
                action = HonorMagicDspService.ACTION_START_OR_SYNC
            }
            ContextCompat.startForegroundService(context, intent)
        }
    )
}

@Composable
private fun HonorSoundStudioScreen(
    state: DspEngineState,
    onToggleBypass: () -> Unit,
    onRescanSessions: () -> Unit,
    onSelectPreset: (MagicPreset) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onResetFlat: () -> Unit,
    onBassBoostChange: (Float) -> Unit,
    onVoiceForwardChange: (Float) -> Unit,
    onSurroundSubwooferToggle: (Boolean) -> Unit,
    onSpatialFieldChange: (Float) -> Unit,
    onUltimateVolumeChange: (Int) -> Unit,
    onNightModeToggle: (Boolean) -> Unit,
    onPrivacyCallToggle: (Boolean) -> Unit,
    onMicMonitorSelect: (MicMonitorMode) -> Unit,
    onRestartService: () -> Unit
) {
    val listState = rememberLazyListState()
    var capsuleExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // Obsidian atmospheric dual-glow backdrop
        ObsidianAtmosphereBackdrop(
            isBypassed = state.masterBypass,
            activeDevice = state.activeOutputDevice
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
            ) {
                // Magic Capsule Top Pill + Master Bypass (A/B) Bar
                MagicCapsuleHeader(
                    state = state,
                    expanded = capsuleExpanded,
                    onToggleExpanded = { capsuleExpanded = !capsuleExpanded },
                    onToggleBypass = onToggleBypass,
                    onRescanSessions = onRescanSessions,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )

                // 120Hz Smooth Scrollable Studio Deck
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("dsp_studio_scroll"),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Live Response Curve + 10-Band Tuning Master EQ
                    item(key = "master_eq_card") {
                        TuningMasterEqCard(
                            state = state,
                            onBandGainChange = onBandGainChange,
                            onResetFlat = onResetFlat
                        )
                    }

                    // 2. 6 Official MagicOS 11 Presets
                    item(key = "presets_card") {
                        MagicPresetsCard(
                            activePreset = state.activePreset,
                            isBypassed = state.masterBypass,
                            onSelectPreset = onSelectPreset
                        )
                    }

                    // 3. Tone Sculptors (Bass Boost + Voice Forward) & Dual 1216 Surround
                    item(key = "tone_and_surround_card") {
                        ToneAndSurroundCard(
                            state = state,
                            onBassBoostChange = onBassBoostChange,
                            onVoiceForwardChange = onVoiceForwardChange,
                            onSurroundSubwooferToggle = onSurroundSubwooferToggle,
                            onSpatialFieldChange = onSpatialFieldChange
                        )
                    }

                    // 4. 150-Step Ultimate Volume & Night Mode
                    item(key = "ultimate_volume_card") {
                        UltimateVolumeAndNightCard(
                            state = state,
                            onUltimateVolumeChange = onUltimateVolumeChange,
                            onNightModeToggle = onNightModeToggle
                        )
                    }

                    // 5. AI Privacy Call Mode & Cinematic Directional Mic Monitor
                    item(key = "privacy_and_mic_card") {
                        PrivacyAndCinematicMicCard(
                            state = state,
                            onPrivacyCallToggle = onPrivacyCallToggle,
                            onMicMonitorSelect = onMicMonitorSelect
                        )
                    }

                    // 6. Infinix Hot 60 5G Calibration & XOS Persistence Telemetry
                    item(key = "hardware_calibration_card") {
                        HardwareCalibrationCard(
                            state = state,
                            onRestartService = onRestartService
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ObsidianAtmosphereBackdrop(
    isBypassed: Boolean,
    activeDevice: AudioOutputDevice
) {
    val primaryGlow by animateColorAsState(
        targetValue = when {
            isBypassed -> MagicAmber.copy(alpha = 0.14f)
            activeDevice == AudioOutputDevice.WIRED_HEADSET -> MagicEmerald.copy(alpha = 0.16f)
            activeDevice == AudioOutputDevice.BLUETOOTH -> MagicSapphire.copy(alpha = 0.18f)
            else -> MagicCyan.copy(alpha = 0.17f)
        },
        animationSpec = tween(450),
        label = "primary_glow"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = ObsidianVoid)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryGlow, Color.Transparent),
                center = Offset(size.width * 0.22f, size.height * 0.08f),
                radius = size.maxDimension * 0.65f
            ),
            radius = size.maxDimension * 0.65f,
            center = Offset(size.width * 0.22f, size.height * 0.08f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(MagicViolet.copy(alpha = 0.11f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.72f),
                radius = size.maxDimension * 0.58f
            ),
            radius = size.maxDimension * 0.58f,
            center = Offset(size.width * 0.85f, size.height * 0.72f)
        )
    }
}

@Composable
private fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    accentBorder: Color = LiquidGlassBorderTint,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        LiquidGlassBase.copy(alpha = 0.78f),
                        ObsidianSurface.copy(alpha = 0.88f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        LiquidGlassBorderLight,
                        accentBorder,
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun MagicCapsuleHeader(
    state: DspEngineState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleBypass: () -> Unit,
    onRescanSessions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "capsule_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val statusColor by animateColorAsState(
        targetValue = when {
            !state.isServiceRunning -> MagicRose
            state.masterBypass -> MagicAmber
            else -> MagicEmerald
        },
        animationSpec = tween(250),
        label = "status_color"
    )

    val deviceIcon = when (state.activeOutputDevice) {
        AudioOutputDevice.BUILT_IN_SPEAKER -> Icons.Rounded.Speaker
        AudioOutputDevice.WIRED_HEADSET -> Icons.Rounded.Headphones
        AudioOutputDevice.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    }

    val capsuleShape = RoundedCornerShape(28.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(capsuleShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        ObsidianElevated.copy(alpha = 0.94f),
                        LiquidGlassBase.copy(alpha = 0.88f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        statusColor.copy(alpha = 0.55f),
                        LiquidGlassBorderLight,
                        MagicCyan.copy(alpha = 0.28f)
                    )
                ),
                shape = capsuleShape
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Expandable Magic Capsule Device + Live Session Pill
            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("magic_capsule_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.16f))
                        .border(1.dp, statusColor.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = deviceIcon,
                        contentDescription = state.activeOutputDevice.displayName,
                        tint = statusColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = if (state.masterBypass) 1f else pulseAlpha))
                        )
                        Text(
                            text = "HonorSound Studio",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${state.activeOutputDevice.displayName} • ${state.hookedSessions.size} hooked",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle hooked audio sessions",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Instant Master Bypass (A/B) Toggle Pill
            val bypassBg by animateColorAsState(
                targetValue = if (state.masterBypass) {
                    MagicAmber.copy(alpha = 0.22f)
                } else {
                    MagicCyan.copy(alpha = 0.20f)
                },
                label = "bypass_bg"
            )
            val bypassText = if (state.masterBypass) "BYPASS" else "DSP ON"
            val bypassTint = if (state.masterBypass) MagicAmber else MagicCyan

            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bypassBg)
                    .border(1.dp, bypassTint.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleBypass()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("master_bypass_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = "Toggle Master DSP Bypass",
                    tint = bypassTint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = bypassText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = bypassTint
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE HOOKED AUDIO SESSIONS (DYNAMICS API 28+)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MagicCyan
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onRescanSessions)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("rescan_sessions_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Rescan audio sessions",
                            tint = MagicCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Rescan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MagicCyan
                        )
                    }
                }

                if (state.hookedSessions.isEmpty()) {
                    Text(
                        text = "Waiting for playback stream (Spotify, YouTube, TikTok, MLBB)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                } else {
                    state.hookedSessions.forEach { session ->
                        SessionBadgeRow(session = session)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionBadgeRow(session: HookedSessionInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.Sensors,
                contentDescription = null,
                tint = MagicEmerald,
                modifier = Modifier.size(15.dp)
            )
            Column {
                Text(
                    text = session.appLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${session.packageName} • ${session.origin}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = "#${session.sessionId}",
            style = MaterialTheme.typography.labelMedium,
            color = MagicCyan
        )
    }
}

@Composable
private fun TuningMasterEqCard(
    state: DspEngineState,
    onBandGainChange: (Int, Float) -> Unit,
    onResetFlat: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    LiquidGlassCard(
        accentBorder = if (state.masterBypass) {
            MagicAmber.copy(alpha = 0.35f)
        } else {
            MagicCyan.copy(alpha = 0.42f)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MagicCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Tuning Master 10-Band EQ",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Pre-EQ + Infinix Hot 60 5G Post-EQ • Limiter -0.2 dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onResetFlat()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("eq_flat_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset EQ to Flat",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Flat", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Live Frequency Response Curve Canvas
            LiveFrequencyResponseGraph(
                userBandsDb = state.eqBandsDb,
                effectivePostEqDb = state.lastEffectivePostEqDb,
                bassBoostDb = state.bassBoostDb,
                surroundSubwoofer = state.surroundSubwooferEnabled,
                isBypassed = state.masterBypass
            )

            // Interactive 10-Band Vertical Faders
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useFullWidth = maxWidth >= 520.dp
                if (useFullWidth) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (index in 0 until 10) {
                            EqBandFader(
                                bandIndex = index,
                                freqHz = HonorMagicDspService.EQ_CENTER_FREQUENCIES_HZ[index],
                                gainDb = state.eqBandsDb.getOrElse(index) { 0f },
                                isBypassed = state.masterBypass,
                                onGainChange = { newGain -> onBandGainChange(index, newGain) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (index in 0 until 10) {
                            EqBandFader(
                                bandIndex = index,
                                freqHz = HonorMagicDspService.EQ_CENTER_FREQUENCIES_HZ[index],
                                gainDb = state.eqBandsDb.getOrElse(index) { 0f },
                                isBypassed = state.masterBypass,
                                onGainChange = { newGain -> onBandGainChange(index, newGain) },
                                modifier = Modifier.width(48.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveFrequencyResponseGraph(
    userBandsDb: List<Float>,
    effectivePostEqDb: List<Float>,
    bassBoostDb: Float,
    surroundSubwoofer: Boolean,
    isBypassed: Boolean
) {
    val curveColor = if (isBypassed) MagicAmber else MagicCyan
    val subBoost = if (surroundSubwoofer) 1.0f else 0.0f

    val combinedPoints = remember(userBandsDb, effectivePostEqDb, bassBoostDb, surroundSubwoofer, isBypassed) {
        (0 until 10).map { i ->
            if (isBypassed) {
                0f
            } else {
                val preBase = userBandsDb.getOrElse(i) { 0f }
                val bassShelf = when (i) {
                    0 -> bassBoostDb * 1.00f + subBoost * 3.2f
                    1 -> bassBoostDb * 0.85f + subBoost * 2.4f
                    2 -> bassBoostDb * 0.45f + subBoost * 1.0f
                    else -> 0f
                }
                val post = effectivePostEqDb.getOrElse(i) { 0f }
                (preBase + bassShelf + post).coerceIn(-15f, 15f)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianVoid.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f
            val maxRangeDb = 15f

            // Reference dB gridlines (+12, 0, -12 dB)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            listOf(-12f, 0f, 12f).forEach { db ->
                val y = midY - (db / maxRangeDb) * (h * 0.44f)
                drawLine(
                    color = if (db == 0f) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (db == 0f) 1.5f else 1f,
                    pathEffect = if (db == 0f) null else dashEffect
                )
            }

            fun mapPoints(values: List<Float>): List<Offset> {
                val stepX = w / (values.size - 1).coerceAtLeast(1)
                return values.mapIndexed { idx, db ->
                    val clamped = db.coerceIn(-15f, 15f)
                    val x = idx * stepX
                    val y = midY - (clamped / maxRangeDb) * (h * 0.44f)
                    Offset(x, y)
                }
            }

            // 1. User Pre-EQ dashed reference curve
            val userPts = mapPoints(if (isBypassed) List(10) { 0f } else userBandsDb)
            val userPath = buildSmoothCurvePath(userPts)
            drawPath(
                path = userPath,
                color = MagicSapphire.copy(alpha = 0.55f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
            )

            // 2. Combined Effective Hardware Curve + Soft Gradient Fill
            val combinedPts = mapPoints(combinedPoints)
            val combinedPath = buildSmoothCurvePath(combinedPts)
            val fillPath = Path().apply {
                addPath(combinedPath)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        curveColor.copy(alpha = 0.28f),
                        Color.Transparent
                    )
                )
            )

            drawPath(
                path = combinedPath,
                color = curveColor,
                style = Stroke(
                    width = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            combinedPts.forEach { pt ->
                drawCircle(
                    color = ObsidianVoid,
                    radius = 4.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = curveColor,
                    radius = 2.6.dp.toPx(),
                    center = pt
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isBypassed) "BYPASSED (FLAT)" else "LIVE HARDWARE CURVE",
                style = MaterialTheme.typography.labelSmall,
                color = curveColor
            )
        }
    }
}

private fun buildSmoothCurvePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val midX = (p0.x + p1.x) / 2f
        path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
    }
    return path
}

@Composable
private fun EqBandFader(
    bandIndex: Int,
    freqHz: Int,
    gainDb: Float,
    isBypassed: Boolean,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var trackHeightPx by remember { mutableIntStateOf(1) }
    var currentGain by remember(gainDb) { mutableFloatStateOf(gainDb) }
    val animatedGain by animateFloatAsState(
        targetValue = currentGain.coerceIn(-12f, 12f),
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "eq_gain_$bandIndex"
    )

    val activeTint = when {
        isBypassed -> TextMuted
        animatedGain >= 0f -> MagicCyan
        else -> MagicSapphire
    }

    val freqLabel = if (freqHz >= 1000) "${freqHz / 1000}K" else "$freqHz"

    Column(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = String.format(Locale.US, "%+.1f", animatedGain),
            style = MaterialTheme.typography.labelSmall,
            color = if (isBypassed) TextMuted else TextPrimary,
            maxLines = 1
        )

        Box(
            modifier = Modifier
                .width(48.dp)
                .height(164.dp)
                .clip(RoundedCornerShape(14.dp))
                .onSizeChanged { trackHeightPx = it.height.coerceAtLeast(1) }
                .pointerInput(bandIndex) {
                    detectTapGestures(
                        onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentGain = 0f
                            onGainChange(0f)
                        },
                        onTap = { offset ->
                            val ratio = 1f - (offset.y / trackHeightPx.toFloat()).coerceIn(0f, 1f)
                            val snapped = (((ratio * 24f) - 12f) * 2f).roundToInt() / 2f
                            val clamped = snapped.coerceIn(-12f, 12f)
                            currentGain = clamped
                            onGainChange(clamped)
                        }
                    )
                }
                .pointerInput(bandIndex) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val ratio = 1f - (change.position.y / trackHeightPx.toFloat()).coerceIn(0f, 1f)
                        val snapped = (((ratio * 24f) - 12f) * 2f).roundToInt() / 2f
                        val clamped = snapped.coerceIn(-12f, 12f)
                        if (clamped != currentGain) {
                            currentGain = clamped
                            onGainChange(clamped)
                        }
                    }
                }
                .testTag("eq_fader_$bandIndex"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val topPad = 10.dp.toPx()
                val usableH = (size.height - topPad * 2).coerceAtLeast(1f)
                val centerY = topPad + usableH / 2f
                val railW = 6.dp.toPx()

                // Rail backdrop
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.09f),
                    topLeft = Offset(centerX - railW / 2f, topPad),
                    size = Size(railW, usableH),
                    cornerRadius = CornerRadius(railW / 2f, railW / 2f)
                )

                // Center 0 dB tick
                drawLine(
                    color = Color.White.copy(alpha = 0.28f),
                    start = Offset(centerX - 8.dp.toPx(), centerY),
                    end = Offset(centerX + 8.dp.toPx(), centerY),
                    strokeWidth = 1.2.dp.toPx()
                )

                val normalized = (animatedGain + 12f) / 24f
                val thumbY = topPad + (1f - normalized) * usableH

                val fillTop = minOf(centerY, thumbY)
                val fillHeight = kotlin.math.abs(centerY - thumbY)
                if (fillHeight > 1f) {
                    drawRoundRect(
                        color = activeTint.copy(alpha = 0.85f),
                        topLeft = Offset(centerX - railW / 2f, fillTop),
                        size = Size(railW, fillHeight),
                        cornerRadius = CornerRadius(railW / 2f, railW / 2f)
                    )
                }

                // Glowing thumb knob
                drawCircle(
                    color = activeTint.copy(alpha = 0.25f),
                    radius = 12.dp.toPx(),
                    center = Offset(centerX, thumbY)
                )
                drawCircle(
                    color = TextPrimary,
                    radius = 7.dp.toPx(),
                    center = Offset(centerX, thumbY)
                )
                drawCircle(
                    color = activeTint,
                    radius = 4.dp.toPx(),
                    center = Offset(centerX, thumbY)
                )
            }
        }

        Text(
            text = freqLabel,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun MagicPresetsCard(
    activePreset: MagicPreset,
    isBypassed: Boolean,
    onSelectPreset: (MagicPreset) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    LiquidGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MagicCyan,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "MagicOS 11 Sound Presets",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                if (activePreset == MagicPreset.CUSTOM) {
                    Text(
                        text = "CUSTOM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MagicAmber
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(MagicPreset.OFFICIAL_PRESETS, key = { it.name }) { preset ->
                    val selected = activePreset == preset
                    val bgColor by animateColorAsState(
                        targetValue = when {
                            selected && !isBypassed -> MagicCyan.copy(alpha = 0.20f)
                            selected && isBypassed -> MagicAmber.copy(alpha = 0.18f)
                            else -> Color.White.copy(alpha = 0.05f)
                        },
                        label = "preset_bg_${preset.name}"
                    )
                    val borderColor = when {
                        selected && !isBypassed -> MagicCyan
                        selected && isBypassed -> MagicAmber
                        else -> Color.White.copy(alpha = 0.12f)
                    }
                    val textColor = when {
                        selected && !isBypassed -> MagicCyan
                        selected && isBypassed -> MagicAmber
                        else -> TextSecondary
                    }

                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectPreset(preset)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("preset_chip_${preset.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = textColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneAndSurroundCard(
    state: DspEngineState,
    onBassBoostChange: (Float) -> Unit,
    onVoiceForwardChange: (Float) -> Unit,
    onSurroundSubwooferToggle: (Boolean) -> Unit,
    onSpatialFieldChange: (Float) -> Unit
) {
    LiquidGlassCard(accentBorder = MagicSapphire.copy(alpha = 0.42f)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SurroundSound,
                    contentDescription = null,
                    tint = MagicSapphire,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Magic9 Pro Max Soundstage",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            PrecisionDspSlider(
                label = "Bass Boost",
                valueLabel = String.format(Locale.US, "+%.1f dB", state.bassBoostDb),
                value = state.bassBoostDb,
                valueRange = 0f..6f,
                accentColor = MagicCyan,
                onValueChange = onBassBoostChange,
                testTag = "slider_bass_boost"
            )

            PrecisionDspSlider(
                label = "Voice Forward",
                valueLabel = String.format(Locale.US, "+%.1f dB", state.voiceForwardDb),
                value = state.voiceForwardDb,
                valueRange = 0f..6f,
                accentColor = MagicSapphire,
                onValueChange = onVoiceForwardChange,
                testTag = "slider_voice_forward"
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dual 1216 AI Surround Subwoofer",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                    Text(
                        text = "Sub-bass warmth + multi-band compressor punch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = state.surroundSubwooferEnabled,
                    onCheckedChange = onSurroundSubwooferToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianVoid,
                        checkedTrackColor = MagicCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("switch_surround_subwoofer")
                )
            }

            PrecisionDspSlider(
                label = "Spatial Field Width",
                valueLabel = "${(state.spatialFieldLevel * 100f).roundToInt()}%",
                value = state.spatialFieldLevel,
                valueRange = 0f..1f,
                accentColor = MagicViolet,
                onValueChange = onSpatialFieldChange,
                testTag = "slider_spatial_field"
            )
        }
    }
}

@Composable
private fun UltimateVolumeAndNightCard(
    state: DspEngineState,
    onUltimateVolumeChange: (Int) -> Unit,
    onNightModeToggle: (Boolean) -> Unit
) {
    LiquidGlassCard(accentBorder = MagicEmerald.copy(alpha = 0.34f)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = MagicEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "150-Step Ultimate Volume",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MagicEmerald.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "LIMITER -0.2 dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MagicEmerald
                    )
                }
            }

            PrecisionDspSlider(
                label = "Pre-Gain Drive (${state.ultimateVolumeStep}%)",
                valueLabel = String.format(Locale.US, "+%.1f dB", state.ultimateVolumePreGainDb),
                value = state.ultimateVolumeStep.toFloat(),
                valueRange = 0f..150f,
                steps = 149,
                accentColor = if (state.ultimateVolumeStep > 100) MagicAmber else MagicEmerald,
                onValueChange = { onUltimateVolumeChange(it.roundToInt()) },
                testTag = "slider_ultimate_volume"
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NightsStay,
                        contentDescription = null,
                        tint = MagicAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Night Mode Dynamic Leveling",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = TextPrimary
                        )
                        Text(
                            text = "Lifts quiet dialogue & tames sudden peaks via 3-band MBC",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = state.nightModeEnabled,
                    onCheckedChange = onNightModeToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianVoid,
                        checkedTrackColor = MagicAmber,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("switch_night_mode")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrivacyAndCinematicMicCard(
    state: DspEngineState,
    onPrivacyCallToggle: (Boolean) -> Unit,
    onMicMonitorSelect: (MicMonitorMode) -> Unit
) {
    val isSpeaker = state.activeOutputDevice == AudioOutputDevice.BUILT_IN_SPEAKER

    LiquidGlassCard(accentBorder = MagicRose.copy(alpha = 0.34f)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // AI Privacy Call Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MagicRose,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "AI Privacy Call Mode",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = TextPrimary
                        )
                        Text(
                            text = "Cuts >3.5 kHz leakage, boosts 1.2–2.8 kHz close-ear clarity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = state.aiPrivacyCallEnabled,
                    onCheckedChange = onPrivacyCallToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianVoid,
                        checkedTrackColor = MagicRose,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("switch_ai_privacy_call")
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Cinematic Directional Mic Monitor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (state.micMonitorMode == MicMonitorMode.OFF || isSpeaker) {
                            Icons.Rounded.MicOff
                        } else {
                            Icons.Rounded.Mic
                        },
                        contentDescription = null,
                        tint = if (isSpeaker) MagicAmber else MagicCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Cinematic Directional Mic Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Low-latency headphone monitor via AudioRecord + AudioTrack",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (isSpeaker) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MagicAmber.copy(alpha = 0.12f))
                        .border(1.dp, MagicAmber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MagicAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Speaker Mode active — mic monitor auto-muted to prevent acoustic feedback. Plug in 3.5mm jack or connect Bluetooth 5.4.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MagicAmber
                    )
                }
            }

            if (state.micPermissionMissing) {
                Text(
                    text = "Microphone permission is required to enable live headphone monitoring.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MagicRose
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MicMonitorMode.entries.forEach { mode ->
                    val selected = state.micMonitorMode == mode
                    val chipColor = when {
                        selected && mode == MicMonitorMode.OFF -> TextSecondary
                        selected && isSpeaker -> MagicAmber
                        selected -> MagicCyan
                        else -> TextSecondary
                    }
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) chipColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) chipColor else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onMicMonitorSelect(mode) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("mic_mode_${mode.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = chipColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareCalibrationCard(
    state: DspEngineState,
    onRestartService: () -> Unit
) {
    val calibrationDetail = when (state.activeOutputDevice) {
        AudioOutputDevice.BUILT_IN_SPEAKER ->
            "Asymmetrical Dual-Speaker Guard active: protects top punch-hole earpiece (<160 Hz roll-off) + boosts +2.0 dB at 2.1 kHz."
        AudioOutputDevice.WIRED_HEADSET ->
            "3.5mm Hi-Res DAC reference mode: zero colorization + dedicated saved profile."
        AudioOutputDevice.BLUETOOTH ->
            "Bluetooth 5.4 wireless compensation: crisp high-frequency air recovery (+0.8 dB @ 16 kHz) + dedicated saved profile."
    }

    LiquidGlassCard(accentBorder = Color.White.copy(alpha = 0.16f)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MagicCyan,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "Infinix Hot 60 5G Acoustic Calibration",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Lock-screen foreground persistence active",
                    tint = MagicEmerald,
                    modifier = Modifier.size(17.dp)
                )
            }

            Text(
                text = calibrationDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Hearing,
                        contentDescription = null,
                        tint = MagicEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (state.isServiceRunning) {
                            "XOS Foreground Service • Active"
                        } else {
                            "Service Stopped"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.isServiceRunning) MagicEmerald else MagicRose
                    )
                }

                if (!state.isServiceRunning) {
                    FilledTonalButton(
                        onClick = onRestartService,
                        modifier = Modifier.testTag("restart_service_button")
                    ) {
                        Text("Start Engine")
                    }
                }
            }
        }
    }
}

@Composable
private fun PrecisionDspSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    testTag: String,
    steps: Int = 0
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = TextPrimary,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
