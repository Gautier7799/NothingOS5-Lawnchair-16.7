package dev.widgetloft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLoftLayoutEngineTest {
    private val first = WidgetLoftCard("a", "Daily", listOf(AppEntry("phone", "Phone")))
    private val second = WidgetLoftCard("b", "Tools", listOf(AppEntry("camera", "Camera")))

    @Test
    fun stackMergeKeepsOriginalCardsForLaterSplit() {
        val merged = WidgetLoftLayoutEngine.merge(
            listOf(first, second),
            setOf("a", "b"),
            MergeMode.STACK,
            "stack",
        )
        assertEquals(1, merged.size)
        assertEquals(listOf(first, second), merged.single().stackedCards)
        assertEquals(listOf(first, second), WidgetLoftLayoutEngine.split(merged.single()))
    }

    @Test
    fun combineMergeKeepsAllAppsInOneCard() {
        val merged = WidgetLoftLayoutEngine.merge(
            listOf(first, second),
            setOf("a", "b"),
            MergeMode.COMBINE,
            "combined",
        )
        assertEquals(2, merged.single().apps.size)
        assertTrue(merged.single().stackedCards.isEmpty())
    }

    @Test
    fun resizeClampsColumnsAndHeight() {
        val resized = WidgetLoftLayoutEngine.resize(first, 9, 900)
        assertEquals(2, resized.columnSpan)
        assertEquals(360, resized.heightDp)
    }

    @Test
    fun mergeRequiresExactlyTwoSelectedCards() {
        val original = listOf(first, second)
        assertEquals(original, WidgetLoftLayoutEngine.merge(original, setOf("a"), MergeMode.STACK, "x"))
    }
}
