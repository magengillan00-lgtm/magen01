package com.anthonyla.paperize.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anthonyla.paperize.core.ScreenType
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
 * Entry point for Hilt injection in UnlockReceiver
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UnlockReceiverEntryPoint {
    fun preferencesManager(): PreferencesManager
}

class UnlockReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            Log.d("UnlockReceiver", "Screen unlocked, checking if wallpaper change is enabled")
            
            // Get entry point for Hilt injection
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                UnlockReceiverEntryPoint::class.java
            )
            val preferencesManager = entryPoint.preferencesManager()

            scope.launch {
                try {
                    val settings = preferencesManager.getScheduleSettings()
                    if (settings.enableChanger && settings.changeOnUnlock) {
                        Log.d("UnlockReceiver", "Change on unlock is enabled, triggering wallpaper change")
                        
                        // Use WallpaperChangeService for immediate change on unlock
                        // This is more reliable than WorkManager for immediate actions, especially on OEM devices
                        
                        // Trigger for Home screen if enabled
                        if (settings.homeEnabled) {
                            val serviceIntent = Intent(context, WallpaperChangeService::class.java).apply {
                                action = WallpaperChangeService.ACTION_CHANGE_WALLPAPER
                                putExtra(WallpaperChangeService.EXTRA_SCREEN_TYPE, ScreenType.HOME.name)
                            }
                            context.startForegroundService(serviceIntent)
                        }
                        
                        // Trigger for Lock screen if enabled and separate
                        if (settings.lockEnabled && settings.separateSchedules) {
                            val serviceIntent = Intent(context, WallpaperChangeService::class.java).apply {
                                action = WallpaperChangeService.ACTION_CHANGE_WALLPAPER
                                putExtra(WallpaperChangeService.EXTRA_SCREEN_TYPE, ScreenType.LOCK.name)
                            }
                            context.startForegroundService(serviceIntent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UnlockReceiver", "Error in onReceive", e)
                }
            }
        }
    }
}
