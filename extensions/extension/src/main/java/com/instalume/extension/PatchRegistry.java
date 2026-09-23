package com.instalume.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runtime registry of patches + diagnostics.
 * Build-time truth is build/patch-report.json; this is the on-device view.
 */
public final class PatchRegistry {
    public enum Status { COMPATIBLE, UNSUPPORTED, UNKNOWN }

    public static final class Entry {
        public final String id;
        public final String name;
        public final String target;
        public final String fingerprint;
        public final boolean defaultEnabled;
        public Entry(String id, String name, String target, String fingerprint, boolean def) {
            this.id = id; this.name = name; this.target = target;
            this.fingerprint = fingerprint; this.defaultEnabled = def;
        }
    }

    private static final List<Entry> PATCHES;
    static {
        List<Entry> l = new ArrayList<>();
        l.add(new Entry("settings_entry", "Settings entry point", "TabBar binder <init>(View)",
                "TabBarBinderFingerprint: ViewGroup+View IPUT shape, LX/*", true));
        l.add(new Entry("net_block", "Network content blocking", "TigonServiceLayer.startRequest",
                "definingClass=com.instagram.api.tigon.TigonServiceLayer", true));
        l.add(new Entry("feed_filter", "Feed item filtering", "feed-item parseFromJson",
                "strings media_or_ad,clips_netego,stories_netego,ad4ad + parseFromJson", true));
        l.add(new Entry("limit_feed", "Limit feed to following", "main-feed request <init>",
                "Request{mReason=,mInstanceNumber= + pagination_source", true));
        l.add(new Entry("popup_hide", "Popup hiding", "IGToast.show()",
                "sole Toast subclass, show()V", true));
        l.add(new Entry("sig_bypass", "Signature check bypass (DISABLED policy)", "key-hash allowlist",
                "Invalid SHA256 key hash → Z(X.3uq); legacy+446 forms", true));
        l.add(new Entry("force_sdr", "Force SDR display", "Window.setColorMode",
                "framework ref android.view.Window.setColorMode", true));
        l.add(new Entry("restart_relay", "Restart relay", "AndroidManifest.xml",
                "declares RestartActivity :instalume_restart", true));
        l.add(new Entry("install_perm", "Install-packages permission", "AndroidManifest.xml",
                "REQUEST_INSTALL_PACKAGES", true));
        l.add(new Entry("clone", "Clone", "manifest+strings",
                "package rename com.instagram.android.instalume", false));
        l.add(new Entry("debug_bridge", "Debug bridge (dev only)", "TabBar anchor",
                "same as settings entry, default=false", false));
        PATCHES = Collections.unmodifiableList(l);
    }

    private PatchRegistry() {}

    public static List<Entry> all() { return PATCHES; }

    public static String diagnose(String igVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append(BuildInfo.longVersion()).append('\n');
        sb.append("IG=").append(igVersion).append(" → ")
          .append(VersionCompatibility.statusFor(igVersion)).append('\n');
        for (Entry e : PATCHES) {
            Status s;
            if ("debug_bridge".equals(e.id) || "clone".equals(e.id)) s = Status.UNKNOWN;
            else if (VersionCompatibility.isSupported(igVersion)) s = Status.COMPATIBLE;
            else s = Status.UNSUPPORTED;
            sb.append(e.id).append(" | ").append(e.name).append(" | ").append(s);
            if (s == Status.UNSUPPORTED) sb.append(" | Reason: fingerprint not validated in IG ").append(igVersion);
            sb.append('\n');
        }
        // Security policy note: sig_bypass must not ship as enabled.
        sb.append("NOTE: sig_bypass conflicts with no-security-bypass policy — see docs/VERSION_COMPATIBILITY.md\n");
        return sb.toString();
    }
}
