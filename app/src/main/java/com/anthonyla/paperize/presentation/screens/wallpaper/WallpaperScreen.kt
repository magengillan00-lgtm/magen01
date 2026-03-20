package com.anthonyla.paperize.presentation.screens.wallpaper
import com.anthonyla.paperize.core.constants.Constants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.anthonyla.paperize.R
import com.anthonyla.paperize.core.ScalingType
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.core.WallpaperMode
import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.domain.model.AlbumSummary
import com.anthonyla.paperize.domain.model.AppSettings
import com.anthonyla.paperize.domain.model.ScheduleSettings
import com.anthonyla.paperize.presentation.common.components.SettingSwitchItem
import com.anthonyla.paperize.presentation.screens.wallpaper.components.AlbumSelectionBottomSheet
import com.anthonyla.paperize.presentation.screens.wallpaper.components.CurrentWallpaperPreview
import com.anthonyla.paperize.presentation.screens.wallpaper.components.SettingSwitch
import com.anthonyla.paperize.presentation.screens.wallpaper.components.SettingSwitchWithSlider
import com.anthonyla.paperize.presentation.screens.wallpaper.components.TimeIntervalPicker
import com.anthonyla.paperize.presentation.theme.AppSpacing
import kotlinx.coroutines.delay

enum class AlbumSelectionContext {
    HOME, LOCK, BOTH, LIVE
}

@Composable
fun WallpaperScreen(
    albums: List<AlbumSummary>,
    scheduleSettings: ScheduleSettings,
    appSettings: AppSettings,
    wallpaperMode: WallpaperMode,
    onToggleChanger: (Boolean) -> Unit,
    onSelectHomeAlbum: (AlbumSummary?) -> Unit,
    onSelectLockAlbum: (AlbumSummary?) -> Unit,
    onSelectLiveAlbum: (AlbumSummary?) -> Unit,
    onUpdateScheduleSettings: (ScheduleSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAlbumSelectionSheet by rememberSaveable { mutableStateOf(false) }
    var albumSelectionContext by rememberSaveable { mutableStateOf(AlbumSelectionContext.BOTH) }
    var showEmptyAlbumWarning by remember { mutableStateOf(false) }

    // Settings debouncing
    var pendingSettings by remember { mutableStateOf<ScheduleSettings?>(null) }
    var lastSettingsChangeTimestamp by remember { mutableLongStateOf(0L) }

    // Debounce settings updates to avoid excessive database writes and rescheduling
    LaunchedEffect(lastSettingsChangeTimestamp) {
        if (lastSettingsChangeTimestamp > 0 && pendingSettings != null) {
            delay(Constants.SETTINGS_DEBOUNCE_MS)
            pendingSettings?.let { onUpdateScheduleSettings(it) }
            pendingSettings = null
        }
    }

    // Helper function to update settings with debouncing
    fun updateSettingsDebounced(newSettings: ScheduleSettings) {
        pendingSettings = newSettings
        lastSettingsChangeTimestamp = System.currentTimeMillis()
    }

    // Wrapper for immediate updates that cancels pending debounced updates
    fun updateSettingsImmediate(newSettings: ScheduleSettings) {
        // Cancel any pending debounced updates
        pendingSettings = null
        lastSettingsChangeTimestamp = 0L
        // Apply the update immediately
        onUpdateScheduleSettings(newSettings)
    }

    val homeEnabled = scheduleSettings.homeEnabled
    val lockEnabled = scheduleSettings.lockEnabled

    // Get currently selected albums by ID
    val homeAlbum by remember(albums, scheduleSettings.homeAlbumId) {
        derivedStateOf {
            scheduleSettings.homeAlbumId?.let { id -> albums.find { it.id == id } }
        }
    }
    val lockAlbum by remember(albums, scheduleSettings.lockAlbumId) {
        derivedStateOf {
            scheduleSettings.lockAlbumId?.let { id -> albums.find { it.id == id } }
        }
    }
    val liveAlbum by remember(albums, scheduleSettings.liveAlbumId) {
        derivedStateOf {
            scheduleSettings.liveAlbumId?.let { id -> albums.find { it.id == id } }
        }
    }

    // Auto-toggle wallpaper changer based on album selection state
    LaunchedEffect(scheduleSettings.homeAlbumId, scheduleSettings.lockAlbumId, scheduleSettings.liveAlbumId, homeEnabled, lockEnabled, wallpaperMode) {
        val allRequiredAlbumsSet = if (wallpaperMode == WallpaperMode.STATIC) {
            when {
                homeEnabled && lockEnabled -> {
                    scheduleSettings.homeAlbumId != null && scheduleSettings.lockAlbumId != null
                }
                homeEnabled -> {
                    scheduleSettings.homeAlbumId != null
                }
                lockEnabled -> {
                    scheduleSettings.lockAlbumId != null
                }
                else -> false
            }
        } else {
            scheduleSettings.liveAlbumId != null
        }

        if (allRequiredAlbumsSet != scheduleSettings.enableChanger) {
            onToggleChanger(allRequiredAlbumsSet)
        }
    }

    val scalingOptions = listOf(
        stringResource(R.string.fill),
        stringResource(R.string.fit),
        stringResource(R.string.stretch),
        stringResource(R.string.none)
    )

    var selectedScalingIndex by rememberSaveable {
        mutableIntStateOf(
            when (if (wallpaperMode == WallpaperMode.LIVE) scheduleSettings.liveScalingType else scheduleSettings.homeScalingType) {
                ScalingType.FILL -> 0
                ScalingType.FIT -> 1
                ScalingType.STRETCH -> 2
                ScalingType.NONE -> 3
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = AppSpacing.small, end = AppSpacing.small, bottom = AppSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        if (wallpaperMode == WallpaperMode.STATIC) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        updateSettingsImmediate(scheduleSettings.copy(lockEnabled = !lockEnabled))
                    },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (lockEnabled)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (lockEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.lock),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (lockEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (lockEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (lockEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        updateSettingsImmediate(scheduleSettings.copy(homeEnabled = !homeEnabled))
                    },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (homeEnabled)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            tint = if (homeEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.home),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (homeEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (homeEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (homeEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (wallpaperMode == WallpaperMode.STATIC) {
            if (homeEnabled || lockEnabled) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall))
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                    ) {
                        if (homeEnabled && lockEnabled) {
                            SettingSwitch(
                                title = R.string.individual_scheduling,
                                description = R.string.show_interval_sliders,
                                checked = scheduleSettings.separateSchedules,
                                onCheckedChange = { enabled ->
                                    updateSettingsImmediate(scheduleSettings.copy(separateSchedules = enabled))
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))
                        }

                        if (homeEnabled && lockEnabled && !scheduleSettings.separateSchedules) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = AppSpacing.small),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.select_album),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = homeAlbum?.name ?: stringResource(R.string.no_album_selected),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = {
                                    albumSelectionContext = AlbumSelectionContext.BOTH
                                    showAlbumSelectionSheet = true
                                }) {
                                    Text(stringResource(R.string.change_name))
                                }
                            }
                        } else {
                            if (homeEnabled) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = AppSpacing.small),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.home_album_label),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = homeAlbum?.name ?: stringResource(R.string.no_album_selected),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(onClick = {
                                        albumSelectionContext = AlbumSelectionContext.HOME
                                        showAlbumSelectionSheet = true
                                    }) {
                                        Text(stringResource(R.string.change_name))
                                    }
                                }
                            }
                            if (lockEnabled) {
                                if (homeEnabled) HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = AppSpacing.small),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.lock_album_label),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = lockAlbum?.name ?: stringResource(R.string.no_album_selected),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(onClick = {
                                        albumSelectionContext = AlbumSelectionContext.LOCK
                                        showAlbumSelectionSheet = true
                                    }) {
                                        Text(stringResource(R.string.change_name))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.large),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.select_album),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = liveAlbum?.name ?: stringResource(R.string.no_album_selected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = {
                        albumSelectionContext = AlbumSelectionContext.LIVE
                        showAlbumSelectionSheet = true
                    }) {
                        Text(stringResource(R.string.change_name))
                    }
                }
            }
        }

        val hasAlbumSelected = if (wallpaperMode == WallpaperMode.STATIC) {
            (homeEnabled && scheduleSettings.homeAlbumId != null) ||
            (lockEnabled && scheduleSettings.lockAlbumId != null)
        } else {
            scheduleSettings.liveAlbumId != null
        }

        if (hasAlbumSelected) {
            if (wallpaperMode == WallpaperMode.STATIC) {
                if (!scheduleSettings.separateSchedules || !homeEnabled || !lockEnabled) {
                    TimeIntervalPicker(
                        title = stringResource(R.string.interval_text),
                        minutes = scheduleSettings.homeIntervalMinutes,
                        onMinutesChange = { minutes ->
                            onUpdateScheduleSettings(
                                scheduleSettings.copy(
                                    homeIntervalMinutes = minutes,
                                    lockIntervalMinutes = minutes
                                )
                            )
                        }
                    )
                } else {
                    if (lockEnabled) {
                        TimeIntervalPicker(
                            title = stringResource(R.string.lock_screen_btn),
                            minutes = scheduleSettings.lockIntervalMinutes,
                            onMinutesChange = { minutes ->
                                onUpdateScheduleSettings(
                                    scheduleSettings.copy(lockIntervalMinutes = minutes)
                                )
                            }
                        )
                    }
                    if (homeEnabled) {
                        TimeIntervalPicker(
                            title = stringResource(R.string.home_screen_btn),
                            minutes = scheduleSettings.homeIntervalMinutes,
                            onMinutesChange = { minutes ->
                                onUpdateScheduleSettings(
                                    scheduleSettings.copy(homeIntervalMinutes = minutes)
                                )
                            }
                        )
                    }
                }
            } else {
                TimeIntervalPicker(
                    title = stringResource(R.string.interval_text),
                    minutes = scheduleSettings.liveIntervalMinutes,
                    onMinutesChange = { minutes ->
                        onUpdateScheduleSettings(
                            scheduleSettings.copy(liveIntervalMinutes = minutes)
                        )
                    }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))

        if (wallpaperMode == WallpaperMode.STATIC) {
            CurrentWallpaperPreview(animate = appSettings.animate)
            HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))
        }

        Text(
            text = stringResource(R.string.wallpaper_effects_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = AppSpacing.large, vertical = AppSpacing.small),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall))
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.large),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
            ) {
                Text(
                    text = stringResource(R.string.scaling),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    scalingOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = scalingOptions.size
                            ),
                            onClick = {
                                selectedScalingIndex = index
                                val scalingType = when (index) {
                                    0 -> ScalingType.FILL
                                    1 -> ScalingType.FIT
                                    2 -> ScalingType.STRETCH
                                    else -> ScalingType.NONE
                                }
                                onUpdateScheduleSettings(
                                    if (wallpaperMode == WallpaperMode.LIVE) {
                                        scheduleSettings.copy(liveScalingType = scalingType)
                                    } else {
                                        scheduleSettings.copy(
                                            homeScalingType = scalingType,
                                            lockScalingType = scalingType
                                        )
                                    }
                                )
                            },
                            selected = index == selectedScalingIndex
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall))
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.large),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
            ) {
                SettingSwitch(
                    title = R.string.shuffle,
                    description = if (scheduleSettings.shuffleEnabled && !scheduleSettings.separateSchedules) null else R.string.randomly_shuffle_the_wallpapers,
                    checked = scheduleSettings.shuffleEnabled,
                    onCheckedChange = { enabled ->
                        updateSettingsImmediate(scheduleSettings.copy(shuffleEnabled = enabled))
                    }
                )
                
                if (wallpaperMode == WallpaperMode.STATIC) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))
                    SettingSwitch(
                        title = R.string.change_on_unlock,
                        description = R.string.change_on_unlock_desc,
                        checked = scheduleSettings.changeOnUnlock,
                        onCheckedChange = { enabled ->
                            updateSettingsImmediate(scheduleSettings.copy(changeOnUnlock = enabled))
                        }
                    )
                }
            }
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall))
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.large),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Text(
                    text = stringResource(R.string.visual_effects),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = AppSpacing.small),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                SettingSwitchWithSlider(
                    title = R.string.change_brightness,
                    description = R.string.change_the_image_brightness,
                    checked = if (wallpaperMode == WallpaperMode.STATIC) {
                        when {
                            homeEnabled && lockEnabled -> scheduleSettings.homeEffects.enableDarken && scheduleSettings.lockEffects.enableDarken
                            homeEnabled -> scheduleSettings.homeEffects.enableDarken
                            else -> scheduleSettings.lockEffects.enableDarken
                        }
                    } else {
                        scheduleSettings.liveEffects.enableDarken
                    },
                    onCheckedChange = { enabled ->
                        if (wallpaperMode == WallpaperMode.STATIC) {
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    homeEffects = if (homeEnabled) scheduleSettings.homeEffects.copy(enableDarken = enabled) else scheduleSettings.homeEffects,
                                    lockEffects = if (lockEnabled) scheduleSettings.lockEffects.copy(enableDarken = enabled) else scheduleSettings.lockEffects
                                )
                            )
                        } else {
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(enableDarken = enabled)
                                )
                            )
                        }
                    },
                    bothEnabled = homeEnabled && lockEnabled,
                    homePercentage = scheduleSettings.homeEffects.darkenPercentage,
                    lockPercentage = scheduleSettings.lockEffects.darkenPercentage,
                    onPercentageChange = { home, lock ->
                        if (wallpaperMode == WallpaperMode.STATIC) {
                            updateSettingsDebounced(
                                scheduleSettings.copy(
                                    homeEffects = if (homeEnabled) scheduleSettings.homeEffects.copy(darkenPercentage = home) else scheduleSettings.homeEffects,
                                    lockEffects = if (lockEnabled) scheduleSettings.lockEffects.copy(darkenPercentage = lock) else scheduleSettings.lockEffects
                                )
                            )
                        } else {
                            updateSettingsDebounced(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(darkenPercentage = home)
                                )
                            )
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))

                SettingSwitchWithSlider(
                    title = R.string.change_blur,
                    description = R.string.add_blur_to_the_image,
                    checked = if (wallpaperMode == WallpaperMode.STATIC) {
                        when {
                            homeEnabled && lockEnabled -> scheduleSettings.homeEffects.enableBlur && scheduleSettings.lockEffects.enableBlur
                            homeEnabled -> scheduleSettings.homeEffects.enableBlur
                            else -> scheduleSettings.lockEffects.enableBlur
                        }
                    } else {
                        scheduleSettings.liveEffects.enableBlur
                    },
                    onCheckedChange = { enabled ->
                        if (wallpaperMode == WallpaperMode.STATIC) {
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    homeEffects = if (homeEnabled) scheduleSettings.homeEffects.copy(enableBlur = enabled) else scheduleSettings.homeEffects,
                                    lockEffects = if (lockEnabled) scheduleSettings.lockEffects.copy(enableBlur = enabled) else scheduleSettings.lockEffects
                                )
                            )
                        } else {
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(enableBlur = enabled)
                                )
                            )
                        }
                    },
                    bothEnabled = homeEnabled && lockEnabled,
                    homePercentage = scheduleSettings.homeEffects.blurPercentage,
                    lockPercentage = scheduleSettings.lockEffects.blurPercentage,
                    onPercentageChange = { home, lock ->
                        if (wallpaperMode == WallpaperMode.STATIC) {
                            updateSettingsDebounced(
                                scheduleSettings.copy(
                                    homeEffects = if (homeEnabled) scheduleSettings.homeEffects.copy(blurPercentage = home) else scheduleSettings.homeEffects,
                                    lockEffects = if (lockEnabled) scheduleSettings.lockEffects.copy(blurPercentage = lock) else scheduleSettings.lockEffects
                                )
                            )
                        } else {
                            updateSettingsDebounced(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(blurPercentage = home)
                                )
                            )
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))

                SettingSwitchWithSlider(
                    title = R.string.change_vignette,
                    description = R.string.darken_the_edges_of_the_image,
                    checked = if (wallpaperMode == WallpaperMode.STATIC) {
                        when {
                            homeEnabled && lockEnabled -> scheduleSettings.homeEffects.enableVignette && scheduleSettings.lockEffects.enableVignette
                            homeEnabled -> scheduleSettings.homeEffects.enableVignette
                            else -> scheduleSettings.lockEffects.enableVignette
                        }
                    } else {
                        scheduleSettings.liveEffects.enableVignette
                    },
                    onCheckedChange = { enabled ->
                        if (wallpaperMode == WallpaperMode.STATIC) {
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    homeEffects = if (homeEnabled) scheduleSettings.homeEffects.copy(enableVignette = enabled) else scheduleSettings.homeEffects,
                                    lockEffects = if (lockEnabled) scheduleSettings.lockEffects.copy(enableVignette = enabled) else scheduleSettings.lockEffects
                                )
                            )
                        } else {
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(enableVignette = enabled)
                                )
                            )
                        }
                    },
                    bothEnabled = homeEnabled && lockEnabled,
                    homePercentage = scheduleSettings.homeEffects.vignettePercentage,
                    lockPercentage = scheduleSettings.lockEffects.vignettePercentage,
                    onPercentageChange = { home, lock ->
                        if (wallpaperMode == WallpaperMode.STATIC) {
                            updateSettingsDebounced(
                                scheduleSettings.copy(
                                    homeEffects = if (homeEnabled) scheduleSettings.homeEffects.copy(vignettePercentage = home) else scheduleSettings.homeEffects,
                                    lockEffects = if (lockEnabled) scheduleSettings.lockEffects.copy(vignettePercentage = lock) else scheduleSettings.lockEffects
                                )
                            )
                        } else {
                            updateSettingsDebounced(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(vignettePercentage = home)
                                )
                            )
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))

                SettingSwitchWithSlider(
                    title = R.string.gray_filter,
                    description = R.string.make_the_colors_grayscale,
                    checked = if (wallpaperMode == WallpaperMode.STATIC) {
                        when {
                            homeEnabled && lockEnabled -> scheduleSettings.homeEffects.enableGrayscale && scheduleSettings.lockEffects.enableGrayscale
                            homeEnabled -> scheduleSettings.homeEffects.enableGrayscale
                            else -> scheduleSettings.lockEffects.enableGrayscale
                        }
                    } else {
                        scheduleSettings.liveEffects.enableGrayscale
                    },
                    onCheckedChange = { enabled ->
                        if (wallpaperMode == WallpaperMode.STATIC) {
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    homeEffects = if (homeEnabled) scheduleSettings.homeEffects.copy(enableGrayscale = enabled) else scheduleSettings.homeEffects,
                                    lockEffects = if (lockEnabled) scheduleSettings.lockEffects.copy(enableGrayscale = enabled) else scheduleSettings.lockEffects
                                )
                            )
                        } else {
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(enableGrayscale = enabled)
                                )
                            )
                        }
                    },
                    bothEnabled = homeEnabled && lockEnabled,
                    homePercentage = scheduleSettings.homeEffects.grayscalePercentage,
                    lockPercentage = scheduleSettings.lockEffects.grayscalePercentage,
                    onPercentageChange = { home, lock ->
                        if (wallpaperMode == WallpaperMode.STATIC) {
                            updateSettingsDebounced(
                                scheduleSettings.copy(
                                    homeEffects = if (homeEnabled) scheduleSettings.homeEffects.copy(grayscalePercentage = home) else scheduleSettings.homeEffects,
                                    lockEffects = if (lockEnabled) scheduleSettings.lockEffects.copy(grayscalePercentage = lock) else scheduleSettings.lockEffects
                                )
                            )
                        } else {
                            updateSettingsDebounced(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(grayscalePercentage = home)
                                )
                            )
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))

                SettingSwitch(
                    title = R.string.adaptive_brightness,
                    description = R.string.adjust_brightness_based_on_mode,
                    checked = scheduleSettings.adaptiveBrightness,
                    onCheckedChange = { enabled ->
                        updateSettingsImmediate(scheduleSettings.copy(adaptiveBrightness = enabled))
                    }
                )
            }
        }

        if (wallpaperMode == WallpaperMode.LIVE) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(horizontal = AppSpacing.small, vertical = AppSpacing.extraSmall))
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    Text(
                        text = stringResource(R.string.interactive_effects),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = AppSpacing.small),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    SettingSwitch(
                        title = R.string.double_tap_to_change,
                        description = R.string.double_tap_wallpaper_to_change_it,
                        checked = scheduleSettings.liveEffects.enableDoubleTap,
                        onCheckedChange = { enabled ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(enableDoubleTap = enabled)
                                )
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))

                    SettingSwitch(
                        title = R.string.change_on_screen_off,
                        description = R.string.change_wallpaper_when_screen_turns_off,
                        checked = scheduleSettings.liveEffects.enableChangeOnScreenOn,
                        onCheckedChange = { enabled ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(enableChangeOnScreenOn = enabled)
                                )
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = AppSpacing.small))

                    SettingSwitchWithSlider(
                        title = R.string.parallax_effect,
                        description = R.string.wallpaper_moves_with_screen_scroll,
                        checked = scheduleSettings.liveEffects.enableParallax,
                        onCheckedChange = { enabled ->
                            updateSettingsImmediate(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(enableParallax = enabled)
                                )
                            )
                        },
                        bothEnabled = false,
                        homePercentage = scheduleSettings.liveEffects.parallaxIntensity,
                        lockPercentage = scheduleSettings.liveEffects.parallaxIntensity,
                        onPercentageChange = { value, _ ->
                            updateSettingsDebounced(
                                scheduleSettings.copy(
                                    liveEffects = scheduleSettings.liveEffects.copy(parallaxIntensity = value)
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    if (showAlbumSelectionSheet) {
        AlbumSelectionBottomSheet(
            albums = albums,
            selectedAlbums = when (albumSelectionContext) {
                AlbumSelectionContext.LIVE -> {
                    scheduleSettings.liveAlbumId?.let { id ->
                        albums.find { it.id == id }?.let { listOf(it) }
                            ?: listOf(AlbumSummary.empty(id = id))
                    } ?: emptyList()
                }
                AlbumSelectionContext.HOME -> {
                    scheduleSettings.homeAlbumId?.let { id ->
                        albums.find { it.id == id }?.let { listOf(it) }
                            ?: listOf(AlbumSummary.empty(id = id))
                    } ?: emptyList()
                }
                AlbumSelectionContext.LOCK -> {
                    scheduleSettings.lockAlbumId?.let { id ->
                        albums.find { it.id == id }?.let { listOf(it) }
                            ?: listOf(AlbumSummary.empty(id = id))
                    } ?: emptyList()
                }
                AlbumSelectionContext.BOTH -> {
                    when {
                        homeEnabled -> {
                            scheduleSettings.homeAlbumId?.let { id ->
                                albums.find { it.id == id }?.let { listOf(it) }
                                    ?: listOf(AlbumSummary.empty(id = id))
                            } ?: emptyList()
                        }
                        lockEnabled -> {
                            scheduleSettings.lockAlbumId?.let { id ->
                                albums.find { it.id == id }?.let { listOf(it) }
                                    ?: listOf(AlbumSummary.empty(id = id))
                            } ?: emptyList()
                        }
                        else -> emptyList()
                    }
                }
            },
            onAlbumSelect = { album ->
                if (album.wallpaperCount == 0) {
                    showEmptyAlbumWarning = true
                } else {
                    when (albumSelectionContext) {
                        AlbumSelectionContext.LIVE -> {
                            val isCurrentlySelected = scheduleSettings.liveAlbumId == album.id
                            if (isCurrentlySelected) {
                                onSelectLiveAlbum(null)
                            } else {
                                onSelectLiveAlbum(album)
                            }
                        }
                        AlbumSelectionContext.HOME -> {
                            val isCurrentlySelected = scheduleSettings.homeAlbumId == album.id
                            if (isCurrentlySelected) {
                                onSelectHomeAlbum(null)
                            } else {
                                onSelectHomeAlbum(album)
                            }
                        }
                        AlbumSelectionContext.LOCK -> {
                            val isCurrentlySelected = scheduleSettings.lockAlbumId == album.id
                            if (isCurrentlySelected) {
                                onSelectLockAlbum(null)
                            } else {
                                onSelectLockAlbum(album)
                            }
                        }
                        AlbumSelectionContext.BOTH -> {
                            val isCurrentlySelected = (homeEnabled && scheduleSettings.homeAlbumId == album.id) ||
                                                     (lockEnabled && scheduleSettings.lockAlbumId == album.id)
                            if (isCurrentlySelected) {
                                if (homeEnabled) onSelectHomeAlbum(null)
                                if (lockEnabled) onSelectLockAlbum(null)
                            } else {
                                if (homeEnabled) onSelectHomeAlbum(album)
                                if (lockEnabled) onSelectLockAlbum(album)
                            }
                        }
                    }
                }
                showAlbumSelectionSheet = false
            },
            onDismiss = { showAlbumSelectionSheet = false }
        )
    }

    if (showEmptyAlbumWarning) {
        AlertDialog(
            onDismissRequest = { showEmptyAlbumWarning = false },
            title = {
                Text(
                    text = stringResource(R.string.empty_album),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.empty_album_message),
                    maxLines = Constants.DIALOG_MESSAGE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                TextButton(onClick = { showEmptyAlbumWarning = false }) {
                    Text(
                        text = stringResource(R.string.ok),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}
