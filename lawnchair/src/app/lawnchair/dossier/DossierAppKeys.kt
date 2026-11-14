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

package app.lawnchair.dossier

/**
 * Pure helpers for the app-key list stored on a Dossier card.
 *
 * Kept free of Android dependencies so the drop-onto-card behavior can be
 * exercised on the JVM.
 */
object DossierAppKeys {

    /**
     * Appends [key] to [keys] when it is non-blank and not already present.
     *
     * A drop that adds an already-present app is a no-op, so the card never grows
     * duplicate icons and the caller can skip the database write.
     *
     * @return true when [keys] changed.
     */
    fun add(keys: MutableList<String>, key: String?): Boolean {
        val normalized = key?.trim().orEmpty()
        if (normalized.isEmpty() || normalized in keys) return false
        keys.add(normalized)
        return true
    }
}
