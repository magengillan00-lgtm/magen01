package com.anthonyla.paperize.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.core.constants.Constants
import com.anthonyla.paperize.core.constants.PreferenceKeys
import com.anthonyla.paperize.domain.model.AppSettings
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.domain.model.WallpaperEffects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Context extension for DataStore
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.PREFERENCES_NAME
)

/**
 * PreferencesManager - Clean interface for DataStore operations
 *
 * Replaces the old SettingsDataStore with better organization and type safety
 */
@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.dataStore

    // ============ App Settings ============

    suspend fun getAppSettings(): AppSettings {
        val prefs = dataStore.data.first()
        return AppSettings(
            darkMode = prefs[booleanPreferencesKey(PreferenceKeys.DARK_MODE)] ?: false,
            dynamicTheming = prefs[booleanPreferencesKey(PreferenceKeys.DYNAMIC_THEMING)] ?: false,
            animate = prefs[booleanPreferencesKey(PreferenceKeys.ANIMATE)] ?: true,
            firstLaunch = prefs[booleanPreferencesKey(PreferenceKeys.FIRST_LAUNCH)] ?: true,
            currentHomeWallpaperId = prefs[stringPreferencesKey(PreferenceKeys.CURRENT_HOME_WALLPAPER_ID)],
            currentLockWallpaperId = prefs[stringPreferencesKey(PreferenceKeys.CURRENT_LOCK_WALLPAPER_ID)]
        )
    }

    fun getAppSettingsFlow(): Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            darkMode = prefs[booleanPreferencesKey(PreferenceKeys.DARK_MODE)] ?: false,
            dynamicTheming = prefs[booleanPreferencesKey(PreferenceKeys.DYNAMIC_THEMING)] ?: false,
            animate = prefs[booleanPreferencesKey(PreferenceKeys.ANIMATE)] ?: true,
            firstLaunch = prefs[booleanPreferencesKey(PreferenceKeys.FIRST_LAUNCH)] ?: true,
            currentHomeWallpaperId = prefs[stringPreferencesKey(PreferenceKeys.CURRENT_HOME_WALLPAPER_ID)],
            currentLockWallpaperId = prefs[stringPreferencesKey(PreferenceKeys.CURRENT_LOCK_WALLPAPER_ID)]
        )
    }

    suspend fun updateAppSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PreferenceKeys.DARK_MODE)] = settings.darkMode ?: false
            prefs[booleanPreferencesKey(PreferenceKeys.DYNAMIC_THEMING)] = settings.dynamicTheming
            prefs[booleanPreferencesKey(PreferenceKeys.ANIMATE)] = settings.animate
            prefs[booleanPreferencesKey(PreferenceKeys.FIRST_LAUNCH)] = settings.firstLaunch
            settings.currentHomeWallpaperId?.let {
                prefs[stringPreferencesKey(PreferenceKeys.CURRENT_HOME_WALLPAPER_ID)] = it
            }
            settings.currentLockWallpaperId?.let {
                prefs[stringPreferencesKey(PreferenceKeys.CURRENT_LOCK_WALLPAPER_ID)] = it
            }
        }
    }

    // ============ Wallpaper Mode ============

    /**
     * Get current wallpaper mode (STATIC or LIVE)
     * Defaults to STATIC for new installations and backward compatibility
     */
    suspend fun getWallpaperMode(): WallpaperMode {
        val prefs = dataStore.data.first()
        val modeString = prefs[stringPreferencesKey(PreferenceKeys.WALLPAPER_MODE)]
        return WallpaperMode.fromString(modeString)
    }

    /**
     * Get wallpaper mode as a Flow for reactive updates
     */
    fun getWallpaperModeFlow(): Flow<WallpaperMode> = dataStore.data.map { prefs ->
        val modeString = prefs[stringPreferencesKey(PreferenceKeys.WALLPAPER_MODE)]
        WallpaperMode.fromString(modeString)
    }

    /**
     * Update wallpaper mode
     * IMPORTANT: Caller should handle data reset when switching modes
     */
    suspend fun updateWallpaperMode(mode: WallpaperMode) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(PreferenceKeys.WALLPAPER_MODE)] = mode.name
        }
    }

    // ============ Schedule Settings ============

    suspend fun getScheduleSettings(): ScheduleSettings {
        val prefs = dataStore.data.first()
        return ScheduleSettings(
            enableChanger = prefs[booleanPreferencesKey(PreferenceKeys.ENABLE_CHANGER)] ?: false,
            separateSchedules = prefs[booleanPreferencesKey(PreferenceKeys.SEPARATE_SCHEDULES)] ?: false,
            shuffleEnabled = prefs[booleanPreferencesKey(PreferenceKeys.SHUFFLE_ENABLED)] ?: false,
            homeEnabled = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLED)] ?: false,
            lockEnabled = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLED)] ?: false,
            homeAlbumId = prefs[stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID)],
            lockAlbumId = prefs[stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID)],
            liveAlbumId = prefs[stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID)],
            homeIntervalMinutes = prefs[intPreferencesKey(PreferenceKeys.HOME_INTERVAL_MINUTES)]
                ?: Constants.DEFAULT_INTERVAL_MINUTES,
            lockIntervalMinutes = prefs[intPreferencesKey(PreferenceKeys.LOCK_INTERVAL_MINUTES)]
                ?: Constants.DEFAULT_INTERVAL_MINUTES,
            liveIntervalMinutes = prefs[intPreferencesKey(PreferenceKeys.LIVE_INTERVAL_MINUTES)]
                ?: Constants.DEFAULT_INTERVAL_MINUTES,
            homeScalingType = ScalingType.fromString(prefs[stringPreferencesKey(PreferenceKeys.HOME_SCALING_TYPE)]),
            lockScalingType = ScalingType.fromString(prefs[stringPreferencesKey(PreferenceKeys.LOCK_SCALING_TYPE)]),
            liveScalingType = ScalingType.fromString(prefs[stringPreferencesKey(PreferenceKeys.LIVE_SCALING_TYPE)]),
            homeEffects = WallpaperEffects(
                enableBlur = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_BLUR)] ?: false,
                blurPercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_BLUR)] ?: 0,
                enableDarken = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DARKEN)] ?: false,
                darkenPercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_DARKEN)] ?: 0,
                enableVignette = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_VIGNETTE)] ?: false,
                vignettePercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_VIGNETTE)] ?: 0,
                enableGrayscale = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_GRAYSCALE)] ?: false,
                grayscalePercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_GRAYSCALE)] ?: 0,
                enableDoubleTap = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DOUBLE_TAP)] ?: false,
                enableParallax = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_PARALLAX)] ?: false,
                parallaxIntensity = prefs[intPreferencesKey(PreferenceKeys.HOME_PARALLAX_INTENSITY)] ?: Constants.DEFAULT_PARALLAX_INTENSITY
            ),
            lockEffects = WallpaperEffects(
                enableBlur = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_BLUR)] ?: false,
                blurPercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_BLUR)] ?: 0,
                enableDarken = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DARKEN)] ?: false,
                darkenPercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_DARKEN)] ?: 0,
                enableVignette = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_VIGNETTE)] ?: false,
                vignettePercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_VIGNETTE)] ?: 0,
                enableGrayscale = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_GRAYSCALE)] ?: false,
                grayscalePercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_GRAYSCALE)] ?: 0,
                enableDoubleTap = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DOUBLE_TAP)] ?: false,
                enableParallax = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_PARALLAX)] ?: false,
                parallaxIntensity = prefs[intPreferencesKey(PreferenceKeys.LOCK_PARALLAX_INTENSITY)] ?: Constants.DEFAULT_PARALLAX_INTENSITY
            ),
            liveEffects = WallpaperEffects(
                enableBlur = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_BLUR)] ?: false,
                blurPercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_BLUR)] ?: 0,
                enableDarken = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DARKEN)] ?: false,
                darkenPercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_DARKEN)] ?: 0,
                enableVignette = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_VIGNETTE)] ?: false,
                vignettePercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_VIGNETTE)] ?: 0,
                enableGrayscale = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_GRAYSCALE)] ?: false,
                grayscalePercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_GRAYSCALE)] ?: 0,
                enableDoubleTap = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DOUBLE_TAP)] ?: false,
                enableChangeOnScreenOn = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_CHANGE_ON_SCREEN_ON)] ?: false,
                enableParallax = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_PARALLAX)] ?: false,
                parallaxIntensity = prefs[intPreferencesKey(PreferenceKeys.LIVE_PARALLAX_INTENSITY)] ?: Constants.DEFAULT_PARALLAX_INTENSITY
            ),
            adaptiveBrightness = prefs[booleanPreferencesKey(PreferenceKeys.ADAPTIVE_BRIGHTNESS)] ?: false,
            changeOnUnlock = prefs[booleanPreferencesKey(PreferenceKeys.CHANGE_ON_UNLOCK)] ?: false
        )
    }

    fun getScheduleSettingsFlow(): Flow<ScheduleSettings> = dataStore.data.map { prefs ->
        ScheduleSettings(
            enableChanger = prefs[booleanPreferencesKey(PreferenceKeys.ENABLE_CHANGER)] ?: false,
            separateSchedules = prefs[booleanPreferencesKey(PreferenceKeys.SEPARATE_SCHEDULES)] ?: false,
            shuffleEnabled = prefs[booleanPreferencesKey(PreferenceKeys.SHUFFLE_ENABLED)] ?: false,
            homeEnabled = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLED)] ?: false,
            lockEnabled = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLED)] ?: false,
            homeAlbumId = prefs[stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID)],
            lockAlbumId = prefs[stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID)],
            liveAlbumId = prefs[stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID)],
            homeIntervalMinutes = prefs[intPreferencesKey(PreferenceKeys.HOME_INTERVAL_MINUTES)]
                ?: Constants.DEFAULT_INTERVAL_MINUTES,
            lockIntervalMinutes = prefs[intPreferencesKey(PreferenceKeys.LOCK_INTERVAL_MINUTES)]
                ?: Constants.DEFAULT_INTERVAL_MINUTES,
            liveIntervalMinutes = prefs[intPreferencesKey(PreferenceKeys.LIVE_INTERVAL_MINUTES)]
                ?: Constants.DEFAULT_INTERVAL_MINUTES,
            homeScalingType = ScalingType.fromString(prefs[stringPreferencesKey(PreferenceKeys.HOME_SCALING_TYPE)]),
            lockScalingType = ScalingType.fromString(prefs[stringPreferencesKey(PreferenceKeys.LOCK_SCALING_TYPE)]),
            liveScalingType = ScalingType.fromString(prefs[stringPreferencesKey(PreferenceKeys.LIVE_SCALING_TYPE)]),
            homeEffects = WallpaperEffects(
                enableBlur = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_BLUR)] ?: false,
                blurPercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_BLUR)] ?: 0,
                enableDarken = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DARKEN)] ?: false,
                darkenPercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_DARKEN)] ?: 0,
                enableVignette = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_VIGNETTE)] ?: false,
                vignettePercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_VIGNETTE)] ?: 0,
                enableGrayscale = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_GRAYSCALE)] ?: false,
                grayscalePercentage = prefs[intPreferencesKey(PreferenceKeys.HOME_GRAYSCALE)] ?: 0,
                enableDoubleTap = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DOUBLE_TAP)] ?: false,
                enableParallax = prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_PARALLAX)] ?: false,
                parallaxIntensity = prefs[intPreferencesKey(PreferenceKeys.HOME_PARALLAX_INTENSITY)] ?: Constants.DEFAULT_PARALLAX_INTENSITY
            ),
            lockEffects = WallpaperEffects(
                enableBlur = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_BLUR)] ?: false,
                blurPercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_BLUR)] ?: 0,
                enableDarken = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DARKEN)] ?: false,
                darkenPercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_DARKEN)] ?: 0,
                enableVignette = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_VIGNETTE)] ?: false,
                vignettePercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_VIGNETTE)] ?: 0,
                enableGrayscale = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_GRAYSCALE)] ?: false,
                grayscalePercentage = prefs[intPreferencesKey(PreferenceKeys.LOCK_GRAYSCALE)] ?: 0,
                enableDoubleTap = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DOUBLE_TAP)] ?: false,
                enableParallax = prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_PARALLAX)] ?: false,
                parallaxIntensity = prefs[intPreferencesKey(PreferenceKeys.LOCK_PARALLAX_INTENSITY)] ?: Constants.DEFAULT_PARALLAX_INTENSITY
            ),
            liveEffects = WallpaperEffects(
                enableBlur = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_BLUR)] ?: false,
                blurPercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_BLUR)] ?: 0,
                enableDarken = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DARKEN)] ?: false,
                darkenPercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_DARKEN)] ?: 0,
                enableVignette = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_VIGNETTE)] ?: false,
                vignettePercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_VIGNETTE)] ?: 0,
                enableGrayscale = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_GRAYSCALE)] ?: false,
                grayscalePercentage = prefs[intPreferencesKey(PreferenceKeys.LIVE_GRAYSCALE)] ?: 0,
                enableDoubleTap = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DOUBLE_TAP)] ?: false,
                enableChangeOnScreenOn = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_CHANGE_ON_SCREEN_ON)] ?: false,
                enableParallax = prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_PARALLAX)] ?: false,
                parallaxIntensity = prefs[intPreferencesKey(PreferenceKeys.LIVE_PARALLAX_INTENSITY)] ?: Constants.DEFAULT_PARALLAX_INTENSITY
            ),
            adaptiveBrightness = prefs[booleanPreferencesKey(PreferenceKeys.ADAPTIVE_BRIGHTNESS)] ?: false,
            changeOnUnlock = prefs[booleanPreferencesKey(PreferenceKeys.CHANGE_ON_UNLOCK)] ?: false
        )
    }

    suspend fun updateScheduleSettings(settings: ScheduleSettings) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PreferenceKeys.ENABLE_CHANGER)] = settings.enableChanger
            prefs[booleanPreferencesKey(PreferenceKeys.SEPARATE_SCHEDULES)] = settings.separateSchedules
            prefs[booleanPreferencesKey(PreferenceKeys.SHUFFLE_ENABLED)] = settings.shuffleEnabled
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLED)] = settings.homeEnabled
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLED)] = settings.lockEnabled
            if (settings.homeAlbumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID)] = settings.homeAlbumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID))
            }
            if (settings.lockAlbumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID)] = settings.lockAlbumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID))
            }
            if (settings.liveAlbumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID)] = settings.liveAlbumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID))
            }
            prefs[intPreferencesKey(PreferenceKeys.HOME_INTERVAL_MINUTES)] = settings.homeIntervalMinutes
            prefs[intPreferencesKey(PreferenceKeys.LOCK_INTERVAL_MINUTES)] = settings.lockIntervalMinutes
            prefs[intPreferencesKey(PreferenceKeys.LIVE_INTERVAL_MINUTES)] = settings.liveIntervalMinutes
            prefs[stringPreferencesKey(PreferenceKeys.HOME_SCALING_TYPE)] = settings.homeScalingType.name
            prefs[stringPreferencesKey(PreferenceKeys.LOCK_SCALING_TYPE)] = settings.lockScalingType.name
            prefs[stringPreferencesKey(PreferenceKeys.LIVE_SCALING_TYPE)] = settings.liveScalingType.name

            // Home effects
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_BLUR)] = settings.homeEffects.enableBlur
            prefs[intPreferencesKey(PreferenceKeys.HOME_BLUR)] = settings.homeEffects.blurPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DARKEN)] = settings.homeEffects.enableDarken
            prefs[intPreferencesKey(PreferenceKeys.HOME_DARKEN)] = settings.homeEffects.darkenPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_VIGNETTE)] = settings.homeEffects.enableVignette
            prefs[intPreferencesKey(PreferenceKeys.HOME_VIGNETTE)] = settings.homeEffects.vignettePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_GRAYSCALE)] = settings.homeEffects.enableGrayscale
            prefs[intPreferencesKey(PreferenceKeys.HOME_GRAYSCALE)] = settings.homeEffects.grayscalePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DOUBLE_TAP)] = settings.homeEffects.enableDoubleTap
            prefs[booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_PARALLAX)] = settings.homeEffects.enableParallax
            prefs[intPreferencesKey(PreferenceKeys.HOME_PARALLAX_INTENSITY)] = settings.homeEffects.parallaxIntensity

            // Lock effects
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_BLUR)] = settings.lockEffects.enableBlur
            prefs[intPreferencesKey(PreferenceKeys.LOCK_BLUR)] = settings.lockEffects.blurPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DARKEN)] = settings.lockEffects.enableDarken
            prefs[intPreferencesKey(PreferenceKeys.LOCK_DARKEN)] = settings.lockEffects.darkenPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_VIGNETTE)] = settings.lockEffects.enableVignette
            prefs[intPreferencesKey(PreferenceKeys.LOCK_VIGNETTE)] = settings.lockEffects.vignettePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_GRAYSCALE)] = settings.lockEffects.enableGrayscale
            prefs[intPreferencesKey(PreferenceKeys.LOCK_GRAYSCALE)] = settings.lockEffects.grayscalePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DOUBLE_TAP)] = settings.lockEffects.enableDoubleTap
            prefs[booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_PARALLAX)] = settings.lockEffects.enableParallax
            prefs[intPreferencesKey(PreferenceKeys.LOCK_PARALLAX_INTENSITY)] = settings.lockEffects.parallaxIntensity

            // Live effects
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_BLUR)] = settings.liveEffects.enableBlur
            prefs[intPreferencesKey(PreferenceKeys.LIVE_BLUR)] = settings.liveEffects.blurPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DARKEN)] = settings.liveEffects.enableDarken
            prefs[intPreferencesKey(PreferenceKeys.LIVE_DARKEN)] = settings.liveEffects.darkenPercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_VIGNETTE)] = settings.liveEffects.enableVignette
            prefs[intPreferencesKey(PreferenceKeys.LIVE_VIGNETTE)] = settings.liveEffects.vignettePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_GRAYSCALE)] = settings.liveEffects.enableGrayscale
            prefs[intPreferencesKey(PreferenceKeys.LIVE_GRAYSCALE)] = settings.liveEffects.grayscalePercentage
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DOUBLE_TAP)] = settings.liveEffects.enableDoubleTap
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_CHANGE_ON_SCREEN_ON)] = settings.liveEffects.enableChangeOnScreenOn
            prefs[booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_PARALLAX)] = settings.liveEffects.enableParallax
            prefs[intPreferencesKey(PreferenceKeys.LIVE_PARALLAX_INTENSITY)] = settings.liveEffects.parallaxIntensity

            // Adaptive brightness
            prefs[booleanPreferencesKey(PreferenceKeys.ADAPTIVE_BRIGHTNESS)] = settings.adaptiveBrightness
            prefs[booleanPreferencesKey(PreferenceKeys.CHANGE_ON_UNLOCK)] = settings.changeOnUnlock
        }
    }

    // ============ Atomic Album Selection Operations ============

    /**
     * Atomically update home album ID without race conditions
     * This prevents lost updates when both home and lock albums are selected simultaneously
     */
    suspend fun updateHomeAlbumId(albumId: String?) {
        dataStore.edit { prefs ->
            if (albumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID)] = albumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID))
            }
        }
    }

    /**
     * Atomically update lock album ID without race conditions
     */
    suspend fun updateLockAlbumId(albumId: String?) {
        dataStore.edit { prefs ->
            if (albumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID)] = albumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID))
            }
        }
    }

    /**
     * Atomically update live album ID without race conditions
     */
    suspend fun updateLiveAlbumId(albumId: String?) {
        dataStore.edit { prefs ->
            if (albumId != null) {
                prefs[stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID)] = albumId
            } else {
                prefs.remove(stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID))
            }
        }
    }

    /**
     * Atomically clear album selections if they match the given album ID
     * Returns true if any selections were cleared
     */
    suspend fun clearAlbumSelectionsIfMatches(albumId: String): Boolean {
        var cleared = false
        dataStore.edit { prefs ->
            if (prefs[stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID)] == albumId) {
                prefs.remove(stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID))
                cleared = true
            }
            if (prefs[stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID)] == albumId) {
                prefs.remove(stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID))
                cleared = true
            }
            if (prefs[stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID)] == albumId) {
                prefs.remove(stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID))
                cleared = true
            }
        }
        return cleared
    }

    // ============ Atomic AppSettings Operations ============

    suspend fun updateDarkMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PreferenceKeys.DARK_MODE)] = enabled
        }
    }

    suspend fun updateDynamicTheming(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PreferenceKeys.DYNAMIC_THEMING)] = enabled
        }
    }

    suspend fun updateAnimate(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PreferenceKeys.ANIMATE)] = enabled
        }
    }

    suspend fun updateFirstLaunch(isFirstLaunch: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PreferenceKeys.FIRST_LAUNCH)] = isFirstLaunch
        }
    }

    // ============ Atomic ScheduleSettings Operations ============

    suspend fun updateEnableChanger(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PreferenceKeys.ENABLE_CHANGER)] = enabled
        }
    }

    /**
     * Clear all settings
     */
    suspend fun clearAllSettings() {
        dataStore.edit { it.clear() }
    }

    /**
     * Clear schedule settings (reset to defaults)
     */
    suspend fun clearScheduleSettings() {
        dataStore.edit { prefs ->
            prefs.remove(booleanPreferencesKey(PreferenceKeys.ENABLE_CHANGER))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.SEPARATE_SCHEDULES))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.SHUFFLE_ENABLED))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.HOME_ENABLED))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LOCK_ENABLED))
            prefs.remove(stringPreferencesKey(PreferenceKeys.HOME_ALBUM_ID))
            prefs.remove(stringPreferencesKey(PreferenceKeys.LOCK_ALBUM_ID))
            prefs.remove(stringPreferencesKey(PreferenceKeys.LIVE_ALBUM_ID))
            prefs.remove(intPreferencesKey(PreferenceKeys.HOME_INTERVAL_MINUTES))
            prefs.remove(intPreferencesKey(PreferenceKeys.LOCK_INTERVAL_MINUTES))
            prefs.remove(intPreferencesKey(PreferenceKeys.LIVE_INTERVAL_MINUTES))
            prefs.remove(stringPreferencesKey(PreferenceKeys.HOME_SCALING_TYPE))
            prefs.remove(stringPreferencesKey(PreferenceKeys.LOCK_SCALING_TYPE))
            prefs.remove(stringPreferencesKey(PreferenceKeys.LIVE_SCALING_TYPE))
            
            // Clear all effects
            prefs.remove(booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_BLUR))
            prefs.remove(intPreferencesKey(PreferenceKeys.HOME_BLUR))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DARKEN))
            prefs.remove(intPreferencesKey(PreferenceKeys.HOME_DARKEN))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_VIGNETTE))
            prefs.remove(intPreferencesKey(PreferenceKeys.HOME_VIGNETTE))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_GRAYSCALE))
            prefs.remove(intPreferencesKey(PreferenceKeys.HOME_GRAYSCALE))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_DOUBLE_TAP))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.HOME_ENABLE_PARALLAX))
            prefs.remove(intPreferencesKey(PreferenceKeys.HOME_PARALLAX_INTENSITY))

            prefs.remove(booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_BLUR))
            prefs.remove(intPreferencesKey(PreferenceKeys.LOCK_BLUR))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DARKEN))
            prefs.remove(intPreferencesKey(PreferenceKeys.LOCK_DARKEN))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_VIGNETTE))
            prefs.remove(intPreferencesKey(PreferenceKeys.LOCK_VIGNETTE))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_GRAYSCALE))
            prefs.remove(intPreferencesKey(PreferenceKeys.LOCK_GRAYSCALE))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_DOUBLE_TAP))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LOCK_ENABLE_PARALLAX))
            prefs.remove(intPreferencesKey(PreferenceKeys.LOCK_PARALLAX_INTENSITY))

            prefs.remove(booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_BLUR))
            prefs.remove(intPreferencesKey(PreferenceKeys.LIVE_BLUR))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DARKEN))
            prefs.remove(intPreferencesKey(PreferenceKeys.LIVE_DARKEN))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_VIGNETTE))
            prefs.remove(intPreferencesKey(PreferenceKeys.LIVE_VIGNETTE))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_GRAYSCALE))
            prefs.remove(intPreferencesKey(PreferenceKeys.LIVE_GRAYSCALE))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_DOUBLE_TAP))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_CHANGE_ON_SCREEN_ON))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.LIVE_ENABLE_PARALLAX))
            prefs.remove(intPreferencesKey(PreferenceKeys.LIVE_PARALLAX_INTENSITY))

            prefs.remove(booleanPreferencesKey(PreferenceKeys.ADAPTIVE_BRIGHTNESS))
            prefs.remove(booleanPreferencesKey(PreferenceKeys.CHANGE_ON_UNLOCK))
        }
    }
}
