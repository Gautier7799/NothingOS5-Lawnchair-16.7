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

package app.lawnchair.allapps.caddy

import com.android.launcher3.AbstractFloatingView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the hosting contract that makes dragging an app out of the Caddy panel work.
 *
 * The category panel used to be a `Dialog`. A dialog lives in its own window, so
 * `DragPreviewProvider.getScaleAndPosition` could not map the pressed view into the
 * DragLayer, and the move/up events went to the dialog window instead of
 * `DragController`'s drag driver. Both failures made the drop get refused, which is the
 * reported "cannot drag apps out of the category panel" bug.
 *
 * The fix is to host the panel as a floating view inside the DragLayer. These tests pin
 * that contract: the panel must be an [AbstractFloatingView] with its own type bit, so
 * the launcher adds it to the DragLayer and routes touches through the DragController.
 */
class CaddyCategoryPanelContractTest {

    @Test
    fun panelIsAFloatingView() {
        assertTrue(
            "the panel must be a floating view so the launcher hosts it in the DragLayer",
            AbstractFloatingView::class.java.isAssignableFrom(CaddyCategoryPanel::class.java),
        )
    }

    @Test
    fun panelTypeIsRegisteredInTypeAll() {
        assertEquals(
            AbstractFloatingView.TYPE_CADDY_PANEL,
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_CADDY_PANEL,
        )
    }

    @Test
    fun panelTypeDoesNotCollideWithOtherFloatingViews() {
        val others = listOf(
            AbstractFloatingView.TYPE_FOLDER,
            AbstractFloatingView.TYPE_ACTION_POPUP,
            AbstractFloatingView.TYPE_DRAG_DROP_POPUP,
            AbstractFloatingView.TYPE_OPTIONS_POPUP,
            AbstractFloatingView.TYPE_ALL_APPS_EDU,
            AbstractFloatingView.TYPE_DISCOVERY_BOUNCE,
            AbstractFloatingView.TYPE_COMPOSE_VIEW,
        )
        for (type in others) {
            assertNotEquals(
                "TYPE_CADDY_PANEL must not share a bit with another floating view",
                type,
                AbstractFloatingView.TYPE_CADDY_PANEL,
            )
        }
    }

    @Test
    fun panelTypeIsNotRebindSafe() {
        // A rebind replaces the workspace; the panel's grid would go stale, so it closes.
        assertEquals(
            0,
            AbstractFloatingView.TYPE_REBIND_SAFE and AbstractFloatingView.TYPE_CADDY_PANEL,
        )
    }

}
