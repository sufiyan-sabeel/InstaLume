package com.instalume.extension;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Explicit Instagram version compatibility.
 *
 * <p>FeurStagram used {@code AppTarget(version=null)} — apply to any IG.
 * InstaLume keeps fingerprint patching but fails closed at runtime:
 * unknown IG versions get compatible-only behavior + diagnostics,
 * never silent mis-patching.
 */
public final class VersionCompatibility {
    /** Only profile validated for V1.0.0. */
    public static final String SUPPORTED_IG = "446.0.0.49.77";
    /** Prefix match allows patch-level rebuilds on same base (e.g. 446.0.0.49.x). */
    private static final String SUPPORTED_PREFIX = "446.0.0.49.";

    private VersionCompatibility() {}

    public static String installedIgVersion(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            String v = pm.getPackageInfo(ctx.getPackageName(), 0).versionName;
            return v == null ? "unknown" : v;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    public static boolean isSupported(String igVersion) {
        if (igVersion == null) return false;
        return igVersion.equals(SUPPORTED_IG) || igVersion.startsWith(SUPPORTED_PREFIX);
    }

    public static String statusFor(String igVersion) {
        if (isSupported(igVersion)) return "Compatible";
        if (igVersion == null || "unknown".equals(igVersion)) return "Unknown — run diagnostics";
        return "Unsupported — fingerprint not validated for IG " + igVersion;
    }
}
