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
 * Database operations needed to create a Dossier card, kept behind a port so the
 * ordering and rollback rules can be exercised on the JVM.
 *
 * Every method is expected to run inside one open transaction: when any of them
 * fails the caller aborts the transaction, which rolls back all of them together.
 */
interface DossierCardWriteOps {

    /** A fresh, unused item id, or a non-positive value when none can be allocated. */
    fun nextItemId(): Int

    /**
     * Inserts the card with [cardId] at the given placement. Returns the new row id,
     * or a negative value on failure.
     */
    fun insertCard(cardId: Int, container: Int, screenId: Int, cellX: Int, cellY: Int): Int

    /** Deletes the row with [id]. Returns false when the row could not be removed. */
    fun deleteRow(id: Int): Boolean
}

/**
 * Atomic creation of a Dossier card from two source app icons.
 *
 * The card is inserted first and the source icons are deleted only after that
 * insert succeeded. The whole sequence is wrapped in a single database
 * transaction by the caller, so a failure at any step rolls everything back and
 * the user keeps both apps. A card that already carries an id is refused so a
 * retried drop can never insert a duplicate row.
 */
object DossierCardWriter {

    @JvmStatic
    fun write(
        existingCardId: Int,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
        sourceIds: List<Int>,
        ops: DossierCardWriteOps,
    ): DossierCreationResult {
        if (existingCardId != DossierCreationResult.NO_ID) {
            return DossierCreationResult.failure("card already has an id")
        }
        val newId = ops.nextItemId()
        if (newId <= 0) {
            return DossierCreationResult.failure("could not allocate an item id")
        }

        val rowId = ops.insertCard(newId, container, screenId, cellX, cellY)
        if (rowId < 0) {
            return DossierCreationResult.failure("card insert failed")
        }

        // Only now that the card row exists do we remove the sources. Any failure
        // here must surface so the caller aborts the transaction and keeps both apps.
        // A source with no persisted row (an app dragged straight from the drawer)
        // has nothing to delete.
        for (id in sourceIds) {
            if (id == DossierCreationResult.NO_ID) continue
            if (!ops.deleteRow(id)) {
                return DossierCreationResult.failure("source delete failed")
            }
        }

        return DossierCreationResult.success(newId)
    }
}
