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
 * whenever the widget is reconfigured.
 */
class StorageWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_storage)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)

        val usage = runCatching {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val available = stat.availableBlocksLong * stat.blockSizeLong
            total to (total - available)
        }.getOrNull()

        if (usage == null || usage.first <= 0L) {
            views.setTextViewText(R.id.widget_storage_percent, context.getString(R.string.widget_storage_unavailable))
            views.setTextViewText(R.id.widget_storage_detail, "")
            views.setProgressBar(R.id.widget_storage_bar, 100, 0, false)
            return views
        }

        val (total, used) = usage
        val fraction = WidgetFormat.usedFraction(used, total)
        val percent = (fraction * 100).toInt()
        views.setTextViewText(R.id.widget_storage_percent, "$percent%")
        views.setTextViewText(
            R.id.widget_storage_detail,
            context.getString(
                R.string.widget_storage_detail,
                WidgetFormat.formatBytes(used),
                WidgetFormat.formatBytes(total),
            ),
        )
        views.setProgressBar(R.id.widget_storage_bar, 100, percent, false)
        views.setTextColor(
            R.id.widget_storage_percent,
            if (fraction >= 0.9f) {
                context.getColor(R.color.widget_warning)
            } else {
                context.getColor(R.color.widget_on_surface)
            },
        )
        return views
    }
}
