package com.example.multitap

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusBadge: TextView
    private lateinit var serviceStatusDesc: TextView
    private lateinit var toggleOverlayButton: Button
    private lateinit var delayLabel: TextView
    private lateinit var delaySeekBar: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "MultiTap Settings"

        val density = resources.displayMetrics.density

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A")) // Slate 900
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(),
                (24 * density).toInt(),
                (20 * density).toInt(),
                (32 * density).toInt()
            )
        }
        scrollView.addView(container)

        // Header Title
        val titleView = TextView(this).apply {
            text = "MultiTap"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val subtitleView = TextView(this).apply {
            text = "Floating Rapid Touch Multiplier"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, (20 * density).toInt())
        }
        container.addView(titleView)
        container.addView(subtitleView)

        // 1. Accessibility Service Status Card
        val statusCard = createCard(density).apply {
            val cardTitle = createCardTitle("1. ACCESSIBILITY SERVICE STATUS", density)
            addView(cardTitle)

            statusBadge = TextView(this@MainActivity).apply {
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(
                    (14 * density).toInt(),
                    (8 * density).toInt(),
                    (14 * density).toInt(),
                    (8 * density).toInt()
                )
            }
            addView(statusBadge)

            serviceStatusDesc = TextView(this@MainActivity).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, (8 * density).toInt(), 0, (14 * density).toInt())
            }
            addView(serviceStatusDesc)

            val openSettingsBtn = createStyledButton(
                "Open Accessibility Settings",
                Color.parseColor("#3B82F6"),
                density
            ).apply {
                setOnClickListener {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
            }
            addView(openSettingsBtn)
        }
        container.addView(statusCard)

        addSpacer(container, density, 16)

        // 2. Floating Overlay Controls Card
        val overlayCard = createCard(density).apply {
            val cardTitle = createCardTitle("2. FLOATING OVERLAY BUTTON", density)
            addView(cardTitle)

            val overlayExplainer = TextView(this@MainActivity).apply {
                text = "The button appears automatically when the service starts. You can also hide or re-show it here."
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, 0, 0, (12 * density).toInt())
            }
            addView(overlayExplainer)

            toggleOverlayButton = createStyledButton(
                "Show / Hide Floating Button",
                Color.parseColor("#10B981"),
                density
            ).apply {
                setOnClickListener {
                    val service = TapAccessibilityService.instance
                    if (service == null) {
                        serviceStatusDesc.text = "Please turn on MultiTap in Accessibility Settings first!"
                    } else {
                        service.toggleOverlay()
                        updateUIState()
                    }
                }
            }
            addView(toggleOverlayButton)
        }
        container.addView(overlayCard)

        addSpacer(container, density, 16)

        // 3. Delay & Speed Configuration Card
        val delayCard = createCard(density).apply {
            val cardTitle = createCardTitle("3. TAP INTERVAL SPEED", density)
            addView(cardTitle)

            delayLabel = TextView(this@MainActivity).apply {
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, (8 * density).toInt())
            }
            addView(delayLabel)

            val prefs = getSharedPreferences(TapAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
            val initialDelay = prefs.getInt(
                TapAccessibilityService.KEY_DELAY,
                TapAccessibilityService.DEFAULT_DELAY
            )

            delaySeekBar = SeekBar(this@MainActivity).apply {
                min = TapAccessibilityService.MIN_DELAY
                max = TapAccessibilityService.MAX_DELAY
                progress = initialDelay
                setPadding(
                    (8 * density).toInt(),
                    (12 * density).toInt(),
                    (8 * density).toInt(),
                    (12 * density).toInt()
                )

                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val bounded = progress.coerceIn(
                            TapAccessibilityService.MIN_DELAY,
                            TapAccessibilityService.MAX_DELAY
                        )
                        delayLabel.text = "Delay between taps: ${bounded} ms (~${1000 / bounded.coerceAtLeast(1)} taps/sec)"
                        if (fromUser) {
                            prefs.edit().putInt(TapAccessibilityService.KEY_DELAY, bounded).apply()
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            addView(delaySeekBar)

            delayLabel.text = "Delay between taps: ${initialDelay} ms (~${1000 / initialDelay.coerceAtLeast(1)} taps/sec)"

            val delayNote = TextView(this@MainActivity).apply {
                text = "Min: 20 ms (ultra-rapid) | Default: 60 ms | Max: 500 ms (relaxed).\nRead on every tick in real-time."
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, (4 * density).toInt(), 0, 0)
            }
            addView(delayNote)
        }
        container.addView(delayCard)

        addSpacer(container, density, 16)

        // 4. Android 13+ Sideload Restricted Settings Tip Card
        val tipCard = createCard(density).apply {
            val tipTitle = TextView(this@MainActivity).apply {
                text = "ℹ️ TIP: Sideloading on Android 13, 14, 15"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#FBBF24")) // Amber
                setPadding(0, 0, 0, (6 * density).toInt())
            }
            addView(tipTitle)

            val tipBody = TextView(this@MainActivity).apply {
                text = "If Android shows 'Restricted setting' and grays out the switch in Accessibility settings:\n\n" +
                        "1. Go to phone Settings -> Apps -> MultiTap.\n" +
                        "2. Tap the 3 dots (⋮) in the top-right corner.\n" +
                        "3. Tap 'Allow restricted settings'.\n" +
                        "4. Authenticate with your phone's PIN/fingerprint.\n" +
                        "5. Return here and enable MultiTap in Accessibility."
                textSize = 12f
                setTextColor(Color.parseColor("#E2E8F0"))
                setLineSpacing(4f, 1f)
            }
            addView(tipBody)
        }
        container.addView(tipCard)

        setContentView(scrollView)

        TapAccessibilityService.onStateChangeListener = {
            runOnUiThread {
                updateUIState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun updateUIState() {
        val service = TapAccessibilityService.instance
        val isServiceRunning = service != null
        val density = resources.displayMetrics.density

        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * density
            if (isServiceRunning) {
                setColor(Color.parseColor("#059669")) // Emerald 600
            } else {
                setColor(Color.parseColor("#DC2626")) // Red 600
            }
        }
        statusBadge.background = badgeBg

        if (isServiceRunning) {
            val isOverlayOn = service.isOverlayAttached()
            statusBadge.text = "SERVICE ACTIVE: ON"
            serviceStatusDesc.text = if (isOverlayOn) {
                "Service connected. Floating overlay is visible on screen."
            } else {
                "Service connected. Floating overlay is hidden."
            }
            toggleOverlayButton.text = if (isOverlayOn) "Hide Floating Overlay" else "Show Floating Overlay"
            toggleOverlayButton.isEnabled = true
        } else {
            statusBadge.text = "SERVICE INACTIVE: OFF"
            serviceStatusDesc.text = "MultiTap Accessibility Service is not enabled. Tap the button below to turn it ON."
            toggleOverlayButton.text = "Show Floating Button (Service Inactive)"
            toggleOverlayButton.isEnabled = false
        }
    }

    private fun createCard(density: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * density
                setColor(Color.parseColor("#1E293B")) // Slate 800
                setStroke((1 * density).toInt(), Color.parseColor("#334155")) // Slate 700
            }
            background = bg
            setPadding(
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt()
            )
        }
    }

    private fun createCardTitle(title: String, density: Float): TextView {
        return TextView(this).apply {
            text = title
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#60A5FA")) // Blue 400
            setPadding(0, 0, 0, (10 * density).toInt())
        }
    }

    private fun createStyledButton(text: String, bgColor: Int, density: Float): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 13f
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(bgColor)
            }
            background = bg
            setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                (12 * density).toInt()
            )
        }
    }

    private fun addSpacer(parent: LinearLayout, density: Float, heightDp: Int) {
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (heightDp * density).toInt()
            )
        }
        parent.addView(spacer)
    }
}
