package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.BrokenPreset
import com.example.model.PrankConfig
import com.example.model.TriggerMode
import com.example.sensors.ShakeDetector
import com.example.service.BrokenScreenOverlayService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrankUiState(
    val config: PrankConfig = PrankConfig(),
    val isOverlayActive: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val countdownSeconds: Int? = null,
    val isWaitingForShake: Boolean = false,
    val activeTab: Int = 0 // 0: Dashboard/Live Preview, 1: Presets, 2: Custom Studio, 3: Safety & Help
)

class PrankViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PrankUiState())
    val uiState = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var shakeDetector: ShakeDetector? = null

    init {
        checkPermissions()

        // Observe service state
        viewModelScope.launch {
            BrokenScreenOverlayService.isServiceRunning.collect { isRunning ->
                _uiState.update { it.copy(isOverlayActive = isRunning) }
            }
        }
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val hasOverlay = Settings.canDrawOverlays(context)
        _uiState.update { it.copy(hasOverlayPermission = hasOverlay) }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(activeTab = index) }
    }

    fun selectPreset(preset: BrokenPreset) {
        val newConfig = PrankConfig.createFromPreset(preset)
        _uiState.update { it.copy(config = newConfig) }

        if (_uiState.value.isOverlayActive) {
            BrokenScreenOverlayService.update(getApplication(), newConfig)
        }
    }

    fun updateConfig(reducer: (PrankConfig) -> PrankConfig) {
        val newConfig = reducer(_uiState.value.config)
        _uiState.update { it.copy(config = newConfig) }

        if (_uiState.value.isOverlayActive) {
            BrokenScreenOverlayService.update(getApplication(), newConfig)
        }
    }

    fun startPrank(context: Context) {
        if (!_uiState.value.hasOverlayPermission) {
            requestOverlayPermission(context)
            return
        }

        val config = _uiState.value.config

        when (config.triggerMode) {
            TriggerMode.IMMEDIATE -> {
                launchOverlayService(context, config)
            }
            TriggerMode.TIMER_5S, TriggerMode.TIMER_10S, TriggerMode.TIMER_30S -> {
                startCountdown(context, config.triggerMode.delaySeconds, config)
            }
            TriggerMode.SHAKE -> {
                armShakeTrigger(context, config)
            }
        }
    }

    private fun startCountdown(context: Context, seconds: Int, config: PrankConfig) {
        countdownJob?.cancel()
        _uiState.update { it.copy(countdownSeconds = seconds, isWaitingForShake = false) }

        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _uiState.update { it.copy(countdownSeconds = remaining) }
            }
            _uiState.update { it.copy(countdownSeconds = null) }
            launchOverlayService(context, config)
        }
    }

    private fun armShakeTrigger(context: Context, config: PrankConfig) {
        shakeDetector?.stop()
        _uiState.update { it.copy(isWaitingForShake = true, countdownSeconds = null) }

        shakeDetector = ShakeDetector(context, threshold = 15.0f) {
            viewModelScope.launch {
                shakeDetector?.stop()
                shakeDetector = null
                _uiState.update { it.copy(isWaitingForShake = false) }
                launchOverlayService(context, config)
            }
        }
        shakeDetector?.start()
    }

    fun cancelArming() {
        countdownJob?.cancel()
        countdownJob = null
        shakeDetector?.stop()
        shakeDetector = null
        _uiState.update { it.copy(countdownSeconds = null, isWaitingForShake = false) }
    }

    private fun launchOverlayService(context: Context, config: PrankConfig) {
        BrokenScreenOverlayService.start(context, config)
    }

    fun stopPrank(context: Context) {
        cancelArming()
        BrokenScreenOverlayService.stop(context)
    }

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    override fun onCleared() {
        super.onCleared()
        shakeDetector?.stop()
        shakeDetector = null
        countdownJob?.cancel()
    }
}
