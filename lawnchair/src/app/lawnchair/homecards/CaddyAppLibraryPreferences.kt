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
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached

/**
 * Decides whether the swipe-right gesture opens the full-screen Caddy App Library.
 *
 * Kept separate from the page so the gesture wiring in [com.android.launcher3.Workspace] stays a
 * one-line check and the rule can be read on its own.
 */
object CaddyAppLibraryPreferences {

    /** Whether swiping right past the first page opens the App Library. */
    @JvmStatic
    fun isEnabled(context: Context): Boolean = PreferenceManager2.getInstance(context).caddyAppLibraryEnabled.firstCached()
}
