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
import app.lawnchair.predictions.LawnchairPredictionManager
import app.lawnchair.predictions.PredictionAppKey
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
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.opto.core.onEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.function.Predicate

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
    private var suggestionRefreshOffset = 0

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
            val finalCategorizedApps = categorizeAppsWithSystemAndGoogle(validApps, context)

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

            // Suggestions reuse Lawnchair's existing all-app launch history. The ranking
            // is resolved against the current AppInfo set (including user identity), so
            // unavailable packages and profile mismatches can never leak into the card.
            val predictionStore = LawnchairPredictionManager.getInstance(context).allAppsStore
            val userCache = UserCache.INSTANCE.get(context)
            val appsByPredictionKey = validApps.mapNotNull { app ->
                val componentName = app.componentName ?: return@mapNotNull null
                PredictionAppKey.create(
                    componentName,
                    userCache.getSerialNumberForUser(app.user).toString(),
                ) to app
            }.toMap()
            val usageRankedApps = predictionStore.getRanked().mapNotNull(appsByPredictionKey::get)
            val suggestionPool = (usageRankedApps + validApps.filterNot { it in usageRankedApps })
                .distinctBy { app ->
                    app.componentName?.let { componentName ->
                        PredictionAppKey.create(
                            componentName,
                            userCache.getSerialNumberForUser(app.user).toString(),
                        )
                    } ?: app.title.toString()
                }
            val maxSuggestionOffset = (suggestionPool.size - 8).coerceAtLeast(0)
            suggestionRefreshOffset = suggestionRefreshOffset.coerceIn(0, maxSuggestionOffset)
            val suggestions = if (suggestionPool.size <= 8) {
                suggestionPool
            } else {
                suggestionPool.drop(suggestionRefreshOffset).take(8)
            }
            mAdapterItems.add(
                AdapterItem.asCaddySuggestions(suggestions) {
                    val range = maxSuggestionOffset + 1
                    val step = minOf(8, range)
                    suggestionRefreshOffset = if (range <= 1) 0
                    else (suggestionRefreshOffset + step) % range
                    updateAdapterItems()
                },
            )
            position++

            caddyCategories.forEach { (category, apps) ->
                // Caddy categories use their own adapter item and renderer; they are
                // intentionally not wrapped in FolderInfo or FolderIcon.
                mAdapterItems.add(AdapterItem.asCaddyCategory(category, apps))
                position++
            }
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
