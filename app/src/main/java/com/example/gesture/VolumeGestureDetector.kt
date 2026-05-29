package com.example.gesture

import android.content.Context
import android.view.MotionEvent
import kotlin.math.abs

class VolumeGestureDetector(
    private val context: Context,
    private val onGestureStart: () -> Unit,
    private val onVolumeAdjusted: (direction: String, change: Int) -> Unit,
    private val onGestureEnd: (startVol: Int, finalVol: Int, direction: String, change: Int) -> Unit,
    private val getCurrentVolume: () -> Int,
    private val getMaxVolume: () -> Int,
    private val isEnabled: () -> Boolean = { true },
    private val allowSingleFingerDrag: Boolean = false
) {
    enum class State {
        IDLE,
        SINGLE_FINGER_DOWN,
        SINGLE_FINGER_ENGAGED,
        FIRST_TAP_DOWN,
        FIRST_TAP_UP,
        SECOND_TAP_DOWN,
        ENGAGED
    }

    var currentState = State.IDLE
        private set

    // Timing constants
    private val TAP_TIMEOUT = 300L // Max time for a tap to be considered valid
    private val DOUBLE_TAP_TIMEOUT = 350L // Max delay between taps
    private val ENGAGE_TIMEOUT = 200L // Time to hold on the second tap to trigger engagement
    private val SINGLE_FINGER_ENGAGE_TIMEOUT = 120L
    private val SINGLE_FINGER_MOVE_SLOP = 18f

    // Tracking variables
    private var firstTapTime = 0L
    private var firstReleaseTime = 0L
    private var secondTapTime = 0L
    private var singleFingerDownTime = 0L

    private var initialAvgY = 0f
    private var lastAvgY = 0f
    private var accumulatedDeltaY = 0f

    // Sensitivity: Pixels of vertical travel requested per 1 step of volume scale
    var sensitivityPixels = 40f

    private var startVolVal = 0

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled()) {
            currentState = State.IDLE
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleActionDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_CANCEL -> handleCancel()
        }

        // Consume touches once we start interacting
        return currentState != State.IDLE
    }

    private fun handleActionDown(event: MotionEvent) {
        if (!allowSingleFingerDrag || currentState != State.IDLE || event.pointerCount != 1) return

        currentState = State.SINGLE_FINGER_DOWN
        singleFingerDownTime = System.currentTimeMillis()
        initialAvgY = event.getY(0)
        lastAvgY = initialAvgY
        accumulatedDeltaY = 0f
        startVolVal = getCurrentVolume()
    }

    private fun handlePointerDown(event: MotionEvent) {
        if (allowSingleFingerDrag && currentState == State.SINGLE_FINGER_DOWN && event.pointerCount == 2) {
            currentState = State.IDLE
        }

        val pCount = event.pointerCount
        if (pCount == 2) {
            val avgY = (event.getY(0) + event.getY(1)) / 2f
            when (currentState) {
                State.IDLE -> {
                    currentState = State.FIRST_TAP_DOWN
                    firstTapTime = System.currentTimeMillis()
                    initialAvgY = avgY
                    lastAvgY = avgY
                }
                State.FIRST_TAP_UP -> {
                    val now = System.currentTimeMillis()
                    if (now - firstReleaseTime <= DOUBLE_TAP_TIMEOUT) {
                        currentState = State.SECOND_TAP_DOWN
                        secondTapTime = now
                        initialAvgY = avgY
                        lastAvgY = avgY
                        accumulatedDeltaY = 0f
                        startVolVal = getCurrentVolume()
                        onGestureStart()
                    } else {
                        // Timeout, treat as a new first tap
                        currentState = State.FIRST_TAP_DOWN
                        firstTapTime = now
                        initialAvgY = avgY
                        lastAvgY = avgY
                    }
                }
                else -> {}
            }
        } else if (pCount > 2) {
            currentState = State.IDLE
        }
    }

    private fun handleMove(event: MotionEvent) {
        if (allowSingleFingerDrag && event.pointerCount == 1 &&
            (currentState == State.SINGLE_FINGER_DOWN || currentState == State.SINGLE_FINGER_ENGAGED)
        ) {
            val currentY = event.getY(0)

            if (currentState == State.SINGLE_FINGER_DOWN) {
                val now = System.currentTimeMillis()
                if (now - singleFingerDownTime >= SINGLE_FINGER_ENGAGE_TIMEOUT ||
                    abs(currentY - initialAvgY) > SINGLE_FINGER_MOVE_SLOP
                ) {
                    currentState = State.SINGLE_FINGER_ENGAGED
                    lastAvgY = currentY
                    onGestureStart()
                }
            }

            if (currentState == State.SINGLE_FINGER_ENGAGED) {
                processVerticalDrag(currentY)
            }
            return
        }

        if (event.pointerCount == 2) {
            val currentAvgY = (event.getY(0) + event.getY(1)) / 2f

            if (currentState == State.SECOND_TAP_DOWN) {
                val now = System.currentTimeMillis()
                // Transition to ENGAGED if the hold time is met or we detect movement
                if (now - secondTapTime >= ENGAGE_TIMEOUT || abs(currentAvgY - initialAvgY) > 25f) {
                    currentState = State.ENGAGED
                    lastAvgY = currentAvgY
                }
            }

            if (currentState == State.ENGAGED) {
                processVerticalDrag(currentAvgY)
            }
        }
    }

    private fun handlePointerUp(event: MotionEvent) {
        val pCount = event.pointerCount
        // Releasing finger
        if (pCount <= 2) {
            when (currentState) {
                State.SINGLE_FINGER_DOWN -> {
                    currentState = State.IDLE
                }
                State.SINGLE_FINGER_ENGAGED -> {
                    finishGesture()
                }
                State.FIRST_TAP_DOWN -> {
                    val now = System.currentTimeMillis()
                    if (now - firstTapTime <= TAP_TIMEOUT) {
                        currentState = State.FIRST_TAP_UP
                        firstReleaseTime = now
                    } else {
                        currentState = State.IDLE
                    }
                }
                State.SECOND_TAP_DOWN -> {
                    currentState = State.IDLE
                }
                State.ENGAGED -> {
                    finishGesture()
                }
                else -> {
                    currentState = State.IDLE
                }
            }
        }
    }

    private fun handleCancel() {
        if (currentState == State.ENGAGED || currentState == State.SINGLE_FINGER_ENGAGED) {
            finishGesture()
        }
        currentState = State.IDLE
    }

    private fun processVerticalDrag(currentY: Float) {
        // Scrolling up decreases coordinate Y, increasing volume
        val deltaY = lastAvgY - currentY
        accumulatedDeltaY += deltaY
        lastAvgY = currentY

        if (abs(accumulatedDeltaY) >= sensitivityPixels) {
            val steps = (accumulatedDeltaY / sensitivityPixels).toInt()
            if (steps != 0) {
                val direction = if (steps > 0) "Aumento" else "Diminuição"
                onVolumeAdjusted(direction, abs(steps))
                accumulatedDeltaY -= steps * sensitivityPixels
            }
        }
    }

    private fun finishGesture() {
        val endVolVal = getCurrentVolume()
        val change = endVolVal - startVolVal
        val dir = if (change >= 0) "Aumento" else "Diminuição"
        onGestureEnd(startVolVal, endVolVal, dir, abs(change))
        currentState = State.IDLE
    }
}
