package com.instalume.extension;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;

/**
 * InstaLume Liquid Glass system — programmatic, no bundled res.
 * Android 13+ gets controlled blur where safe; older versions get
 * translucent fallback. Never continuous blur, never unreadable.
 */
public final class Glass {
    private Glass() {}

    public static boolean blurSupported() {
        return Build.VERSION.SDK_INT >= 31;
    }

    public static GradientDrawable background(Context ctx, int opacityPct) {
        int a = Math.max(20, Math.min(95, opacityPct)) * 255 / 100;
        GradientDrawable d = new GradientDrawable();
        boolean dark = true; // IG dark-first; theme_mode refines text, not base
        d.setColor((a << 24) | (dark ? 0x1B1B1B : 0xF2F2F2));
        int r = dp(ctx, Config.getCornerRadius());
        d.setCornerRadius(r);
        d.setStroke(dp(ctx, 1), (Math.min(a, 90) << 24) | 0x3A3A3A);
        return d;
    }

    public static void applyTo(View v) {
        if (v == null || v.getContext() == null) return;
        Context c = v.getContext();
        if (!Config.isGlassEnabled()) {
            v.setBackgroundColor(0xFF1B1B1B);
            return;
        }
        v.setBackground(background(c, Config.getGlassOpacity()));
        if (blurSupported() && Config.getBlurStrength() > 0 && !Config.isReducedMotion()) {
            try {
                float radius = Math.min(40, Math.max(0, Config.getBlurStrength()));
                v.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                        radius, radius, android.graphics.Shader.TileMode.CLAMP));
            } catch (Throwable ignored) {
                v.setRenderEffect(null);
            }
        } else {
            try { v.setRenderEffect(null); } catch (Throwable ignored) {}
        }
        v.setClipToOutline(true);
    }

    private static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
