package dev.widgetloft

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.ClipData
import android.graphics.drawable.GradientDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The WidgetLoft board: a scrollable grid of cards plus the action bar.
 *
 * Every mutation goes through [WidgetLoftLayoutEngine] and is persisted with [store] immediately, so
 * the board survives process death and reboot. Interaction summary:
 *  - tap a card heading to select it (needed by Stack/Combine/Group/Split);
 *  - long-press a heading to drag the card, dropping it at the index under the finger;
 *  - the corner handle resizes: vertical drag changes height, horizontal drag changes column span;
 *  - a card's "Apps" button opens the picker; "Widget" hosts a real system widget in the card.
 */
class WidgetLoftBoardView(
    private val activity: Activity,
    private val theme: WidgetLoftTheme,
    private val store: WidgetLoftStore,
    private val widgets: WidgetLoftAppWidgets,
    private val onRequestWidget: (String) -> Unit,
) : ScrollView(activity) {

    private val board = GridLayout(activity).apply {
        columnCount = COLUMNS
        useDefaultMargins = false
        setPadding(dp(8), dp(8), dp(8), dp(24))
    }
    private val selection = linkedSetOf<String>()
    private var nextId = 1
    private var allocatedWidgetId: Int? = null

    private val cards = mutableListOf<WidgetLoftCard>()

    init {
        isFillViewport = true
        setBackgroundColor(theme.surface())
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        content.addView(header(), LinearLayout.LayoutParams(-1, -2))
        content.addView(board, LinearLayout.LayoutParams(-1, -2))
        addView(content)

        board.setOnDragListener { _, event -> handleBoardDrag(event) }

        val restored = store.load()
        if (restored.isEmpty()) {
            seedDefaultBoard()
        } else {
            cards.addAll(restored)
            nextId = (cards.mapNotNull { idNumber(it.id) }.maxOrNull() ?: 0) + 1
        }
        renderCards()
    }

    private fun seedDefaultBoard() {
        val installed = WidgetLoftAppPicker.loadLaunchableApps(activity)
        cards.add(WidgetLoftCard("group-1", "Daily", installed.take(4)))
        cards.add(WidgetLoftCard("group-2", "Tools", installed.drop(4).take(4)))
        persist()
    }

    private fun header(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(activity).apply {
            text = "WidgetLoft"
            textSize = 28f
            setTextColor(theme.onSurface())
        })
        addView(TextView(activity).apply {
            text = "Independent card workspace"
            textSize = 13f
            setTextColor(theme.onSurfaceVariant())
        })
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(action("Add card") { addCard() })
            addView(action("Stack") { merge(MergeMode.STACK) })
            addView(action("Combine") { merge(MergeMode.COMBINE) })
            addView(action("Group") { groupSelected() })
        })
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(action("Split") { splitSelected() })
            addView(action("Widget") { addWidgetToSelected() })
            addView(action("Clear") { clearSelection() })
        })
    }

    private fun action(label: String, onClick: () -> Unit) = Button(activity).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        }
    }

    // ---------------------------------------------------------------- actions

    private fun addCard() {
        val installed = WidgetLoftAppPicker.loadLaunchableApps(activity)
        val start = ((nextId - 1) * 4) % (installed.size.coerceAtLeast(1))
        val apps = installed.drop(start).take(4)
        cards.add(WidgetLoftCard("card-${nextId}", "Collection $nextId", apps))
        nextId++
        commit()
    }

    private fun merge(mode: MergeMode) {
        if (selection.size != 2) return
        val merged = WidgetLoftLayoutEngine.merge(cards, selection, mode, "merged-${nextId++}")
        replaceAll(merged)
    }

    private fun groupSelected() {
        if (selection.size < 2) return
        val grouped = WidgetLoftLayoutEngine.group(cards, selection, "group-${nextId++}", "Group ${nextId - 1}")
        replaceAll(grouped)
    }

    private fun splitSelected() {
        val card = cards.firstOrNull { it.id in selection } ?: return
        val pieces = WidgetLoftLayoutEngine.split(card)
        if (pieces.size <= 1) return
        val index = cards.indexOf(card)
        cards.removeAt(index)
        cards.addAll(index, pieces)
        commit()
    }

    private fun clearSelection() {
        selection.clear()
        renderCards()
    }

    private fun addWidgetToSelected() {
        val card = cards.firstOrNull { it.id in selection } ?: cards.firstOrNull() ?: return
        onRequestWidget(card.id)
    }

    private fun replaceAll(newCards: List<WidgetLoftCard>) {
        cards.clear()
        cards.addAll(newCards)
        selection.clear()
        commit()
    }

    // ---------------------------------------------------------------- drag & drop

    private fun handleBoardDrag(event: DragEvent): Boolean {
        if (event.action == DragEvent.ACTION_DROP) {
            val draggedId = event.localState as? String ?: return false
            val targetIndex = dropIndexFor(event.x, event.y, draggedId)
            val moved = WidgetLoftLayoutEngine.moveTo(cards, draggedId, targetIndex)
            if (moved !== cards) {
                cards.clear()
                cards.addAll(moved)
                commit()
            }
        }
        return true
    }

    /**
     * The index the dragged card should land at: the board child under the drop point. When the
     * drop point is past every child the card lands at the end. [WidgetLoftLayoutEngine.moveTo]
     * removes the card before inserting, so this index is interpreted against the list without it.
     */
    private fun dropIndexFor(x: Float, y: Float, draggedId: String): Int {
        for (i in 0 until board.childCount) {
            val child = board.getChildAt(i)
            if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
                return i
            }
        }
        return cards.size - 1
    }

    // ---------------------------------------------------------------- rendering

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
            background = theme.rounded(
                color = theme.cardSurface(),
                radiusDp = 26f,
                strokeColor = if (card.id in selection) theme.accent() else theme.accentOutline(),
                strokeDp = if (card.id in selection) 2.5f else 1f,
            )
        }
        container.addView(heading(card), LinearLayout.LayoutParams(-1, dp(34)))

        if (card.isContainer) {
            card.stackedCards.forEach { child ->
                container.addView(nestedRow(child), LinearLayout.LayoutParams(-1, dp(26)))
            }
        } else {
            container.addView(appGrid(card), LinearLayout.LayoutParams(-1, 0, 1f))
        }

        card.appWidgetIds.forEach { id ->
            widgets.createView(id)?.let { hostView ->
                container.addView(
                    hostView,
                    LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(4) },
                )
            }
        }

        container.addView(footer(card, container))
        return container
    }

    private fun heading(card: WidgetLoftCard): View = TextView(activity).apply {
        val suffix = when {
            card.isContainer -> " · ${card.stackedCards.size} cards"
            else -> " · ${card.apps.size} apps"
        }
        text = card.title + suffix
        textSize = 15f
        setTextColor(theme.onSurface())
        setOnClickListener {
            if (!selection.add(card.id)) selection.remove(card.id)
            renderCards()
        }
        setOnLongClickListener {
            val clip = ClipData.newPlainText("widgetloft-card", card.id)
            @Suppress("DEPRECATION")
            startDragAndDrop(clip, View.DragShadowBuilder(this), card.id, 0)
        }
    }

    private fun nestedRow(child: WidgetLoftCard): View = TextView(activity).apply {
        text = "▦  ${child.title} · ${child.apps.size} apps"
        textSize = 12f
        setTextColor(theme.onSurfaceVariant())
    }

    private fun appGrid(card: WidgetLoftCard): View {
        val grid = GridLayout(activity).apply { columnCount = 2 }
        card.apps.take(MAX_CARD_APPS).forEach { app ->
            grid.addView(appEntry(card, app), GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1, 1f),
            ).apply { width = 0; height = dp(70) })
        }
        return grid
    }

    private fun appEntry(card: WidgetLoftCard, app: AppEntry): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(3), dp(3), dp(3), dp(3))
        setOnClickListener {
            activity.packageManager.getLaunchIntentForPackage(app.packageName)
                ?.let { activity.startActivity(it) }
        }
        setOnLongClickListener {
            val updated = WidgetLoftLayoutEngine.removeApp(card, app.packageName)
            cards[cards.indexOfFirst { it.id == card.id }] = updated
            commit()
            true
        }
        addView(ImageView(activity).apply {
            setImageDrawable(runCatching {
                activity.packageManager.getApplicationIcon(app.packageName)
            }.getOrNull())
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        })
        addView(TextView(activity).apply {
            text = app.label.take(14)
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(theme.onSurface())
        })
    }

    private fun footer(card: WidgetLoftCard, container: View): View {
        val footer = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        footer.addView(TextView(activity).apply {
            text = "Apps"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(theme.accent())
            setOnClickListener { editApps(card) }
        }, LinearLayout.LayoutParams(0, dp(32), 1f))
        footer.addView(TextView(activity).apply {
            text = if (card.columnSpan == 2) "⇔ 2 cols" else "⇔ 1 col"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(theme.accent())
            setOnClickListener {
                val span = if (card.columnSpan == 2) 1 else 2
                updateCard(WidgetLoftLayoutEngine.resize(card, span, card.heightDp))
            }
        }, LinearLayout.LayoutParams(0, dp(32), 1f))
        footer.addView(resizeHandle(card, container), LinearLayout.LayoutParams(0, dp(32), 1f))
        return footer
    }

    /** Corner handle: vertical drag sets height, horizontal drag sets the column span. */
    private fun resizeHandle(card: WidgetLoftCard, container: View): View {
        var startY = 0f
        var startX = 0f
        var startHeight = card.heightDp
        var startSpan = card.columnSpan
        var liveHeight = card.heightDp
        var liveSpan = card.columnSpan
        return TextView(activity).apply {
            text = "↘ Resize"
            textSize = 11f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(theme.accent())
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        startX = event.rawX
                        startHeight = card.heightDp
                        startSpan = card.columnSpan
                        liveHeight = card.heightDp
                        liveSpan = card.columnSpan
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val density = resources.displayMetrics.density
                        liveHeight = (startHeight + ((event.rawY - startY) / density).toInt())
                            .coerceIn(WidgetLoftCard.MIN_HEIGHT_DP, WidgetLoftCard.MAX_HEIGHT_DP)
                        val spanDelta = ((event.rawX - startX) / (view.width.coerceAtLeast(1) * 1.5f)).toInt()
                        liveSpan = (startSpan + spanDelta)
                            .coerceIn(WidgetLoftCard.MIN_SPAN, WidgetLoftCard.MAX_SPAN)
                        applyLiveResize(container, liveSpan, liveHeight)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        updateCard(WidgetLoftLayoutEngine.resize(card, liveSpan, liveHeight))
                        true
                    }
                    else -> true
                }
            }
        }
    }

    /** Applies a resize to the live view so the card follows the finger before it is committed. */
    private fun applyLiveResize(container: View, span: Int, heightDp: Int) {
        val lp = container.layoutParams as? GridLayout.LayoutParams ?: return
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, span, 1f)
        lp.height = dp(heightDp)
        container.layoutParams = lp
    }

    private fun editApps(card: WidgetLoftCard) {
        val current = card.apps.mapTo(linkedSetOf()) { it.packageName }
        WidgetLoftAppPicker.show(activity, "Apps in ${card.title}", current) { picked ->
            val index = cards.indexOfFirst { it.id == card.id }
            if (index >= 0) {
                cards[index] = card.copy(apps = picked)
                commit()
            }
        }
    }

    private fun updateCard(updated: WidgetLoftCard) {
        val index = cards.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            cards[index] = updated
            commit()
        }
    }

    private fun commit() {
        persist()
        renderCards()
    }

    private fun persist() = store.save(cards)

    // ---------------------------------------------------------------- widget flow

    fun rememberAllocatedWidget(appWidgetId: Int) {
        allocatedWidgetId = appWidgetId
    }

    fun allocatedWidgetId(): Int? = allocatedWidgetId

    fun clearAllocatedWidget() {
        allocatedWidgetId = null
    }

    /** Adds a bound system widget to [cardId] and persists the board. */
    fun attachHostedWidget(cardId: String, appWidgetId: Int) {
        val index = cards.indexOfFirst { it.id == cardId }
        if (index < 0) return
        cards[index] = WidgetLoftLayoutEngine.addAppWidget(cards[index], appWidgetId)
        commit()
    }

    private fun idNumber(id: String): Int? = id.substringAfterLast('-').toIntOrNull()

    private fun dp(value: Int): Int = theme.dp(value.toFloat())

    private companion object {
        const val COLUMNS = 2
        const val MAX_CARD_APPS = 4
    }
}
