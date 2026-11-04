/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.allapps;

import static android.view.View.GONE;

import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_BOTTOM_LEFT;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_BOTTOM_RIGHT;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_NOTHING;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_TOP_LEFT;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_TOP_RIGHT;
import static com.android.launcher3.allapps.UserProfileManager.STATE_DISABLED;
import static com.android.launcher3.allapps.UserProfileManager.STATE_ENABLED;

import android.content.Context;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.views.ActivityContext;

import app.lawnchair.preferences.PreferenceManager;
import app.lawnchair.util.LawnchairUtilsKt;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for all the apps.
 *
 * @param <T> Type of context inflating all apps.
 */
public abstract class BaseAllAppsAdapter<T extends Context & ActivityContext> extends
        RecyclerView.Adapter<BaseAllAppsAdapter.ViewHolder> {

    public static final String TAG = "BaseAllAppsAdapter";

    // A normal icon
    public static final int VIEW_TYPE_ICON = 1 << 1;
    // The message shown when there are no filtered results
    public static final int VIEW_TYPE_EMPTY_SEARCH = 1 << 2;
    // A divider that separates the apps list and the search market button
    public static final int VIEW_TYPE_ALL_APPS_DIVIDER = 1 << 3;

    public static final int VIEW_TYPE_WORK_EDU_CARD = 1 << 4;
    public static final int VIEW_TYPE_WORK_DISABLED_CARD = 1 << 5;
    public static final int VIEW_TYPE_PRIVATE_SPACE_HEADER = 1 << 6;
    public static final int VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER = 1 << 7;
    public static final int VIEW_TYPE_BOTTOM_VIEW_TO_SCROLL_TO = 1 << 8;
    public static final int NEXT_ID = 9;

    // LC-Feature: Folder support in All Apps, can be any ID
    public static final int VIEW_TYPE_FOLDER = 1 << 10;
    // Caddy owns a separate card type; it is not a native FolderIcon/FolderInfo.
    public static final int VIEW_TYPE_CADDY_CATEGORY = 1 << 11;

    // Common view type masks
    public static final int VIEW_TYPE_MASK_DIVIDER = VIEW_TYPE_ALL_APPS_DIVIDER;
    public static final int VIEW_TYPE_MASK_ICON = VIEW_TYPE_CADDY_CATEGORY | VIEW_TYPE_FOLDER | VIEW_TYPE_ICON;

    public static final int VIEW_TYPE_MASK_PRIVATE_SPACE_HEADER =
            VIEW_TYPE_PRIVATE_SPACE_HEADER;
    public static final int VIEW_TYPE_MASK_PRIVATE_SPACE_SYS_APPS_DIVIDER =
            VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER;

    protected final SearchAdapterProvider<?> mAdapterProvider;

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View v) {
            super(v);
        }
    }

    /** Sets the number of apps to be displayed in one row of the all apps screen. */
    public abstract void setAppsPerRow(int appsPerRow);

    /**
     * Info about a particular adapter item (can be either section or app)
     */
    public static class AdapterItem {
        /** Common properties */
        // The type of this item
        public final int viewType;

        // The row that this item shows up on
        public int rowIndex;
        // The index of this app in the row
        public int rowAppIndex;
        // The associated ItemInfoWithIcon for the item
        public AppInfo itemInfo = null;
        // Private App Decorator
        public SectionDecorationInfo decorationInfo = null;
        public AdapterItem(int viewType) {
            this.viewType = viewType;
        }

        // LC-Feature: Folder support in All Apps
        public FolderInfo folderInfo = new FolderInfo();
        public String caddyTitle;
        public List<AppInfo> caddyApps;
        public boolean caddyIsSuggestions;
        public Runnable caddyRefreshAction;

        /**
         * Factory method for AppIcon AdapterItem
         */
        public static AdapterItem asApp(AppInfo appInfo) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_ICON);
            item.itemInfo = appInfo;
            return item;
        }

        public static AdapterItem asAppWithDecorationInfo(AppInfo appInfo,
                SectionDecorationInfo decorationInfo) {
            AdapterItem item = asApp(appInfo);
            item.decorationInfo = decorationInfo;
            return item;
        }

        public static AdapterItem asFolder(FolderInfo folderInfo) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_FOLDER);
            item.folderInfo = folderInfo;
            return item;
        }

        public static AdapterItem asCaddyCategory(String title, List<AppInfo> apps) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_CADDY_CATEGORY);
            item.caddyTitle = title;
            item.caddyApps = new ArrayList<>(apps);
            return item;
        }

        public static AdapterItem asCaddySuggestions(List<AppInfo> apps, Runnable refreshAction) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_CADDY_CATEGORY);
            item.caddyTitle = "Suggestions";
            item.caddyApps = new ArrayList<>(apps);
            item.caddyIsSuggestions = true;
            item.caddyRefreshAction = refreshAction;
            return item;
        }

        protected boolean isCountedForAccessibility() {
            return viewType == VIEW_TYPE_ICON || viewType == VIEW_TYPE_FOLDER
                    || viewType == VIEW_TYPE_CADDY_CATEGORY;
        }

        /**
         * Returns true if the items represent the same object
         */
        public boolean isSameAs(AdapterItem other) {
            return (other.viewType == viewType) && (other.getClass() == getClass());
        }

        /**
         * This is called only if {@link #isSameAs} returns true to check if the contents are same
         * as well. Returning true will prevent redrawing of thee item.
         */
        public boolean isContentSame(AdapterItem other) {
            if (viewType == VIEW_TYPE_CADDY_CATEGORY) {
                return itemInfo == null && other.itemInfo == null
                        && java.util.Objects.equals(caddyTitle, other.caddyTitle)
                        && java.util.Objects.equals(caddyApps, other.caddyApps)
                        && caddyIsSuggestions == other.caddyIsSuggestions;
            }
            return itemInfo == null && other.itemInfo == null;
        }

        @Nullable
        public SectionDecorationInfo getDecorationInfo() {
            return decorationInfo;
        }

        /** Sets the alpha of the decorator for this item. */
        protected void setDecorationFillAlpha(int alpha) {
            if (decorationInfo == null || decorationInfo.getDecorationHandler() == null) {
                return;
            }
            decorationInfo.getDecorationHandler().setFillAlpha(alpha);
        }
    }

    protected final T mActivityContext;
    protected final AlphabeticalAppsList<T> mApps;
    // The text to show when there are no search results and no market search handler.
    protected int mAppsPerRow;

    protected final LayoutInflater mLayoutInflater;
    protected final OnClickListener mOnIconClickListener;
    protected final OnLongClickListener mOnIconLongClickListener;
    protected OnFocusChangeListener mIconFocusListener;

    public BaseAllAppsAdapter(T activityContext, LayoutInflater inflater,
            AlphabeticalAppsList<T> apps, SearchAdapterProvider<?> adapterProvider) {
        mActivityContext = activityContext;
        mApps = apps;
        mLayoutInflater = inflater;

        mOnIconClickListener = mActivityContext.getItemOnClickListener();
        mOnIconLongClickListener = mActivityContext.getAllAppsItemLongClickListener();

        mAdapterProvider = adapterProvider;
    }

    /** Checks if the passed viewType represents all apps divider. */
    public static boolean isDividerViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_DIVIDER);
    }

    /** Checks if the passed viewType represents all apps icon. */
    public static boolean isIconViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_ICON);
    }

    /** Checks if the passed viewType represents private space header. */
    public static boolean isPrivateSpaceHeaderView(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_PRIVATE_SPACE_HEADER);
    }

    /** Checks if the passed viewType represents private space system apps divider. */
    public static boolean isPrivateSpaceSysAppsDividerView(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_PRIVATE_SPACE_SYS_APPS_DIVIDER);
    }

    public void setIconFocusListener(OnFocusChangeListener focusListener) {
        mIconFocusListener = focusListener;
    }

    /**
     * Returns the layout manager.
     */
    public abstract RecyclerView.LayoutManager getLayoutManager();

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_ICON:
                int layout = mActivityContext.getDeviceProfile().inv.enableTwoLinesInAllApps
                        ? R.layout.all_apps_icon_twoline : R.layout.all_apps_icon;
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        layout, parent, false);
                icon.setLongPressTimeoutFactor(1f);
                icon.setOnFocusChangeListener(mIconFocusListener);
                icon.setOnClickListener(mOnIconClickListener);
                icon.setOnLongClickListener(mOnIconLongClickListener);
                // Ensure the all apps icon height matches the workspace icons in portrait mode.
                icon.getLayoutParams().height =
                        mActivityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx();
                return new ViewHolder(icon);
            case VIEW_TYPE_EMPTY_SEARCH:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_empty_search,
                        parent, false));
            case VIEW_TYPE_ALL_APPS_DIVIDER, VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.private_space_divider, parent, false));
            case VIEW_TYPE_WORK_EDU_CARD:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.work_apps_edu, parent, false));
            case VIEW_TYPE_WORK_DISABLED_CARD:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.work_apps_paused, parent, false));
            case VIEW_TYPE_PRIVATE_SPACE_HEADER:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.private_space_header, parent, false));
            case VIEW_TYPE_BOTTOM_VIEW_TO_SCROLL_TO:
                return new ViewHolder(new View(mActivityContext));
            case VIEW_TYPE_CADDY_CATEGORY:
                FrameLayout caddyCard = new FrameLayout(mActivityContext);
                int caddyHeight = Math.round(248f * mActivityContext.getResources()
                        .getDisplayMetrics().density);
                caddyCard.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, caddyHeight));
                return new ViewHolder(caddyCard);
            case VIEW_TYPE_FOLDER:
                // LC-Feature: Folder support in All Apps
                FrameLayout fl = new FrameLayout(mActivityContext);
                int folderCardHeight = mActivityContext.getDeviceProfile()
                        .getAllAppsProfile().getCellHeightPx();
                boolean isCaddyDrawer =
                        !PreferenceManager.getInstance(mActivityContext).getDrawerList().get();
                // Give Caddy category cards enough vertical space for visibly larger content.
                if (isCaddyDrawer) {
                    folderCardHeight = Math.max(folderCardHeight, Math.round(168f *
                            mActivityContext.getResources().getDisplayMetrics().density));
                }
                fl.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        folderCardHeight));
                return new ViewHolder(fl);
            default:
                if (mAdapterProvider.isViewSupported(viewType)) {
                    return mAdapterProvider.onCreateViewHolder(mLayoutInflater, parent, viewType);
                }
                throw new RuntimeException("Unexpected view type" + viewType);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.itemView.setVisibility(View.VISIBLE);
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_ICON: {
                AdapterItem adapterItem = mApps.getAdapterItems().get(position);
                BubbleTextView icon = (BubbleTextView) holder.itemView;
                icon.reset();
                // Work/Private contexts already imply the profile; skip redundant user badges.
                // Keep badges on home, search, and personal icons (including clones).
                PrivateProfileManager privateProfileManager = mApps.getPrivateProfileManager();
                boolean skipUserBadge = !mApps.hasSearchResults()
                        && (mApps.isWorkAppsList()
                                || (privateProfileManager != null
                                        && privateProfileManager.isPrivateSpaceItem(adapterItem)));
                icon.setSkipUserBadge(skipUserBadge);
                icon.applyFromApplicationInfo(adapterItem.itemInfo);
                icon.setOnFocusChangeListener(mIconFocusListener);
                if (privateProfileManager != null) {
                    // Set the alpha of the private space icon to 0 upon expanding the header so the
                    // alpha can animate -> 1. This should only be in effect when doing a
                    // transitioning between Locked/Unlocked state.
                    boolean isPrivateSpaceItem =
                            privateProfileManager.isPrivateSpaceItem(adapterItem);
                    if (icon.getAlpha() == 0 || icon.getAlpha() == 1) {
                        icon.setAlpha(isPrivateSpaceItem
                                && privateProfileManager.isStateTransitioning()
                                && (privateProfileManager.isScrolling() ||
                                    privateProfileManager.getReadyToAnimate())
                                && privateProfileManager.getCurrentState() == STATE_ENABLED
                                ? 0 : 1);
                        Log.d(TAG, "onBindViewHolder: "
                                + "isPrivateSpaceItem: " + isPrivateSpaceItem
                        + " isStateTransitioning: " + privateProfileManager.isStateTransitioning()
                        + " isScrolling: " + privateProfileManager.isScrolling()
                        + " readyToAnimate: " + privateProfileManager.getReadyToAnimate()
                        + " currentState: " + privateProfileManager.getCurrentState()
                        + " currentAlpha: " + icon.getAlpha());
                    }
                    // Views can still be bounded before the app list is updated hence showing icons
                    // after collapsing.
                    if (privateProfileManager.getCurrentState() == STATE_DISABLED
                            && isPrivateSpaceItem) {
                        adapterItem.decorationInfo = null;
                        icon.setVisibility(GONE);
                    }
                }
                break;
            }
            case VIEW_TYPE_EMPTY_SEARCH: {
                AppInfo info = mApps.getAdapterItems().get(position).itemInfo;
                if (info != null) {
                    ((TextView) holder.itemView).setText(mActivityContext.getString(
                            R.string.all_apps_no_search_results, info.title));
                }
                break;
            }
            case VIEW_TYPE_PRIVATE_SPACE_HEADER:
                RelativeLayout psHeaderLayout = holder.itemView.findViewById(
                        R.id.ps_header_layout);
                mApps.getPrivateProfileManager().bindPrivateSpaceHeaderViewElements(psHeaderLayout);
                AdapterItem adapterItem = mApps.getAdapterItems().get(position);
                int roundRegions = ROUND_TOP_LEFT | ROUND_TOP_RIGHT;
                if (mApps.getPrivateProfileManager().getCurrentState() == STATE_DISABLED) {
                    roundRegions |= (ROUND_BOTTOM_LEFT | ROUND_BOTTOM_RIGHT);
                }
                adapterItem.decorationInfo =
                        new SectionDecorationInfo(mActivityContext, roundRegions);
                break;
            case VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER:
                adapterItem = mApps.getAdapterItems().get(position);
                adapterItem.decorationInfo =
                        mApps.getPrivateProfileManager().getCurrentState() == STATE_DISABLED ? null
                                : new SectionDecorationInfo(mActivityContext, ROUND_NOTHING);
                break;
            case VIEW_TYPE_BOTTOM_VIEW_TO_SCROLL_TO:
            case VIEW_TYPE_ALL_APPS_DIVIDER:
            case VIEW_TYPE_WORK_DISABLED_CARD:
                // nothing to do
                break;
            case VIEW_TYPE_WORK_EDU_CARD:
                ((WorkEduCard) holder.itemView).setPosition(position);
                break;
            case VIEW_TYPE_CADDY_CATEGORY: {
                AdapterItem caddyItem = mApps.getAdapterItems().get(position);
                ViewGroup.LayoutParams caddyParams = holder.itemView.getLayoutParams();
                if (caddyParams != null) {
                    caddyParams.height = dp(caddyItem.caddyIsSuggestions ? 248 : 184);
                    holder.itemView.setLayoutParams(caddyParams);
                }
                if (caddyItem.caddyIsSuggestions) {
                    bindCaddySuggestionsCard((FrameLayout) holder.itemView, caddyItem);
                } else {
                    bindIndependentCaddyCard((FrameLayout) holder.itemView, caddyItem);
                }
                break;
            }
            case VIEW_TYPE_FOLDER:
                // Recalculate height on every bind so recycled holders follow the active drawer mode.
                boolean isCaddyDrawer =
                        !PreferenceManager.getInstance(mActivityContext).getDrawerList().get();
                ViewGroup.LayoutParams folderItemParams = holder.itemView.getLayoutParams();
                if (folderItemParams != null) {
                    int normalHeight = mActivityContext.getDeviceProfile()
                            .getAllAppsProfile().getCellHeightPx();
                    folderItemParams.height = isCaddyDrawer
                            ? Math.max(normalHeight, dp(168)) : normalHeight;
                    holder.itemView.setLayoutParams(folderItemParams);
                }
                // Caddy uses a dedicated category-card presentation while retaining FolderIcon
                // as the interaction host, so folder opening and accessibility stay native.
                FolderInfo folderInfo = mApps.getAdapterItems().get(position).folderInfo;
                ViewGroup container = (ViewGroup) holder.itemView;
                container.removeAllViews();
                FolderIcon folderIcon = FolderIcon.inflateFolderAndIcon(
                        R.layout.all_apps_folder_icon, mActivityContext, container, folderInfo);
                folderIcon.setOnLongClickListener(mOnIconLongClickListener);
                if (!PreferenceManager.getInstance(mActivityContext).getDrawerList().get()) {
                    folderIcon.setCaddyCard(true);
                    bindCaddyCategoryCard(folderIcon, folderInfo);
                }
                container.addView(folderIcon);
                break;
            default:
                if (mAdapterProvider.isViewSupported(holder.getItemViewType())) {
                    mAdapterProvider.onBindView(holder, position);
                }
        }
    }


    /**
     * Renders a Caddy category card while keeping FolderIcon as the native
     * interaction host. App shortcuts retain their own click targets.
     */
    private void bindCaddyCategoryCard(FolderIcon folderIcon, FolderInfo folderInfo) {
        folderIcon.setIconVisible(false);
        folderIcon.setTextVisible(false);
        folderIcon.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable background = new GradientDrawable();
        int cardColor = resolveCaddyDynamicColor();
        // Reuse Lawnchair's folder color and preview-opacity preferences. The card
        // foreground follows that color so custom/dynamic colors remain legible.
        int cardForeground = Color.luminance(cardColor) > 0.48f
                ? Color.BLACK : Color.WHITE;
        background.setColor(cardColor);
        background.setCornerRadius(dp(24));
        background.setStroke(dp(1), Color.argb(0x38,
                Color.red(cardForeground), Color.green(cardForeground),
                Color.blue(cardForeground)));
        folderIcon.setBackground(background);
        folderIcon.setClipToOutline(true);
        // Use native View elevation for a subtle card shadow without adding a new UI dependency.
        folderIcon.setElevation(dp(2));
        folderIcon.setMinimumHeight(dp(168));

        LinearLayout content = new LinearLayout(mActivityContext);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setClickable(true);
        content.setFocusable(true);
        // Delegate to the real FolderIcon; the standard click handler expects
        // a FolderIcon, not a LinearLayout tagged with FolderInfo.
        content.setOnClickListener(v -> folderIcon.performClick());
        content.setOnLongClickListener(v -> folderIcon.performLongClick());

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        folderIcon.addView(content, contentParams);

        TextView title = new TextView(mActivityContext);
        title.setText(folderInfo.title);
        title.setTextColor(cardForeground);
        title.setTextSize(13.5f);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        java.util.ArrayList<ItemInfoWithIcon> apps = new java.util.ArrayList<>();
        for (ItemInfo item : folderInfo.getContents()) {
            if (item instanceof ItemInfoWithIcon) {
                apps.add((ItemInfoWithIcon) item);
            }
        }

        LinearLayout topRow = new LinearLayout(mActivityContext);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        rowParams.topMargin = dp(8);
        content.addView(topRow, rowParams);
        addCaddyBigSlot(topRow, apps, 0);
        addCaddyBigSlot(topRow, apps, 1);

        LinearLayout bottomRow = new LinearLayout(mActivityContext);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        bottomParams.topMargin = dp(5);
        content.addView(bottomRow, bottomParams);
        addCaddyBigSlot(bottomRow, apps, 2);
        addCaddyMiniCluster(bottomRow, apps, folderIcon);
    }

    private void addCaddyBigSlot(LinearLayout row,
            java.util.ArrayList<ItemInfoWithIcon> apps, int index) {
        FrameLayout slot = new FrameLayout(mActivityContext);
        LinearLayout.LayoutParams slotParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        row.addView(slot, slotParams);
        if (index >= apps.size()) return;

        ItemInfoWithIcon app = apps.get(index);
        ImageView icon = new ImageView(mActivityContext);
        icon.setImageDrawable(app.newIcon(mActivityContext));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setTag(app);
        icon.setContentDescription(app.title);
        icon.setFocusable(true);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                dp(44), dp(44), Gravity.CENTER);
        slot.addView(icon, iconParams);
        icon.setOnClickListener(mOnIconClickListener);
        icon.setOnLongClickListener(mOnIconLongClickListener);
    }

    private void addCaddyMiniCluster(LinearLayout row,
            java.util.ArrayList<ItemInfoWithIcon> apps, FolderIcon folderIcon) {
        FrameLayout cluster = new FrameLayout(mActivityContext);
        LinearLayout.LayoutParams clusterParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        row.addView(cluster, clusterParams);

        android.widget.GridLayout miniGrid = new android.widget.GridLayout(mActivityContext);
        miniGrid.setColumnCount(2);
        miniGrid.setRowCount(2);
        miniGrid.setUseDefaultMargins(false);
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(
                dp(44), dp(44), Gravity.CENTER);
        cluster.addView(miniGrid, gridParams);
        for (int i = 3; i < Math.min(apps.size(), 7); i++) {
            ImageView miniIcon = new ImageView(mActivityContext);
            miniIcon.setImageDrawable(apps.get(i).newIcon(mActivityContext));
            miniIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            android.widget.GridLayout.LayoutParams miniParams =
                    new android.widget.GridLayout.LayoutParams();
            miniParams.width = dp(18);
            miniParams.height = dp(18);
            miniParams.setMargins(dp(1), dp(1), dp(1), dp(1));
            miniGrid.addView(miniIcon, miniParams);
        }
        // The preview cluster represents the remaining category apps.
        cluster.setClickable(true);
        cluster.setFocusable(true);
        cluster.setContentDescription(folderIcon.getContentDescription());
        cluster.setOnClickListener(v -> folderIcon.performClick());
        cluster.setOnLongClickListener(v -> folderIcon.performLongClick());
    }

    /**
     * Caddy card renderer. This view is deliberately independent from FolderIcon,
     * FolderInfo and Folder's open/close lifecycle. Each app remains a real launcher
     * AppInfo shortcut and keeps the normal click/long-click handlers.
     */
    private void bindIndependentCaddyCard(FrameLayout host, AdapterItem item) {
        host.removeAllViews();
        host.setClipToPadding(false);
        host.setClipChildren(false);
        // Add a small gutter between neighboring Caddy cards without changing
        // the RecyclerView's grid sizing or the normal app drawer.
        host.setPadding(dp(4), dp(4), dp(4), dp(4));

        int densityPadding = dp(14);
        int themeColor = resolveCaddyDynamicColor();
        // Translucent Nothing-style glass, tinted by Lawnchair's existing folder /
        // preview color so the user's selected and dynamic colors remain visible.
        int glassRed = Math.round(Color.red(themeColor) * 0.30f + 28 * 0.70f);
        int glassGreen = Math.round(Color.green(themeColor) * 0.30f + 31 * 0.70f);
        int glassBlue = Math.round(Color.blue(themeColor) * 0.30f + 38 * 0.70f);
        int topGlass = Color.argb(184, glassRed, glassGreen, glassBlue);
        int bottomGlass = Color.argb(148,
                Math.max(12, glassRed - 10),
                Math.max(14, glassGreen - 10),
                Math.max(18, glassBlue - 10));
        int foreground = Color.WHITE;
        int accentStroke = Color.argb(116, Color.red(themeColor),
                Color.green(themeColor), Color.blue(themeColor));

        LinearLayout card = new LinearLayout(mActivityContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(densityPadding, dp(12), densityPadding, dp(12));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> showCaddyCategory(item));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { topGlass, bottomGlass });
        background.setCornerRadius(dp(28));
        background.setStroke(dp(1), accentStroke);
        card.setBackground(background);
        card.setClipToOutline(true);
        card.setElevation(dp(1));
        host.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(mActivityContext);
        title.setText(item.caddyTitle);
        title.setTextColor(foreground);
        title.setTextSize(14);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<AppInfo> apps = item.caddyApps == null ? java.util.Collections.emptyList()
                : item.caddyApps;
        android.widget.GridLayout grid = new android.widget.GridLayout(mActivityContext);
        grid.setColumnCount(2);
        grid.setRowCount(2);
        grid.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(112));
        gridLp.topMargin = dp(8);
        card.addView(grid, gridLp);

        // The Nothing-style card uses two prominent icons on the first row,
        // one prominent icon plus a compact 2x2 cluster on the second row.
        for (int i = 0; i < Math.min(apps.size(), 3); i++) {
            AppInfo app = apps.get(i);
            int column = i == 1 ? 1 : 0;
            int row = i == 2 ? 1 : 0;
            FrameLayout cell = new FrameLayout(mActivityContext);
            android.widget.GridLayout.LayoutParams cellLp =
                    new android.widget.GridLayout.LayoutParams();
            cellLp.width = 0;
            cellLp.height = dp(52);
            cellLp.columnSpec = android.widget.GridLayout.spec(column, 1f);
            cellLp.rowSpec = android.widget.GridLayout.spec(row);
            grid.addView(cell, cellLp);
            addCaddyAppIcon(cell, app, dp(48));
        }

        if (apps.size() > 3) {
            FrameLayout clusterCell = new FrameLayout(mActivityContext);
            android.widget.GridLayout.LayoutParams clusterLp =
                    new android.widget.GridLayout.LayoutParams();
            clusterLp.width = 0;
            clusterLp.height = dp(52);
            clusterLp.columnSpec = android.widget.GridLayout.spec(1, 1f);
            clusterLp.rowSpec = android.widget.GridLayout.spec(1);
            grid.addView(clusterCell, clusterLp);

            android.widget.GridLayout miniGrid = new android.widget.GridLayout(mActivityContext);
            miniGrid.setColumnCount(2);
            miniGrid.setRowCount(2);
            FrameLayout.LayoutParams miniGridLp = new FrameLayout.LayoutParams(
                    dp(54), dp(48), Gravity.CENTER);
            clusterCell.addView(miniGrid, miniGridLp);
            for (int i = 3; i < Math.min(apps.size(), 7); i++) {
                AppInfo app = apps.get(i);
                FrameLayout miniCell = new FrameLayout(mActivityContext);
                android.widget.GridLayout.LayoutParams miniCellLp =
                        new android.widget.GridLayout.LayoutParams();
                miniCellLp.width = dp(27);
                miniCellLp.height = dp(24);
                miniGrid.addView(miniCell, miniCellLp);
                addCaddyAppIcon(miniCell, app, dp(21));
            }
        }
        host.setContentDescription(item.caddyTitle);
    }

    private void bindCaddySuggestionsCard(FrameLayout host, AdapterItem item) {
        host.removeAllViews();
        host.setPadding(dp(4), dp(4), dp(4), dp(4));

        int accent = resolveCaddyDynamicColor();
        int top = Color.argb(190,
                (Color.red(accent) + 28) / 2,
                (Color.green(accent) + 31) / 2,
                (Color.blue(accent) + 38) / 2);
        int bottom = Color.argb(166, 24, 29, 33);

        LinearLayout card = new LinearLayout(mActivityContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(10));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[] { top, bottom });
        background.setCornerRadius(dp(26));
        background.setStroke(dp(1), Color.argb(100, Color.red(accent),
                Color.green(accent), Color.blue(accent)));
        card.setBackground(background);
        card.setClipToOutline(true);
        host.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(mActivityContext);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        TextView title = new TextView(mActivityContext);
        title.setText("Suggestions");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView refresh = new TextView(mActivityContext);
        refresh.setText("↻");
        refresh.setTextColor(Color.WHITE);
        refresh.setTextSize(22);
        refresh.setGravity(Gravity.CENTER);
        refresh.setContentDescription("Refresh suggestions");
        refresh.setFocusable(true);
        refresh.setClickable(true);
        refresh.setPadding(dp(8), 0, dp(4), 0);
        refresh.setOnClickListener(v -> {
            if (item.caddyRefreshAction != null) item.caddyRefreshAction.run();
        });
        header.addView(refresh, new LinearLayout.LayoutParams(dp(38), dp(30)));

        android.widget.GridLayout grid = new android.widget.GridLayout(mActivityContext);
        grid.setColumnCount(4);
        grid.setRowCount(2);
        grid.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gridLp.topMargin = dp(4);
        card.addView(grid, gridLp);

        List<AppInfo> apps = item.caddyApps == null
                ? java.util.Collections.emptyList() : item.caddyApps;
        for (int i = 0; i < Math.min(apps.size(), 8); i++) {
            AppInfo app = apps.get(i);
            LinearLayout cell = new LinearLayout(mActivityContext);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            android.widget.GridLayout.LayoutParams cellLp =
                    new android.widget.GridLayout.LayoutParams();
            cellLp.width = 0;
            cellLp.height = dp(88);
            cellLp.columnSpec = android.widget.GridLayout.spec(i % 4, 1f);
            cellLp.rowSpec = android.widget.GridLayout.spec(i / 4);
            grid.addView(cell, cellLp);

            FrameLayout iconHost = new FrameLayout(mActivityContext);
            cell.addView(iconHost, new LinearLayout.LayoutParams(dp(50), dp(50)));
            addCaddyAppIcon(iconHost, app, dp(46));

            TextView label = new TextView(mActivityContext);
            label.setText(app.title);
            label.setTextColor(Color.WHITE);
            label.setTextSize(11);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(3);
            cell.addView(label, labelLp);
        }
        host.setContentDescription("Suggestions, up to eight frequently used apps");
    }

    /**
     * Opens the selected Caddy category in its own lightweight app grid.
     * This deliberately does not create or open a FolderInfo/FolderIcon.
     */
    private void showCaddyCategory(AdapterItem item) {
        List<AppInfo> apps = item.caddyApps == null
                ? java.util.Collections.emptyList() : item.caddyApps;
        if (apps.isEmpty()) {
            return;
        }

        Dialog dialog = new Dialog(mActivityContext);
        LinearLayout panel = new LinearLayout(mActivityContext);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(16));

        int dynamicAccent = resolveCaddyDynamicColor();
        int glassBase = Color.rgb(27, 35, 37);
        int glassTop = Color.rgb(39, 49, 51);
        int glassBottom = Color.rgb(25, 33, 35);
        int tintedTop = Color.rgb(
                (Color.red(glassTop) * 3 + Color.red(dynamicAccent)) / 4,
                (Color.green(glassTop) * 3 + Color.green(dynamicAccent)) / 4,
                (Color.blue(glassTop) * 3 + Color.blue(dynamicAccent)) / 4);
        GradientDrawable panelBackground = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(246, Color.red(tintedTop), Color.green(tintedTop),
                                Color.blue(tintedTop)),
                        Color.argb(250, Color.red(glassBase), Color.green(glassBase),
                                Color.blue(glassBase)),
                        Color.argb(252, Color.red(glassBottom), Color.green(glassBottom),
                                Color.blue(glassBottom))
                });
        panelBackground.setCornerRadius(dp(30));
        panelBackground.setStroke(dp(1), Color.argb(48, 255, 255, 255));
        panel.setBackground(panelBackground);
        panel.setClipToOutline(true);
        panel.setElevation(dp(12));

        TextView heading = new TextView(mActivityContext);
        heading.setText(item.caddyTitle);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(20);
        heading.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        heading.setPadding(0, 0, 0, dp(14));
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(mActivityContext);
        scrollView.setFillViewport(false);
        panel.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        android.widget.GridLayout appGrid = new android.widget.GridLayout(mActivityContext);
        appGrid.setColumnCount(4);
        appGrid.setUseDefaultMargins(false);
        scrollView.addView(appGrid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (AppInfo app : apps) {
            LinearLayout appCell = new LinearLayout(mActivityContext);
            appCell.setOrientation(LinearLayout.VERTICAL);
            appCell.setGravity(Gravity.CENTER);
            appCell.setPadding(dp(3), dp(8), dp(3), dp(8));

            ImageView appIcon = new ImageView(mActivityContext);
            appIcon.setImageDrawable(app.newIcon(mActivityContext));
            appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            appIcon.setTag(app);
            appIcon.setContentDescription(app.title);
            appIcon.setFocusable(true);
            appIcon.setOnClickListener(v -> {
                dialog.dismiss();
                mOnIconClickListener.onClick(v);
            });
            appCell.addView(appIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));

            TextView appLabel = new TextView(mActivityContext);
            appLabel.setText(app.title);
            appLabel.setTextColor(Color.WHITE);
            appLabel.setTextSize(11);
            appLabel.setGravity(Gravity.CENTER);
            appLabel.setMaxLines(2);
            appLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
            labelParams.topMargin = dp(5);
            appCell.addView(appLabel, labelParams);

            android.widget.GridLayout.LayoutParams cellParams =
                    new android.widget.GridLayout.LayoutParams();
            cellParams.width = 0;
            cellParams.columnSpec = android.widget.GridLayout.spec(
                    android.widget.GridLayout.UNDEFINED, 1f);
            cellParams.setMargins(dp(2), dp(2), dp(2), dp(2));
            appGrid.addView(appCell, cellParams);
        }

        int screenHeight = mActivityContext.getResources().getDisplayMetrics().heightPixels;
        int maxPanelHeight = Math.max(dp(300), screenHeight - dp(176));
        int contentRows = (apps.size() + 3) / 4;
        int desiredPanelHeight = dp(76) + contentRows * dp(96) + dp(34);
        int panelHeight = Math.min(desiredPanelHeight, maxPanelHeight);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);
        dialog.show();

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams windowParams = window.getAttributes();
            windowParams.width = Math.min(
                    mActivityContext.getResources().getDisplayMetrics().widthPixels - dp(32),
                    dp(560));
            windowParams.height = panelHeight;
            windowParams.gravity = Gravity.CENTER;
            windowParams.dimAmount = 0.34f;
            window.setAttributes(windowParams);
            window.setDimAmount(0.34f);
        }

        // A restrained scale/fade entrance makes the category panel feel connected
        // to the drawer without changing the underlying category cards.
        panel.setAlpha(0f);
        panel.setScaleX(0.965f);
        panel.setScaleY(0.965f);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(190L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void addCaddyAppIcon(FrameLayout cell, AppInfo app, int iconSize) {
        ImageView icon = new ImageView(mActivityContext);
        icon.setImageDrawable(app.newIcon(mActivityContext));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cell.addView(icon, new FrameLayout.LayoutParams(
                iconSize, iconSize, Gravity.CENTER));
        icon.setTag(app);
        icon.setContentDescription(app.title);
        icon.setFocusable(true);
        icon.setOnClickListener(mOnIconClickListener);
        // Caddy preview icons are plain ImageViews, not DraggableViews. Do not
        // route their long-press gesture into the launcher drag controller.
        icon.setLongClickable(false);
        icon.setOnLongClickListener(null);
    }

    /**
     * Resolve Android's themed accent so Caddy follows the system Monet palette
     * (including wallpaper-based colors on Android 12+). Keep Lawnchair's folder
     * tint as a compatibility fallback for themes that do not expose colorAccent.
     */
    private int resolveCaddyDynamicColor() {
        // Prefer Android's Monet palette directly. On Pixel / Android 12+ these
        // framework colors are supplied by the active wallpaper or system theme.
        // Use the themed accent next so custom ROMs and app themes remain supported.
        android.content.res.Resources resources = mActivityContext.getResources();
        String[] systemPaletteColors = {
                "system_accent1_600",
                "system_accent2_600",
                "system_accent3_600"
        };
        for (String colorName : systemPaletteColors) {
            int colorId = resources.getIdentifier(colorName, "color", "android");
            if (colorId != 0) {
                try {
                    return resources.getColor(colorId, mActivityContext.getTheme());
                } catch (RuntimeException ignored) {
                    // Try the next system palette entry or themed accent.
                }
            }
        }

        TypedValue value = new TypedValue();
        if (mActivityContext.getTheme().resolveAttribute(
                android.R.attr.colorAccent, value, true)) {
            if (value.resourceId != 0) {
                try {
                    return resources.getColor(value.resourceId, mActivityContext.getTheme());
                } catch (RuntimeException ignored) {
                    // Fall through to the resolved inline color or legacy fallback.
                }
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        }
        return LawnchairUtilsKt.resolveFolderPreviewColor(mActivityContext);
    }

    private int dp(float value) {
        return Math.round(value * mActivityContext.getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onFailedToRecycleView(ViewHolder holder) {
        // Always recycle and we will reset the view when it is bound
        return true;
    }

    @Override
    public int getItemCount() {
        return mApps.getAdapterItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        AdapterItem item = mApps.getAdapterItems().get(position);
        return item.viewType;
    }

    protected static boolean isViewType(int viewType, int viewTypeMask) {
        return (viewType & viewTypeMask) != 0;
    }

}
