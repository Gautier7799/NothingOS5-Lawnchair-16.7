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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.text.TextUtils;

import com.android.launcher3.graphics.PathWrapper;

import app.lawnchair.allapps.caddy.CaddyCardStyle;

/**
 * Folder preview background that renders the shared Caddy card surface.
 *
 * A Home Screen folder draws the same rounded, translucent, accent-stroked surface as the
 * app drawer category cards, in place of the native circular folder background. The
 * category title is drawn inside the card, so the closed folder reads exactly like a drawer
 * card and the native label below it is not needed.
 *
 * Only the surface changes. The folder keeps its preview items, click/long-press handling,
 * drag/drop and persistence untouched.
 */
public class CaddyCardBackground extends PreviewBackground {

    // Share of the preview square reserved for the title strip.
    public static final float TITLE_INSET_FRACTION = 0.22f;

    private final Context mCardContext;
    private final GradientDrawable mCard;
    private final TextPaint mTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mCardRect = new RectF();
    private final float mTitlePaddingPx;

    private CharSequence mTitle = "";

    public CaddyCardBackground(Context context) {
        super(context);
        mCardContext = context;
        mCard = CaddyCardStyle.INSTANCE.createCardBackground(context);
        float density = context.getResources().getDisplayMetrics().density;
        mTitlePaddingPx = density * 12f;
        mTitlePaint.setColor(Color.WHITE);
        mTitlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        mTitlePaint.setTextSize(density * 13f);
    }

    /** Sets the category title drawn inside the card. */
    public void setTitle(CharSequence title) {
        mTitle = title == null ? "" : title;
        invalidate();
    }

    private void updateCardRect() {
        // Follow the native hover/accept scale so the card reacts when an icon is dragged
        // over the folder. getScaledRadius() is half of the scaled preview square.
        float half = getScaledRadius();
        float centerX = basePreviewOffsetX + previewSize / 2f;
        float centerY = basePreviewOffsetY + previewSize / 2f;
        mCardRect.set(centerX - half, centerY - half, centerX + half, centerY + half);
    }

    @Override
    public void drawBackground(Canvas canvas) {
        updateCardRect();
        if (mCardRect.width() <= 0 || mCardRect.height() <= 0) {
            return;
        }
        // A card, not a pill: cap the shared 28dp radius against the card height.
        mCard.setCornerRadius(cardRadiusPx());
        mCard.setBounds(Math.round(mCardRect.left), Math.round(mCardRect.top),
                Math.round(mCardRect.right), Math.round(mCardRect.bottom));
        mCard.draw(canvas);

        drawTitle(canvas);
    }

    private float cardRadiusPx() {
        return Math.min(
                CaddyCardStyle.INSTANCE.cornerRadiusPx(mCardContext),
                mCardRect.height() * 0.30f);
    }

    private void drawTitle(Canvas canvas) {
        if (TextUtils.isEmpty(mTitle)) {
            return;
        }
        float left = mCardRect.left + mTitlePaddingPx;
        float right = mCardRect.right - mTitlePaddingPx;
        float maxWidth = right - left;
        if (maxWidth <= 0) {
            return;
        }
        CharSequence text = TextUtils.ellipsize(
                mTitle, mTitlePaint, maxWidth, TextUtils.TruncateAt.END);
        float stripHeight = mCardRect.height() * TITLE_INSET_FRACTION;
        Paint.FontMetrics fm = mTitlePaint.getFontMetrics();
        float textHeight = fm.bottom - fm.top;
        float baseline = mCardRect.top + (stripHeight - textHeight) / 2f - fm.top;
        canvas.drawText(text, 0, text.length(), left, baseline, mTitlePaint);
    }

    @Override
    public void drawBackgroundStroke(Canvas canvas) {
        // The card drawable already carries the accent stroke.
    }

    /**
     * Clips the preview icons to a rounded rectangle instead of the native folder circle.
     *
     * The default folder clip is a circle; a 2x2 grid pushes the corner icons onto that
     * circle, which cuts them. Clipping to the card's own rounded rectangle keeps every
     * icon fully visible while still trimming anything that leaves the card.
     */
    @Override
    public PathWrapper getClipPath() {
        updateCardRect();
        PathWrapper wrapper = super.getClipPath();
        wrapper.reset();
        float radius = cardRadiusPx();
        // Inflate slightly so the clip never trims an icon that sits on the card edge.
        float inset = -previewSize * 0.02f;
        RectF clip = new RectF(
                mCardRect.left + inset, mCardRect.top + inset,
                mCardRect.right - inset, mCardRect.bottom - inset);
        wrapper.getPath().addRoundRect(clip, radius, radius, android.graphics.Path.Direction.CW);
        wrapper.setBounds(clip.left, clip.top, clip.right, clip.bottom);
        wrapper.setCornerRadius(radius);
        return wrapper;
    }
}
