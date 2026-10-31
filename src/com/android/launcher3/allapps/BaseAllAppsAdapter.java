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
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
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

    // Common view type masks
    public static final int VIEW_TYPE_MASK_DIVIDER = VIEW_TYPE_ALL_APPS_DIVIDER;
    public static final int VIEW_TYPE_MASK_ICON = VIEW_TYPE_FOLDER | VIEW_TYPE_ICON;

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

        protected boolean isCountedForAccessibility() {
            return viewType == VIEW_TYPE_ICON || viewType == VIEW_TYPE_FOLDER;
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
        background.setColor(0xFF1E1F24);
        background.setCornerRadius(dp(24));
        background.setStroke(dp(1), 0x24FFFFFF);
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
        title.setTextColor(Color.WHITE);
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
