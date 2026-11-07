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

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Internal storage usage.
 *
 * Reads the shared data partition via [StatFs]. It refreshes on the platform's periodic update and
 * whenever the widget is reconfigured. The detail line reports free space.
 */
class StorageWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val wide = widgetWidth(context, appWidgetId) >= WIDE_WIDTH_DP
        val layout = if (wide) R.layout.widget_storage_wide else R.layout.widget_storage
        val views = RemoteViews(context.packageName, layout)

        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_storage,
            context.getString(R.string.widget_storage_label),
        )
        NativeWidgetPreferences.applyDetailVisibility(context, views, appWidgetId)
        views.setTextColor(R.id.widget_detail, NativeWidgetPreferences.variantColor(context, appWidgetId))

        val usage = runCatching {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val available = stat.availableBlocksLong * stat.blockSizeLong
            StorageUsage(total = total, used = total - available, free = available)
        }.getOrNull()

        if (usage == null || usage.total <= 0L) {
            views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_storage_unavailable))
            views.setTextViewText(R.id.widget_detail, "")
            views.setProgressBar(R.id.widget_bar, 100, 0, false)
            views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
            return views
        }

        val fraction = WidgetFormat.usedFraction(usage.used, usage.total)
        val percent = (fraction * 100).toInt()
        views.setTextViewText(R.id.widget_value, "$percent%")
        views.setTextViewText(
            R.id.widget_detail,
            context.getString(R.string.widget_storage_free_detail, WidgetFormat.formatBytes(usage.free)),
        )
        views.setProgressBar(R.id.widget_bar, 100, percent, false)
        views.setTextColor(
            R.id.widget_value,
            if (fraction >= 0.9f) {
                context.getColor(R.color.widget_warning)
            } else {
                NativeWidgetPreferences.primaryColor(context, appWidgetId)
            },
        )
        return views
    }

    private data class StorageUsage(val total: Long, val used: Long, val free: Long)
}
