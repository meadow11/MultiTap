package com.example.multitap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView

class TapAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TapAccessibilityService? = null
            private set

        const val PREFS_NAME = "multitap_prefs"
        const val KEY_DELAY = "tap_delay_ms"
        const val DEFAULT_DELAY = 60
        const val MIN_DELAY = 20
        const val MAX_DELAY = 500

        var onStateChangeListener: (() -> Unit)? = null
    }

    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var dragBarView: TextView? = null
    private var bodyView: TextView? = null
    private val layoutParams = WindowManager.LayoutParams()

    private val handler = Handler(Looper.getMainLooper())

    private var isHolding = false
    private var heldDeviceId: Int? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        onStateChangeListener?.invoke()
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally no-op: MultiTap does not inspect or retrieve screen contents.
    }

    override fun onInterrupt() {
        stopHolding()
    }

    override fun onDestroy() {
        stopHolding()
        hideOverlay()
        instance = null
        onStateChangeListener?.invoke()
        super.onDestroy()
    }

    fun isOverlayAttached(): Boolean {
        return overlayRoot?.isAttachedToWindow == true
    }

    fun showOverlay() {
        handler.post {
            if (overlayRoot != null && isOverlayAttached()) {
                return@post
            }

            if (windowManager == null) {
                windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }

            val wm = windowManager ?: return@post

            if (overlayRoot == null) {
                createOverlayView()
            }

            val density = resources.displayMetrics.density
            layoutParams.apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
                if (x == 0 && y == 0) {
                    x = (60 * density).toInt()
                    y = (200 * density).toInt()
                }
            }

            try {
                wm.addView(overlayRoot, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onStateChangeListener?.invoke()
        }
    }

    fun hideOverlay() {
        handler.post {
            stopHolding()
            val wm = windowManager ?: return@post
            val view = overlayRoot ?: return@post
            if (view.isAttachedToWindow) {
                try {
                    wm.removeView(view)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            onStateChangeListener?.invoke()
        }
    }

    fun toggleOverlay() {
        if (isOverlayAttached()) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }

    private fun createOverlayView() {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // 1. Thin Drag Bar on top
        val dragBarHeight = (26 * density).toInt()
        val dragBar = TextView(this).apply {
            text = "::: DRAG :::"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1F2937")) // Dark Charcoal
            layoutParams = LinearLayout.LayoutParams(
                (96 * density).toInt(),
                dragBarHeight
            )
        }

        var dragInitialX = 0
        var dragInitialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        dragBar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragInitialX = layoutParams.x
                    dragInitialY = layoutParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - touchStartX).toInt()
                    val deltaY = (event.rawY - touchStartY).toInt()
                    layoutParams.x = dragInitialX + deltaX
                    layoutParams.y = dragInitialY + deltaY
                    if (root.isAttachedToWindow) {
                        windowManager?.updateViewLayout(root, layoutParams)
                    }
                    true
                }
                else -> false
            }
        }

        // 2. Square HOLD Body below
        val bodySize = (96 * density).toInt()
        val body = TextView(this).apply {
            text = "HOLD\nRAPID"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(bodySize, bodySize)
        }
        applyBodyStyle(body, isHeld = false)

        body.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Record deviceId of the real finger touch to prevent injected taps from terminating hold
                    heldDeviceId = event.deviceId
                    isHolding = true
                    applyBodyStyle(body, isHeld = true)
                    performTap()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Stop-detection guard: only stop when the ACTION_UP/CANCEL comes
                    // from the same deviceId as the initial finger touch
                    if (heldDeviceId != null && event.deviceId == heldDeviceId) {
                        stopHolding()
                    }
                    true
                }
                else -> false
            }
        }

        root.addView(dragBar)
        root.addView(body)

        overlayRoot = root
        dragBarView = dragBar
        bodyView = body
    }

    private fun applyBodyStyle(view: TextView, isHeld: Boolean) {
        val density = resources.displayMetrics.density
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12 * density
            if (isHeld) {
                setColor(Color.parseColor("#DC2626")) // Red while held
                setStroke((3 * density).toInt(), Color.parseColor("#FCA5A5"))
            } else {
                setColor(Color.parseColor("#2563EB")) // Vibrant Blue when idle
                setStroke((2 * density).toInt(), Color.parseColor("#93C5FD"))
            }
        }
        view.background = background
        view.text = if (isHeld) "TAPPING...\n[HOLDING]" else "HOLD\nRAPID"
    }

    private fun stopHolding() {
        isHolding = false
        heldDeviceId = null
        handler.removeCallbacksAndMessages(null)
        bodyView?.let { applyBodyStyle(it, isHeld = false) }
        setPassThroughFlag(false)
    }

    private fun setPassThroughFlag(enabled: Boolean) {
        val root = overlayRoot ?: return
        if (!root.isAttachedToWindow) return

        val hasFlag = (layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        if (enabled && !hasFlag) {
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windowManager?.updateViewLayout(root, layoutParams)
        } else if (!enabled && hasFlag) {
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            windowManager?.updateViewLayout(root, layoutParams)
        }
    }

    private fun performTap() {
        if (!isHolding) {
            stopHolding()
            return
        }

        val target = bodyView ?: return
        val root = overlayRoot ?: return
        if (!root.isAttachedToWindow) {
            stopHolding()
            return
        }

        val location = IntArray(2)
        target.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val width = target.width
        val height = target.height

        if (width <= 0 || height <= 0) {
            // View layout not ready, retry in 30ms
            handler.postDelayed({ if (isHolding) performTap() }, 30L)
            return
        }

        // Boundary padding: taps land comfortably inside the body square (6dp or 8% padding)
        val padding = (width * 0.1f).toInt().coerceAtLeast(6)
        val minX = left + padding
        val maxX = (left + width - padding).coerceAtLeast(minX + 1)
        val minY = top + padding
        val maxY = (top + height - padding).coerceAtLeast(minY + 1)

        val tapX = (minX..maxX).random().toFloat()
        val tapY = (minY..maxY).random().toFloat()

        val tapPath = Path().apply {
            moveTo(tapX, tapY)
        }

        val stroke = GestureDescription.StrokeDescription(tapPath, 0L, 15L) // ~15ms short tap
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        // Pass-through: make overlay not touchable during gesture dispatch so tap hits window below
        setPassThroughFlag(true)

        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                setPassThroughFlag(false)
                if (isHolding) {
                    val interval = getDelayInterval()
                    handler.postDelayed({
                        if (isHolding) {
                            performTap()
                        }
                    }, interval)
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                setPassThroughFlag(false)
                if (isHolding) {
                    val interval = getDelayInterval()
                    handler.postDelayed({
                        if (isHolding) {
                            performTap()
                        }
                    }, interval)
                }
            }
        }

        val dispatched = dispatchGesture(gesture, callback, handler)
        if (!dispatched) {
            setPassThroughFlag(false)
            if (isHolding) {
                handler.postDelayed({
                    if (isHolding) performTap()
                }, getDelayInterval())
            }
        }
    }

    private fun getDelayInterval(): Long {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val delay = prefs.getInt(KEY_DELAY, DEFAULT_DELAY)
        return delay.toLong().coerceIn(MIN_DELAY.toLong(), MAX_DELAY.toLong())
    }
}
