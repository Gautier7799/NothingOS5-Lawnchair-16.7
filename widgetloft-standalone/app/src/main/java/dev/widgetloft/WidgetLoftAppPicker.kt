package dev.widgetloft

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * Loads the apps a user can put on the board and shows a multi-select picker.
 *
 * The list comes from [PackageManager.queryIntentActivities] for `MAIN`/`LAUNCHER`, so it contains
 * exactly the apps that appear in the system launcher. Selection is returned through [onPicked] as
 * [AppEntry] values, which is the same model the cards store.
 */
object WidgetLoftAppPicker {

    /** Every launchable app on the device, de-duplicated by package and sorted by label. */
    fun loadLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                AppEntry(pkg, info.loadLabel(pm).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Shows a checkbox list of every launchable app; [alreadyPicked] start out checked. */
    fun show(
        context: Context,
        title: String,
        alreadyPicked: Set<String>,
        onPicked: (List<AppEntry>) -> Unit,
    ) {
        val apps = loadLaunchableApps(context)
        val theme = WidgetLoftTheme(context)

        val list = ListView(context)
        val adapter = AppRowAdapter(context, apps, alreadyPicked, theme)
        list.adapter = adapter
        list.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(list)
            .setPositiveButton("Add") { _, _ -> onPicked(adapter.selected()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class AppRowAdapter(
        private val context: Context,
        private val apps: List<AppEntry>,
        alreadyPicked: Set<String>,
        private val theme: WidgetLoftTheme,
    ) : ArrayAdapter<AppEntry>(context, 0, apps) {

        private val checked = HashSet(alreadyPicked)
        private val iconCache = HashMap<String, Drawable?>()

        fun selected(): List<AppEntry> = apps.filter { it.packageName in checked }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView ?: newRow()) as LinearLayout
            val app = apps[position]
            val check = row.getChildAt(0) as CheckBox
            val icon = row.getChildAt(1) as ImageView
            val label = row.getChildAt(2) as TextView

            check.isChecked = app.packageName in checked
            icon.setImageDrawable(iconCache.getOrPut(app.packageName) {
                runCatching { context.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
            })
            label.text = app.label

            row.setOnClickListener {
                if (!checked.add(app.packageName)) checked.remove(app.packageName)
                check.isChecked = app.packageName in checked
            }
            return row
        }

        private fun newRow(): View {
            val density = context.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
            row.addView(CheckBox(context).apply { isClickable = false; isFocusable = false })
            row.addView(
                ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                        marginStart = dp(8)
                        marginEnd = dp(12)
                    }
                },
            )
            row.addView(
                TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setTextColor(theme.onSurface())
                    textSize = 16f
                },
            )
            return row
        }
    }
}
