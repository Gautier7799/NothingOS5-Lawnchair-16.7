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

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps widgets that mirror a system setting in sync without polling.
 *
 * A single [ContentObserver] per watched [Uri] is registered while at least one instance of the
 * owning widget is on the home screen, and is unregistered as soon as the last one is removed.
 * The registry is keyed by the owning provider class so two widgets watching the same setting do
 * not each register their own observer.
 */
object NativeWidgetRefresh {

    private data class Key(val owner: Class<*>, val uri: Uri)

    private val observers = ConcurrentHashMap<Key, ContentObserver>()

    /** Registers (once) a change observer that invokes [onChange] on the main thread. */
    fun register(context: Context, owner: Class<*>, uri: Uri, onChange: () -> Unit) {
        val key = Key(owner, uri)
        if (observers.containsKey(key)) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
            override fun onChange(selfChange: Boolean, changedUri: Uri?) = onChange()
        }
        val appContext = context.applicationContext
        runCatching { appContext.contentResolver.registerContentObserver(uri, true, observer) }
            .onSuccess { observers[key] = observer }
    }

    /** Unregisters the observer previously registered by [register] for this owner/uri. */
    fun unregister(context: Context, owner: Class<*>, uri: Uri) {
        val observer = observers.remove(Key(owner, uri)) ?: return
        runCatching { context.applicationContext.contentResolver.unregisterContentObserver(observer) }
    }
}
