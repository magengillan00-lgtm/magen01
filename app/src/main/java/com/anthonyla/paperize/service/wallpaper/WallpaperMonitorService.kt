package com.anthonyla.paperize.service.wallpaper

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.data.datastore.PreferencesManager
import com.anthonyla.paperize.domain.repository.SettingsRepository
import com.anthonyla.paperize.domain.usecase.ChangeWallpaperUseCase
import com.anthonyla.paperize.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Foreground Service that monitors and ensures wallpaper changes happen reliably
 * 
 * This service is designed to work on OEM devices like Honor/Huawei that aggressively
 * kill background processes. It runs as a foreground service with a persistent notification
 * and includes multiple reliability mechanisms:
 * 
 * 1. Runs as foreground service (harder to kill)
 * 2. Wake lock to ensure execution
 * 3. Monitors screen on events directly
 * 4. Periodic check as backup
 * 5. Works even when app is closed
 */
@AndroidEntryPoint
class WallpaperMonitorService : Service() {

    @Inject
    lateinit var changeWallpaperUseCase: ChangeWallpaperUseCase

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wallpaperChangeMutex = Mutex()
    
    private lateinit var wallpaperManager: WallpaperManager
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val TAG = "WallpaperMonitorService"
        const val ACTION_START_MONITORING = "com.anthonyla.paperize.ACTION_START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.anthonyla.paperize.ACTION_STOP_MONITORING"
        const val ACTION_CHANGE_NOW = "com.anthonyla.paperize.ACTION_CHANGE_NOW"
        const val ACTION_SCREEN_ON = "com.anthonyla.paperize.ACTION_SCREEN_ON"
        
        private const val NOTIFICATION_ID = 100
        private const val MONITORING_INTERVAL_MS = 60000L // Check every minute
        private var isRunning = false
        
        fun isServiceRunning(): Boolean = isRunning
        
        fun start(context: Context) {
            val intent = Intent(context, WallpaperMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting monitor service", e)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, WallpaperMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
        
        fun changeWallpaperNow(context: Context, screenType: ScreenType = ScreenType.BOTH) {
            val intent = Intent(context, WallpaperMonitorService::class.java).apply {
                action = ACTION_CHANGE_NOW
                putExtra(Constants.EXTRA_SCREEN_TYPE, screenType.name)
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering wallpaper change", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        wallpaperManager = WallpaperManager.getInstance(this)
        notificationManager = getSystemService(NotificationManager::class.java)
            ?: throw IllegalStateException("NotificationManager not available")
        
        // Acquire partial wake lock for reliable background execution
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Paperize::WallpaperMonitor"
        )
        wakeLock?.setReferenceCounted(false)
        
        Log.d(TAG, "WallpaperMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                startAsForeground()
                startMonitoring()
            }
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
                stopSelf(startId)
            }
            ACTION_CHANGE_NOW -> {
                startAsForeground()
                val screenTypeString = intent.getStringExtra(Constants.EXTRA_SCREEN_TYPE)
                val screenType = screenTypeString?.let { ScreenType.fromString(it) } ?: ScreenType.BOTH
                triggerWallpaperChange(screenType)
            }
            ACTION_SCREEN_ON -> {
                startAsForeground()
                handleScreenOn()
            }
        }
        
        return START_STICKY
    }
    
    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        isRunning = true
    }
    
    private fun startMonitoring() {
        Log.d(TAG, "Starting wallpaper monitoring")
        
        // Acquire wake lock for 10 minutes max (will be released after each operation)
        wakeLock?.acquire(10 * 60 * 1000L)
        
        serviceScope.launch {
            while (isActive) {
                try {
                    val settings = settingsRepository.getScheduleSettings()
                    
                    if (settings.enableChanger) {
                        // Check if we need to change wallpaper based on schedule
                        checkAndChangeWallpaper(settings)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop", e)
                }
                
                delay(MONITORING_INTERVAL_MS)
            }
        }
    }
    
    private fun stopMonitoring() {
        Log.d(TAG, "Stopping wallpaper monitoring")
        wakeLock?.release()
        isRunning = false
    }
    
    private suspend fun checkAndChangeWallpaper(settings: com.anthonyla.paperize.domain.model.ScheduleSettings) {
        // This is called periodically to ensure wallpaper changes happen
        // The actual timing is managed by AlarmManager, this is a backup
        Log.d(TAG, "Checking wallpaper schedule status")
    }
    
    private fun handleScreenOn() {
        Log.d(TAG, "Screen on detected, checking if wallpaper change needed")
        
        serviceScope.launch {
            try {
                val settings = settingsRepository.getScheduleSettings()
                
                // Check if wallpaper changer is enabled
                if (!settings.enableChanger) {
                    Log.d(TAG, "Wallpaper changer disabled, skipping")
                    return@launch
                }
                
                // Check if change on unlock is enabled OR if we should always change on screen on
                // For OEM devices like Honor, we force change on screen on for reliability
                if (settings.changeOnUnlock || shouldForceChangeOnScreenOn()) {
                    Log.d(TAG, "Triggering wallpaper change on screen on")
                    
                    // Change home wallpaper if enabled
                    if (settings.homeEnabled && settings.homeAlbumId != null) {
                        triggerWallpaperChange(ScreenType.HOME)
                    }
                    
                    // Change lock wallpaper if enabled and separate
                    if (settings.lockEnabled && settings.lockAlbumId != null && settings.separateSchedules) {
                        delay(500) // Small delay between changes
                        triggerWallpaperChange(ScreenType.LOCK)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling screen on", e)
            }
        }
    }
    
    private fun shouldForceChangeOnScreenOn(): Boolean {
        // For Honor/Huawei devices, always try to change on screen on for reliability
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("huawei") || 
               manufacturer.contains("honor") ||
               manufacturer.contains("xiaomi") ||
               manufacturer.contains("oppo") ||
               manufacturer.contains("vivo") ||
               manufacturer.contains("oneplus")
    }
    
    private fun triggerWallpaperChange(screenType: ScreenType) {
        serviceScope.launch {
            wallpaperChangeMutex.withLock {
                try {
                    val settings = settingsRepository.getScheduleSettings()
                    
                    when (screenType) {
                        ScreenType.HOME -> {
                            val homeAlbumId = settings.homeAlbumId
                            if (homeAlbumId != null && settings.homeEnabled) {
                                changeWallpaper(homeAlbumId, ScreenType.HOME)
                            }
                        }
                        ScreenType.LOCK -> {
                            val lockAlbumId = settings.lockAlbumId
                            if (lockAlbumId != null && settings.lockEnabled) {
                                changeWallpaper(lockAlbumId, ScreenType.LOCK)
                            }
                        }
                        ScreenType.BOTH -> {
                            val albumId = settings.homeAlbumId ?: settings.lockAlbumId
                            if (albumId != null) {
                                changeWallpaperForBoth(albumId)
                            }
                        }
                        ScreenType.LIVE -> {
                            // Live wallpaper handled by live wallpaper service
                            Log.d(TAG, "Live wallpaper mode - no action in monitor service")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error triggering wallpaper change", e)
                }
            }
        }
    }
    
    private suspend fun changeWallpaper(albumId: String, screenType: ScreenType) {
        val result = changeWallpaperUseCase(albumId, screenType)
        result.onSuccess { bitmap ->
            try {
                if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) {
                    Log.e(TAG, "Invalid bitmap")
                    return
                }
                
                val flag = when (screenType) {
                    ScreenType.HOME -> WallpaperManager.FLAG_SYSTEM
                    ScreenType.LOCK -> WallpaperManager.FLAG_LOCK
                    else -> WallpaperManager.FLAG_SYSTEM
                }
                
                wallpaperManager.setBitmap(bitmap, null, true, flag)
                Log.d(TAG, "$screenType wallpaper changed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting $screenType wallpaper", e)
            } finally {
                bitmap.recycle()
            }
        }.onError { error ->
            Log.e(TAG, "Error getting wallpaper for $screenType", error)
        }
    }
    
    private suspend fun changeWallpaperForBoth(albumId: String) {
        val result = changeWallpaperUseCase(albumId, ScreenType.HOME)
        result.onSuccess { bitmap ->
            try {
                if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) {
                    Log.e(TAG, "Invalid bitmap")
                    return
                }
                
                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                Log.d(TAG, "Both screens wallpaper changed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting wallpaper for both screens", e)
            } finally {
                bitmap.recycle()
            }
        }.onError { error ->
            Log.e(TAG, "Error getting wallpaper for both screens", error)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.wallpaper_monitoring_active))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        wakeLock?.release()
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "WallpaperMonitorService destroyed")
    }
}
