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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM unit tests for the atomic card write.
 *
 * These tests exercise the ordering and rollback rules of [DossierCardWriter]
 * against an in-memory row set that mimics a database transaction. They do NOT
 * exercise Android's SQLite layer, the view inflation, or the workspace drop
 * paths; those are integration concerns verified separately.
 */
class DossierCardWriterTest {

    /** In-memory favourites table: a set of row ids. */
    private class FakeRows(val present: MutableSet<Int>)

    private class FakeOps(
        private val rows: FakeRows,
        var failInsert: Boolean = false,
        var failDelete: Boolean = false,
    ) : DossierCardWriteOps {
        var insertCalls = 0
        val deleted = mutableListOf<Int>()

        override fun nextItemId(): Int = NEXT_ID

        override fun insertCard(
            cardId: Int,
            container: Int,
            screenId: Int,
            cellX: Int,
            cellY: Int,
        ): Int {
            insertCalls++
            if (failInsert) return -1
            rows.present.add(cardId)
            return cardId
        }

        override fun deleteRow(id: Int): Boolean {
            if (failDelete) return false
            deleted.add(id)
            return rows.present.remove(id)
        }

        companion object {
            const val NEXT_ID = 42
        }
    }

    /**
     * Runs [write] the way ModelWriter runs it: inside one transaction. On failure
     * the row set is restored, so a failed write must leave it untouched.
     */
    private fun runInTransaction(
        rows: FakeRows,
        ops: DossierCardWriteOps,
        write: () -> DossierCreationResult,
    ): DossierCreationResult {
        val snapshot = rows.present.toSet()
        val result = write()
        if (!result.isSuccess) {
            rows.present.clear()
            rows.present.addAll(snapshot)
        }
        return result
    }

    @Test
    fun write_completeCard_insertsCardAndDeletesBothSources() {
        val rows = FakeRows(mutableSetOf(1, 2))
        val ops = FakeOps(rows)

        val result = runInTransaction(rows, ops) {
            DossierCardWriter.write(
                existingCardId = DossierCreationResult.NO_ID,
                container = 0,
                screenId = 0,
                cellX = 1,
                cellY = 1,
                sourceIds = listOf(1, 2),
                ops = ops,
            )
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(result.cardId).isEqualTo(FakeOps.NEXT_ID)
        assertThat(rows.present).containsExactly(FakeOps.NEXT_ID)
        assertThat(ops.deleted).containsExactly(1, 2).inOrder()
    }

    @Test
    fun write_cardInsertFailure_doesNotDeleteAnySource() {
        val rows = FakeRows(mutableSetOf(1, 2))
        val ops = FakeOps(rows, failInsert = true)

        val result = runInTransaction(rows, ops) {
            DossierCardWriter.write(
                existingCardId = DossierCreationResult.NO_ID,
                container = 0,
                screenId = 0,
                cellX = 1,
                cellY = 1,
                sourceIds = listOf(1, 2),
                ops = ops,
            )
        }

        assertThat(result.isSuccess).isFalse()
        assertThat(result.failureReason).isEqualTo("card insert failed")
        // Rollback: both source apps are still on the workspace, no card row.
        assertThat(rows.present).containsExactly(1, 2)
        assertThat(ops.deleted).isEmpty()
    }

    @Test
    fun write_sourceDeleteFailure_reportsFailureAndRollsBackEverything() {
        val rows = FakeRows(mutableSetOf(1, 2))
        val ops = FakeOps(rows, failDelete = true)

        val result = runInTransaction(rows, ops) {
            DossierCardWriter.write(
                existingCardId = DossierCreationResult.NO_ID,
                container = 0,
                screenId = 0,
                cellX = 1,
                cellY = 1,
                sourceIds = listOf(1, 2),
                ops = ops,
            )
        }

        assertThat(result.isSuccess).isFalse()
        assertThat(result.failureReason).isEqualTo("source delete failed")
        // Rollback: the partially inserted card is gone and both apps remain.
        assertThat(rows.present).containsExactly(1, 2)
    }

    @Test
    fun write_retryWithExistingCardId_isRefusedSoNoDuplicateRow() {
        val rows = FakeRows(mutableSetOf(1, 2))
        val ops = FakeOps(rows)

        val result = runInTransaction(rows, ops) {
            DossierCardWriter.write(
                existingCardId = FakeOps.NEXT_ID,
                container = 0,
                screenId = 0,
                cellX = 1,
                cellY = 1,
                sourceIds = listOf(1, 2),
                ops = ops,
            )
        }

        assertThat(result.isSuccess).isFalse()
        assertThat(result.failureReason).isEqualTo("card already has an id")
        assertThat(ops.insertCalls).isEqualTo(0)
        assertThat(rows.present).containsExactly(1, 2)
    }

    @Test
    fun write_externalDrop_keepsDraggedAppBecauseItHasNoPersistedRow() {
        // An app dragged from the drawer has NO_ID, so only the target row is deleted.
        val rows = FakeRows(mutableSetOf(1))
        val ops = FakeOps(rows)

        val result = runInTransaction(rows, ops) {
            DossierCardWriter.write(
                existingCardId = DossierCreationResult.NO_ID,
                container = 0,
                screenId = 0,
                cellX = 1,
                cellY = 1,
                sourceIds = listOf(1, DossierCreationResult.NO_ID),
                ops = ops,
            )
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(rows.present).containsExactly(FakeOps.NEXT_ID)
        assertThat(ops.deleted).containsExactly(1)
    }
}
