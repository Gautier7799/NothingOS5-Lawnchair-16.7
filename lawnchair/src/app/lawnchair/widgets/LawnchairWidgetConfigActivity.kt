/*
 * Copyright 2026, Lawnchair
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

package app.lawnchair.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R

/**
 * Configuration screen shown when a native Lawnchair widget is dropped on the home screen.
 *
 * Persists the per-instance options in [NativeWidgetPreferences] and then asks the owning
 * provider to redraw itself.
 */
class LawnchairWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val showClockOptions = isClockWidget(appWidgetId)
        val initialStyle = NativeWidgetPreferences.getStyle(this, appWidgetId)
        val initialShowSeconds = NativeWidgetPreferences.getShowSeconds(this, appWidgetId)

        setContent {
            LawnchairTheme {
                EdgeToEdge()
                var style by remember { mutableIntStateOf(initialStyle) }
                var showSeconds by remember { mutableStateOf(initialShowSeconds) }

                Scaffold { innerPadding ->
                    WidgetConfigContent(
                        showClockOptions = showClockOptions,
                        selectedStyle = style,
                        showSeconds = showSeconds,
                        onStyleSelect = { style = it },
                        onShowSecondsChange = { showSeconds = it },
                        onSave = {
                            NativeWidgetPreferences.setStyle(this, appWidgetId, style)
                            NativeWidgetPreferences.setShowSeconds(this, appWidgetId, showSeconds)
                            requestUpdate(appWidgetId)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                            finish()
                        },
                        onCancel = {
                            setResult(RESULT_CANCELED)
                            finish()
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    private fun isClockWidget(appWidgetId: Int): Boolean {
        val provider = AppWidgetManager.getInstance(this)
            ?.getAppWidgetInfo(appWidgetId)
            ?.provider
            ?.className
        return provider == ClockWidgetProvider::class.java.name
    }

    private fun requestUpdate(appWidgetId: Int) {
        val manager = AppWidgetManager.getInstance(this) ?: return
        val provider = manager.getAppWidgetInfo(appWidgetId)?.provider ?: return
        sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = provider
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            },
        )
    }
}

@Composable
private fun WidgetConfigContent(
    showClockOptions: Boolean,
    selectedStyle: Int,
    showSeconds: Boolean,
    onStyleSelect: (Int) -> Unit,
    onShowSecondsChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.widget_settings_label),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.widget_style_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))

        StyleOption(
            label = stringResource(R.string.widget_style_filled),
            selected = selectedStyle == NativeWidgetPreferences.STYLE_FILLED,
            onSelect = { onStyleSelect(NativeWidgetPreferences.STYLE_FILLED) },
        )
        StyleOption(
            label = stringResource(R.string.widget_style_transparent),
            selected = selectedStyle == NativeWidgetPreferences.STYLE_TRANSPARENT,
            onSelect = { onStyleSelect(NativeWidgetPreferences.STYLE_TRANSPARENT) },
        )
        Text(
            text = stringResource(R.string.widget_settings_use_dynamic_color_applied_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (showClockOptions) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.widget_clock_show_seconds),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = showSeconds, onCheckedChange = onShowSecondsChange)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(android.R.string.cancel))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onSave) {
                Text(text = stringResource(android.R.string.ok))
            }
        }
    }
}

@Composable
private fun StyleOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
