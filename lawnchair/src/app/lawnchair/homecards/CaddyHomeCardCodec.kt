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

/**
 * Serializes [CaddyHomeCardInfo] lists to and from a plain string.
 *
 * Kept free of Android dependencies so it can be exercised on the JVM. Each card
 * occupies one line; fields are tab separated. The title is escaped because it is
 * the only field that may contain arbitrary user text, while component keys and
 * grid coordinates are already delimiter free.
 */
object CaddyHomeCardCodec {

    private const val FIELD_SEP = '\t'
    private const val LINE_SEP = '\n'
    private const val KEY_SEP = ','
    private const val EMPTY = ""

    fun encode(cards: List<CaddyHomeCardInfo>): String = cards.joinToString(LINE_SEP.toString()) { encodeCard(it) }

    fun decode(raw: String?): List<CaddyHomeCardInfo> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(LINE_SEP)
            .filter { it.isNotEmpty() }
            .mapNotNull(::decodeCard)
    }

    private fun encodeCard(card: CaddyHomeCardInfo): String = listOf(
        card.id.toString(),
        escape(card.title),
        card.appKeys.joinToString(KEY_SEP.toString()),
        card.screenId.toString(),
        card.cellX.toString(),
        card.cellY.toString(),
        card.spanX.toString(),
        card.spanY.toString(),
    ).joinToString(FIELD_SEP.toString())

    private fun decodeCard(line: String): CaddyHomeCardInfo? {
        val fields = line.split(FIELD_SEP)
        // Accept the original six-field format and migrate it to the default 2x2 span.
        if (fields.size != LEGACY_FIELD_COUNT && fields.size != FIELD_COUNT) return null
        val id = fields[0].toIntOrNull() ?: return null
        val title = unescape(fields[1])
        val appKeys = fields[2].takeIf { it.isNotEmpty() }
            ?.split(KEY_SEP)
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        return CaddyHomeCardInfo(
            id = id,
            title = title,
            appKeys = appKeys,
            screenId = fields[3].toIntOrNull() ?: -1,
            cellX = fields[4].toIntOrNull() ?: 0,
            cellY = fields[5].toIntOrNull() ?: 0,
            spanX = fields.getOrNull(6)?.toIntOrNull()?.coerceIn(MIN_SPAN, MAX_SPAN) ?: DEFAULT_SPAN,
            spanY = fields.getOrNull(7)?.toIntOrNull()?.coerceIn(MIN_SPAN, MAX_SPAN) ?: DEFAULT_SPAN,
        )
    }

    private fun escape(value: String): String {
        val builder = StringBuilder(value.length)
        value.forEach { char ->
            when (char) {
                '\\' -> builder.append("\\\\")
                FIELD_SEP -> builder.append("\\t")
                LINE_SEP -> builder.append("\\n")
                else -> builder.append(char)
            }
        }
        return builder.toString()
    }

    private fun unescape(value: String): String {
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    '\\' -> builder.append('\\')
                    't' -> builder.append(FIELD_SEP)
                    'n' -> builder.append(LINE_SEP)
                    else -> builder.append(value[index + 1])
                }
                index += 2
            } else {
                builder.append(char)
                index++
            }
        }
        return builder.toString()
    }

    private const val LEGACY_FIELD_COUNT = 6
    private const val FIELD_COUNT = 8
    private const val DEFAULT_SPAN = 2
    private const val MIN_SPAN = 1
    private const val MAX_SPAN = 8
}
