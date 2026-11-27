package com.honorsound.studio

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tanh

/**
 * HonorSound Studio — System-Wide MagicOS 11 & Magic9 Pro Max Audio DSP Engine.
 * Calibrated specifically for Infinix Hot 60 5G (MediaTek Dimensity HAL,
 * asymmetrical dual stereo speakers, 3.5mm Hi-Res jack, Bluetooth 5.4, and XOS persistence).
 */
class HonorMagicDspService : Service() {

    companion object {
        private const val TAG = "HonorMagicDspService"
        private const val NOTIFICATION_ID = 1109
        private const val CHANNEL_ID = "honorsound_magic_dsp_channel"
        private const val PREFS_NAME = "honorsound_studio_device_presets_v1"

        const val ACTION_START_OR_SYNC = "com.honorsound.studio.action.START_OR_SYNC"
        const val ACTION_TOGGLE_BYPASS = "com.honorsound.studio.action.TOGGLE_BYPASS"
        const val ACTION_NEXT_PRESET = "com.honorsound.studio.action.NEXT_PRESET"
        const val ACTION_STOP_SERVICE = "com.honorsound.studio.action.STOP_SERVICE"

        private const val SAMPLE_RATE_HZ = 48000
        private const val LIMITER_THRESHOLD_DB = -0.2f
        private const val FRAME_DURATION_MS = 10.0f

        val EQ_CENTER_FREQUENCIES_HZ = intArrayOf(
            31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
        )

        // Geometric upper cutoff edges matching ISO 10-band centers in Android libdynamicsprocessing
        private val EQ_UPPER_CUTOFF_HZ = floatArrayOf(
            44f, 88f, 177f, 354f, 707f, 1414f, 2828f, 5657f, 11314f, 20000f
        )

        // 3-Band Multi-Band Compressor upper cutoffs: Sub/Bass, Vocal/Mid, Air/Treble
        private val MBC_UPPER_CUTOFF_HZ = floatArrayOf(220f, 3200f, 20000f)

        // Infinix Hot 60 5G asymmetrical speaker compensation:
        // Protects top punch-hole earpiece below 160 Hz & boosts +2.0 dB at ~2.1 kHz
        private val INFINIX_SPEAKER_COMP_DB = floatArrayOf(
            -10.0f, // 31 Hz: steep sub-bass excursion cut
            -7.0f,  // 62 Hz: top earpiece protection
            -2.5f,  // 125 Hz: <160 Hz transition roll-off
            0.0f,   // 250 Hz
            0.0f,   // 500 Hz
            0.4f,   // 1000 Hz
            2.0f,   // 2000 Hz: +2.0 dB presence & dual-speaker symmetry
            0.6f,   // 4000 Hz: smooth 2.1 kHz shoulder
            0.0f,   // 8000 Hz
            0.0f    // 16000 Hz
        )

        private val BLUETOOTH_54_COMP_DB = floatArrayOf(
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.2f, 0.3f, 0.5f, 0.8f
        )

        private val WIRED_HIRES_COMP_DB = floatArrayOf(
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f
        )
    }

    enum class AudioOutputDevice(val displayName: String, val storageKey: String) {
        BUILT_IN_SPEAKER("Infinix Speakers", "infinix_speaker"),
        WIRED_HEADSET("3.5mm Hi-Res Jack", "wired_3_5mm"),
        BLUETOOTH("Bluetooth 5.4", "bluetooth_5_4")
    }

    enum class MagicPreset(val displayName: String, val bandsDb: FloatArray) {
        STRONG_BASS(
            "Strong Bass",
            floatArrayOf(6.5f, 5.5f, 3.5f, 1.0f, -0.5f, 0.0f, 1.0f, 2.0f, 2.5f, 2.0f)
        ),
        CLEAR_VOCALS(
            "Clear Vocals",
            floatArrayOf(-2.0f, -1.5f, -0.5f, 0.5f, 2.0f, 4.5f, 5.0f, 3.5f, 1.5f, 0.5f)
        ),
        ENHANCED_TREBLE(
            "Enhanced Treble",
            floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 2.5f, 4.5f, 6.0f, 6.5f)
        ),
        ENERGETIC_BGM(
            "Energetic Background Music",
            floatArrayOf(4.5f, 3.5f, 1.5f, -0.5f, 0.0f, 1.5f, 2.5f, 3.5f, 4.0f, 3.5f)
        ),
        RELAXED_AUDIOBOOK(
            "Relaxed Audiobook",
            floatArrayOf(-4.0f, -2.5f, 0.0f, 1.5f, 2.5f, 3.0f, 2.0f, -1.0f, -2.5f, -3.5f)
        ),
        MLBB_COMPETITIVE(
            "MLBB Competitive",
            floatArrayOf(-1.5f, 1.0f, 2.5f, 1.5f, 0.5f, 2.5f, 4.5f, 5.5f, 3.0f, 1.5f)
        ),
        CUSTOM(
            "Custom",
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        );

        companion object {
            val OFFICIAL_PRESETS: List<MagicPreset> = listOf(
                STRONG_BASS,
                CLEAR_VOCALS,
                ENHANCED_TREBLE,
                ENERGETIC_BGM,
                RELAXED_AUDIOBOOK,
                MLBB_COMPETITIVE
            )
        }
    }

    enum class MicMonitorMode(val displayName: String) {
        OFF("Off"),
        NOISE_ISOLATION("Noise Isolation"),
        SUBJECT_VOICE_BOOST("Subject Voice Boost"),
        ARRI_AMBIENT("ARRI Ambient")
    }

    data class HookedSessionInfo(
        val sessionId: Int,
        val packageName: String,
        val appLabel: String,
        val origin: String
    )

    data class DspEngineState(
        val isServiceRunning: Boolean = false,
        val masterBypass: Boolean = false,
        val activeOutputDevice: AudioOutputDevice = AudioOutputDevice.BUILT_IN_SPEAKER,
        val activePreset: MagicPreset = MagicPreset.ENERGETIC_BGM,
        val eqBandsDb: List<Float> = MagicPreset.ENERGETIC_BGM.bandsDb.toList(),
        val bassBoostDb: Float = 0.0f,
        val voiceForwardDb: Float = 0.0f,
        val surroundSubwooferEnabled: Boolean = true,
        val spatialFieldLevel: Float = 0.45f,
        val ultimateVolumeStep: Int = 0, // 0..150 -> 0.0f..15.0f dB pre-gain
        val nightModeEnabled: Boolean = false,
        val aiPrivacyCallEnabled: Boolean = false,
        val micMonitorMode: MicMonitorMode = MicMonitorMode.OFF,
        val micMonitorAutoMuted: Boolean = false,
        val micPermissionMissing: Boolean = false,
        val hookedSessions: List<HookedSessionInfo> = emptyList(),
        val dynamicsSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
        val lastEffectivePostEqDb: List<Float> = List(10) { 0f }
    ) {
        val ultimateVolumePreGainDb: Float
            get() = (ultimateVolumeStep.coerceIn(0, 150) * 0.1f)
    }

    inner class LocalBinder : Binder() {
        fun getService(): HonorMagicDspService = this@HonorMagicDspService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dspLock = Any()

    private val _state = MutableStateFlow(DspEngineState())
    val state: StateFlow<DspEngineState> = _state.asStateFlow()

    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var prefs: SharedPreferences

    private val attachedEffects = ConcurrentHashMap<Int, DynamicsProcessing>()
    private val sessionMetadata = ConcurrentHashMap<Int, HookedSessionInfo>()
    private val excludedMonitorSessions = ConcurrentHashMap.newKeySet<Int>()

    private var saveJob: Job? = null
    private var monitorJob: Job? = null
    @Volatile
    private var explicitlyStopped = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshOutputDeviceAndReloadPreset()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshOutputDeviceAndReloadPreset()
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (configs == null) return
            handlePlaybackConfigChanged(configs)
        }
    }

    private val audioEffectSessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val sessionId = intent.getIntExtra(
                AudioEffect.EXTRA_AUDIO_SESSION,
                AudioEffect.ERROR
            )
            if (sessionId <= 0 || excludedMonitorSessions.contains(sessionId)) return

            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown"
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    attachEffectToSession(
                        sessionId = sessionId,
                        packageName = pkg,
                        origin = "EffectSession"
                    )
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    detachEffectFromSession(sessionId)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        createNotificationChannel()

        val initialDevice = detectActiveOutputDevice()
        val loadedState = loadDevicePresetState(initialDevice).copy(
            isServiceRunning = true,
            activeOutputDevice = initialDevice,
            dynamicsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        )
        _state.value = loadedState

        // Register hardware & session callbacks
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        audioManager.registerAudioPlaybackCallback(playbackCallback, mainHandler)
        registerEffectControlReceiver()

        // Hook global output mix (Session 0) + any currently active playback sessions
        attachEffectToSession(
            sessionId = 0,
            packageName = packageName,
            origin = "GlobalMix"
        )
        handlePlaybackConfigChanged(audioManager.activePlaybackConfigurations)

        // Apply initial DSP curve and start foreground notification
        applyCurrentDspParameters()
        promoteToForeground(includeMicrophoneType = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_BYPASS -> {
                setMasterBypass(!_state.value.masterBypass)
            }
            ACTION_NEXT_PRESET -> {
                cycleNextOfficialPreset()
            }
            ACTION_STOP_SERVICE -> {
                explicitlyStopped = true
                stopMicMonitorInternal()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                explicitlyStopped = false
                promoteToForeground(
                    includeMicrophoneType = isMicMonitorStreamingActive(_state.value)
                )
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // XOS background persistence safeguard: reschedule sticky start if task is swiped away
        if (!explicitlyStopped) {
            runCatching {
                val restartIntent = Intent(applicationContext, HonorMagicDspService::class.java).apply {
                    action = ACTION_START_OR_SYNC
                }
                val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PendingIntent.getForegroundService(
                        applicationContext,
                        2048,
                        restartIntent,
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    PendingIntent.getService(
                        applicationContext,
                        2048,
                        restartIntent,
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
                val alarmManager = getSystemService(AlarmManager::class.java)
                alarmManager?.set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 800L,
                    pendingIntent
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMicMonitorInternal()

        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        runCatching { audioManager.unregisterAudioPlaybackCallback(playbackCallback) }
        runCatching { unregisterReceiver(audioEffectSessionReceiver) }

        synchronized(dspLock) {
            attachedEffects.values.forEach { effect ->
                runCatching {
                    effect.enabled = false
                    effect.release()
                }
            }
            attachedEffects.clear()
            sessionMetadata.clear()
        }

        _state.update { it.copy(isServiceRunning = false, hookedSessions = emptyList()) }
        serviceScope.cancel()
    }

    // -------------------------------------------------------------------------
    // Public Controls (Called by Jetpack Compose UI or Notification Actions)
    // -------------------------------------------------------------------------

    fun setMasterBypass(bypass: Boolean) {
        updateStateAndApply { it.copy(masterBypass = bypass) }
    }

    fun selectPreset(preset: MagicPreset) {
        if (preset == MagicPreset.CUSTOM) {
            updateStateAndApply { it.copy(activePreset = MagicPreset.CUSTOM) }
            return
        }
        val clampedBands = preset.bandsDb.map { it.coerceIn(-12f, 12f) }
        updateStateAndApply {
            it.copy(
                activePreset = preset,
                eqBandsDb = clampedBands
            )
        }
    }

    fun cycleNextOfficialPreset() {
        val presets = MagicPreset.OFFICIAL_PRESETS
        val currentIdx = presets.indexOf(_state.value.activePreset)
        val next = presets[(currentIdx + 1) % presets.size]
        selectPreset(next)
    }

    fun setEqBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0..9) return
        val clamped = gainDb.coerceIn(-12f, 12f)
        updateStateAndApply { current ->
            val updated = current.eqBandsDb.toMutableList()
            updated[bandIndex] = clamped
            current.copy(
                activePreset = MagicPreset.CUSTOM,
                eqBandsDb = updated
            )
        }
    }

    fun resetEqFlat() {
        updateStateAndApply {
            it.copy(
                activePreset = MagicPreset.CUSTOM,
                eqBandsDb = List(10) { 0f },
                bassBoostDb = 0f,
                voiceForwardDb = 0f
            )
        }
    }

    fun setBassBoost(bassBoostDb: Float) {
        updateStateAndApply {
            it.copy(bassBoostDb = bassBoostDb.coerceIn(0f, 6f))
        }
    }

    fun setVoiceForward(voiceForwardDb: Float) {
        updateStateAndApply {
            it.copy(voiceForwardDb = voiceForwardDb.coerceIn(0f, 6f))
        }
    }

    fun setSurroundSubwoofer(enabled: Boolean) {
        updateStateAndApply { it.copy(surroundSubwooferEnabled = enabled) }
    }

    fun setSpatialFieldLevel(level: Float) {
        updateStateAndApply { it.copy(spatialFieldLevel = level.coerceIn(0f, 1f)) }
    }

    fun setUltimateVolumeStep(step: Int) {
        updateStateAndApply { it.copy(ultimateVolumeStep = step.coerceIn(0, 150)) }
    }

    fun setNightMode(enabled: Boolean) {
        updateStateAndApply { it.copy(nightModeEnabled = enabled) }
    }

    fun setAiPrivacyCallMode(enabled: Boolean) {
        updateStateAndApply { it.copy(aiPrivacyCallEnabled = enabled) }
    }

    fun setMicMonitorMode(mode: MicMonitorMode) {
        val currentDevice = _state.value.activeOutputDevice
        val isSpeaker = currentDevice == AudioOutputDevice.BUILT_IN_SPEAKER

        if (mode == MicMonitorMode.OFF) {
            stopMicMonitorInternal()
            _state.update {
                it.copy(
                    micMonitorMode = MicMonitorMode.OFF,
                    micMonitorAutoMuted = false,
                    micPermissionMissing = false
                )
            }
            promoteToForeground(includeMicrophoneType = false)
            scheduleSaveForDevice(currentDevice)
            return
        }

        val hasRecordPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasRecordPermission) {
            stopMicMonitorInternal()
            _state.update {
                it.copy(
                    micMonitorMode = MicMonitorMode.OFF,
                    micMonitorAutoMuted = false,
                    micPermissionMissing = true
                )
            }
            promoteToForeground(includeMicrophoneType = false)
            return
        }

        _state.update {
            it.copy(
                micMonitorMode = mode,
                micMonitorAutoMuted = isSpeaker,
                micPermissionMissing = false
            )
        }
        scheduleSaveForDevice(currentDevice)

        if (isSpeaker) {
            // Prevent acoustic feedback in Infinix speaker mode
            stopMicMonitorInternal()
            promoteToForeground(includeMicrophoneType = false)
        } else {
            promoteToForeground(includeMicrophoneType = true)
            startMicMonitorInternal()
        }
    }

    fun rescanActiveSessions() {
        attachEffectToSession(0, packageName, "GlobalMix")
        handlePlaybackConfigChanged(audioManager.activePlaybackConfigurations)
    }

    // -------------------------------------------------------------------------
    // State Update + Persistence
    // -------------------------------------------------------------------------

    private inline fun updateStateAndApply(crossinline transform: (DspEngineState) -> DspEngineState) {
        val updated = synchronized(dspLock) {
            val next = transform(_state.value)
            _state.value = next
            next
        }
        applyDspParametersLocked(updated)
        updateNotification(updated)
        scheduleSaveForDevice(updated.activeOutputDevice)
    }

    private fun scheduleSaveForDevice(device: AudioOutputDevice) {
        saveJob?.cancel()
        val snapshot = _state.value
        saveJob = serviceScope.launch {
            delay(200L)
            saveDevicePresetState(device, snapshot)
        }
    }

    private fun saveDevicePresetState(device: AudioOutputDevice, snapshot: DspEngineState) {
        val prefix = "dev_${device.storageKey}_"
        prefs.edit().apply {
            putString("${prefix}preset", snapshot.activePreset.name)
            snapshot.eqBandsDb.forEachIndexed { idx, value ->
                putFloat("${prefix}eq_$idx", value.coerceIn(-12f, 12f))
            }
            putFloat("${prefix}bass_boost", snapshot.bassBoostDb.coerceIn(0f, 6f))
            putFloat("${prefix}voice_forward", snapshot.voiceForwardDb.coerceIn(0f, 6f))
            putBoolean("${prefix}surround_sub", snapshot.surroundSubwooferEnabled)
            putFloat("${prefix}spatial_field", snapshot.spatialFieldLevel.coerceIn(0f, 1f))
            putInt("${prefix}ultimate_vol_step", snapshot.ultimateVolumeStep.coerceIn(0, 150))
            putBoolean("${prefix}night_mode", snapshot.nightModeEnabled)
            putBoolean("${prefix}privacy_call", snapshot.aiPrivacyCallEnabled)
            putBoolean("${prefix}master_bypass", snapshot.masterBypass)
            putString("${prefix}mic_mode", snapshot.micMonitorMode.name)
            apply()
        }
    }

    private fun loadDevicePresetState(device: AudioOutputDevice): DspEngineState {
        val prefix = "dev_${device.storageKey}_"
        val defaultPreset = when (device) {
            AudioOutputDevice.BUILT_IN_SPEAKER -> MagicPreset.ENERGETIC_BGM
            AudioOutputDevice.WIRED_HEADSET -> MagicPreset.CLEAR_VOCALS
            AudioOutputDevice.BLUETOOTH -> MagicPreset.STRONG_BASS
        }

        val presetName = prefs.getString("${prefix}preset", defaultPreset.name) ?: defaultPreset.name
        val preset = runCatching { MagicPreset.valueOf(presetName) }.getOrDefault(defaultPreset)

        val bands = (0..9).map { idx ->
            prefs.getFloat("${prefix}eq_$idx", preset.bandsDb[idx]).coerceIn(-12f, 12f)
        }

        val savedMicModeName = prefs.getString("${prefix}mic_mode", MicMonitorMode.OFF.name)
        val savedMicMode = runCatching {
            MicMonitorMode.valueOf(savedMicModeName ?: MicMonitorMode.OFF.name)
        }.getOrDefault(MicMonitorMode.OFF)

        val isSpeaker = device == AudioOutputDevice.BUILT_IN_SPEAKER
        val effectiveMicMode = if (savedMicMode != MicMonitorMode.OFF &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            MicMonitorMode.OFF
        } else {
            savedMicMode
        }

        return _state.value.copy(
            activeOutputDevice = device,
            activePreset = preset,
            eqBandsDb = bands,
            bassBoostDb = prefs.getFloat("${prefix}bass_boost", 0f).coerceIn(0f, 6f),
            voiceForwardDb = prefs.getFloat("${prefix}voice_forward", 0f).coerceIn(0f, 6f),
            surroundSubwooferEnabled = prefs.getBoolean("${prefix}surround_sub", true),
            spatialFieldLevel = prefs.getFloat("${prefix}spatial_field", 0.45f).coerceIn(0f, 1f),
            ultimateVolumeStep = prefs.getInt("${prefix}ultimate_vol_step", 0).coerceIn(0, 150),
            nightModeEnabled = prefs.getBoolean("${prefix}night_mode", false),
            aiPrivacyCallEnabled = prefs.getBoolean("${prefix}privacy_call", false),
            masterBypass = prefs.getBoolean("${prefix}master_bypass", false),
            micMonitorMode = effectiveMicMode,
            micMonitorAutoMuted = isSpeaker && effectiveMicMode != MicMonitorMode.OFF
        )
    }

    // -------------------------------------------------------------------------
    // Audio Device Detection & Automatic Switching
    // -------------------------------------------------------------------------

    private fun detectActiveOutputDevice(): AudioOutputDevice {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var hasWired = false
        var hasBluetooth = false

        for (device in outputs) {
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> hasWired = true

                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_HEARING_AID -> hasBluetooth = true

                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                    ) {
                        hasBluetooth = true
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                    ) {
                        hasBluetooth = true
                    }
                }
            }
        }

        return when {
            hasWired -> AudioOutputDevice.WIRED_HEADSET
            hasBluetooth -> AudioOutputDevice.BLUETOOTH
            else -> AudioOutputDevice.BUILT_IN_SPEAKER
        }
    }

    private fun refreshOutputDeviceAndReloadPreset() {
        val previousSnapshot = _state.value
        val newDevice = detectActiveOutputDevice()
        if (newDevice == previousSnapshot.activeOutputDevice) return

        // Persist outgoing device profile immediately before switching
        saveDevicePresetState(previousSnapshot.activeOutputDevice, previousSnapshot)

        val reloaded = loadDevicePresetState(newDevice).copy(
            isServiceRunning = true,
            hookedSessions = previousSnapshot.hookedSessions,
            dynamicsSupported = previousSnapshot.dynamicsSupported
        )
        _state.value = reloaded

        if (newDevice == AudioOutputDevice.BUILT_IN_SPEAKER) {
            // Auto-mute monitor in speaker mode to prevent feedback
            stopMicMonitorInternal()
            promoteToForeground(includeMicrophoneType = false)
        } else if (reloaded.micMonitorMode != MicMonitorMode.OFF) {
            promoteToForeground(includeMicrophoneType = true)
            startMicMonitorInternal()
        } else {
            promoteToForeground(includeMicrophoneType = false)
        }

        applyDspParametersLocked(reloaded)
        updateNotification(reloaded)
    }

    // -------------------------------------------------------------------------
    // Dynamic Session Hooking (Session 0 + PlaybackCallback + Effect Broadcast)
    // -------------------------------------------------------------------------

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerEffectControlReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                audioEffectSessionReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(audioEffectSessionReceiver, filter)
        }
    }

    private fun handlePlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
        val activePlaybackSessions = mutableMapOf<Int, Pair<String, String>>()

        for (config in configs) {
            val attrs = config.audioAttributes
            val usage = attrs.usage
            val isTargetStream = usage == AudioAttributes.USAGE_MEDIA ||
                usage == AudioAttributes.USAGE_GAME ||
                usage == AudioAttributes.USAGE_UNKNOWN ||
                usage == AudioAttributes.USAGE_VOICE_COMMUNICATION ||
                usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE

            if (!isTargetStream) continue

            val sessionId = extractSessionIdCompat(config) ?: continue
            if (sessionId <= 0 || excludedMonitorSessions.contains(sessionId)) continue

            val pkg = extractPackageNameCompat(config) ?: "media.session"
            val label = resolveAppLabel(pkg)
            activePlaybackSessions[sessionId] = pkg to label
        }

        var changed = false
        for ((sessionId, info) in activePlaybackSessions) {
            if (!attachedEffects.containsKey(sessionId)) {
                attachEffectToSession(
                    sessionId = sessionId,
                    packageName = info.first,
                    origin = "PlaybackCallback"
                )
                changed = true
            }
        }

        // Release playback-callback-only sessions that have completely stopped
        val toRemove = mutableListOf<Int>()
        for ((sessionId, meta) in sessionMetadata) {
            if (sessionId != 0 &&
                meta.origin == "PlaybackCallback" &&
                !activePlaybackSessions.containsKey(sessionId)
            ) {
                toRemove.add(sessionId)
            }
        }
        for (sessionId in toRemove) {
            detachEffectFromSession(sessionId)
            changed = true
        }

        if (changed) {
            syncHookedSessionsList()
        }
    }

    private fun extractSessionIdCompat(config: AudioPlaybackConfiguration): Int? {
        return runCatching {
            val method = config.javaClass.getMethod("getSessionId")
            (method.invoke(config) as? Int)
        }.getOrNull()
    }

    private fun extractPackageNameCompat(config: AudioPlaybackConfiguration): String? {
        return runCatching {
            val uidMethod = config.javaClass.getMethod("getClientUid")
            val uid = uidMethod.invoke(config) as? Int ?: return@runCatching null
            packageManager.getPackagesForUid(uid)?.firstOrNull()
        }.getOrNull()
    }

    private fun resolveAppLabel(pkg: String): String {
        if (pkg == packageName) return "Global Mix (Session 0)"
        if (pkg == "media.session" || pkg == "unknown") return "Active Media Stream"
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrElse { pkg.substringAfterLast('.') }
    }

    private fun attachEffectToSession(sessionId: Int, packageName: String, origin: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (sessionId < 0 || excludedMonitorSessions.contains(sessionId)) return

        synchronized(dspLock) {
            if (attachedEffects.containsKey(sessionId)) return

            val dp = createDynamicsProcessingInstance(sessionId) ?: return
            attachedEffects[sessionId] = dp
            sessionMetadata[sessionId] = HookedSessionInfo(
                sessionId = sessionId,
                packageName = packageName,
                appLabel = if (sessionId == 0) "System Mix (Session 0)" else resolveAppLabel(packageName),
                origin = origin
            )
            applySingleEffectParameters(dp, _state.value)
        }

        syncHookedSessionsList()
        updateNotification(_state.value)
    }

    private fun detachEffectFromSession(sessionId: Int) {
        if (sessionId == 0) return
        synchronized(dspLock) {
            attachedEffects.remove(sessionId)?.let { dp ->
                runCatching {
                    dp.enabled = false
                    dp.release()
                }
            }
            sessionMetadata.remove(sessionId)
        }
        syncHookedSessionsList()
        updateNotification(_state.value)
    }

    private fun syncHookedSessionsList() {
        val list = sessionMetadata.values.sortedBy { it.sessionId }
        _state.update { it.copy(hookedSessions = list) }
    }

    // -------------------------------------------------------------------------
    // Hardware-Accelerated DynamicsProcessing Pipeline (API 28+)
    // -------------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createDynamicsProcessingInstance(audioSessionId: Int): DynamicsProcessing? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null

        val cfg = buildInitialDynamicsConfig()
        val prioritiesToTry = intArrayOf(Int.MAX_VALUE, 0)

        for (priority in prioritiesToTry) {
            val instance = runCatching {
                DynamicsProcessing(priority, audioSessionId, cfg)
            }.getOrNull()
            if (instance != null) {
                return instance
            }
        }
        Log.w(TAG, "Failed to attach DynamicsProcessing to session $audioSessionId")
        return null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun buildInitialDynamicsConfig(): DynamicsProcessing.Config {
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            true, 10,
            true, 3,
            true, 10,
            true
        )
        builder.setPreferredFrameDuration(FRAME_DURATION_MS)
        builder.setInputGainAllChannelsTo(0.0f)

        val initialPreEq = DynamicsProcessing.Eq(true, true, 10)
        val initialPostEq = DynamicsProcessing.Eq(true, true, 10)
        for (band in 0 until 10) {
            val cutoff = EQ_UPPER_CUTOFF_HZ[band]
            initialPreEq.setBand(band, DynamicsProcessing.EqBand(true, cutoff, 0.0f))
            initialPostEq.setBand(band, DynamicsProcessing.EqBand(true, cutoff, 0.0f))
        }
        builder.setPreEqAllChannelsTo(initialPreEq)
        builder.setPostEqAllChannelsTo(initialPostEq)

        val initialMbc = DynamicsProcessing.Mbc(true, true, 3)
        for (band in 0 until 3) {
            initialMbc.setBand(
                band,
                DynamicsProcessing.MbcBand(
                    true,
                    MBC_UPPER_CUTOFF_HZ[band],
                    10.0f,
                    120.0f,
                    2.0f,
                    -18.0f,
                    6.0f,
                    -80.0f,
                    1.0f,
                    0.0f,
                    0.0f
                )
            )
        }
        builder.setMbcAllChannelsTo(initialMbc)

        builder.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(
                true,
                true,
                0,
                1.0f,
                60.0f,
                10.0f,
                LIMITER_THRESHOLD_DB,
                0.0f
            )
        )

        return builder.build()
    }

    private fun applyCurrentDspParameters() {
        applyDspParametersLocked(_state.value)
    }

    private fun applyDspParametersLocked(engineState: DspEngineState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        val effectivePostEq = computePostEqCurve(engineState)
        _state.update { it.copy(lastEffectivePostEqDb = effectivePostEq.toList()) }

        synchronized(dspLock) {
            for ((_, dp) in attachedEffects) {
                applySingleEffectParameters(dp, engineState, effectivePostEq)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applySingleEffectParameters(
        dp: DynamicsProcessing,
        engineState: DspEngineState,
        precomputedPostEq: FloatArray = computePostEqCurve(engineState)
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        runCatching {
            val isBypassed = engineState.masterBypass

            // Keep hardware effect enabled cleanly and use zeroed neutral gains on bypass
            // or toggle enabled state cleanly without recreating the native effect.
            if (isBypassed) {
                dp.setInputGainAllChannelsTo(0.0f)
                for (i in 0 until 10) {
                    val cutoff = EQ_UPPER_CUTOFF_HZ[i]
                    dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoff, 0.0f))
                    dp.setPostEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoff, 0.0f))
                }
                for (i in 0 until 3) {
                    dp.setMbcBandAllChannelsTo(
                        i,
                        DynamicsProcessing.MbcBand(
                            false,
                            MBC_UPPER_CUTOFF_HZ[i],
                            10.0f,
                            120.0f,
                            1.0f,
                            0.0f,
                            6.0f,
                            -80.0f,
                            1.0f,
                            0.0f,
                            0.0f
                        )
                    )
                }
                dp.setLimiterAllChannelsTo(
                    DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        1.0f,
                        60.0f,
                        10.0f,
                        LIMITER_THRESHOLD_DB,
                        0.0f
                    )
                )
                dp.enabled = false
                return@runCatching
            }

            if (!dp.enabled) {
                dp.enabled = true
            }

            // 1. 150-Step Ultimate Volume (0..150% -> 0.0f..15.0f dB pre-gain)
            val preGainDb = engineState.ultimateVolumePreGainDb.coerceIn(0.0f, 15.0f)
            dp.setInputGainAllChannelsTo(preGainDb)

            // 2. 10-Band Pre-EQ: User/Preset EQ + Bass Boost + Dual 1216 AI Surround Subwoofer
            val bassBoost = engineState.bassBoostDb.coerceIn(0.0f, 6.0f)
            val subBoost = if (engineState.surroundSubwooferEnabled) 1.0f else 0.0f
            for (i in 0 until 10) {
                val baseGain = engineState.eqBandsDb.getOrElse(i) { 0.0f }.coerceIn(-12.0f, 12.0f)
                val bassShelf = when (i) {
                    0 -> bassBoost * 1.00f + subBoost * 3.2f
                    1 -> bassBoost * 0.85f + subBoost * 2.4f
                    2 -> bassBoost * 0.45f + subBoost * 1.0f
                    else -> 0.0f
                }
                val clampedPreGain = (baseGain + bassShelf).coerceIn(-15.0f, 15.0f)
                dp.setPreEqBandAllChannelsTo(
                    i,
                    DynamicsProcessing.EqBand(true, EQ_UPPER_CUTOFF_HZ[i], clampedPreGain)
                )
            }

            // 3. 3-Band Multi-Band Compressor:
            //    Combines Night Mode dynamic leveling + Dual 1216 Spatial Field + MLBB punch
            val spatial = engineState.spatialFieldLevel.coerceIn(0.0f, 1.0f)
            val night = engineState.nightModeEnabled
            val isMlbb = engineState.activePreset == MagicPreset.MLBB_COMPETITIVE

            for (band in 0 until 3) {
                val mbcBand = buildMbcBand(
                    bandIndex = band,
                    nightMode = night,
                    subwoofer = engineState.surroundSubwooferEnabled,
                    spatialLevel = spatial,
                    mlbbCompetitive = isMlbb
                )
                dp.setMbcBandAllChannelsTo(band, mbcBand)
            }

            // 4. 10-Band Post-EQ:
            //    Infinix Hot 60 5G Hardware Compensation + Voice Forward + AI Privacy Call + Spatial Air
            for (i in 0 until 10) {
                val postGain = precomputedPostEq[i].coerceIn(-15.0f, 15.0f)
                dp.setPostEqBandAllChannelsTo(
                    i,
                    DynamicsProcessing.EqBand(true, EQ_UPPER_CUTOFF_HZ[i], postGain)
                )
            }

            // 5. Final Zero-Clip Brickwall Limiter (-0.2 dB)
            dp.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    1.0f,
                    60.0f,
                    10.0f,
                    LIMITER_THRESHOLD_DB,
                    0.0f
                )
            )
        }.onFailure { err ->
            Log.w(TAG, "Error applying DynamicsProcessing parameters", err)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun buildMbcBand(
        bandIndex: Int,
        nightMode: Boolean,
        subwoofer: Boolean,
        spatialLevel: Float,
        mlbbCompetitive: Boolean
    ): DynamicsProcessing.MbcBand {
        val cutoff = MBC_UPPER_CUTOFF_HZ[bandIndex]

        if (nightMode) {
            val threshold = when (bandIndex) {
                0 -> -28.0f
                1 -> -26.0f
                else -> -25.0f
            }
            val ratio = when (bandIndex) {
                0 -> 4.2f
                1 -> 3.8f
                else -> 3.5f
            }
            return DynamicsProcessing.MbcBand(
                true,
                cutoff,
                5.0f,
                180.0f,
                ratio,
                threshold,
                8.0f,
                -80.0f,
                1.0f,
                0.0f,
                3.5f
            )
        }

        return when (bandIndex) {
            0 -> {
                // Band 0 (0..220 Hz): Sub-bass & Dual 1216 Subwoofer control
                val subPre = if (subwoofer) 1.5f else 0.0f
                val subPost = if (subwoofer) 1.5f else 0.0f
                DynamicsProcessing.MbcBand(
                    true,
                    cutoff,
                    12.0f,
                    130.0f,
                    if (subwoofer) 2.4f else 1.8f,
                    -20.0f,
                    6.0f,
                    -80.0f,
                    1.0f,
                    subPre,
                    subPost
                )
            }
            1 -> {
                // Band 1 (220..3200 Hz): Vocal body, footstep transients & spatial punch
                val midPunch = (spatialLevel * 1.4f) + (if (mlbbCompetitive) 1.0f else 0.0f)
                DynamicsProcessing.MbcBand(
                    true,
                    cutoff,
                    if (mlbbCompetitive) 6.0f else 10.0f,
                    100.0f,
                    if (mlbbCompetitive) 2.6f else 2.0f,
                    -18.0f,
                    6.0f,
                    -80.0f,
                    1.0f,
                    0.0f,
                    midPunch.coerceIn(0.0f, 4.0f)
                )
            }
            else -> {
                // Band 2 (3200..20000 Hz): Spatial air & crispness
                val airGain = (spatialLevel * 2.2f) + (if (mlbbCompetitive) 0.8f else 0.0f)
                DynamicsProcessing.MbcBand(
                    true,
                    cutoff,
                    8.0f,
                    90.0f,
                    2.0f,
                    -19.0f,
                    6.0f,
                    -80.0f,
                    1.0f,
                    0.0f,
                    airGain.coerceIn(0.0f, 4.0f)
                )
            }
        }
    }

    private fun computePostEqCurve(engineState: DspEngineState): FloatArray {
        val result = FloatArray(10)

        // 1. Infinix Hot 60 5G hardware output compensation
        val deviceComp = when (engineState.activeOutputDevice) {
            AudioOutputDevice.BUILT_IN_SPEAKER -> INFINIX_SPEAKER_COMP_DB
            AudioOutputDevice.BLUETOOTH -> BLUETOOTH_54_COMP_DB
            AudioOutputDevice.WIRED_HEADSET -> WIRED_HIRES_COMP_DB
        }

        // 2. Voice Forward (0..6 dB bell centered on 1 kHz - 2.5 kHz)
        val voiceForward = engineState.voiceForwardDb.coerceIn(0.0f, 6.0f)

        // 3. Spatial Field upper-mid & air expansion
        val spatial = engineState.spatialFieldLevel.coerceIn(0.0f, 1.0f)

        // 4. AI Privacy Call Mode (cuts >3.5 kHz leakage, boosts 1.2-2.8 kHz close-ear clarity)
        val privacy = engineState.aiPrivacyCallEnabled

        for (i in 0 until 10) {
            var gain = deviceComp[i]

            gain += when (i) {
                4 -> voiceForward * 0.25f // 500 Hz
                5 -> voiceForward * 0.75f // 1000 Hz
                6 -> voiceForward * 1.00f // 2000 Hz
                7 -> voiceForward * 0.55f // 4000 Hz
                else -> 0.0f
            }

            gain += when (i) {
                7 -> spatial * 1.0f // 4000 Hz
                8 -> spatial * 1.8f // 8000 Hz
                9 -> spatial * 2.2f // 16000 Hz
                else -> 0.0f
            }

            if (privacy) {
                gain += when (i) {
                    0 -> -4.0f  // 31 Hz
                    1 -> -3.0f  // 62 Hz
                    5 -> 2.5f   // 1000 Hz (1.2 kHz border)
                    6 -> 4.5f   // 2000 Hz (1.2-2.8 kHz close-ear clarity)
                    7 -> -8.0f  // 4000 Hz (>3.5 kHz leakage cut)
                    8 -> -12.0f // 8000 Hz
                    9 -> -12.0f // 16000 Hz
                    else -> 0.0f
                }
            }

            result[i] = gain.coerceIn(-15.0f, 15.0f)
        }

        return result
    }

    // -------------------------------------------------------------------------
    // Cinematic Directional Mic Monitor (AudioRecord + AudioTrack)
    // -------------------------------------------------------------------------

    private fun isMicMonitorStreamingActive(engineState: DspEngineState): Boolean {
        return engineState.micMonitorMode != MicMonitorMode.OFF &&
            engineState.activeOutputDevice != AudioOutputDevice.BUILT_IN_SPEAKER &&
            !engineState.micMonitorAutoMuted
    }

    @SuppressLint("MissingPermission")
    private fun startMicMonitorInternal() {
        if (monitorJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        monitorJob = serviceScope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            val minRecordBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1024)

            val minTrackBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1024)

            val frameSamples = 256
            val recordBufferBytes = maxOf(minRecordBuf, frameSamples * 4)
            val trackBufferBytes = maxOf(minTrackBuf, frameSamples * 4)

            var audioRecord: AudioRecord? = null
            var audioTrack: AudioTrack? = null

            try {
                audioRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE_HZ)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(recordBufferBytes)
                    .build()

                val trackBuilder = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE_HZ)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(trackBufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }

                audioTrack = trackBuilder.build()
                excludedMonitorSessions.add(audioTrack.audioSessionId)

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED ||
                    audioTrack.state != AudioTrack.STATE_INITIALIZED
                ) {
                    Log.w(TAG, "AudioRecord/AudioTrack failed to initialize for mic monitor")
                    return@launch
                }

                audioRecord.startRecording()
                audioTrack.play()

                val pcmBuffer = ShortArray(frameSamples)

                // Lightweight single-pole filter state for zero-latency processing
                var hpPrevX = 0.0f
                var hpPrevY = 0.0f
                var lpPrevY = 0.0f
                var envFollower = 0.0f

                // Precomputed filter coefficients at 48 kHz
                val dt = 1.0f / SAMPLE_RATE_HZ
                val hpIsoRc = 1.0f / (2.0f * PI.toFloat() * 140.0f)
                val hpIsoAlpha = hpIsoRc / (hpIsoRc + dt)

                val hpVoiceRc = 1.0f / (2.0f * PI.toFloat() * 300.0f)
                val hpVoiceAlpha = hpVoiceRc / (hpVoiceRc + dt)

                val lpVoiceRc = 1.0f / (2.0f * PI.toFloat() * 3400.0f)
                val lpVoiceAlpha = dt / (lpVoiceRc + dt)

                val hpArriRc = 1.0f / (2.0f * PI.toFloat() * 60.0f)
                val hpArriAlpha = hpArriRc / (hpArriRc + dt)

                while (isActive) {
                    val snapshot = _state.value
                    if (snapshot.micMonitorMode == MicMonitorMode.OFF ||
                        snapshot.activeOutputDevice == AudioOutputDevice.BUILT_IN_SPEAKER
                    ) {
                        break
                    }

                    val read = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
                    if (read <= 0) continue

                    val mode = snapshot.micMonitorMode
                    for (i in 0 until read) {
                        val x = pcmBuffer[i] / 32768.0f

                        val processed = when (mode) {
                            MicMonitorMode.NOISE_ISOLATION -> {
                                // 140 Hz rumble cut + soft noise floor gate
                                val hp = hpIsoAlpha * (hpPrevY + x - hpPrevX)
                                hpPrevX = x
                                hpPrevY = hp

                                val level = abs(hp)
                                envFollower = if (level > envFollower) {
                                    0.85f * envFollower + 0.15f * level
                                } else {
                                    0.996f * envFollower + 0.004f * level
                                }
                                val gateGain = ((envFollower - 0.010f) / 0.025f).coerceIn(0.08f, 1.0f)
                                tanh(hp * gateGain * 1.4f)
                            }

                            MicMonitorMode.SUBJECT_VOICE_BOOST -> {
                                // 300 Hz - 3.4 kHz vocal focus + clean soft-clipped boost
                                val hp = hpVoiceAlpha * (hpPrevY + x - hpPrevX)
                                hpPrevX = x
                                hpPrevY = hp

                                lpPrevY += lpVoiceAlpha * (hp - lpPrevY)
                                val boosted = (lpPrevY * 1.9f) + (hp * 0.25f)
                                tanh(boosted)
                            }

                            MicMonitorMode.ARRI_AMBIENT -> {
                                // Wideband cinematic ambient pass with 60 Hz wind filter
                                val hp = hpArriAlpha * (hpPrevY + x - hpPrevX)
                                hpPrevX = x
                                hpPrevY = hp
                                tanh(hp * 1.25f)
                            }

                            MicMonitorMode.OFF -> 0.0f
                        }

                        pcmBuffer[i] = (processed * 32767.0f)
                            .toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                    }

                    audioTrack.write(pcmBuffer, 0, read, AudioTrack.WRITE_BLOCKING)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Cinematic Mic Monitor stopped", t)
            } finally {
                audioTrack?.let { track ->
                    excludedMonitorSessions.remove(track.audioSessionId)
                    runCatching {
                        track.pause()
                        track.flush()
                        track.release()
                    }
                }
                audioRecord?.let { rec ->
                    runCatching {
                        rec.stop()
                        rec.release()
                    }
                }
            }
        }
    }

    private fun stopMicMonitorInternal() {
        monitorJob?.cancel()
        monitorJob = null
    }

    // -------------------------------------------------------------------------
    // Notification & Foreground Service Management (XOS Background Persistence)
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HonorSound Studio DSP Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps system-wide MagicOS 11 audio processing active in background"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun promoteToForeground(includeMicrophoneType: Boolean) {
        val notification = buildNotification(_state.value)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && includeMicrophoneType) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun updateNotification(engineState: DspEngineState) {
        if (!engineState.isServiceRunning) return
        notificationManager.notify(NOTIFICATION_ID, buildNotification(engineState))
    }

    private fun buildNotification(engineState: DspEngineState): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                100,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val toggleBypassIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, HonorMagicDspService::class.java).apply {
                action = ACTION_TOGGLE_BYPASS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextPresetIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, HonorMagicDspService::class.java).apply {
                action = ACTION_NEXT_PRESET
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            103,
            Intent(this, HonorMagicDspService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusPrefix = if (engineState.masterBypass) "BYPASSED" else "ACTIVE"
        val title = "HonorSound Studio • $statusPrefix"
        val subtitle = buildString {
            append(engineState.activeOutputDevice.displayName)
            append(" • ")
            append(engineState.activePreset.displayName)
            append(" • ")
            append(engineState.hookedSessions.size)
            append(" sessions")
        }

        val smallIcon = android.R.drawable.stat_sys_headset

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .apply {
                if (contentPendingIntent != null) {
                    setContentIntent(contentPendingIntent)
                }
            }
            .addAction(
                0,
                if (engineState.masterBypass) "Enable DSP" else "Bypass DSP",
                toggleBypassIntent
            )
            .addAction(
                0,
                "Next Preset",
                nextPresetIntent
            )
            .addAction(
                0,
                "Stop",
                stopIntent
            )
            .build()
    }
}
