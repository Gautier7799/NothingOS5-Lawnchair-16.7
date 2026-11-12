package dev.widgetloft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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
        assertTrue(merged.single().isContainer)
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
    fun combineDeduplicatesAppsByPackage() {
        val dup = WidgetLoftCard("c", "Dup", listOf(AppEntry("phone", "Phone Again")))
        val merged = WidgetLoftLayoutEngine.merge(
            listOf(first, dup),
            setOf("a", "c"),
            MergeMode.COMBINE,
            "combined",
        )
        assertEquals(1, merged.single().apps.size)
    }

    @Test
    fun mergeRequiresExactlyTwoSelectedCards() {
        val third = WidgetLoftCard("c", "Extra", emptyList())
        val original = listOf(first, second, third)
        assertEquals(original, WidgetLoftLayoutEngine.merge(original, setOf("a"), MergeMode.STACK, "x"))
        assertEquals(original, WidgetLoftLayoutEngine.merge(original, setOf("a", "b", "c"), MergeMode.STACK, "x"))
    }

    @Test
    fun groupNestsTwoOrMoreCards() {
        val third = WidgetLoftCard("c", "Extra", listOf(AppEntry("mail", "Mail")))
        val grouped = WidgetLoftLayoutEngine.group(
            listOf(first, second, third),
            setOf("a", "b", "c"),
            "g1",
            "Work",
        )
        assertEquals(1, grouped.size)
        assertEquals("Work", grouped.single().title)
        assertEquals(3, grouped.single().stackedCards.size)
        assertEquals("g1", grouped.single().group)
    }

    @Test
    fun groupNeedsAtLeastTwoCards() {
        val original = listOf(first, second)
        assertEquals(original, WidgetLoftLayoutEngine.group(original, setOf("a"), "g1", "Work"))
    }

    @Test
    fun splitOfPlainCardReturnsItself() {
        assertEquals(listOf(first), WidgetLoftLayoutEngine.split(first))
    }

    @Test
    fun resizeClampsColumnsAndHeight() {
        val resized = WidgetLoftLayoutEngine.resize(first, 9, 900)
        assertEquals(WidgetLoftCard.MAX_SPAN, resized.columnSpan)
        assertEquals(WidgetLoftCard.MAX_HEIGHT_DP, resized.heightDp)

        val shrunk = WidgetLoftLayoutEngine.resize(first, -4, 10)
        assertEquals(WidgetLoftCard.MIN_SPAN, shrunk.columnSpan)
        assertEquals(WidgetLoftCard.MIN_HEIGHT_DP, shrunk.heightDp)
    }

    @Test
    fun moveToPlacesCardAtDropIndex() {
        val third = WidgetLoftCard("c", "Extra", emptyList())
        val moved = WidgetLoftLayoutEngine.moveTo(listOf(first, second, third), "a", 2)
        assertEquals(listOf("b", "c", "a"), moved.map { it.id })
    }

    @Test
    fun moveToClampsOutOfRangeIndex() {
        val moved = WidgetLoftLayoutEngine.moveTo(listOf(first, second), "a", 99)
        assertEquals(listOf("b", "a"), moved.map { it.id })
    }

    @Test
    fun moveToIgnoresUnknownCard() {
        val original = listOf(first, second)
        assertSame(original, WidgetLoftLayoutEngine.moveTo(original, "missing", 0))
    }

    @Test
    fun addAppsSkipsDuplicatesAndAppendsNew() {
        val updated = WidgetLoftLayoutEngine.addApps(
            first,
            listOf(AppEntry("phone", "Phone"), AppEntry("maps", "Maps")),
        )
        assertEquals(listOf("phone", "maps"), updated.apps.map { it.packageName })
        assertNotSame(first, updated)
    }

    @Test
    fun addAppsWithNoNewAppsReturnsSameInstance() {
        assertSame(first, WidgetLoftLayoutEngine.addApps(first, listOf(AppEntry("phone", "Phone"))))
    }

    @Test
    fun removeAppDropsOnlyThatPackage() {
        val card = WidgetLoftCard("x", "X", listOf(AppEntry("a", "A"), AppEntry("b", "B")))
        val updated = WidgetLoftLayoutEngine.removeApp(card, "a")
        assertEquals(listOf("b"), updated.apps.map { it.packageName })
    }

    @Test
    fun addAppWidgetIgnoresDuplicateIds() {
        val once = WidgetLoftLayoutEngine.addAppWidget(first, 42)
        val twice = WidgetLoftLayoutEngine.addAppWidget(once, 42)
        assertEquals(listOf(42), twice.appWidgetIds)
    }

    @Test
    fun replaceUpdatesCardInPlace() {
        val updated = first.copy(title = "Renamed")
        val replaced = WidgetLoftLayoutEngine.replace(listOf(first, second), updated)
        assertEquals("Renamed", replaced[0].title)
        assertEquals("b", replaced[1].id)
    }

    @Test
    fun replaceIgnoresUnknownId() {
        val original = listOf(first, second)
        assertSame(original, WidgetLoftLayoutEngine.replace(original, WidgetLoftCard("z", "Z")))
    }

    @Test
    fun codecRoundTripsNestedStacks() {
        val stack = WidgetLoftCard(
            id = "s1",
            title = "Stack",
            stackedCards = listOf(first, second),
            group = "s1",
        )
        val board = listOf(stack, WidgetLoftCard("plain", "Plain", listOf(AppEntry("x", "X"))))
        val decoded = WidgetLoftCodec.decode(WidgetLoftCodec.encode(board))
        assertEquals(board, decoded)
    }

    @Test
    fun codecEscapesSeparatorsInTitles() {
        val tricky = WidgetLoftCard("t1", "A,B\\C\nD", listOf(AppEntry("p,q", "Label")))
        val decoded = WidgetLoftCodec.decode(WidgetLoftCodec.encode(listOf(tricky)))
        assertEquals(listOf(tricky), decoded)
    }

    @Test
    fun codecRoundTripsWidgetIdsAndSpans() {
        val card = WidgetLoftCard("w1", "W", columnSpan = 1, heightDp = 300, appWidgetIds = listOf(7, 9))
        val decoded = WidgetLoftCodec.decode(WidgetLoftCodec.encode(listOf(card)))
        assertEquals(card, decoded.single())
    }

    @Test
    fun codecRejectsMissingOrBadHeader() {
        assertTrue(WidgetLoftCodec.decode(null).isEmpty())
        assertTrue(WidgetLoftCodec.decode("").isEmpty())
        assertTrue(WidgetLoftCodec.decode("garbage\nC\u00010\u0001a").isEmpty())
    }

    @Test
    fun codecHandlesDeepNesting() {
        val leaf = WidgetLoftCard("leaf", "Leaf", listOf(AppEntry("a", "A")))
        val mid = WidgetLoftCard("mid", "Mid", stackedCards = listOf(leaf))
        val root = WidgetLoftCard("root", "Root", stackedCards = listOf(mid))
        val decoded = WidgetLoftCodec.decode(WidgetLoftCodec.encode(listOf(root)))
        assertEquals(root, decoded.single())
        assertFalse(decoded.single().stackedCards.single().stackedCards.isEmpty())
    }
}
