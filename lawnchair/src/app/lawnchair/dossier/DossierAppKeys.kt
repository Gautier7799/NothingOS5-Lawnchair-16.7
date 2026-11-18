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

    /**
     * True when [key] is a canonical `ComponentKey.toString()` value of the form
     * `<flattenedComponent>#<userId>`.
     *
     * The Dossier payload is parsed back with `ComponentKey.fromString`, which only
     * accepts this exact shape. A key written in any other order (for example
     * `<userId>:<flattenedComponent>`) parses to null, so the app never resolves and the
     * dropped icon silently fails to appear. Rejecting non-canonical keys here keeps that
     * failure at the drop site instead of the render site.
     */
    fun isCanonical(key: String?): Boolean {
        val value = key?.trim().orEmpty()
        val separator = value.indexOf('#')
        if (separator <= 0 || separator == value.lastIndex) return false
        if (!value.substring(0, separator).contains('/')) return false
        return value.substring(separator + 1).toIntOrNull() != null
    }

    /**
     * Trims [rawKeys], drops blanks, drops non-canonical keys and removes duplicates,
     * keeping first-seen order.
     *
     * A key that is not canonical can never be resolved by `ComponentKey.fromString`,
     * so keeping it would leave an app slot that renders as empty. Filtering here means
     * a card built from a mixed batch of keys only ever holds resolvable apps.
     */
    fun sanitize(rawKeys: List<String>?): List<String> {
        if (rawKeys == null) return emptyList()
        val keys = LinkedHashSet<String>()
        for (raw in rawKeys) {
            val key = raw?.trim().orEmpty()
            if (isCanonical(key)) keys.add(key)
        }
        return keys.toList()
    }
}
