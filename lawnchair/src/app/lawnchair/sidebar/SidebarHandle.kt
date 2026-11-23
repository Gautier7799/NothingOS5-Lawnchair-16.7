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

package app.lawnchair.sidebar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import app.lawnchair.allapps.caddy.CaddyMaterialYou
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.Launcher
import com.android.launcher3.views.BaseDragLayer

/**
 * The thin tab on the right edge that opens the [SidebarView].
 *
 * It is a separate view from the panel on purpose: the tab must stay reachable while the
 * panel is closed, and it must not steal touches from the workspace when the user is not
 * near it. Only a touch on the tab opens the panel; everything else passes through to the
 * icons below.
 */
@SuppressLint("ViewConstructor")
class SidebarHandle(context: Context) : View(context) {

    private var onOpen: (() -> Unit)? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var dragging = false
    private var hapticsEnabled = true
    private var onRightEdge = true

    init {
        background = GradientDrawable().apply {
            setColor(CaddyMaterialYou.accent(context))
            cornerRadius = dp(SidebarMetrics.HANDLE_WIDTH_DP / 2).toFloat()
        }
        contentDescription = context.getString(com.android.launcher3.R.string.sidebar_open)
        elevation = dp(6).toFloat()
        isClickable = true
    }

    fun setOnOpen(listener: () -> Unit) {
        onOpen = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging && draggedFarEnough(event.rawX)) {
                    dragging = true
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging || draggedFarEnough(event.rawX)) {
                    onOpen?.invoke()
                } else {
                    performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * Whether the finger moved far enough, in the direction that opens the panel.
     *
     * The tab is pulled inward, so the opening direction is leftwards on the right edge and
     * rightwards on the left edge; using a single sign would make the left-edge tab inert.
     */
    private fun draggedFarEnough(rawX: Float): Boolean = SidebarMetrics.isOpenPull(rawX - downX, onRightEdge, touchSlop.toFloat())

    override fun performClick(): Boolean {
        super.performClick()
        if (hapticsEnabled) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        onOpen?.invoke()
        return true
    }

    /** Re-reads edge, vertical position and haptics from the current preferences. */
    fun applyPreferences(launcher: Launcher) {
        val prefs = app.lawnchair.preferences2.PreferenceManager2.getInstance(launcher)
        hapticsEnabled = prefs.sidebarHaptics.firstCached(prefs)
        onRightEdge = prefs.sidebarOnRightEdge.firstCached(prefs)
        layoutParams = layoutParamsFor(launcher)
        requestLayout()
    }

    /**
     * Places the tab on the chosen edge, at the chosen fraction of the host height.
     *
     * The tab is anchored to the top and offset, rather than centred, because the offset is
     * what makes a vertical position meaningful: `CENTER_VERTICAL` would ignore it.
     */
    fun layoutParamsFor(launcher: Launcher): BaseDragLayer.LayoutParams {
        val prefs = app.lawnchair.preferences2.PreferenceManager2.getInstance(launcher)
        val onRight = prefs.sidebarOnRightEdge.firstCached(prefs)
        val fraction = prefs.sidebarHandlePosition.firstCached(prefs)
        val hostHeight = launcher.dragLayer?.height ?: resources.displayMetrics.heightPixels
        val handleHeight = dp(SidebarMetrics.HANDLE_HEIGHT_DP)
        val centre = SidebarMetrics.handleCenterPx(fraction, hostHeight, handleHeight)
        // The handle is added straight into the DragLayer, so its params must be the DragLayer's
        // own type; a FrameLayout.LayoutParams child makes inset dispatch throw ClassCastException.
        return BaseDragLayer.LayoutParams(
            dp(SidebarMetrics.HANDLE_WIDTH_DP),
            handleHeight,
        ).apply {
            gravity = (if (onRight) Gravity.END else Gravity.START) or Gravity.TOP
            topMargin = (centre - handleHeight / 2).coerceAtLeast(0)
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()

    companion object {
        private const val TAG = "sidebar_handle"

        /** Adds the tab to the DragLayer, or updates the existing one. */
        @JvmStatic
        fun attach(launcher: Launcher, onOpen: () -> Unit) {
            val dragLayer = launcher.dragLayer ?: return
            val existing = dragLayer.findViewWithTag<SidebarHandle>(TAG)
            val handle = existing ?: SidebarHandle(launcher).also { created ->
                created.tag = TAG
                dragLayer.addView(created, created.layoutParamsFor(launcher))
            }
            handle.setOnOpen(onOpen)
            handle.applyPreferences(launcher)
            handle.visibility = VISIBLE
        }

        /** Shows or hides an attached tab without destroying it. */
        @JvmStatic
        fun setVisible(launcher: Launcher, visible: Boolean) {
            launcher.dragLayer?.findViewWithTag<SidebarHandle>(TAG)?.visibility =
                if (visible) VISIBLE else GONE
        }

        /** Removes the tab from the DragLayer. */
        @JvmStatic
        fun detach(launcher: Launcher) {
            launcher.dragLayer?.findViewWithTag<SidebarHandle>(TAG)?.let {
                (it.parent as? ViewGroup)?.removeView(it)
            }
        }
    }
}
