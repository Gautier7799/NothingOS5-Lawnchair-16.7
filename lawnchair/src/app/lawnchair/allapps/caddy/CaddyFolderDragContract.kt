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

/**
 * Android-free constants for the Caddy card drag gesture.
 *
 * They live in their own object so the marker type and span can be unit-tested on the
 * JVM without loading [com.android.launcher3.model.data.ItemInfo], whose static
 * initialiser touches the Android framework.
 */
object CaddyFolderDragContract {

    /**
     * Marker item type for an in-flight card drag. Negative and distinct from every
     * persisted `Favorites` type, so it can never be mistaken for a row loaded from
     * the database.
     */
    const val DRAG_ITEM_TYPE = -102

    /** A native Lawnchair folder occupies a single cell; match it so the drop fits. */
    const val SPAN_X = 1
    const val SPAN_Y = 1
}
