package com.anthonyla.paperize

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.service.worker.AlbumRefreshWorker
import com.anthonyla.paperize.core.util.DataResetManager
import com.anthonyla.paperize.service.receiver.UnlockReceiver
import com.anthonyla.paperize.service.receiver.ScreenOnReceiver
import com.anthonyla.paperize.service.wallpaper.WallpaperMonitorService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Paperize
 *
 * Annotated with @HiltAndroidApp to enable dependency injection
 * Implements Configuration.Provider for WorkManager with Hilt support
 */
@HiltAndroidApp
class PaperizeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    companion object {
        private const val TAG = "PaperizeApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // Perform one-time data reset for major version upgrades (e.g., v3 -> v4)
        // Must run before any other initialization that accesses DB/preferences
        DataResetManager.performResetIfNeeded(this)

        // Create notification channel (minSdk is 31, so always supported)
        createNotificationChannel()

        // Trigger album refresh on app cold start to validate and update all albums
        refreshAlbumsOnStartup()

        // Register receivers dynamically for OEM device compatibility
        registerReceivers()
        
        // Start the wallpaper monitor service for reliable background execution
        startWallpaperMonitorService()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Refresh all albums on app startup to validate and update wallpapers/folders
     *
     * This runs in the background without blocking app startup and ensures:
     * - Invalid wallpapers/folders are removed
     * - New wallpapers are discovered in existing folders
     * - Album covers are up-to-date
     */
    private fun refreshAlbumsOnStartup() {
        val workManager = WorkManager.getInstance(this)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val refreshWorkRequest = OneTimeWorkRequestBuilder<AlbumRefreshWorker>()
            .setConstraints(constraints)
            .addTag("startup_refresh")
            .build()

        // Use KEEP policy to avoid duplicate refreshes if app is quickly reopened
        workManager.enqueueUniqueWork(
            "album_refresh_on_startup",
            ExistingWorkPolicy.KEEP,
            refreshWorkRequest
        )
    }

    /**
     * Register receivers dynamically to listen for screen events
     * 
     * Dynamic registration is more reliable for USER_PRESENT and SCREEN_ON
     * on many Android versions and OEM skins (like Honor/Huawei)
     */
    private fun registerReceivers() {
        try {
            // Register UnlockReceiver for device unlock events
            val unlockFilter = IntentFilter(Intent.ACTION_USER_PRESENT)
            registerReceiver(UnlockReceiver(), unlockFilter)
            Log.d(TAG, "UnlockReceiver registered")

            // Register ScreenOnReceiver for screen on events
            // This is critical for OEM devices that kill background processes
            val screenOnFilter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(ScreenOnReceiver(), screenOnFilter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(ScreenOnReceiver(), screenOnFilter)
            }
            Log.d(TAG, "ScreenOnReceiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering receivers", e)
        }
    }
    
    /**
     * Start the wallpaper monitor service
     * 
     * This service runs as a foreground service and ensures wallpaper changes
     * happen reliably even when the app is closed or on OEM devices with
     * aggressive battery optimization.
     */
    private fun startWallpaperMonitorService() {
        try {
            val intent = Intent(this, WallpaperMonitorService::class.java).apply {
                action = WallpaperMonitorService.ACTION_START_MONITORING
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "WallpaperMonitorService started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting WallpaperMonitorService", e)
        }
    }
}
