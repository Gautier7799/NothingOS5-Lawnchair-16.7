package dev.widgetloft

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(WidgetLoftBoardView(this))
    }
}

private class WidgetLoftBoardView(private val activity: Activity) : ScrollView(activity) {
    private val preferences = activity.getSharedPreferences("widgetloft-board", Activity.MODE_PRIVATE)
    private val board = GridLayout(activity).apply { columnCount = 2; useDefaultMargins = true; setPadding(dp(8), dp(8), dp(8), dp(24)) }
    private val selectedIds = linkedSetOf<String>()
    private val installedApps = loadLaunchableApps()
    private val cards = loadBoard()

    init {
        isFillViewport = true
        setBackgroundColor(systemColor("system_neutral1_50", "system_neutral1_900", android.R.color.background_dark))
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        content.addView(header(), LinearLayout.LayoutParams(-1, -2))
        content.addView(board, LinearLayout.LayoutParams(-1, -2))
        addView(content)
        board.setOnDragListener { _, event ->
            if (event.action == android.view.DragEvent.ACTION_DROP) {
                val id = event.localState as? String
                val dragged = cards.firstOrNull { it.id == id }
                if (dragged != null) {
                    val target = (0 until board.childCount).firstOrNull { index ->
                        val bounds = android.graphics.Rect()
                        board.getChildAt(index).getHitRect(bounds)
                        bounds.contains(event.x.toInt(), event.y.toInt())
                    } ?: (cards.size - 1)
                    cards.remove(dragged)
                    cards.add(target.coerceIn(0, cards.size), dragged)
                    persistBoard()
                    renderCards()
                }
            }
            true
        }
        renderCards()
    }

    private fun header(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(activity).apply { text = "WidgetLoft"; textSize = 28f; setTextColor(themeColor(android.R.attr.textColorPrimary)) })
        addView(TextView(activity).apply { text = "Your app collections"; textSize = 13f; setTextColor(themeColor(android.R.attr.textColorSecondary)) })
        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(action("Add apps") { openAppPicker() })
        actions.addView(action("Stack") { merge(MergeMode.STACK) })
        actions.addView(action("Combine") { merge(MergeMode.COMBINE) })
        actions.addView(action("Split") { splitSelected() })
        actions.addView(action("Delete") { deleteSelected() })
        addView(actions)
    }

    private fun action(label: String, callback: () -> Unit) = Button(activity).apply {
        text = label
        textSize = 10f
        setOnClickListener { callback() }
        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
    }

    private fun openAppPicker() {
        if (installedApps.isEmpty()) return
        val target = cards.singleOrNull { selectedIds.contains(it.id) }
        val existing = target?.apps?.map { it.packageName }?.toSet().orEmpty()
        val labels = installedApps.map { it.label }.toTypedArray()
        val checked = BooleanArray(installedApps.size) { installedApps[it].packageName in existing }
        AlertDialog.Builder(activity)
            .setTitle(if (target == null) "Create collection" else "Add apps to " + target.title)
            .setMultiChoiceItems(labels, checked) { _, which, value -> checked[which] = value }
            .setPositiveButton("Apply") { _, _ ->
                val chosen = installedApps.filterIndexed { index, _ -> checked[index] }
                if (chosen.isNotEmpty()) {
                    if (target == null) {
                        val number = cards.size + 1
                        cards.add(WidgetLoftCard("collection-" + System.currentTimeMillis(), "Collection " + number, chosen))
                    } else {
                        val updated = target.copy(apps = (target.apps + chosen).distinctBy { it.packageName })
                        replaceCard(updated)
                    }
                    selectedIds.clear()
                    persistBoard()
                    renderCards()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun merge(mode: MergeMode) {
        if (selectedIds.size != 2) { toast("Select exactly two cards first"); return }
        val result = WidgetLoftLayoutEngine.merge(cards, selectedIds, mode, "merged-" + System.currentTimeMillis())
        cards.clear()
        cards.addAll(result)
        selectedIds.clear()
        persistBoard()
        renderCards()
    }

    private fun splitSelected() {
        val card = cards.firstOrNull { selectedIds.contains(it.id) } ?: return
        val parts = WidgetLoftLayoutEngine.split(card)
        if (parts.size <= 1) { toast("Select a stack to split"); return }
        val index = cards.indexOf(card)
        cards.removeAt(index)
        cards.addAll(index, parts)
        selectedIds.clear()
        persistBoard()
        renderCards()
    }

    private fun deleteSelected() {
        if (selectedIds.isEmpty()) { toast("Select a collection first"); return }
        AlertDialog.Builder(activity)
            .setMessage("Delete selected collection(s)?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                cards.removeAll { selectedIds.contains(it.id) }
                selectedIds.clear()
                persistBoard()
                renderCards()
            }.show()
    }

    private fun renderCards() {
        board.removeAllViews()
        cards.forEach { card ->
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1),
                GridLayout.spec(GridLayout.UNDEFINED, card.columnSpan, 1f),
            ).apply { width = 0; height = dp(card.heightDp); setMargins(dp(5), dp(5), dp(5), dp(5)) }
            board.addView(cardView(card), params)
        }
    }

    private fun cardView(card: WidgetLoftCard): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            background = rounded(systemColor("system_neutral1_100", "system_neutral1_800", android.R.color.darker_gray), 26)
            if (selectedIds.contains(card.id)) alpha = 0.72f
        }
        val heading = TextView(activity).apply {
            text = if (card.stackedCards.isEmpty()) card.title + " · " + card.apps.size + " apps" else card.title + " · " + card.stackedCards.size + " cards"
            textSize = 15f
            setTextColor(themeColor(android.R.attr.textColorPrimary))
            setOnClickListener {
                if (!selectedIds.add(card.id)) selectedIds.remove(card.id)
                renderCards()
            }
            setOnLongClickListener {
                startDragAndDrop(ClipData.newPlainText("widgetloft-card", card.id), View.DragShadowBuilder(this), card.id, 0)
            }
        }
        container.addView(heading, LinearLayout.LayoutParams(-1, dp(32)))
        if (card.stackedCards.isNotEmpty()) {
            card.stackedCards.forEach { child ->
                container.addView(TextView(activity).apply {
                    text = "▦  " + child.title + " · " + child.apps.size + " apps"
                    textSize = 12f
                    setTextColor(themeColor(android.R.attr.textColorSecondary))
                    setOnClickListener {
                        val index = cards.indexOf(card)
                        if (index >= 0) { cards[index] = child; persistBoard(); renderCards() }
                    }
                })
            }
        } else {
            val appGrid = GridLayout(activity).apply { columnCount = 2 }
            card.apps.take(8).forEach { app ->
                val item = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                    setOnClickListener { activity.packageManager.getLaunchIntentForPackage(app.packageName)?.let(activity::startActivity) }
                    setOnLongClickListener {
                        AlertDialog.Builder(activity)
                            .setMessage("Remove " + app.label + " from " + card.title + "?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Remove") { _, _ -> replaceCard(card.copy(apps = card.apps.filterNot { it.packageName == app.packageName })) }
                            .show()
                        true
                    }
                    addView(ImageView(activity).apply {
                        setImageDrawable(runCatching { activity.packageManager.getApplicationIcon(app.packageName) }.getOrNull())
                        layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                    })
                    addView(TextView(activity).apply {
                        text = app.label.take(16)
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(themeColor(android.R.attr.textColorPrimary))
                    })
                }
                appGrid.addView(item, GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1, 1f),
                ).apply { width = 0; height = dp(70) })
            }
            container.addView(appGrid, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val footer = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        footer.addView(TextView(activity).apply {
            text = if (card.columnSpan == 2) "Width: 2 columns" else "Width: 1 column"
            textSize = 10f
            setTextColor(themeColor(android.R.attr.colorAccent))
            setOnClickListener { replaceCard(WidgetLoftLayoutEngine.resize(card, if (card.columnSpan == 2) 1 else 2, card.heightDp)) }
        }, LinearLayout.LayoutParams(0, dp(30), 1f))
        var startY = 0f
        var startHeight = card.heightDp
        footer.addView(TextView(activity).apply {
            text = "↘ Resize"
            textSize = 11f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(themeColor(android.R.attr.colorAccent))
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { startY = event.rawY; startHeight = card.heightDp; true }
                    MotionEvent.ACTION_MOVE -> {
                        val height = (startHeight + ((event.rawY - startY) / resources.displayMetrics.density).toInt()).coerceIn(144, 360)
                        container.layoutParams = container.layoutParams.apply { this.height = dp(height) }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val height = (startHeight + ((event.rawY - startY) / resources.displayMetrics.density).toInt()).coerceIn(144, 360)
                        replaceCard(WidgetLoftLayoutEngine.resize(card, card.columnSpan, height), false)
                        persistBoard()
                        true
                    }
                    else -> true
                }
            }
        }, LinearLayout.LayoutParams(-2, dp(30)))
        container.addView(footer)
        return container
    }

    private fun replaceCard(updated: WidgetLoftCard, rerender: Boolean = true) {
        val index = cards.indexOfFirst { it.id == updated.id }
        if (index >= 0) cards[index] = updated
        persistBoard()
        if (rerender) renderCards()
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return activity.packageManager.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(activity.packageManager).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    private fun loadBoard(): MutableList<WidgetLoftCard> {
        val saved = preferences.getString("cards", null)
        if (!saved.isNullOrBlank()) {
            val restored = runCatching {
                val array = JSONArray(saved)
                MutableList(array.length()) { index -> decodeCard(array.getJSONObject(index)) }
            }.getOrNull()
            if (restored != null) return restored
        }
        return mutableListOf(
            WidgetLoftCard("group-daily", "Daily", installedApps.take(4)),
            WidgetLoftCard("group-tools", "Tools", installedApps.drop(4).take(4)),
        )
    }

    private fun persistBoard() {
        val json = JSONArray()
        cards.forEach { json.put(encodeCard(it)) }
        preferences.edit().putString("cards", json.toString()).apply()
    }

    private fun encodeCard(card: WidgetLoftCard): JSONObject = JSONObject().apply {
        put("id", card.id)
        put("title", card.title)
        put("columnSpan", card.columnSpan)
        put("heightDp", card.heightDp)
        put("apps", JSONArray().apply {
            card.apps.forEach { put(JSONObject().put("packageName", it.packageName).put("label", it.label)) }
        })
        put("stackedCards", JSONArray().apply { card.stackedCards.forEach { put(encodeCard(it)) } })
    }

    private fun decodeCard(json: JSONObject): WidgetLoftCard {
        val apps = json.optJSONArray("apps") ?: JSONArray()
        val stack = json.optJSONArray("stackedCards") ?: JSONArray()
        return WidgetLoftCard(
            json.optString("id", "card-" + System.currentTimeMillis()),
            json.optString("title", "Collection"),
            List(apps.length()) { index ->
                val item = apps.getJSONObject(index)
                AppEntry(item.optString("packageName"), item.optString("label"))
            },
            List(stack.length()) { index -> decodeCard(stack.getJSONObject(index)) },
            json.optInt("columnSpan", 2).coerceIn(1, 2),
            json.optInt("heightDp", 208).coerceIn(144, 360),
        )
    }

    private fun toast(message: String) = android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun systemColor(light: String, dark: String, fallback: Int): Int {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val id = resources.getIdentifier(if (night) dark else light, "color", "android")
        return if (id != 0) resources.getColor(id, activity.theme) else resources.getColor(fallback, activity.theme)
    }

    private fun themeColor(attribute: Int): Int {
        val value = android.util.TypedValue()
        return if (activity.theme.resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) resources.getColor(value.resourceId, activity.theme) else value.data
        } else 0xff222222.toInt()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
