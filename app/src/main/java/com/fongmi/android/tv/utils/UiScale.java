package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

/**
 * 大屏（平板）UI 等比放大。
 *
 * <p>手机版布局是按手机宽度设计的，直接铺满 11 寸平板会显得偏小。这里不重建平板布局，
 * 而是把大屏设备的密度按比例调高（dp 变小、视觉变大），让整套手机 UI 等比放大，
 * 页面结构保持不变。</p>
 *
 * <p>缩放比例 = 设备最小宽度 dp / {@link #TARGET_SW_DP}，限制在 1.0 ~ {@link #MAX_SCALE}。
 * 当前平板 711dp 时约 1.30 倍。手机（&lt; 600dp）不做任何处理。</p>
 */
public final class UiScale {

    private static final int LARGE_SCREEN_SW_DP = 600;
    private static final float TARGET_SW_DP = 547f;
    private static final float MAX_SCALE = 1.45f;
    private static final float MIN_SCALE = 1.05f;

    private static volatile float scale = 1f;

    private UiScale() {
    }

    /**
     * 供 dp2px/sp2px 等手动换算的地方复用，保证代码里算出来的尺寸和 XML 里的 dp 一致。
     */
    public static float scale() {
        return scale;
    }

    public static boolean isScaled() {
        return scale() > 1f;
    }

    /**
     * 按设备最小宽度包装 Activity 的 Context（只在 attachBaseContext 里调用）。
     */
    public static Context wrap(Context base) {
        if (base == null) return null;
        Configuration scaled = scaled(base.getResources().getConfiguration());
        if (scaled == null) return base;
        return base.createConfigurationContext(scaled);
    }

    /**
     * 屏幕旋转等配置变化时，把同样的缩放应用到当前 Activity 的 Resources。
     */
    public static void apply(Resources resources, Configuration newConfig) {
        if (resources == null || newConfig == null) return;
        Configuration scaled = scaled(newConfig);
        if (scaled == null) return;
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setTo(resources.getDisplayMetrics());
        metrics.densityDpi = scaled.densityDpi;
        metrics.density = scaled.densityDpi / 160f;
        metrics.scaledDensity = metrics.density * scaled.fontScale;
        try {
            resources.updateConfiguration(scaled, metrics);
        } catch (Throwable ignored) {
        }
    }

    private static Configuration scaled(Configuration source) {
        if (source == null) return null;
        int sw = source.smallestScreenWidthDp;
        if (sw < LARGE_SCREEN_SW_DP) {
            scale = 1f;
            return null;
        }
        float factor = Math.min(MAX_SCALE, sw / TARGET_SW_DP);
        if (factor < MIN_SCALE) {
            scale = 1f;
            return null;
        }
        scale = factor;
        Configuration config = new Configuration(source);
        config.densityDpi = Math.round(source.densityDpi * factor);
        return config;
    }
}
