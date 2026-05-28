package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.VolumeGestureLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.volumeGestureLogDao()

    private val _logs = MutableStateFlow<List<VolumeGestureLog>>(emptyList())
    val logs: StateFlow<List<VolumeGestureLog>> = _logs

    private val _logCount = MutableStateFlow(0)
    val logCount: StateFlow<Int> = _logCount

    val prefs: SharedPreferences = application.getSharedPreferences("com.example.volume_prefs", Context.MODE_PRIVATE)

    val gestureEnabled = MutableStateFlow(prefs.getBoolean("pref_gesture_enabled", true))
    val triggerSide = MutableStateFlow(prefs.getString("pref_trigger_side", "RIGHT") ?: "RIGHT")
    val triggerHeight = MutableStateFlow(prefs.getFloat("pref_trigger_height", 0.6f))
    val triggerWidth = MutableStateFlow(prefs.getInt("pref_trigger_width", 24))
    val sensitivity = MutableStateFlow(prefs.getFloat("pref_sensitivity", 40f))
    val hapticFeedback = MutableStateFlow(prefs.getBoolean("pref_haptic_feedback", true))
    val soundFeedback = MutableStateFlow(prefs.getBoolean("pref_sound_feedback", false))
    val invisibleTrigger = MutableStateFlow(prefs.getBoolean("pref_invisible_trigger", false))

    init {
        viewModelScope.launch {
            dao.getAllLogs().collectLatest {
                _logs.value = it
            }
        }
        viewModelScope.launch {
            dao.getLogCount().collectLatest {
                _logCount.value = it
            }
        }
    }

    fun updateGestureEnabled(enabled: Boolean) {
        gestureEnabled.value = enabled
        prefs.edit().putBoolean("pref_gesture_enabled", enabled).apply()
    }

    fun updateTriggerSide(side: String) {
        triggerSide.value = side
        prefs.edit().putString("pref_trigger_side", side).apply()
    }

    fun updateTriggerHeight(height: Float) {
        triggerHeight.value = height
        prefs.edit().putFloat("pref_trigger_height", height).apply()
    }

    fun updateTriggerWidth(width: Int) {
        triggerWidth.value = width
        prefs.edit().putInt("pref_trigger_width", width).apply()
    }

    fun updateSensitivity(value: Float) {
        sensitivity.value = value
        prefs.edit().putFloat("pref_sensitivity", value).apply()
    }

    fun updateHapticFeedback(enabled: Boolean) {
        hapticFeedback.value = enabled
        prefs.edit().putBoolean("pref_haptic_feedback", enabled).apply()
    }

    fun updateSoundFeedback(enabled: Boolean) {
        soundFeedback.value = enabled
        prefs.edit().putBoolean("pref_sound_feedback", enabled).apply()
    }

    fun updateInvisibleTrigger(enabled: Boolean) {
        invisibleTrigger.value = enabled
        prefs.edit().putBoolean("pref_invisible_trigger", enabled).apply()
    }

    fun insertLog(log: VolumeGestureLog) {
        viewModelScope.launch {
            dao.insertLog(log)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            dao.clearAllLogs()
        }
    }
}
