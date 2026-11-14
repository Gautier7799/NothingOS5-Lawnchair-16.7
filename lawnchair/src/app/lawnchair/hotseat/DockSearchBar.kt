package app.lawnchair.hotseat

/**
 * Decides whether the dock inflates its search bar capsule.
 *
 * The capsule is only shown when the hotseat mode actually provides a search bar and the user has
 * not turned the dock search bar off in the dock settings.
 */
object DockSearchBar {

    @JvmStatic
    fun isEnabled(mode: HotseatMode, showDockSearchBar: Boolean): Boolean =
        showDockSearchBar && mode.hasSearchBar
}
