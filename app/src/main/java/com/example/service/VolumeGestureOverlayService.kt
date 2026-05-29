package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.VolumeGestureLog
import com.example.gesture.VolumeGestureDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class VolumeGestureOverlayService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "volume_gesture_overlay"
        private const val NOTIFICATION_ID = 1001

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
    private val isManualControlVisibleState = MutableStateFlow(false)

    private lateinit var gestureDetector: VolumeGestureDetector

    // Settings cached locally
    private var isGestureEnabled = true
    private var triggerSide = "RIGHT"
    private var triggerHeightPercent = 0.6f
    private var triggerWidthDp = 24
    private var sensitivityPixels = 40f
    private var isHapticFeedbackEnabled = true
    private var isSoundFeedbackEnabled = false
    private var isManualControlAfterGestureEnabled = false
    private var isManualControlSwipeEnabled = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        startAsForegroundService()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = getSharedPreferences("com.example.volume_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        loadSettings()

        composeViewHelper = ServiceComposeViewHelper(this).apply { onCreate() }

        initGestureDetector()
        createFloatingOverlay()
    }

    private fun startAsForegroundService() {
        val notification = buildForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildForegroundNotification(): Notification {
        createNotificationChannel()

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }
        val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Controle de Volume por Gesto")
            .setContentText("Controle lateral de volume ativo")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Controle de volume por gesto",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantem o controlador lateral de volume ativo"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
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
        isManualControlAfterGestureEnabled = prefs.getBoolean("pref_manual_control_after_gesture", false)
        isManualControlSwipeEnabled = prefs.getBoolean("pref_manual_control_swipe", true)

        if (::gestureDetector.isInitialized) {
            gestureDetector.sensitivityPixels = sensitivityPixels
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        loadSettings()
        if (!isManualControlAfterGestureEnabled && isManualControlVisibleState.value) {
            closeManualVolumeControl()
        }
        updateOverlayDimensions()
    }

    private fun initGestureDetector() {
        gestureDetector = VolumeGestureDetector(
            context = this,
            onGestureStart = {
                isEngagedState.value = true
                isManualControlVisibleState.value = false
                triggerHapticFeedback(true) // Start tap feedback
                expandOverlayFullscreen()
            },
            onVolumeAdjusted = { direction, steps ->
                adjustSystemVolume(direction, steps)
            },
            onGestureEnd = { startVol, finalVol, dir, change ->
                if (isManualControlAfterGestureEnabled) {
                    isManualControlVisibleState.value = true
                    isEngagedState.value = true
                } else {
                    isManualControlVisibleState.value = false
                    isEngagedState.value = false
                    shrinkOverlayToHandle()
                }
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
            isEnabled = { isGestureEnabled },
            allowSingleFingerDrag = true
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

    private fun setSystemVolumePercent(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return

        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent.coerceIn(0, 100) / 100f) * max).roundToInt().coerceIn(0, max)
        if (target == current) return

        val flag = if (isSoundFeedbackEnabled) AudioManager.FLAG_PLAY_SOUND else 0
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, flag)
        updateCurrentVolumeState()
        triggerHapticFeedback(false)
    }

    private fun closeManualVolumeControl() {
        isManualControlVisibleState.value = false
        isEngagedState.value = false
        shrinkOverlayToHandle()
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
                if (isManualControlVisibleState.value) {
                    false
                } else {
                    gestureDetector.onTouchEvent(event)
                }
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
        val isManualControlVisible by isManualControlVisibleState.collectAsState()
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
                VolumeHudOsd(
                    volumePercent = volumePercent,
                    isManualMode = isManualControlVisible
                )
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun VolumeHudOsd(volumePercent: Int, isManualMode: Boolean) {
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
        var barHeightPx by remember { mutableStateOf(1) }
        var manualDragLastY by remember { mutableStateOf(0f) }
        var manualDragAccumulatedDelta by remember { mutableStateOf(0f) }

        fun setVolumeFromBarPosition(y: Float) {
            val ratio = 1f - (y / barHeightPx.coerceAtLeast(1)).coerceIn(0f, 1f)
            setSystemVolumePercent((ratio * 100).roundToInt())
        }

        fun processManualVerticalDrag(y: Float) {
            val deltaY = manualDragLastY - y
            manualDragAccumulatedDelta += deltaY
            manualDragLastY = y

            if (abs(manualDragAccumulatedDelta) >= sensitivityPixels) {
                val steps = (manualDragAccumulatedDelta / sensitivityPixels).toInt()
                if (steps != 0) {
                    val direction = if (steps > 0) "Aumento" else "Diminuição"
                    adjustSystemVolume(direction, abs(steps))
                    manualDragAccumulatedDelta -= steps * sensitivityPixels
                }
            }
        }

        fun Modifier.manualSwipeControlArea(): Modifier {
            if (!isManualMode || !isManualControlSwipeEnabled) return this

            return pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        manualDragLastY = event.y
                        manualDragAccumulatedDelta = 0f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        processManualVerticalDrag(event.y)
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> true
                    else -> false
                }
            }
        }

        Box(
            modifier = Modifier
                .width(if (isManualMode) 220.dp else 130.dp)
                .height(if (isManualMode) 390.dp else 340.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xE012121E)) // Dark slate translucent backing
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isManualMode) {
                IconButton(
                    onClick = { closeManualVolumeControl() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar controle",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            val speakerIcon = when {
                volumePercent == 0 -> Icons.Default.VolumeOff
                volumePercent < 30 -> Icons.Default.VolumeMute
                volumePercent < 70 -> Icons.Default.VolumeDown
                else -> Icons.Default.VolumeUp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (isManualMode) 14.dp else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .manualSwipeControlArea(),
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
                            .width(if (isManualMode) 54.dp else 42.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .onSizeChanged { barHeightPx = it.height.coerceAtLeast(1) }
                            .then(
                                if (isManualMode && isManualControlSwipeEnabled) {
                                    Modifier.pointerInteropFilter { event ->
                                        when (event.actionMasked) {
                                            MotionEvent.ACTION_DOWN,
                                            MotionEvent.ACTION_MOVE -> {
                                                setVolumeFromBarPosition(event.y)
                                                true
                                            }
                                            MotionEvent.ACTION_UP,
                                            MotionEvent.ACTION_CANCEL -> true
                                            else -> false
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            ),
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isManualMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ManualVolumeIconButton(
                            icon = Icons.Default.Remove,
                            contentDescription = "Diminuir volume",
                            onClick = { adjustSystemVolume("Diminuição", 1) }
                        )
                        Icon(
                            imageVector = speakerIcon,
                            contentDescription = "Volume",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        ManualVolumeIconButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Aumentar volume",
                            onClick = { adjustSystemVolume("Aumento", 1) }
                        )
                    }
                } else {
                    Icon(
                        imageVector = speakerIcon,
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun ManualVolumeIconButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        contentDescription: String,
        onClick: () -> Unit
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.09f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
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
