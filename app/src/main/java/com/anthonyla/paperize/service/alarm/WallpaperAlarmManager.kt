package com.anthonyla.paperize.service.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.constants.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager-based scheduler for wallpaper changes
 *
 * This serves as a fallback for OEM devices (like Huawei/Honor) that aggressively
 * kill WorkManager jobs. Using setExactAndAllowWhileIdle ensures the alarm fires
 * even in Doze mode.
 *
 * Note: On Android 12+ (API 31+), SCHEDULE_EXACT_ALARM permission is required for
 * exact alarms. This is automatically granted on most devices but users can revoke it.
 */
@Singleton
class WallpaperAlarmManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "WallpaperAlarmManager"
        private const val REQUEST_CODE_HOME = 1001
        private const val REQUEST_CODE_LOCK = 1002
        private const val REQUEST_CODE_BOTH = 1003
        private const val REQUEST_CODE_LIVE = 1004
    }

    /**
     * Schedule an exact alarm for wallpaper change
     *
     * Uses setExactAndAllowWhileIdle for maximum reliability on OEM devices
     * This will wake up the device even in Doze mode
     */
    fun scheduleWallpaperAlarm(
        screenType: ScreenType,
        intervalMinutes: Int
    ) {
        val intervalMs = intervalMinutes.toLong() * 60 * 1000
        val triggerAtMs = System.currentTimeMillis() + intervalMs

        val intent = createAlarmIntent(screenType)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(screenType),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Use setExactAndAllowWhileIdle for maximum reliability
            // This works even in Doze mode and on aggressive OEM devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    pendingIntent
                )
            }

            Log.d(TAG, "Scheduled exact alarm for $screenType wallpaper change in $intervalMinutes minutes")
        } catch (e: SecurityException) {
            // Fallback to inexact alarm if exact alarm permission is not granted
            Log.w(TAG, "Exact alarm permission not granted, using inexact alarm")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling wallpaper alarm", e)
        }
    }

    /**
     * Schedule a repeating alarm for wallpaper changes
     *
     * Note: On Android 12+, exact repeating alarms are restricted.
     * This uses setRepeating which is inexact but doesn't require special permission.
     * For better reliability, we reschedule after each alarm fires.
     */
    fun scheduleRepeatingWallpaperAlarm(
        screenType: ScreenType,
        intervalMinutes: Int
    ) {
        // Schedule first alarm immediately
        scheduleWallpaperAlarm(screenType, intervalMinutes)
    }

    /**
     * Cancel wallpaper alarm for specific screen
     */
    fun cancelWallpaperAlarm(screenType: ScreenType) {
        val intent = createAlarmIntent(screenType)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(screenType),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Cancelled $screenType wallpaper alarm")
    }

    /**
     * Cancel all wallpaper alarms
     */
    fun cancelAllAlarms() {
        cancelWallpaperAlarm(ScreenType.HOME)
        cancelWallpaperAlarm(ScreenType.LOCK)
        cancelWallpaperAlarm(ScreenType.BOTH)
        cancelWallpaperAlarm(ScreenType.LIVE)
        Log.d(TAG, "Cancelled all wallpaper alarms")
    }

    /**
     * Check if exact alarm permission is granted (Android 12+)
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun createAlarmIntent(screenType: ScreenType): Intent {
        return Intent(context, WallpaperAlarmReceiver::class.java).apply {
            action = Constants.ACTION_CHANGE_WALLPAPER
            putExtra(Constants.EXTRA_SCREEN_TYPE, screenType.name)
        }
    }

    private fun getRequestCode(screenType: ScreenType): Int {
        return when (screenType) {
            ScreenType.HOME -> REQUEST_CODE_HOME
            ScreenType.LOCK -> REQUEST_CODE_LOCK
            ScreenType.BOTH -> REQUEST_CODE_BOTH
            ScreenType.LIVE -> REQUEST_CODE_LIVE
        }
    }
}
