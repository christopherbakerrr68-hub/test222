package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class OverlayActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        when (intent.action) {
            BrokenScreenOverlayService.ACTION_STOP -> {
                BrokenScreenOverlayService.stop(context)
            }
        }
    }
}
