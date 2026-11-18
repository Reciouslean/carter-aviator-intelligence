package com.carter.aviator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private val requestCapture = 7001
    private lateinit var status: TextView
    private lateinit var results: TextView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 9, 9)
        window.navigationBarColor = Color.rgb(9, 9, 9)
        buildUi()
        askNotificationPermission()
        refreshResults()
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 20)
            setBackgroundColor(Color.rgb(8, 8, 8))
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 8, 0, 8)
            }

        body.addView(text("CART3R", 14f, Color.LTGRAY, true))
        body.addView(text("AVIATOR LIVE OCR", 27f, Color.rgb(239, 51, 71), true))
        body.addView(text("USER-APPROVED SCREEN RECOGNITION", 10f, Color.GRAY, true))
        status = text("MONITORING OFF", 19f, Color.rgb(255, 110, 125), true)
        status.setPadding(0, 24, 0, 14)
        body.addView(status)
        body.addView(text(
            "This prototype scans visible screen text with on-device OCR. It may miss or misread results and cannot predict the next round or identify a safe bet.",
            13f, Color.LTGRAY
        ))
        startButton = Button(this).apply {
            text = "START SCREEN MONITORING"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(155, 24, 43))
            setOnClickListener { requestScreenCapture() }
        }
        body.addView(startButton)
        val stop = Button(this).apply {
            text = "STOP MONITORING"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                status.text = "MONITORING OFF"
                startButton.isEnabled = true
            }
        }
        body.addView(stop)
        body.addView(text("CAPTURED MULTIPLIER CANDIDATES", 13f, Color.rgb(239, 51, 71), true))
        results = text("No candidates captured yet.", 14f, Color.WHITE)
        body.addView(results)
        val clear = Button(this).apply {
            text = "CLEAR LOCAL HISTORY"
            setOnClickListener {
                getSharedPreferences(CaptureService.PREFS, Context.MODE_PRIVATE)
                    .edit().remove(CaptureService.KEY_RESULTS).apply()
                refreshResults()
            }
        }
        body.addView(clear)
        body.addView(text(
            "Privacy: screen text is processed on this device. No screenshots or OCR text are uploaded. Android's screen-capture consent is required each session.",
            11f, Color.GRAY
        ))
        scroll.addView(body)
        outer.addView(scroll)
        setContentView(outer)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7010)
        }
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestCapture)
    }

    @Deprecated("Activity result API kept simple for this standalone prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCapture && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(this, service)
            status.text = "MONITORING ACTIVE — SWITCH TO CHROME"
            startButton.isEnabled = false
            Toast.makeText(this, "Screen monitoring started. Switch to Chrome.", Toast.LENGTH_LONG).show()
        } else if (requestCode == requestCapture) {
            Toast.makeText(this, "Screen capture permission was not granted.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshResults()
        val active = CaptureService.isRunning
        if (::status.isInitialized) status.text = if (active) "MONITORING ACTIVE" else "MONITORING OFF"
        if (::startButton.isInitialized) startButton.isEnabled = !active
    }

    private fun refreshResults() {
        if (!::results.isInitialized) return
        val prefs = getSharedPreferences(CaptureService.PREFS, Context.MODE_PRIVATE)
        val list = prefs.getString(CaptureService.KEY_RESULTS, "") ?: ""
        results.text = if (list.isBlank()) "No candidates captured yet.\n\nOCR results require manual verification." else list
    }
}
