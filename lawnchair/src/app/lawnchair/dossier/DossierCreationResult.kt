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
 * Outcome of an atomic Dossier card creation.
 *
 * The write (insert the card, then delete the source icons) runs in a single
 * database transaction. This type is what [com.android.launcher3.model.ModelWriter]
 * hands back to `Workspace` so the caller can decide whether to place the card view
 * or leave the drop to the ordinary paths. A failure always means nothing was
 * committed, so the two source apps are still on the workspace.
 */
class DossierCreationResult private constructor(
    val isSuccess: Boolean,
    val cardId: Int,
    val failureReason: String?,
) {

    companion object {
        /** Matches {@code ItemInfo.NO_ID}: no persisted row. */
        const val NO_ID = -1

        /** The card row was inserted and every source row deleted in one transaction. */
        @JvmStatic
        fun success(cardId: Int): DossierCreationResult = DossierCreationResult(isSuccess = true, cardId = cardId, failureReason = null)

        /** Nothing was committed; [reason] explains why for logs and tests. */
        @JvmStatic
        fun failure(reason: String): DossierCreationResult = DossierCreationResult(isSuccess = false, cardId = NO_ID, failureReason = reason)
    }
}
