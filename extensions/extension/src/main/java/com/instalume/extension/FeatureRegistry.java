package com.instalume.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modular feature registry — makes IG updates maintainable.
 * Every UI toggle must map to a Feature with honest status.
 */
public final class FeatureRegistry {
    public enum Status { IMPLEMENTED, VERSION_DEPENDENT, UNAVAILABLE }

    public static final class Feature {
        public final String id;
        public final String name;
        public final String description;
        public final String category;
        public final boolean enabled;
        public final String supportedVersions;
        public final String dependencies;
        public final Status status;
        public Feature(String id, String name, String desc, String cat,
                       boolean enabled, String versions, String deps, Status st) {
            this.id = id; this.name = name; this.description = desc; this.category = cat;
            this.enabled = enabled; this.supportedVersions = versions;
            this.dependencies = deps; this.status = st;
        }
    }

    private static final List<Feature> FEATURES;
    static {
        List<Feature> l = new ArrayList<>();
        l.add(new Feature("block_feed", "Block Home feed", "Network block /feed/timeline/", "Mods",
                Config.isFeedBlocked(), "IG 446 tested", "net_block", Status.IMPLEMENTED));
        l.add(new Feature("block_reels", "Block Reels", "Network + tab hide", "Mods",
                Config.isReelsBlocked(), "IG 446 tested", "net_block,hiders", Status.IMPLEMENTED));
        l.add(new Feature("liquid_glass_nav", "Liquid Glass navigation", "Translucent nav styling",
                "UI", Config.isGlassEnabled(), "IG 446 tested (UI overlay)", "hiders",
                Status.IMPLEMENTED));
        l.add(new Feature("ghost_read", "Ghost: read status", "Prevent DM read receipts",
                "Privacy", false, "no fingerprint in V1.0.0", "net_block?",
                Status.UNAVAILABLE));
        l.add(new Feature("ghost_story", "Ghost: story seen", "Prevent story-seen marking",
                "Privacy", false, "no fingerprint in V1.0.0", "—", Status.VERSION_DEPENDENT));
        l.add(new Feature("ghost_typing", "Ghost: typing indicator", "Hide typing/activity",
                "Privacy", false, "no fingerprint in V1.0.0", "—", Status.VERSION_DEPENDENT));
        l.add(new Feature("media_viewer", "Enhanced media viewer", "Zoom/gestures/fullscreen",
                "Media", false, "requires media-viewer fingerprints", "—", Status.VERSION_DEPENDENT));
        l.add(new Feature("backup_restore", "Backup & restore", "JSON export/import/merge",
                "App", true, "all", "Config", Status.IMPLEMENTED));
        FEATURES = Collections.unmodifiableList(l);
    }

    private FeatureRegistry() {}
    public static List<Feature> all() { return FEATURES; }
}
