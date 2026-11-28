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

package app.lawnchair.util

/**
 * Decides whether the app-drawer suggestion row must be rebound.
 *
 * The row is bound from several independent paths: the model dispatch
 * (`bindPredictedContainerInfo`), the resume path, and the end of a state transition. They all
 * deliver the same predictions, so binding each time re-inflates every icon. `BubbleTextView`
 * clears its drawable and starts an asynchronous icon load on each bind, which the user sees as
 * the suggestions appearing and disappearing.
 *
 * A rebind is needed only when the predicted apps change, or when the row does not already hold
 * the right number of icon views (first bind, or a device-profile change that dropped the views).
 */
object PredictionRowBinding {

    /**
     * Identity of one predicted app as the row renders it.
     *
     * The component alone is not enough: switching icon pack or theme keeps every component but
     * replaces every bitmap, and a row judged unchanged would keep drawing the old icons until the
     * launcher restarted. `IconCache` returns the same `BitmapInfo` instance for an unchanged icon,
     * so its identity separates a real icon change from a rebuilt `WorkspaceItemInfo` (which has no
     * `equals`). The length prefix keeps component/title characters from colliding with the
     * separators.
     */
    @JvmStatic
    fun key(
        componentOrPackage: String,
        userHash: Int,
        bitmapIdentity: Int,
        title: CharSequence?,
    ): String {
        val label = title?.toString().orEmpty()
        return "${componentOrPackage.length}:$componentOrPackage|$userHash|$bitmapIdentity|" +
            "${label.length}:$label"
    }

    @JvmStatic
    fun shouldRebind(
        appliedKeys: List<String>?,
        nextKeys: List<String>,
        childCount: Int,
        expectedCount: Int,
    ): Boolean {
        if (childCount != expectedCount) return true
        if (appliedKeys == null) return true
        return appliedKeys != nextKeys
    }
}
