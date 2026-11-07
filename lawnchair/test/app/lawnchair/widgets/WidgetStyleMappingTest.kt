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

package app.lawnchair.widgets

import com.android.launcher3.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The card and inset backgrounds are the only per-style visuals of the widget family, so the
 * style to drawable mapping is asserted directly. Any unknown style must fall back to the filled
 * card rather than leaving the widget without a background.
 */
class WidgetStyleMappingTest {

    @Test
    fun cardBackgroundForStyle_mapsEachStyleToItsOwnDrawable() {
        assertThat(cardBackgroundForStyle(NativeWidgetPreferences.STYLE_FILLED))
            .isEqualTo(R.drawable.widget_card_background)
        assertThat(cardBackgroundForStyle(NativeWidgetPreferences.STYLE_TONAL))
            .isEqualTo(R.drawable.widget_card_background_tonal)
        assertThat(cardBackgroundForStyle(NativeWidgetPreferences.STYLE_TRANSPARENT))
            .isEqualTo(R.drawable.widget_card_background_transparent)
    }

    @Test
    fun cardBackgroundForStyle_unknownStyleFallsBackToFilled() {
        assertThat(cardBackgroundForStyle(-1)).isEqualTo(R.drawable.widget_card_background)
        assertThat(cardBackgroundForStyle(99)).isEqualTo(R.drawable.widget_card_background)
    }

    @Test
    fun insetBackgroundForStyle_mapsEachStyleToItsOwnDrawable() {
        assertThat(insetBackgroundForStyle(NativeWidgetPreferences.STYLE_FILLED))
            .isEqualTo(R.drawable.widget_inset_background)
        assertThat(insetBackgroundForStyle(NativeWidgetPreferences.STYLE_TONAL))
            .isEqualTo(R.drawable.widget_inset_background_tonal)
        assertThat(insetBackgroundForStyle(NativeWidgetPreferences.STYLE_TRANSPARENT))
            .isEqualTo(R.drawable.widget_inset_background_transparent)
    }

    @Test
    fun insetBackgroundForStyle_unknownStyleFallsBackToFilled() {
        assertThat(insetBackgroundForStyle(-1)).isEqualTo(R.drawable.widget_inset_background)
    }

    @Test
    fun cardAndInset_stylesAreDistinct() {
        val cardStyles = listOf(
            NativeWidgetPreferences.STYLE_FILLED,
            NativeWidgetPreferences.STYLE_TONAL,
            NativeWidgetPreferences.STYLE_TRANSPARENT,
        ).map(::cardBackgroundForStyle)
        assertThat(cardStyles.toSet()).hasSize(3)

        val insetStyles = listOf(
            NativeWidgetPreferences.STYLE_FILLED,
            NativeWidgetPreferences.STYLE_TONAL,
            NativeWidgetPreferences.STYLE_TRANSPARENT,
        ).map(::insetBackgroundForStyle)
        assertThat(insetStyles.toSet()).hasSize(3)
    }
}
