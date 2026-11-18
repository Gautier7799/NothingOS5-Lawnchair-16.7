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

package app.lawnchair.homecards

import android.content.Context
import android.content.DialogInterface
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey

/**
 * The "create a category" editor.
 *
 * This is the same flow the drawer's "+ Add category" card opens: name the category, tick its
 * apps, and save. It writes the exact storage shape [CaddyHomeCardSource] reads
 * (`caddy_custom_categories` / `categories`, a JSON array of `{title, apps:[componentKey]}`), so a
 * category created here is indistinguishable from one created in the drawer.
 *
 * It is shared by the drawer and the App Library page so both create categories identically.
 */
object CaddyCategoryEditor {

    /** Opens the editor over [context] for [apps]. */
    @JvmStatic
    fun show(context: Context, apps: List<AppInfo>) {
        val available = LinkedHashMap<String, AppInfo>()
        apps.forEach { app ->
            val component = app.componentName ?: return@forEach
            available[ComponentKey(component, app.user).toString()] = app
        }
        if (available.isEmpty()) return

        val content = LinearLayout(context)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(context, 20f), dp(context, 8f), dp(context, 20f), dp(context, 4f))

        val nameInput = EditText(context)
        nameInput.isSingleLine = true
        nameInput.hint = context.getString(R.string.caddy_category_name_hint)
        content.addView(
            nameInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val scroll = ScrollView(context)
        val choices = LinearLayout(context)
        choices.orientation = LinearLayout.VERTICAL
        scroll.addView(choices)
        content.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 320f)),
        )

        val selectedKeys = ArrayList<String>()
        available.forEach { (key, app) ->
            val checkBox = CheckBox(context)
            checkBox.text = app.title
            checkBox.textSize = 14f
            checkBox.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    if (!selectedKeys.contains(key)) selectedKeys.add(key)
                } else {
                    selectedKeys.remove(key)
                }
            }
            choices.addView(
                checkBox,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.caddy_category_editor_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.caddy_category_create, null)
            .create()
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val title = nameInput.text.toString().trim()
            if (title.isEmpty() || selectedKeys.isEmpty()) {
                Toast.makeText(context, R.string.caddy_category_editor_validation, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if (save(context, title, selectedKeys)) {
                Toast.makeText(context, R.string.caddy_category_created, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(context, R.string.caddy_category_save_failed, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    /**
     * Appends a category to the stored list. The write is synchronous on purpose: the caller
     * re-reads the list immediately, and an async apply could surface a stale snapshot.
     */
    private fun save(context: Context, title: String, appKeys: List<String>): Boolean = runCatching {
        val prefs = context.getSharedPreferences(
            CaddyHomeCardSource.CUSTOM_CATEGORIES_PREFS,
            Context.MODE_PRIVATE,
        )
        val updated = CaddyCategoryStore.addCategory(
            existing = prefs.getString(CaddyHomeCardSource.CUSTOM_CATEGORIES_KEY, null),
            title = title,
            appKeys = appKeys,
        )
        prefs.edit()
            .putString(CaddyHomeCardSource.CUSTOM_CATEGORIES_KEY, updated)
            .commit()
    }.getOrDefault(false)

    private fun dp(context: Context, value: Float): Int = (value * context.resources.displayMetrics.density).toInt()
}
