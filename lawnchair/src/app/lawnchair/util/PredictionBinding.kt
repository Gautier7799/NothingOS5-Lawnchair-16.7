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
 * Decides whether a prediction list needs to be re-applied to the dock.
 *
 * The launcher re-applies predictions whenever it returns to home: once from the resume path and
 * once from the model refresh. Re-applying a list that is already on screen makes every dock icon
 * re-inflate and redraw, which the user sees as a flicker over the translucent dock.
 *
 * Only an exact repeat of the same item objects is skipped. Any rebuilt list, or a list holding new
 * item instances, is applied, so icon-pack changes and real prediction changes still reach the dock.
 *
 * [applied] must be a snapshot taken when the dock was last bound, not the live list held by the
 * prediction container: that list is pruned in place when apps are uninstalled, and comparing it
 * against itself would hide the change forever.
 */
object PredictionBinding {

    @JvmStatic
    fun shouldRebind(applied: List<*>?, next: List<*>?): Boolean {
        if (next == null) return false
        if (applied == null) return true
        if (applied.size != next.size) return true
        for (i in applied.indices) {
            if (applied[i] !== next[i]) return true
        }
        return false
    }
}
