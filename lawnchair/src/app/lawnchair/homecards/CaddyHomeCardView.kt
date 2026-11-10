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

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.touch.ItemLongClickListener

/**
 * A Caddy category card on the workspace.
 *
 * The card is rendered entirely by [CaddyCardRenderer], the same object the app
 * drawer uses, so a home card is identical to a drawer card. App icons reuse the
 * drawer's own click and long-click handlers, so tapping an icon launches the app
 * and long-pressing it behaves exactly as it does inside the drawer.
 */
class CaddyHomeCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var onCardClick: (() -> Unit)? = null
    var onCardLongClick: (() -> Boolean)? = null

    fun bind(card: CaddyHomeCardInfo, resolveApp: (String) -> AppInfo?) {
        CaddyCardRenderer.renderCategoryCard(
            host = this,
            title = card.title,
            apps = card.appKeys.mapNotNull(resolveApp),
            cardClick = { onCardClick?.invoke() },
            fitGridToHost = true,
        ) { icon, _ ->
            icon.setOnClickListener(ItemClickHandler.INSTANCE)
            icon.setOnLongClickListener(ItemLongClickListener.INSTANCE_ALL_APPS)
        }
        setOnLongClickListener { onCardLongClick?.invoke() ?: false }
    }
}
