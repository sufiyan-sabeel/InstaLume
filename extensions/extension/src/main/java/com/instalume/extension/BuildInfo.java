package com.instalume.extension;

/**
 * InstaLume build/product metadata — single source of truth for V1.0.0.
 * Independent project, not affiliated with Meta/Instagram.
 */
public final class BuildInfo {
    public static final String PRODUCT = "InstaLume";
    public static final String VERSION = "1.0.0";
    public static final String VERSION_TAGLINE = "Social, reimagined.";
    public static final String CREATOR = "Umaiz Sufiyan";
    public static final String REPO = "sufiyan-sabeel/InstaLume";
    public static final String REPO_URL = "https://github.com/sufiyan-sabeel/InstaLume";
    public static final String ISSUES_URL = "https://github.com/sufiyan-sabeel/InstaLume/issues";
    public static final String DOCS_URL = "https://github.com/sufiyan-sabeel/InstaLume#readme";

    /** Instagram base this V1.0.0 was audited against. */
    public static final String IG_BASE_TESTED = "446.0.0.49.77";

    /** Foundation attribution (GPL-3.0). */
    public static final String FOUNDATION = "Based on FeurStagram (GPLv3) by jean-voila + contributors, with Morphe + Piko attribution — see NOTICE.";

    private BuildInfo() {}

    public static String longVersion() {
        return PRODUCT + " V" + VERSION + " · IG " + IG_BASE_TESTED;
    }
}
