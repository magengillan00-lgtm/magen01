package com.anthonyla.paperize.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.anthonyla.paperize.service.wallpaper.WallpaperMonitorService

/**
 * BroadcastReceiver that listens for screen on events
 * 
 * This receiver triggers wallpaper changes when the screen turns on.
 * It's registered both statically in the manifest and dynamically in the application
 * for maximum reliability on OEM devices.
 */
class ScreenOnReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenOnReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen turned ON - triggering wallpaper check")
                
                // Try to start the monitor service which will handle the wallpaper change
                try {
                    val serviceIntent = Intent(context, WallpaperMonitorService::class.java).apply {
                        action = WallpaperMonitorService.ACTION_SCREEN_ON
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting monitor service on screen on", e)
                }
            }
            
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present (device unlocked)")
                // Also trigger on user present as backup
                try {
                    val serviceIntent = Intent(context, WallpaperMonitorService::class.java).apply {
                        action = WallpaperMonitorService.ACTION_SCREEN_ON
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting monitor service on user present", e)
                }
            }
        }
    }
}
