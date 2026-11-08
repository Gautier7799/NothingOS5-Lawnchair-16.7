package app.lawnchair.predictions

/**
 * Pure ordering and change-detection helpers for app predictions.
 *
 * These helpers hold no Android state so the stability of the drawer suggestion row can be
 * unit tested on the JVM: the fallback ranking must be deterministic, and the predictor must
 * only re-dispatch work when the ordered targets actually change.
 */
object PredictionOrdering {

    /**
     * Returns a deterministic ordering for the fallback store keys.
     *
     * The fallback is displayed whenever no app usage has been recorded yet. A random order
     * would reshuffle the suggestion row on every prediction update, which makes the icons
     * appear to flicker. A stable sort keeps the row identical between updates.
     */
    fun stableFallback(keys: List<String>): List<String> = keys.sorted()

    /**
     * Returns `true` when the ordered target keys differ from the previously dispatched ones.
     *
     * Mirrors the system predictor's "skip when there is no diff" behaviour so that unchanged
     * predictions do not trigger redundant model updates and view rebinds.
     */
    fun keysDiffer(previous: List<String>, current: List<String>): Boolean {
        if (previous.size != current.size) return true
        return previous.indices.any { previous[it] != current[it] }
    }
}
