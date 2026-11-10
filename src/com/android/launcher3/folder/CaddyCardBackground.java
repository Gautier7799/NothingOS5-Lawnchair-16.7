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

package com.android.launcher3.folder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;

import app.lawnchair.allapps.caddy.CaddyCardStyle;

/**
 * Folder preview background that renders the shared Caddy card surface.
 *
 * A Home Screen folder flagged as a Caddy card draws the same rounded, translucent,
 * accent-stroked surface as the app drawer category cards, in place of the native circular
 * folder background. The card covers the folder's icon area, so the preview icons keep
 * their native 2x2 placement on top of it and the native folder label stays underneath.
 *
 * Only the surface changes. The folder keeps its preview items, click/long-press handling,
 * drag/drop and persistence untouched.
 */
public class CaddyCardBackground extends PreviewBackground {

    private final Context mCardContext;
    private final GradientDrawable mCard;
    private final float mInsetPx;

    public CaddyCardBackground(Context context) {
        super(context);
        mCardContext = context;
        mCard = CaddyCardStyle.INSTANCE.createCardBackground(context);
        mInsetPx = context.getResources().getDisplayMetrics().density * 2f;
    }

    @Override
    public void drawBackground(Canvas canvas) {
        // Follow the native hover/accept scale so the card still reacts when an icon is
        // dragged over the folder. getScaledRadius() is half the scaled preview square.
        float half = getScaledRadius();
        float centerX = basePreviewOffsetX + previewSize / 2f;
        float centerY = basePreviewOffsetY + previewSize / 2f;
        float left = centerX - half + mInsetPx;
        float top = centerY - half + mInsetPx;
        float right = centerX + half - mInsetPx;
        float bottom = centerY + half - mInsetPx;
        if (right <= left || bottom <= top) {
            return;
        }
        // The shared 28dp radius targets the large drawer card. On the compact Home Screen
        // footprint it is capped so the surface stays a card and never a pill.
        float radius = Math.min(
                CaddyCardStyle.INSTANCE.cornerRadiusPx(mCardContext),
                Math.min(right - left, bottom - top) * 0.32f);
        mCard.setCornerRadius(radius);
        mCard.setBounds(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
        mCard.draw(canvas);
    }

    @Override
    public void drawBackgroundStroke(Canvas canvas) {
        // The card drawable already carries the accent stroke.
    }
}
