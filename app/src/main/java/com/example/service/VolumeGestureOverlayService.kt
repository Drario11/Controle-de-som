package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.VolumeGestureLog
import com.example.gesture.VolumeGestureDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class VolumeGestureOverlayService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        var isServiceRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: SharedPreferences
    private lateinit var composeViewHelper: ServiceComposeViewHelper

    private var triggerHandleComposeView: ComposeView? = null
    private var baseParams: WindowManager.LayoutParams? = null

    // Reactive states for the overlay
    private val isEngagedState = MutableStateFlow(false)
    private val currentVolumePercent = MutableStateFlow(0)
    private val gestureDirection = MutableStateFlow("Nenhum")
    private val isTriggerInvisibleState = MutableStateFlow(false)

    private lateinit var gestureDetector: VolumeGestureDetector

    // Settings cached locally
    private var isGestureEnabled = true
    private var triggerSide = "RIGHT"
    private var triggerHeightPercent = 0.6f
    private var triggerWidthDp = 24
    private var sensitivityPixels = 40f
    private var isHapticFeedbackEnabled = true
    private var isSoundFeedbackEnabled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = getSharedPreferences("com.example.volume_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        loadSettings()

        composeViewHelper = ServiceComposeViewHelper(this).apply { onCreate() }

        initGestureDetector()
        createFloatingOverlay()
    }

    private fun loadSettings() {
        isGestureEnabled = prefs.getBoolean("pref_gesture_enabled", true)
        triggerSide = prefs.getString("pref_trigger_side", "RIGHT") ?: "RIGHT"
        triggerHeightPercent = prefs.getFloat("pref_trigger_height", 0.6f)
        triggerWidthDp = prefs.getInt("pref_trigger_width", 24)
        sensitivityPixels = prefs.getFloat("pref_sensitivity", 40f)
        isHapticFeedbackEnabled = prefs.getBoolean("pref_haptic_feedback", true)
        isSoundFeedbackEnabled = prefs.getBoolean("pref_sound_feedback", false)
        isTriggerInvisibleState.value = prefs.getBoolean("pref_invisible_trigger", false)

        if (::gestureDetector.isInitialized) {
            gestureDetector.sensitivityPixels = sensitivityPixels
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        loadSettings()
        updateOverlayDimensions()
    }

    private fun initGestureDetector() {
        gestureDetector = VolumeGestureDetector(
            context = this,
            onGestureStart = {
                isEngagedState.value = true
                triggerHapticFeedback(true) // Start tap feedback
                expandOverlayFullscreen()
            },
            onVolumeAdjusted = { direction, steps ->
                adjustSystemVolume(direction, steps)
            },
            onGestureEnd = { startVol, finalVol, dir, change ->
                isEngagedState.value = false
                shrinkOverlayToHandle()
                if (change > 0) {
                    saveGestureLogToDb(startVol, finalVol, dir, change)
                }
            },
            getCurrentVolume = {
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            },
            getMaxVolume = {
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            },
            isEnabled = { isGestureEnabled }
        ).apply {
            sensitivityPixels = this@VolumeGestureOverlayService.sensitivityPixels
        }

        // Initialize current volume state
        updateCurrentVolumeState()
    }

    private fun updateCurrentVolumeState() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        currentVolumePercent.value = if (max > 0) ((current.toFloat() / max) * 100).roundToInt() else 0
    }

    private fun adjustSystemVolume(direction: String, steps: Int) {
        val flag = if (isSoundFeedbackEnabled) AudioManager.FLAG_PLAY_SOUND else 0
        val adjustType = if (direction == "Aumento") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER

        // Apply change incrementally for steps
        for (i in 0 until steps) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjustType, flag)
        }

        updateCurrentVolumeState()
        gestureDirection.value = direction
        triggerHapticFeedback(false) // Dynamic click feedback during scroll
    }

    private fun triggerHapticFeedback(isHeavy: Boolean) {
        if (!isHapticFeedbackEnabled) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val managers = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            managers?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (isHeavy) {
                    VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                it.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(if (isHeavy) 45 else 15)
            }
        }
    }

    private fun saveGestureLogToDb(startVol: Int, finalVol: Int, direction: String, change: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val startPercentage = if (max > 0) ((startVol.toFloat() / max) * 100).roundToInt() else 0
        val finalPercentage = if (max > 0) ((finalVol.toFloat() / max) * 100).roundToInt() else 0
        val percentChange = finalPercentage - startPercentage

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.volumeGestureLogDao().insertLog(
                VolumeGestureLog(
                    timestamp = System.currentTimeMillis(),
                    direction = direction,
                    changeAmount = percentChange,
                    startVolume = startPercentage,
                    finalVolume = finalPercentage
                )
            )
        }
    }

    private fun createFloatingOverlay() {
        val density = resources.displayMetrics.density
        val widthPx = (triggerWidthDp * density).roundToInt()
        val heightPx = (resources.displayMetrics.heightPixels * triggerHeightPercent).roundToInt()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val gravityValue = when (triggerSide) {
            "LEFT" -> Gravity.START or Gravity.CENTER_VERTICAL
            "BOTH" -> Gravity.END or Gravity.CENTER_VERTICAL // Put handle on right by default if both (or handle as needed)
            else -> Gravity.END or Gravity.CENTER_VERTICAL
        }

        baseParams = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = gravityValue
            x = 0
            y = 0
        }

        triggerHandleComposeView = composeViewHelper.createComposeView(this) {
            OverlayComposeUI()
        }.apply {
            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
            }
        }

        windowManager.addView(triggerHandleComposeView, baseParams)
    }

    private fun updateOverlayDimensions() {
        val view = triggerHandleComposeView ?: return
        val params = baseParams ?: return

        val density = resources.displayMetrics.density
        val widthPx = (triggerWidthDp * density).roundToInt()
        val heightPx = (resources.displayMetrics.heightPixels * triggerHeightPercent).roundToInt()

        params.width = widthPx
        params.height = heightPx
        params.gravity = when (triggerSide) {
            "LEFT" -> Gravity.START or Gravity.CENTER_VERTICAL
            "BOTH" -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.END or Gravity.CENTER_VERTICAL
        }

        if (!isEngagedState.value) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun expandOverlayFullscreen() {
        val view = triggerHandleComposeView ?: return
        val params = baseParams ?: return

        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.CENTER
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
        params.dimAmount = 0.15f // subtle background dimming when gesture is engaged!

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shrinkOverlayToHandle() {
        val view = triggerHandleComposeView ?: return
        val params = baseParams ?: return

        val density = resources.displayMetrics.density
        val widthPx = (triggerWidthDp * density).roundToInt()
        val heightPx = (resources.displayMetrics.heightPixels * triggerHeightPercent).roundToInt()

        params.width = widthPx
        params.height = heightPx
        params.gravity = when (triggerSide) {
            "LEFT" -> Gravity.START or Gravity.CENTER_VERTICAL
            "BOTH" -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.END or Gravity.CENTER_VERTICAL
        }
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        params.dimAmount = 0f

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Composable
    private fun OverlayComposeUI() {
        val engaged by isEngagedState.collectAsState()
        val volumePercent by currentVolumePercent.collectAsState()
        val isTriggerInvisible by isTriggerInvisibleState.collectAsState()
        val side = triggerSide

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 1. Edge handle - visible when IDLE to guide user (with high visual restraint, or hidden when invisible mode is enabled)
            if (!engaged && !isTriggerInvisible) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .padding(vertical = 24.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xAA3B82F6), // Blue accent
                                    Color(0xAA8B5CF6)  // Purple accent
                                )
                            )
                        )
                        .align(
                            if (side == "LEFT") Alignment.CenterStart else Alignment.CenterEnd
                        )
                )
            }

            // 2. Volume OSD Overlay (HUD) - shown in center when engaged
            AnimatedVisibility(
                visible = engaged,
                enter = scaleIn(animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit = scaleOut(animationSpec = tween(150)) + fadeOut()
            ) {
                VolumeHudOsd(volumePercent = volumePercent)
            }
        }
    }

    @Composable
    private fun VolumeHudOsd(volumePercent: Int) {
        val barColor = when {
            volumePercent > 75 -> Color(0xFFEF4444) // Red/Orange active style
            volumePercent > 40 -> Color(0xFF3B82F6) // Premium blue
            else -> Color(0xFF10B981) // Emerald green low
        }

        val animatedHeight by animateFloatAsState(
            targetValue = volumePercent / 100f,
            animationSpec = spring(stiffness = Spring.StiffnessHigh),
            label = "volume_height"
        )

        val wordStatus = when {
            volumePercent == 0 -> "Mutado"
            volumePercent < 35 -> "Baixo"
            volumePercent < 75 -> "Médio"
            else -> "Alto"
        }

        Box(
            modifier = Modifier
                .width(130.dp)
                .height(340.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xE012121E)) // Dark slate translucent backing
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Volume percentage
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$volumePercent%",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = wordStatus,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Modern vertical level scale bar
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(animatedHeight)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        barColor,
                                        barColor.copy(alpha = 0.6f)
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Speaker state icon
                val speakerIcon = when {
                    volumePercent == 0 -> Icons.Default.VolumeOff
                    volumePercent < 30 -> Icons.Default.VolumeMute
                    volumePercent < 70 -> Icons.Default.VolumeDown
                    else -> Icons.Default.VolumeUp
                }

                Icon(
                    imageVector = speakerIcon,
                    contentDescription = "Speaker Icon",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        prefs.unregisterOnSharedPreferenceChangeListener(this)

        triggerHandleComposeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        composeViewHelper.onDestroy()
        super.onDestroy()
    }
}
