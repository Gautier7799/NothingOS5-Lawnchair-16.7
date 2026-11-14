package app.lawnchair.hotseat

import android.content.Context
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.android.launcher3.R

sealed class HotseatMode(
    @StringRes val nameResourceId: Int,
    @LayoutRes val layoutResourceId: Int,
) {
    companion object {
        fun fromString(value: String): HotseatMode = when (value) {
            "disabled" -> DisabledHotseat
            // The Google search bar capsule was removed; any stored preference now uses the
            // Lawnchair (iOS-style) dock instead.
            else -> LawnchairHotseat
        }

        /**
         * @return The list of all hot seat modes.
         */
        fun values() = listOf(
            DisabledHotseat,
            LawnchairHotseat,
        )
    }

    abstract fun isAvailable(context: Context): Boolean

    /** Whether this mode's layout provides a search bar widget inside the dock. */
    open val hasSearchBar: Boolean get() = true
}

object LawnchairHotseat : HotseatMode(
    nameResourceId = R.string.hotseat_mode_lawnchair,
    layoutResourceId = R.layout.search_container_hotseat,
) {
    override fun toString() = "lawnchair"
    override fun isAvailable(context: Context): Boolean = true
}

object DisabledHotseat : HotseatMode(
    nameResourceId = R.string.hotseat_mode_disabled,
    layoutResourceId = R.layout.empty_view,
) {
    override fun toString(): String = "disabled"

    override fun isAvailable(context: Context): Boolean = true

    override val hasSearchBar: Boolean get() = false
}
