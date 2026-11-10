package dev.widgetloft

import android.app.Activity
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

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(WidgetLoftBoardView(this))
    }
}

private class WidgetLoftBoardView(private val activity: Activity) : ScrollView(activity) {
    private val board = GridLayout(activity).apply {
        columnCount = 2
        useDefaultMargins = true
        setPadding(dp(8), dp(8), dp(8), dp(24))
    }
    private val selectedIds = linkedSetOf<String>()
    private var nextId = 1
    private val installedApps = loadLaunchableApps()
    private val cards = mutableListOf(
        WidgetLoftCard("group-1", "Daily", installedApps.take(4)),
        WidgetLoftCard("group-2", "Tools", installedApps.drop(4).take(4)),
    )

    init {
        isFillViewport = true
        setBackgroundColor(systemColor("system_neutral1_50", "system_neutral1_900", android.R.color.background_dark))
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        content.addView(header(), LinearLayout.LayoutParams(-1, -2))
        content.addView(board, LinearLayout.LayoutParams(-1, -2))
        addView(content)
        board.setOnDragListener { _, event ->
            if (event.action == android.view.DragEvent.ACTION_DROP) {
                val draggedId = event.localState as? String
                val dragged = cards.firstOrNull { it.id == draggedId }
                if (dragged != null) {
                    cards.remove(dragged)
                    cards.add(dragged)
                    renderCards()
                }
            }
            true
        }
        renderCards()
    }

    private fun header(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(activity).apply {
            text = "WidgetLoft"
            textSize = 28f
            setTextColor(themeColor(android.R.attr.textColorPrimary))
        })
        addView(TextView(activity).apply {
            text = "Independent card workspace · prototype"
            textSize = 13f
            setTextColor(themeColor(android.R.attr.textColorSecondary))
        })
        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(action("Add") { addCard() })
        actions.addView(action("Stack") { merge(MergeMode.STACK) })
        actions.addView(action("Combine") { merge(MergeMode.COMBINE) })
        actions.addView(action("Split") { splitSelected() })
        addView(actions)
    }

    private fun action(label: String, onClick: () -> Unit) = Button(activity).apply {
        text = label
        textSize = 11f
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
    }

    private fun addCard() {
        val start = ((nextId - 1) * 4).coerceAtMost(installedApps.size)
        val apps = installedApps.drop(start).take(4)
        cards.add(WidgetLoftCard("group-${nextId}", "Collection ${nextId}", apps))
        nextId++
        renderCards()
    }

    private fun merge(mode: MergeMode) {
        if (selectedIds.size != 2) return
        val merged = WidgetLoftLayoutEngine.merge(cards, selectedIds, mode, "merged-${nextId++}")
        cards.clear()
        cards.addAll(merged)
        selectedIds.clear()
        renderCards()
    }

    private fun splitSelected() {
        val card = cards.firstOrNull { it.id in selectedIds } ?: return
        val pieces = WidgetLoftLayoutEngine.split(card)
        if (pieces.size <= 1) return
        val index = cards.indexOf(card)
        cards.removeAt(index)
        cards.addAll(index, pieces)
        selectedIds.clear()
        renderCards()
    }

    private fun renderCards() {
        board.removeAllViews()
        cards.forEach { card ->
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1),
                GridLayout.spec(GridLayout.UNDEFINED, card.columnSpan, 1f),
            ).apply {
                width = 0
                height = dp(card.heightDp)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
            board.addView(cardView(card), params)
        }
    }

    private fun cardView(card: WidgetLoftCard): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            background = rounded(systemColor("system_neutral1_100", "system_neutral1_800", android.R.color.darker_gray), 26)
            if (card.id in selectedIds) alpha = 0.72f
        }
        val heading = TextView(activity).apply {
            text = if (card.stackedCards.isEmpty()) card.title else "${card.title} · ${card.stackedCards.size} cards"
            textSize = 15f
            setTextColor(themeColor(android.R.attr.textColorPrimary))
            setOnClickListener {
                if (!selectedIds.add(card.id)) selectedIds.remove(card.id)
                renderCards()
            }
            setOnLongClickListener {
                val clip = ClipData.newPlainText("widgetloft-card", card.id)
                startDragAndDrop(clip, View.DragShadowBuilder(this), card.id, 0)
            }
        }
        container.addView(heading, LinearLayout.LayoutParams(-1, dp(32)))

        if (card.stackedCards.isNotEmpty()) {
            card.stackedCards.forEach { child ->
                container.addView(TextView(activity).apply {
                    text = "▦  ${child.title} · ${child.apps.size} apps"
                    textSize = 12f
                    setTextColor(themeColor(android.R.attr.textColorSecondary))
                })
            }
        } else {
            val appGrid = GridLayout(activity).apply { columnCount = 2 }
            card.apps.take(4).forEach { app ->
                val entry = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                    setOnClickListener {
                        activity.packageManager.getLaunchIntentForPackage(app.packageName)?.let(activity::startActivity)
                    }
                    val icon = ImageView(activity).apply {
                        setImageDrawable(runCatching { activity.packageManager.getApplicationIcon(app.packageName) }.getOrNull())
                        layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
                    }
                    addView(icon)
                    addView(TextView(activity).apply {
                        text = app.label.take(14)
                        textSize = 11f
                        gravity = Gravity.CENTER
                        setTextColor(themeColor(android.R.attr.textColorPrimary))
                    })
                }
                appGrid.addView(entry, GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1, 1f),
                ).apply { width = 0; height = dp(70) })
            }
            container.addView(appGrid, LinearLayout.LayoutParams(-1, 0, 1f))
        }

        val footer = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        footer.addView(TextView(activity).apply {
            text = if (card.columnSpan == 2) "Width: 2 columns" else "Width: 1 column"
            textSize = 10f
            setTextColor(themeColor(android.R.attr.colorAccent))
            setOnClickListener {
                replaceCard(card.copy(columnSpan = if (card.columnSpan == 2) 1 else 2))
            }
        }, LinearLayout.LayoutParams(0, dp(30), 1f))

        var startY = 0f
        var startHeight = card.heightDp
        val resizeHandle = TextView(activity).apply {
            text = "↘ Resize"
            textSize = 11f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(themeColor(android.R.attr.colorAccent))
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        startHeight = card.heightDp
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val nextHeight = (startHeight + ((event.rawY - startY) / resources.displayMetrics.density).toInt()).coerceIn(144, 360)
                        container.layoutParams = container.layoutParams.apply { height = dp(nextHeight) }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val nextHeight = (startHeight + ((event.rawY - startY) / resources.displayMetrics.density).toInt()).coerceIn(144, 360)
                        replaceCard(card.copy(heightDp = nextHeight), rerender = false)
                        true
                    }
                    else -> true
                }
            }
        }
        footer.addView(resizeHandle, LinearLayout.LayoutParams(-2, dp(30)))
        container.addView(footer)
        return container
    }

    private fun replaceCard(updated: WidgetLoftCard, rerender: Boolean = true) {
        val index = cards.indexOfFirst { it.id == updated.id }
        if (index >= 0) cards[index] = updated
        if (rerender) renderCards()
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return activity.packageManager.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .take(12)
            .map { info ->
                AppEntry(info.activityInfo.packageName, info.loadLabel(activity.packageManager).toString())
            }
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun systemColor(lightName: String, darkName: String, fallback: Int): Int {
        val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val name = if (isNight) darkName else lightName
        val id = resources.getIdentifier(name, "color", "android")
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
