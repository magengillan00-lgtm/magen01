package com.anthonyla.paperize.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.data.datastore.PreferencesManager
import com.anthonyla.paperize.service.wallpaper.WallpaperChangeService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Entry point for Hilt injection in WallpaperAlarmReceiver
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WallpaperAlarmReceiverEntryPoint {
    fun preferencesManager(): PreferencesManager
    fun wallpaperAlarmManager(): WallpaperAlarmManager
}

/**
 * BroadcastReceiver for wallpaper change alarms
 *
 * This receiver is triggered by AlarmManager alarms and:
 * 1. Starts the WallpaperChangeService to change the wallpaper
 * 2. Reschedules the next alarm (for repeating functionality)
 *
 * This approach is more reliable on OEM devices like Honor/Huawei
 * that aggressively kill WorkManager jobs.
 */
class WallpaperAlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "WallpaperAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Constants.ACTION_CHANGE_WALLPAPER) {
            return
        }

        Log.d(TAG, "Received wallpaper change alarm")

        // Get entry point for Hilt injection
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WallpaperAlarmReceiverEntryPoint::class.java
        )
        val preferencesManager = entryPoint.preferencesManager()
        val wallpaperAlarmManager = entryPoint.wallpaperAlarmManager()

        val screenTypeString = intent.getStringExtra(Constants.EXTRA_SCREEN_TYPE)
        val screenType = screenTypeString?.let { ScreenType.fromString(it) } ?: ScreenType.HOME

        // Use goAsync() to ensure async work completes
        val pendingResult = goAsync()

        scope.launch {
            try {
                val settings = preferencesManager.getScheduleSettings()

                if (settings.enableChanger) {
                    // Start the foreground service to change wallpaper
                    val serviceIntent = Intent(context, WallpaperChangeService::class.java).apply {
                        action = WallpaperChangeService.ACTION_CHANGE_WALLPAPER
                        putExtra(WallpaperChangeService.EXTRA_SCREEN_TYPE, screenType.name)
                    }

                    try {
                        context.startForegroundService(serviceIntent)
                        Log.d(TAG, "Started wallpaper change service for $screenType")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting wallpaper service", e)
                    }

                    // Reschedule the next alarm
                    val intervalMinutes = when (screenType) {
                        ScreenType.HOME -> settings.homeIntervalMinutes
                        ScreenType.LOCK -> settings.lockIntervalMinutes
                        ScreenType.BOTH -> settings.homeIntervalMinutes
                        ScreenType.LIVE -> settings.liveIntervalMinutes
                    }

                    if (intervalMinutes > 0) {
                        wallpaperAlarmManager.scheduleWallpaperAlarm(screenType, intervalMinutes)
                        Log.d(TAG, "Rescheduled alarm for $screenType in $intervalMinutes minutes")
                    }
                } else {
                    Log.d(TAG, "Wallpaper changer is disabled, not changing wallpaper")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onReceive", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
