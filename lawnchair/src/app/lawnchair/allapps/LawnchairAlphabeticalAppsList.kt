package app.lawnchair.allapps

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.lawnchair.data.folder.FolderEntry
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.categorizeAppsWithSystemAndGoogle
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.PrivateProfileManager
import com.android.launcher3.allapps.WorkProfileManager
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.opto.core.onEach
import java.util.function.Predicate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Suppress("SYNTHETIC_PROPERTY_WITHOUT_JAVA_ORIGIN")
class LawnchairAlphabeticalAppsList<T>(
    private val context: T,
    private val appsStore: AllAppsStore<T>,
    workProfileManager: WorkProfileManager?,
    privateProfileManager: PrivateProfileManager?,
) : AlphabeticalAppsList<T>(context, appsStore, workProfileManager, privateProfileManager),
    OnIDPChangeListener,
    DefaultLifecycleObserver
    where T : Context, T : ActivityContext {

    private var hiddenApps: Set<String> = setOf()
    private val prefs2 = PreferenceManager2.getInstance(context)
    private val prefs = PreferenceManager.getInstance(context)

    private val viewModel = FolderViewModel(
        (context as? ComponentActivity)?.application ?: context.launcher.application,
    )
    private val folderList = mutableListOf<FolderEntry>()
    private val filteredList = mutableListOf<AppInfo>()
    private val sessionCategoryUse = linkedMapOf<String, Long>()
    private var sessionUseSequence = 0L

    init {
        context.launcher.deviceProfile.inv.addOnChangeListener(this)
        (context as? LifecycleOwner)?.lifecycle?.addObserver(this)
        try {
            prefs2.hiddenApps.onEach(launchIn = context.launcher.lifecycleScope) {
                hiddenApps = it
                onAppsUpdated()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize hidden apps", t)
        }
        observeFolders()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        context.launcher.deviceProfile.inv.removeOnChangeListener(this)
    }

    private fun observeFolders() {
        val lifecycleOwner = context as? LifecycleOwner ?: return
        lifecycleOwner.lifecycleScope.launch {
            viewModel.folders.collect { folders ->
                if (folders != null) {
                    folderList.clear()
                    folderList.addAll(folders)
                    updateAdapterItems()
                }
            }
        }
    }

    override fun updateItemFilter(itemFilter: Predicate<ItemInfo>?) {
        mItemFilter = Predicate { info ->
            require(info is AppInfo) { "`info` must be an instance of `AppInfo`." }
            val componentKey = info.toComponentKey().toString()
            (itemFilter?.test(info) != false) && !hiddenApps.contains(componentKey)
        }
        onAppsUpdated()
    }

    override fun addAppsWithSections(appList: List<AppInfo?>?, startPosition: Int): Int {
        if (appList.isNullOrEmpty()) return startPosition
        val drawerListDefault = prefs.drawerList.get()
        filteredList.clear()
        var position = startPosition

        // Show app drawer folders only on main profile, to prevent state complexity
        if (isWorkOrPrivateSpace(appList)) return super.addAppsWithSections(appList, position)

        if (!drawerListDefault) {
            val validApps = appList.mapNotNull { it }

            // User-created Caddy categories take priority over automatic grouping.
            // Persisted component keys keep each app in the category selected by the user.
            val customCategories = mutableListOf<Pair<String, List<AppInfo>>>()
            val customAssignedKeys = linkedSetOf<String>()
            // This runs on the main thread whenever the drawer opens, so it must never crash the
            // launcher: a corrupt or hand-edited preference is logged and ignored.
            runCatching {
                val stored = context.getSharedPreferences(
                    app.lawnchair.allapps.caddy.CaddyCategoryStore.PREFS_NAME,
                    Context.MODE_PRIVATE,
                ).getString(app.lawnchair.allapps.caddy.CaddyCategoryStore.KEY_CATEGORIES, "[]")
                for (card in app.lawnchair.allapps.caddy.CaddyCategoryStore.decode(stored)) {
                    val selectedKeys = card.appKeys
                    val selectedApps = validApps.filter { app ->
                        val componentKey = app.toComponentKey().toString()
                        componentKey in selectedKeys ||
                            app.componentName?.flattenToString()?.let { it in selectedKeys } == true
                    }
                    if (selectedApps.isNotEmpty()) {
                        customCategories.add(card.title to selectedApps)
                        // Normalize both current and legacy stored keys so an app cannot
                        // appear again in an automatically generated category.
                        customAssignedKeys.addAll(
                            selectedApps.map { it.toComponentKey().toString() },
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "Unable to load custom Caddy categories", it) }

            val automaticApps = validApps.filterNot {
                it.toComponentKey().toString() in customAssignedKeys
            }
            val finalCategorizedApps = categorizeAppsWithSystemAndGoogle(automaticApps, context)

            // Native Launcher folders automatically collapse when they contain one item.
            // Keep Caddy cards compatible with that behavior by collecting singleton categories
            // into Other, so every rendered category card contains at least two apps.
            val caddyCategories = linkedMapOf<String, MutableList<AppInfo>>()
            finalCategorizedApps.forEach { (category, apps) ->
                if (apps.size > 1) {
                    caddyCategories.getOrPut(category) { mutableListOf() }.addAll(apps)
                } else if (apps.size == 1) {
                    caddyCategories.getOrPut("Other") { mutableListOf() }.addAll(apps)
                }
            }

            customCategories.forEach { (category, apps) ->
                mAdapterItems.add(
                    AdapterItem.asCaddyCategory(category, apps) {
                        sessionCategoryUse[category] = ++sessionUseSequence
                        updateAdapterItems()
                    },
                )
                position++
            }

            // Category order is session-aware: opening an automatic category moves it
            // to the front for the remainder of this launcher session.
            val orderedCategories = caddyCategories.entries.sortedByDescending { (category, _) ->
                sessionCategoryUse[category] ?: Long.MIN_VALUE
            }
            orderedCategories.forEach { (category, apps) ->
                mAdapterItems.add(
                    AdapterItem.asCaddyCategory(category, apps) {
                        sessionCategoryUse[category] = ++sessionUseSequence
                        updateAdapterItems()
                    },
                )
                position++
            }

            // Always provide a direct way to create another user-defined category.
            mAdapterItems.add(AdapterItem.asCaddyAddCategory { updateAdapterItems() })
            position++

            // Keep Search in the final right-hand card slot in the two-column layout.
            val categoryCardCount = customCategories.size + caddyCategories.size + 1
            if (categoryCardCount % 2 == 0) {
                mAdapterItems.add(AdapterItem.asCaddySpacer())
                position++
            }
            mAdapterItems.add(AdapterItem.asCaddySearchActions())
            position++
        } else {
            folderList.forEach { folderEntry ->
                val resolvedApps = folderEntry.itemComponentKeys.mapNotNull { keyString ->
                    val componentKey = ComponentKey.fromString(keyString) ?: return@mapNotNull null
                    appsStore.getApp(componentKey) as? AppInfo
                }

                if (resolvedApps.size > 1) {
                    val folderInfo = FolderInfo().apply {
                        id = folderEntry.id
                        title = folderEntry.title
                        resolvedApps.forEach { add(it) }
                    }
                    mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                    position++

                    if (prefs.folderApps.get()) {
                        filteredList.addAll(resolvedApps)
                    }
                }
            }
            val remainingApps = appList.filterNot { app -> filteredList.contains(app) && prefs.folderApps.get() }
            position = super.addAppsWithSections(remainingApps, position)
        }

        return position
    }

    override fun onIdpChanged(modelPropertiesChanged: Boolean) {
        onAppsUpdated()
    }
}
