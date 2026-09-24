package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.GlitchSoundGenerator
import com.example.graphics.BrokenScreenView
import com.example.model.FlickerIntensity
import com.example.model.PrankConfig
import com.example.model.TouchMode
import com.example.sensors.ShakeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random

class BrokenScreenOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.action.START_OVERLAY"
        const val ACTION_STOP = "com.example.action.STOP_OVERLAY"
        const val ACTION_UPDATE = "com.example.action.UPDATE_CONFIG"
        const val EXTRA_CONFIG = "extra_prank_config"

        private const val NOTIFICATION_CHANNEL_ID = "broken_screen_prank_channel"
        private const val NOTIFICATION_ID = 1001

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _currentConfig = MutableStateFlow(PrankConfig())
        val currentConfig = _currentConfig.asStateFlow()

        fun start(context: Context, config: PrankConfig) {
            val intent = Intent(context, BrokenScreenOverlayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG, config)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BrokenScreenOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun update(context: Context, config: PrankConfig) {
            val intent = Intent(context, BrokenScreenOverlayService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_CONFIG, config)
            }
            context.startService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: BrokenScreenView? = null
    private var soundGenerator: GlitchSoundGenerator? = null
    private var shakeDetector: ShakeDetector? = null
    private var serviceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        createNotificationChannel()

        // Safety shake detector: shake phone vigorously 3 times or hard shake to stop
        shakeDetector = ShakeDetector(this, threshold = 18.0f) {
            Log.d("OverlayService", "Emergency shake detected! Stopping overlay.")
            stopSelf()
        }
        shakeDetector?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val newConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getSerializableExtra(EXTRA_CONFIG, PrankConfig::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getSerializableExtra(EXTRA_CONFIG) as? PrankConfig
                } ?: _currentConfig.value

                _currentConfig.value = newConfig
                overlayView?.updateConfig(newConfig)
                updateAudio(newConfig)
            }
            ACTION_START -> {
                val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getSerializableExtra(EXTRA_CONFIG, PrankConfig::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getSerializableExtra(EXTRA_CONFIG) as? PrankConfig
                } ?: PrankConfig()

                _currentConfig.value = config

                startAsForeground()
                setupOverlay(config)
                updateAudio(config)
                startHapticGlitchLoop(config)
                _isServiceRunning.value = true
            }
        }

        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        val stopIntent = Intent(this, BrokenScreenOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            102,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_stop_overlay),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun setupOverlay(config: PrankConfig) {
        if (!Settings.canDrawOverlays(this)) {
            Log.e("OverlayService", "Missing SYSTEM_ALERT_WINDOW permission")
            stopSelf()
            return
        }

        removeOverlay()

        val view = BrokenScreenView(
            context = this,
            config = config,
            onExitRequested = {
                stopSelf()
            },
            onSparkGenerated = { _, _ ->
                soundGenerator?.playSingleSpark()
                triggerElectricVibration()
            }
        )

        val flags = if (config.touchMode == TouchMode.PASS_THROUGH) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager?.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            Log.e("OverlayService", "Failed to add overlay window", e)
        }
    }

    private fun updateAudio(config: PrankConfig) {
        if (config.soundEnabled) {
            if (soundGenerator == null) {
                soundGenerator = GlitchSoundGenerator()
            }
            soundGenerator?.start()
        } else {
            soundGenerator?.stop()
            soundGenerator = null
        }
    }

    private fun startHapticGlitchLoop(config: PrankConfig) {
        serviceJob?.cancel()
        if (!config.hapticsEnabled || config.flickerIntensity == FlickerIntensity.OFF) return

        serviceJob = scope.launch {
            val random = Random()
            while (isActive) {
                val delayMs = when (config.flickerIntensity) {
                    FlickerIntensity.OFF -> 5000L
                    FlickerIntensity.LOW -> 2500L + random.nextInt(3000)
                    FlickerIntensity.MEDIUM -> 1200L + random.nextInt(1500)
                    FlickerIntensity.HIGH -> 500L + random.nextInt(800)
                    FlickerIntensity.CHAOS -> 250L + random.nextInt(400)
                }
                delay(delayMs)
                if (random.nextFloat() < 0.4f) {
                    triggerElectricVibration()
                }
            }
        }
    }

    private fun triggerElectricVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(18)
            }
        } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                view.destroy()
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e("OverlayService", "Error removing overlay", e)
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        shakeDetector?.stop()
        shakeDetector = null
        soundGenerator?.stop()
        soundGenerator = null
        removeOverlay()
        _isServiceRunning.value = false
    }
}
