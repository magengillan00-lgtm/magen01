package com.anthonyla.paperize.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.data.datastore.PreferencesManager
import com.anthonyla.paperize.service.worker.WallpaperChangeWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UnlockReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            Log.d("UnlockReceiver", "Screen unlocked, checking if wallpaper change is enabled")
            scope.launch {
                try {
                    val settings = preferencesManager.getScheduleSettings()
                    if (settings.enableChanger && settings.changeOnUnlock) {
                        Log.d("UnlockReceiver", "Change on unlock is enabled, triggering wallpaper change")
                        
                        val workManager = WorkManager.getInstance(context)
                        
                        // Trigger for Home screen if enabled
                        if (settings.homeEnabled) {
                            val data = Data.Builder()
                                .putString(Constants.EXTRA_SCREEN_TYPE, ScreenType.HOME.toString())
                                .build()
                            val request = OneTimeWorkRequest.Builder(WallpaperChangeWorker::class.java)
                                .setInputData(data)
                                .build()
                            workManager.enqueue(request)
                        }
                        
                        // Trigger for Lock screen if enabled and separate
                        if (settings.lockEnabled && settings.separateSchedules) {
                            val data = Data.Builder()
                                .putString(Constants.EXTRA_SCREEN_TYPE, ScreenType.LOCK.toString())
                                .build()
                            val request = OneTimeWorkRequest.Builder(WallpaperChangeWorker::class.java)
                                .setInputData(data)
                                .build()
                            workManager.enqueue(request)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UnlockReceiver", "Error in onReceive", e)
                }
            }
        }
    }
}
