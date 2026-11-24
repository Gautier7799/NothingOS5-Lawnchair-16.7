/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.destinations

import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.LawnchairApp
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.sidebar.QuickControl
import app.lawnchair.sidebar.QuickControlSelection
import app.lawnchair.sidebar.SidebarGestureAction
import app.lawnchair.sidebar.SidebarGestureBindings
import app.lawnchair.sidebar.SidebarMetrics
import app.lawnchair.sidebar.SidebarTheme
import app.lawnchair.theme.color.ColorMode
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.FeedPreference
import app.lawnchair.ui.preferences.components.GestureHandlerPreference
import app.lawnchair.ui.preferences.components.HomeLayoutSettings
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.OverlayHandlerPreference
import app.lawnchair.ui.preferences.components.PermissionDialog
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.HomeScreenGrid
import app.lawnchair.ui.preferences.navigation.HomeScreenSidebarApps
import app.lawnchair.util.collectAsStateBlocking
import com.android.launcher3.R
import com.android.launcher3.Utilities
import kotlinx.coroutines.launch

object HomeScreenRoutes {
    const val GRID = "grid"
    const val POPUP_EDITOR = "popup_editor"
}

@Composable
fun HomeScreenPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val scope = rememberCoroutineScope()
    PreferenceLayout(
        label = stringResource(id = R.string.home_screen_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val lockHomeScreenAdapter = prefs2.lockHomeScreen.getAdapter()
        val showDeckLayout = prefs2.showDeckLayout.getAdapter().state.value

        if (showDeckLayout) {
            HomeLayoutSettings()
        }

        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            val addIconToHomeAdapter = prefs.addIconToHome.getAdapter()
            val isDeckLayoutAdapter = prefs2.deckLayout.getAdapter()
            ExpandAndShrink(visible = !isDeckLayoutAdapter.state.value) {
                SwitchPreference(
                    checked = (!lockHomeScreenAdapter.state.value && addIconToHomeAdapter.state.value) || isDeckLayoutAdapter.state.value,
                    onCheckedChange = addIconToHomeAdapter::onChange,
                    label = stringResource(id = R.string.auto_add_shortcuts_label),
                    description = if (lockHomeScreenAdapter.state.value) stringResource(id = R.string.home_screen_locked) else null,
                    enabled = lockHomeScreenAdapter.state.value.not(),
                )
            }
            GestureHandlerPreference(
                adapter = prefs2.doubleTapGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_double_tap),
            )
            SwitchPreference(
                prefs.infiniteScrolling.getAdapter(),
                label = stringResource(id = R.string.infinite_scrolling_label),
                description = stringResource(id = R.string.infinite_scrolling_description),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.home_screen_actions)) {
            SwitchPreference(
                adapter = prefs2.hideWorkspaceSmartspace.getAdapter(),
                label = stringResource(R.string.hide_workspace_smartspace_label),
                description = stringResource(R.string.hide_workspace_smartspace_description),
            )
        }
        val dossierEnabledAdapter = prefs2.dossierEnabled.getAdapter()
        val sidebarEnabledAdapter = prefs2.sidebarEnabled.getAdapter()
        val sidebarOnRightEdgeAdapter = prefs2.sidebarOnRightEdge.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.sidebar_section_title)) {
            SwitchPreference(
                adapter = sidebarEnabledAdapter,
                label = stringResource(R.string.sidebar_enabled_label),
                description = stringResource(R.string.sidebar_enabled_description),
            )
            ExpandAndShrink(visible = sidebarEnabledAdapter.state.value) {
                NavigationActionPreference(
                    label = stringResource(R.string.sidebar_apps_label),
                    subtitle = stringResource(R.string.sidebar_apps_description),
                    destination = HomeScreenSidebarApps,
                )
                SliderPreference(
                    label = stringResource(R.string.sidebar_columns_label),
                    adapter = prefs2.sidebarColumns.getAdapter(),
                    valueRange = SidebarMetrics.MIN_COLUMNS..SidebarMetrics.MAX_COLUMNS,
                    step = 1,
                )
                SliderPreference(
                    label = stringResource(R.string.sidebar_opacity_label),
                    adapter = prefs2.sidebarOpacity.getAdapter(),
                    valueRange = SidebarMetrics.MIN_OPACITY_PERCENT..100,
                    step = 10,
                    showAsPercentage = true,
                )
                SliderPreference(
                    label = stringResource(R.string.sidebar_handle_position_label),
                    adapter = prefs2.sidebarHandlePosition.getAdapter(),
                    valueRange = 0f..1f,
                    step = 0.05f,
                    showAsPercentage = true,
                )
                SwitchPreference(
                    adapter = sidebarOnRightEdgeAdapter,
                    label = stringResource(R.string.sidebar_on_right_edge_label),
                    description = stringResource(R.string.sidebar_on_right_edge_description),
                )
                val suggestionsAdapter = prefs2.sidebarSuggestions.getAdapter()
                val context = LocalContext.current
                val appOps = remember { context.getSystemService(AppOpsManager::class.java) }
                val hasUsageAccess = remember {
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName,
                    ) == AppOpsManager.MODE_ALLOWED
                }
                var showUsageDialog by remember { mutableStateOf(false) }
                SwitchPreference(
                    checked = suggestionsAdapter.state.value && hasUsageAccess,
                    onCheckedChange = { newValue ->
                        if (newValue && !hasUsageAccess) {
                            showUsageDialog = true
                        } else {
                            suggestionsAdapter.onChange(newValue)
                        }
                    },
                    label = stringResource(R.string.sidebar_suggestions_label),
                    description = stringResource(R.string.sidebar_suggestions_description),
                )
                if (showUsageDialog) {
                    PermissionDialog(
                        title = stringResource(R.string.missing_usage_access_label),
                        text = stringResource(
                            R.string.missing_usage_access_desc,
                            stringResource(R.string.derived_app_name),
                        ),
                        isPermanentlyDenied = true,
                        onConfirm = {},
                        onDismiss = { showUsageDialog = false },
                        onGoToSettings = {
                            showUsageDialog = false
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        },
                    )
                }
                SwitchPreference(
                    adapter = prefs2.sidebarHaptics.getAdapter(),
                    label = stringResource(R.string.sidebar_haptics_label),
                    description = stringResource(R.string.sidebar_haptics_description),
                )
                val quickControlsAdapter = prefs2.sidebarQuickControls.getAdapter()
                val quickControlListAdapter = prefs2.sidebarQuickControlList.getAdapter()
                SwitchPreference(
                    adapter = quickControlsAdapter,
                    label = stringResource(R.string.sidebar_quick_controls_label),
                    description = stringResource(R.string.sidebar_quick_controls_description),
                )
                ExpandAndShrink(visible = quickControlsAdapter.state.value) {
                    // One switch per tile. The stored value is a name list, so toggling means
                    // re-serialising the current selection rather than flipping a boolean.
                    QuickControl.defaultOrder.forEach { control ->
                        SwitchPreference(
                            checked = QuickControlSelection.isEnabled(
                                QuickControlSelection.resolve(quickControlListAdapter.state.value),
                                control,
                            ),
                            label = stringResource(control.labelRes),
                            description = stringResource(
                                R.string.sidebar_quick_control_toggle_description,
                            ),
                            onCheckedChange = { checked ->
                                val current = QuickControlSelection.resolve(
                                    quickControlListAdapter.state.value,
                                )
                                val next = if (checked) {
                                    current + control
                                } else {
                                    current - control
                                }
                                quickControlListAdapter.onChange(
                                    QuickControlSelection.serialise(next),
                                )
                            },
                        )
                    }
                }
                SwitchPreference(
                    adapter = prefs2.sidebarVolumeSliders.getAdapter(),
                    label = stringResource(R.string.sidebar_volume_sliders_label),
                    description = stringResource(R.string.sidebar_volume_sliders_description),
                )
                SwitchPreference(
                    adapter = prefs2.sidebarSearch.getAdapter(),
                    label = stringResource(R.string.sidebar_search_label),
                    description = stringResource(R.string.sidebar_search_description),
                )
                SwitchPreference(
                    adapter = prefs2.sidebarBrightnessSlider.getAdapter(),
                    label = stringResource(R.string.sidebar_brightness_slider_label),
                    description = stringResource(R.string.sidebar_brightness_slider_description),
                )
                SwitchPreference(
                    adapter = prefs2.sidebarShowLabels.getAdapter(),
                    label = stringResource(R.string.sidebar_show_labels_label),
                    description = stringResource(R.string.sidebar_show_labels_description),
                )
                SwitchPreference(
                    adapter = prefs2.sidebarShowOutline.getAdapter(),
                    label = stringResource(R.string.sidebar_show_outline_label),
                    description = stringResource(R.string.sidebar_show_outline_description),
                )
                SwitchPreference(
                    adapter = prefs2.sidebarAutoHideFullscreen.getAdapter(),
                    label = stringResource(R.string.sidebar_auto_hide_fullscreen_label),
                    description = stringResource(R.string.sidebar_auto_hide_fullscreen_description),
                )
                ListPreference(
                    adapter = prefs2.sidebarTheme.getAdapter(),
                    entries = sidebarThemeEntries,
                    label = stringResource(R.string.sidebar_theme_label),
                    description = stringResource(R.string.sidebar_theme_description),
                )
                val gesturesEnabledAdapter = prefs2.sidebarGesturesEnabled.getAdapter()
                SwitchPreference(
                    adapter = gesturesEnabledAdapter,
                    label = stringResource(R.string.sidebar_gestures_enabled_label),
                    description = stringResource(R.string.sidebar_gestures_enabled_description),
                )
                ExpandAndShrink(visible = gesturesEnabledAdapter.state.value) {
                    SliderPreference(
                        label = stringResource(R.string.sidebar_swipe_sensitivity_label),
                        adapter = prefs2.sidebarSwipeSensitivity.getAdapter(),
                        valueRange = 0..100,
                        step = 10,
                        showAsPercentage = true,
                    )
                }
            }
        }
        val gestureBindingsAdapter = prefs2.sidebarGestureBindings.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.sidebar_gestures_heading)) {
            SidebarGestureBindings.directions.forEach { direction ->
                ListPreference(
                    adapter = object : PreferenceAdapter<String> {
                        // The stored value is the shared bindings string; this adapter projects
                        // one direction out of it and writes the whole string back.
                        override val state = object : State<String> {
                            override val value: String
                                get() {
                                    val resolved = SidebarGestureBindings.resolve(
                                        gestureBindingsAdapter.state.value,
                                    )
                                    return SidebarGestureBindings.actionFor(resolved, direction).name
                                }
                        }

                        override fun onChange(newValue: String) {
                            val current = SidebarGestureBindings.resolve(
                                gestureBindingsAdapter.state.value,
                            )
                            val action = SidebarGestureAction.entries.firstOrNull {
                                it.name.equals(newValue, ignoreCase = true)
                            } ?: return
                            gestureBindingsAdapter.onChange(
                                SidebarGestureBindings.serialise(current + (direction to action)),
                            )
                        }
                    },
                    entries = sidebarGestureActionEntries,
                    label = stringResource(direction.labelRes),
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.dossier)) {
            SwitchPreference(
                adapter = dossierEnabledAdapter,
                label = stringResource(R.string.dossier_enabled_label),
                description = stringResource(R.string.dossier_enabled_description),
            )
            ExpandAndShrink(visible = dossierEnabledAdapter.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.columns),
                    adapter = prefs2.dossierGridColumns.getAdapter(),
                    step = 1,
                    valueRange = 1..8,
                )
                SliderPreference(
                    label = stringResource(id = R.string.rows),
                    adapter = prefs2.dossierGridRows.getAdapter(),
                    step = 1,
                    valueRange = 1..8,
                )
            }
        }
        val feedAvailable = OverlayCallbackImpl.minusOneAvailable(LocalContext.current)
        val enableFeedAdapter = prefs2.enableFeed.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.minus_one)) {
            SwitchPreference(
                adapter = enableFeedAdapter,
                label = stringResource(id = R.string.minus_one_enable),
                description = if (feedAvailable) null else stringResource(id = R.string.minus_one_unavailable),
                enabled = feedAvailable,
            )
            ExpandAndShrink(visible = feedAvailable && enableFeedAdapter.state.value) {
                FeedPreference()
            }
        }
        PreferenceGroup(heading = stringResource(R.string.style)) {
            HomeScreenTextColorPreference()
            OverlayHandlerPreference(
                adapter = prefs2.closingAppOverlay.getAdapter(),
                label = stringResource(id = R.string.app_closing_animation),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.wallpaper)) {
            SwitchPreference(
                prefs.wallpaperScrolling.getAdapter(),
                label = stringResource(id = R.string.wallpaper_scrolling_label),
            )
            ExpandAndShrink(visible = Utilities.ATLEAST_R) {
                SwitchPreference(
                    prefs2.wallpaperDepthEffect.getAdapter(),
                    label = stringResource(id = R.string.wallpaper_depth_effect_label),
                    description = stringResource(id = R.string.wallpaper_depth_effect_description),
                )
            }
            SwitchPreference(
                adapter = prefs2.showTopShadow.getAdapter(),
                label = stringResource(id = R.string.show_sys_ui_scrim),
            )
        }
        val columns by prefs.workspaceColumns.getAdapter()
        val rows by prefs.workspaceRows.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.layout)) {
            NavigationActionPreference(
                label = stringResource(id = R.string.home_screen_grid),
                destination = HomeScreenGrid,
                subtitle = stringResource(id = R.string.x_by_y, columns, rows),
            )
            SliderPreference(
                label = stringResource(id = R.string.horizontal_padding_label),
                adapter = prefs2.workspacePaddingHorizontalFactor.getAdapter(),
                step = 0.05f,
                valueRange = 0F..2F,
                showAsPercentage = true,
            )
            SliderPreference(
                label = stringResource(id = R.string.vertical_padding_label),
                adapter = prefs2.workspacePaddingVerticalFactor.getAdapter(),
                step = 0.05f,
                valueRange = 0F..2F,
                showAsPercentage = true,
            )
            SwitchPreference(
                adapter = lockHomeScreenAdapter,
                label = stringResource(id = R.string.home_screen_lock),
                description = stringResource(id = R.string.home_screen_lock_description),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.popup_menu)) {
            LauncherPopupPreferenceItem()
        }
        val showStatusBarAdapter = prefs2.showStatusBar.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.status_bar_label)) {
            SwitchPreference(
                adapter = showStatusBarAdapter,
                label = stringResource(id = R.string.show_status_bar),
            )
            ExpandAndShrink(visible = showStatusBarAdapter.state.value) {
                SwitchPreference(
                    adapter = prefs2.darkStatusBar.getAdapter(),
                    label = stringResource(id = R.string.dark_status_bar_label),
                )
            }
            ExpandAndShrink(visible = showStatusBarAdapter.state.value && LawnchairApp.isRecentsEnabled) {
                SwitchPreference(
                    adapter = prefs2.statusBarClock.getAdapter(),
                    label = stringResource(id = R.string.status_bar_clock_label),
                    description = stringResource(id = R.string.status_bar_clock_description),
                )
            }
        }
        val homeScreenLabelsAdapter = prefs2.showIconLabelsOnHomeScreen.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.icons)) {
            SliderPreference(
                label = stringResource(id = R.string.icon_sizes),
                adapter = prefs2.homeIconSizeFactor.getAdapter(),
                step = 0.1f,
                valueRange = 0.5F..1.5F,
                showAsPercentage = true,
            )
            SwitchPreference(
                adapter = homeScreenLabelsAdapter,
                label = stringResource(id = R.string.show_labels),
            )
            ExpandAndShrink(visible = homeScreenLabelsAdapter.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.label_size),
                    adapter = prefs2.homeIconLabelSizeFactor.getAdapter(),
                    step = 0.1f,
                    valueRange = 0.5F..1.5F,
                    showAsPercentage = true,
                )
            }
        }
        val overrideRepo = IconOverrideRepository.INSTANCE.get(LocalContext.current)
        val customIconsCount by remember { overrideRepo.observeCount() }.collectAsStateBlocking()
        if (customIconsCount > 0) {
            PreferenceGroup {
                ClickablePreference(
                    label = stringResource(id = R.string.reset_custom_icons),
                    confirmationText = stringResource(id = R.string.reset_custom_icons_confirmation),
                    onClick = { scope.launch { overrideRepo.deleteAll() } },
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.widget_button_text)) {
            SwitchPreference(
                adapter = prefs2.roundedWidgets.getAdapter(),
                label = stringResource(id = R.string.force_rounded_widgets),
            )
            SwitchPreference(
                adapter = prefs2.allowWidgetOverlap.getAdapter(),
                label = stringResource(id = R.string.allow_widget_overlap),
            )
            SwitchPreference(
                adapter = prefs2.widgetUnlimitedSize.getAdapter(),
                label = stringResource(id = R.string.widget_unlimited_size_label),
                description = stringResource(id = R.string.widget_unlimited_size_description),
            )
            SwitchPreference(
                adapter = prefs2.forceWidgetResize.getAdapter(),
                label = stringResource(id = R.string.force_widget_resize_label),
                description = stringResource(id = R.string.force_widget_resize_description),
            )
            SliderPreference(
                label = stringResource(id = R.string.widget_padding_label),
                adapter = prefs2.widgetPaddingFactor.getAdapter(),
                step = 0.05f,
                valueRange = 0F..2F,
                showAsPercentage = true,
            )
        }
    }
}

@Composable
fun HomeScreenTextColorPreference(
    modifier: Modifier = Modifier,
) {
    ListPreference(
        adapter = preferenceManager2().workspaceTextColor.getAdapter(),
        entries = ColorMode.entries(),
        label = stringResource(id = R.string.home_screen_text_color),
        modifier = modifier,
    )
}

private val sidebarThemeEntries = listOf(
    ListPreferenceEntry(SidebarTheme.TONAL.name) { stringResource(R.string.sidebar_theme_tonal) },
    ListPreferenceEntry(SidebarTheme.OPAQUE.name) { stringResource(R.string.sidebar_theme_opaque) },
    ListPreferenceEntry(SidebarTheme.TRANSPARENT.name) { stringResource(R.string.sidebar_theme_transparent) },
)

private val sidebarGestureActionEntries = listOf(
    ListPreferenceEntry(SidebarGestureAction.NONE.name) {
        stringResource(R.string.sidebar_gesture_action_none)
    },
    ListPreferenceEntry(SidebarGestureAction.OPEN_PANEL.name) {
        stringResource(R.string.sidebar_gesture_action_open_panel)
    },
    ListPreferenceEntry(SidebarGestureAction.OPEN_ALL_APPS.name) {
        stringResource(R.string.sidebar_gesture_action_open_all_apps)
    },
    ListPreferenceEntry(SidebarGestureAction.OPEN_SEARCH.name) {
        stringResource(R.string.sidebar_gesture_action_open_search)
    },
    ListPreferenceEntry(SidebarGestureAction.OPEN_ASSISTANT.name) {
        stringResource(R.string.sidebar_gesture_action_open_assistant)
    },
    ListPreferenceEntry(SidebarGestureAction.OPEN_NOTIFICATIONS.name) {
        stringResource(R.string.sidebar_gesture_action_open_notifications)
    },
    ListPreferenceEntry(SidebarGestureAction.OPEN_QUICK_SETTINGS.name) {
        stringResource(R.string.sidebar_gesture_action_open_quick_settings)
    },
    ListPreferenceEntry(SidebarGestureAction.OPEN_POWER_MENU.name) {
        stringResource(R.string.sidebar_gesture_action_open_power_menu)
    },
)
