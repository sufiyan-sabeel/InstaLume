package com.instalume.extension;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * InstaLume settings, shown as a full-screen page when the Home tab is
 * long-pressed. Built entirely in code (no bundled resources) and persisted
 * through {@link Config}.
 *
 * <h3>The look: Material 3 Expressive, monochrome</h3>
 * White is the only accent — no hue anywhere, so the panel reads as part of a
 * dark OS rather than as a themed app. The Expressive part is in the shapes and
 * the type: large corner radii, options grouped into "connected" lists where the
 * group's outer corners are round and the joints between rows are nearly square,
 * pill-shaped buttons, sentence-case section labels, and a tight-tracked headline.
 * This class also owns the palette, type and shape helpers the other InstaLume
 * surfaces ({@link UpdateChecker}, {@link Onboarding}) draw with, so the design
 * only lives in one place.
 *
 * <h3>Fitting any screen</h3>
 * Instagram targets a recent SDK, so its windows are edge-to-edge: system bars
 * and display cutouts overlap the dialog and nothing is inset for us. Every
 * InstaLume window therefore goes through {@link #applyWindowInsets} and pads
 * itself — otherwise the action buttons sit under a three-button navigation bar
 * and the scroll area runs off the bottom of the screen. Content that can grow
 * (the settings list, a release-notes block) lives in a scroller bounded by what
 * is actually on screen, so a short screen or landscape shrinks it instead of
 * pushing the buttons away.
 */
public final class Settings {

    // --- Colour: a neutral dark scheme with white as the single accent --------

    /** Page background. */
    public static final int SURFACE = 0xFF0E0E0E;
    /** Cards, list rows. */
    public static final int SURFACE_CONTAINER = 0xFF1B1B1B;
    /** One step up: tonal buttons, the raised confirm card. */
    public static final int SURFACE_CONTAINER_HIGH = 0xFF262626;
    public static final int ON_SURFACE = 0xFFF4F4F4;
    public static final int ON_SURFACE_VARIANT = 0xFFABABAB;
    public static final int OUTLINE = 0xFF757575;
    public static final int OUTLINE_VARIANT = 0xFF3A3A3A;
    /** The accent. Monochrome by design: white. */
    public static final int PRIMARY = 0xFFFFFFFF;
    public static final int ON_PRIMARY = 0xFF101010;
    /**
     * Destructive actions. A monochrome scheme has no red to lean on, so they
     * carry the same accent and are distinguished by their wording instead.
     */
    public static final int ERROR = 0xFFFFFFFF;
    public static final int ON_ERROR = 0xFF101010;
    public static final int DIVIDER = 0xFF2A2A2A;
    public static final int RIPPLE = 0x26FFFFFF;

    // --- Shape: the M3 Expressive corner scale --------------------------------

    /** Dialog / card corners. */
    private static final float CORNER_XL = 28f;
    /** The outer corners of a connected list group. */
    private static final float CORNER_L = 24f;
    /** The joints inside a connected list group. */
    private static final float CORNER_JOINT = 4f;
    /** Larger than half a view's height, so it renders as a pill. */
    private static final float CORNER_FULL = 200f;
    /** Gap between the rows of a connected group. */
    private static final int ROW_GAP_DP = 3;

    private Settings() {}

    /**
     * Install the InstaLume entry points off the bottom tab bar: the long-press
     * settings gesture, the UI hiders, and the launch update check. The tab bar is
     * just a stable handle into the window's view tree.
     */
    public static void installHomeTabWatcher(ViewGroup tabBar) {
        if (tabBar == null) return;
        tabBar.getViewTreeObserver().addOnGlobalLayoutListener(new HomeTabWatcher(tabBar));
        Hiders.installAll(tabBar);
        Context activity = getActivityContext(tabBar);
        // The follow card comes first after an install or update; "What's new"
        // waits until it is dismissed.
        FollowPrompt.maybeShow(activity, () -> UpdateChecker.checkWhatsNew(activity));
        UpdateChecker.check(activity);
    }

    /** Unwrap a view's context down to the hosting Activity when possible. */
    public static Context getActivityContext(View view) {
        if (view == null) return null;
        Context context = view.getContext();
        Context cursor = context;
        while (cursor != null && !(cursor instanceof Activity) && cursor instanceof ContextWrapper) {
            cursor = ((ContextWrapper) cursor).getBaseContext();
        }
        return cursor != null ? cursor : context;
    }

    /** How far the settings page rises while it opens. */
    private static final int OPEN_RISE_DP = 32;
    /** Length of the settings page's opening animation. */
    private static final long OPEN_DURATION_MS = 280L;

    public static void show(Context context) {
        if (context == null) return;
        try {
            Dialog dialog = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            View content = buildContent(context, dialog);
            dialog.setContentView(content);

            // Opening animation: the page rises and fades in over Instagram. The window
            // itself stays transparent (the page paints its own background), so what is
            // behind shows through until the page has settled.
            content.setAlpha(0f);
            content.setTranslationY(dp(context, OPEN_RISE_DP));
            dialog.setOnShowListener(d -> content.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(OPEN_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start());

            // Intercept Back (incl. predictive-back gesture) to restart on change.
            dialog.setOnCancelListener(d -> {
                if (Config.isRestartPending()) {
                    CacheCleaner.clearAndRestart(context);
                }
            });

            styleWindow(dialog, Color.TRANSPARENT, 0f);
            dialog.show();
        } catch (Throwable t) {
            Toast.makeText(context, "InstaLume settings unavailable here", Toast.LENGTH_LONG).show();
        }
    }

    // --- Window plumbing ------------------------------------------------------

    /**
     * Make a dialog window fill the screen edge to edge with light system-bar
     * icons, so the content underneath can pad itself against the bars and the
     * cutout. Every InstaLume dialog goes through here.
     *
     * @param background window background colour, or 0 for a transparent scrim
     * @param dim        how much to darken what is behind (0 for the full-screen page)
     */
    public static void styleWindow(Dialog dialog, int background, float dim) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(background));
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setDimAmount(dim);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

        // Draw behind the bars and let the content pad itself. Without this the
        // window may not receive insets at all, and the padding below would be
        // computed from zeros.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }
        window.getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // Deprecated (and ignored) from API 35, but still what tints the bars on
        // everything older.
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // The surface underneath is dark, so the bar icons must be light — the
        // host activity may have asked for the opposite.
        View decor = window.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    /** Receives the space taken by system bars and display cutouts, in pixels. */
    public interface InsetTarget {
        void onInsets(int left, int top, int right, int bottom);
    }

    /**
     * Report the window's system-bar + cutout insets to {@code target}, now and on
     * every change (rotation, switching to three-button navigation, a foldable
     * unfolding).
     */
    public static void applyWindowInsets(View view, InsetTarget target) {
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            target.onInsets(left, top, right, bottom);
            return insets;
        });
        view.requestApplyInsets();
    }

    /**
     * Widest a column of text and controls is allowed to get. Past this a tablet,
     * an unfolded foldable or a landscape phone gives rows so wide the label and
     * its switch end up at opposite edges of the screen.
     */
    private static final int CONTENT_MAX_WIDTH_DP = 620;

    /**
     * Pad a full-screen root against the system bars and the cutout, and keep its
     * content centred within {@code maxWidthDp}.
     *
     * The width part has to run on every layout pass rather than only when the
     * insets change: {@code requestApplyInsets()} is asynchronous, so the first
     * layout happens with no insets at all, and a root that only listened for
     * insets would keep whatever it measured then.
     */
    public static void applyPageInsets(final View root, final int sideDp, final int verticalDp,
                                       final int maxWidthDp) {
        final Context context = root.getContext();
        final int[] insets = new int[4];
        Runnable apply = () -> {
            int usable = root.getWidth() - insets[0] - insets[2];
            int extra = Math.max(0, usable - dp(context, maxWidthDp)) / 2;
            int left = dp(context, sideDp) + insets[0] + extra;
            int top = dp(context, verticalDp) + insets[1];
            int right = dp(context, sideDp) + insets[2] + extra;
            int bottom = dp(context, verticalDp) + insets[3];
            if (root.getPaddingLeft() == left && root.getPaddingTop() == top
                    && root.getPaddingRight() == right && root.getPaddingBottom() == bottom) {
                return;
            }
            root.setPadding(left, top, right, bottom);
        };
        applyWindowInsets(root, (l, t, r, b) -> {
            insets[0] = l;
            insets[1] = t;
            insets[2] = r;
            insets[3] = b;
            apply.run();
        });
        root.getViewTreeObserver().addOnGlobalLayoutListener(apply::run);
    }

    // --- The full-screen page scaffold ----------------------------------------

    /**
     * The shape every InstaLume full-screen surface takes: a headline block, a
     * scrolling middle that absorbs whatever space is left, and an action row
     * pinned above the navigation bar. Settings and both update screens are built
     * on it, so they inset, centre and shrink identically.
     */
    public static final class Page {
        public final LinearLayout root;
        public final LinearLayout header;
        public final ScrollView scroll;
        public final LinearLayout content;
        public final LinearLayout actions;

        Page(LinearLayout root, LinearLayout header, ScrollView scroll,
             LinearLayout content, LinearLayout actions) {
            this.root = root;
            this.header = header;
            this.scroll = scroll;
            this.content = content;
            this.actions = actions;
        }

        /** Add a pill that shares the action row equally with its siblings. */
        public Button addAction(Button button) {
            Context context = actions.getContext();
            if (actions.getChildCount() > 0) {
                View spacer = new View(context);
                actions.addView(spacer, new LinearLayout.LayoutParams(dp(context, 8), 1));
            }
            actions.addView(button, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return button;
        }
    }

    public static Page newPage(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);
        applyPageInsets(root, 20, 12, CONTENT_MAX_WIDTH_DP);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 4));
        root.addView(header);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(context, 12));
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(context, 28));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.setMargins(0, dp(context, 12), 0, 0);
        root.addView(actions, actionsLp);

        return new Page(root, header, scroll, content, actions);
    }

    /** Headline plus supporting line, in a page's header block. */
    public static void addPageTitle(Page page, String titleText, String subtitleText) {
        Context context = page.root.getContext();
        TextView title = new TextView(context);
        title.setText(titleText);
        headline(title);
        page.header.addView(title);

        if (subtitleText != null) {
            TextView subtitle = new TextView(context);
            subtitle.setText(subtitleText);
            body(subtitle);
            subtitle.setPadding(0, dp(context, 6), 0, 0);
            page.header.addView(subtitle);
        }
    }

    /** A full-screen page dialog, styled and edge-to-edge. */
    public static Dialog newPageDialog(Context context, View content) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        styleWindow(dialog, SURFACE, 0f);
        return dialog;
    }

    // --- The settings page: INSTA LUME SETTINGS IA -------------------------------

    private static View buildContent(Context context, Dialog dialog) {
        Config.captureBaseline();
        boolean hardcore = Config.isHardcoreMode();

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);
        applyPageInsets(root, 20, 12, CONTENT_MAX_WIDTH_DP);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 4));
        root.addView(header);

        TextView title = new TextView(context);
        title.setText("INSTA LUME SETTINGS");
        headline(title);
        title.setContentDescription("InstaLume Settings");
        header.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText(hardcore
                ? "InstaLume V1.0.0 by Umaiz Sufiyan · Permanent lock is on."
                : "InstaLume V1.0.0 by Umaiz Sufiyan · Social, reimagined.");
        body(subtitle);
        subtitle.setPadding(0, dp(context, 6), 0, 0);
        header.addView(subtitle);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(context, 12));
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(context, 28));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // SUPPORT
        LinearLayout support = addSection(context, column, "Support");
        addNavRow(context, support, "Support InstaLume", "Open the project page.", () -> openUrl(context, BuildInfo.REPO_URL));
        addNavRow(context, support, "Project Information", BuildInfo.longVersion(), () -> showProjectInfo(context));
        addNavRow(context, support, "Documentation", "Build, privacy, compatibility.", () -> openUrl(context, BuildInfo.DOCS_URL));
        addNavRow(context, support, "Report Issue", "Bugs, fingerprints, IG updates.", () -> openUrl(context, BuildInfo.ISSUES_URL));
        sealGroup(context, support);

        // MODS & CUSTOMIZATION
        LinearLayout mods = addSection(context, column, "Mods & customization");
        addNavRow(context, mods, "Ghost Mode", "Version-dependent privacy controls.", () -> showGhostPage(context));
        addNavRow(context, mods, "Extra Features", "Following feed, display, popups.", () -> showExtraPage(context));
        addNavRow(context, mods, "Themes & UI", "Glass, theme, navigation styling.", () -> showThemePage(context));
        addNavRow(context, mods, "Download Settings", "Version dependent.", () -> showDownloadPage(context));
        addNavRow(context, mods, "Saved Copies", "Version dependent.", () -> showUnavailable(context, "Saved Copies is version dependent in V1.0.0."));
        sealGroup(context, mods);

        // APP CUSTOMIZATION
        LinearLayout app = addSection(context, column, "App customization");
        addNavRow(context, app, "Special Features", "Landing page.", () -> showLandingPage(context));
        addNavRow(context, app, "Miscellaneous", "Motion, compact, cache, URLs.", () -> showMiscPage(context));
        addNavRow(context, app, "Distraction Free", "Blocks, nav icons, swipe guard.", () -> showDistractionPage(context));
        addNavRow(context, app, "Backup & Restore", "JSON export / import / merge.", () -> showBackupPage(context));
        sealGroup(context, app);

        // DEVELOPER
        LinearLayout dev = addSection(context, column, "Developer");
        addNavRow(context, dev, "Developer Options", "Logging, dev settings.", () -> showDevPage(context));
        addNavRow(context, dev, "Debug Information", "Patch + feature status.", () -> showDiagnostics(context));
        addNavRow(context, dev, "Version Compatibility", VersionCompatibility.statusFor(currentIg(context)), () -> showVersionPage(context));
        addNavRow(context, dev, "Patch Diagnostics", "Per-patch report.", () -> showDiagnostics(context));
        sealGroup(context, dev);

        // ABOUT
        LinearLayout about = addSection(context, column, "About");
        addNavRow(context, about, "InstaLume V1.0.0", "Created by Umaiz Sufiyan.", () -> showProjectInfo(context));
        addNavRow(context, about, "Open Source Licenses", "GPLv3 + Morphe + Piko.", () -> showLicenses(context));
        addNavRow(context, about, "Privacy", "No credentials, no telemetry.", () -> showPrivacy(context));
        addNavRow(context, about, "Contact", "GitHub issues.", () -> openUrl(context, BuildInfo.ISSUES_URL));
        sealGroup(context, about);

        Button checkUpdate = makeButton(context, "Check for updates", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        checkUpdate.setOnClickListener(v -> UpdateChecker.checkNow(context));
        column.addView(checkUpdate, stackedButtonParams(context, 12));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.setMargins(0, dp(context, 12), 0, 0);
        root.addView(actions, actionsLp);

        Button lock = makeButton(context, "Permanent lock", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        lock.setOnClickListener(v -> {
            dialog.dismiss();
            confirmHardcore(context);
        });
        if (hardcore) {
            lock.setEnabled(false);
            lock.setAlpha(0.38f);
        }
        actions.addView(lock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View spacer = new View(context);
        actions.addView(spacer, new LinearLayout.LayoutParams(dp(context, 8), 1));

        Button done = makeButton(context, "Done", PRIMARY, ON_PRIMARY, true);
        done.setOnClickListener(v -> {
            dialog.dismiss();
            onDone(context);
        });
        actions.addView(done, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return root;
    }

    private static String currentIg(Context c) {
        try { return VersionCompatibility.installedIgVersion(c); } catch (Throwable t) { return "unknown"; }
    }

    private static void addNavRow(Context context, LinearLayout parent, String label, String support, Runnable onClick) {
        LinearLayout row = makeRow(context, parent, label, support);
        TextView arrow = new TextView(context);
        arrow.setText("›");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        arrow.setTextColor(ON_SURFACE_VARIANT);
        arrow.setContentDescription(label + ", open");
        row.addView(arrow);
        row.setOnClickListener(v -> onClick.run());
    }

    private static void showSubPage(Context context, String titleText, String subtitleText, SubBuilder builder) {
        try {
            Page page = newPage(context);
            addPageTitle(page, titleText, subtitleText);
            builder.build(page.content);
            Dialog d = newPageDialog(context, page.root);
            Button back = makeButton(context, "Back", PRIMARY, ON_PRIMARY, true);
            back.setOnClickListener(v -> d.dismiss());
            page.addAction(back);
            d.setOnCancelListener(x -> {
                if (Config.isRestartPending()) CacheCleaner.clearAndRestart(context);
            });
            d.show();
        } catch (Throwable t) {
            android.widget.Toast.makeText(context, "InstaLume page unavailable here", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    interface SubBuilder { void build(LinearLayout column); }

    private static void showGhostPage(Context context) {
        showSubPage(context, "Ghost Mode", "Only safe controls are enabled. Others are version-dependent.",
            column -> {
                LinearLayout g = addSection(context, column, "Privacy controls");
                addDisabledRow(context, g, "Read status", "Unavailable in V1.0.0 — no safe fingerprint.");
                addDisabledRow(context, g, "Story seen", "Version dependent — no safe fingerprint.");
                addDisabledRow(context, g, "Typing indicator", "Version dependent — no safe fingerprint.");
                addDisabledRow(context, g, "Online / activity", "Version dependent — no safe fingerprint.");
                sealGroup(context, g);
                LinearLayout n = addSection(context, column, "Note");
                addDisabledRow(context, n, "Safety", "No auth bypass, no token logging, no traffic proxy.");
                sealGroup(context, n);
            });
    }

    private static void showExtraPage(Context context, boolean unused) { showExtraPage(context); }
    private static void showExtraPage(Context context) {
        showSubPage(context, "Extra Features", "Reliable runtime toggles.",
            column -> {
                LinearLayout f = addSection(context, column, "Feed & display");
                addRow(context, f, "Following feed only", "limit_following_feed", Config.isFollowingFeedOnly());
                addRow(context, f, "Force dark (disable HDR)", "force_sdr", Config.isForceSdr());
                addRow(context, f, "Instagram popups", "hide_toasts", Config.arePopupsHidden());
                addRow(context, f, "Notifications button", "block_notifications", Config.isNotificationsButtonBlocked());
                addRow(context, f, "Friends in Reels", "block_friends_lane", Config.isFriendsLaneBlocked());
                sealGroup(context, f);
            });
    }

    private static void showThemePage(Context context) {
        showSubPage(context, "Themes & UI", "Persisted. Glass uses fallback where blur is unsafe.",
            column -> {
                LinearLayout a = addSection(context, column, "Appearance");
                buildThemeOptions(context, a);
                sealGroup(context, a);
                LinearLayout g = addSection(context, column, "Liquid Glass");
                addRow(context, g, "Enable Liquid Glass", "glass_enabled", Config.isGlassEnabled());
                buildGlassModeOptions(context, g);
                addStepper(context, g, "Glass opacity", "glass_opacity", Config.getGlassOpacity(), 20, 95, 1);
                addStepper(context, g, "Blur strength", "blur_strength", Config.getBlurStrength(), 0, 40, 1);
                addStepper(context, g, "Corner radius", "corner_radius", Config.getCornerRadius(), 0, 32, 1);
                sealGroup(context, g);
                LinearLayout n = addSection(context, column, "Navigation styling");
                addStepper(context, n, "Navigation size", "nav_size", Config.getNavSize(), 80, 120, 5);
                addStepper(context, n, "Navigation position", "nav_y_offset", Config.getNavYOffset(), -24, 24, 2);
                addStepper(context, n, "Icon size", "icon_size", Config.getIconSize(), 80, 130, 5);
                addStepper(context, n, "Icon spacing", "icon_spacing", Config.getIconSpacing(), 80, 140, 5);
                sealGroup(context, n);
                LinearLayout e = addSection(context, column, "Emoji & type");
                addDisabledRow(context, e, "System emoji", "System emoji is used. Custom rendering: version dependent.");
                sealGroup(context, e);
            });
    }

    private static void buildThemeOptions(Context context, LinearLayout group) {
        String cur = Config.getThemeMode();
        String[] vals = {"system","light","dark"};
        String[] labels = {"System","Light","Dark"};
        for (int i = 0; i < vals.length; i++) {
            final String v = vals[i];
            LinearLayout row = makeRow(context, group, labels[i], null);
            android.widget.RadioButton mark = new android.widget.RadioButton(context);
            mark.setClickable(false); mark.setFocusable(false);
            mark.setButtonTintList(buildStateList(PRIMARY, OUTLINE));
            mark.setChecked(v.equals(cur));
            row.addView(mark);
            row.setOnClickListener(vv -> {
                Config.setThemeMode(v);
                Config.setNeedsRestart();
                showThemePageRefresh(context);
            });
        }
    }

    private static void showThemePageRefresh(Context context) {
        // Simplest reliable refresh: toast + restart pending; user reopens page.
        android.widget.Toast.makeText(context, "Theme saved. Reopen Themes & UI to see selection.", android.widget.Toast.LENGTH_SHORT).show();
    }

    private static void buildGlassModeOptions(Context context, LinearLayout group) {
        String cur = Config.getGlassMode();
        String[][] opts = {{"ios","iOS-style Liquid Glass"},{"universal","Universal Liquid Glass"}};
        for (String[] o : opts) {
            final String v = o[0];
            LinearLayout row = makeRow(context, group, o[1], null);
            android.widget.RadioButton mark = new android.widget.RadioButton(context);
            mark.setClickable(false); mark.setFocusable(false);
            mark.setButtonTintList(buildStateList(PRIMARY, OUTLINE));
            mark.setChecked(v.equals(cur));
            row.addView(mark);
            row.setOnClickListener(vv -> {
                Config.setGlassMode(v);
                Config.setNeedsRestart();
                android.widget.Toast.makeText(context, "Glass mode saved.", android.widget.Toast.LENGTH_SHORT).show();
            });
        }
    }

    private static void addStepper(Context context, LinearLayout parent, String label, String key, int cur, int min, int max, int step) {
        LinearLayout row = makeRow(context, parent, label + " · " + cur, "Range " + min + "–" + max);
        LinearLayout ctl = new LinearLayout(context);
        ctl.setOrientation(LinearLayout.HORIZONTAL);
        Button minus = makeButton(context, "−", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        minus.setMinHeight(dp(context, 40)); minus.setMinimumHeight(dp(context, 40));
        minus.setOnClickListener(v -> {
            int nv = Math.max(min, Math.min(max, Config.intClamped(key, cur, min, max) - step));
            Config.setInt(key, nv);
            Config.setNeedsRestart();
            android.widget.Toast.makeText(context, label + " = " + nv, android.widget.Toast.LENGTH_SHORT).show();
        });
        Button plus = makeButton(context, "+", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        plus.setMinHeight(dp(context, 40)); plus.setMinimumHeight(dp(context, 40));
        plus.setOnClickListener(v -> {
            int nv = Math.max(min, Math.min(max, Config.intClamped(key, cur, min, max) + step));
            Config.setInt(key, nv);
            Config.setNeedsRestart();
            android.widget.Toast.makeText(context, label + " = " + nv, android.widget.Toast.LENGTH_SHORT).show();
        });
        ctl.addView(minus, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 44)));
        View sp = new View(context);
        ctl.addView(sp, new LinearLayout.LayoutParams(dp(context, 8), 1));
        ctl.addView(plus, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 44)));
        row.addView(ctl);
    }

    private static void showDownloadPage(Context context) {
        showSubPage(context, "Download Settings", "No safe media-download fingerprint in V1.0.0.",
            column -> {
                LinearLayout m = addSection(context, column, "Media");
                addDisabledRow(context, m, "Media quality", "Version dependent — not available in this IG version.");
                addDisabledRow(context, m, "Autoplay", "Version dependent — not available in this IG version.");
                addDisabledRow(context, m, "Save destination", "Version dependent — use system share.");
                addRow(context, m, "Auto cache cleaner", "auto_cache_clean", Config.isAutoCacheClean());
                sealGroup(context, m);
            });
    }

    private static void showLandingPage(Context context) {
        showSubPage(context, "Special Features", "Cold-start landing surface.",
            column -> {
                LinearLayout landing = addSection(context, column, "Landing page");
                Runnable[] sync = new Runnable[1];
                buildLandingOptions(context, landing, sync);
                sealGroup(context, landing);
            });
    }

    private static void showMiscPage(Context context) {
        showSubPage(context, "Miscellaneous", "Only working toggles are enabled.",
            column -> {
                LinearLayout m = addSection(context, column, "Interface");
                addRow(context, m, "Reduced motion", "reduced_motion", Config.isReducedMotion());
                addRow(context, m, "Compact UI", "compact_ui", Config.isCompactUi());
                addRow(context, m, "URL sanitization (InstaLume links)", "url_sanitize", Config.isUrlSanitize());
                addRow(context, m, "Distraction-free mode", "distraction_free", Config.isDistractionFree());
                addRow(context, m, "Auto cache cleaner", "auto_cache_clean", Config.isAutoCacheClean());
                sealGroup(context, m);
                LinearLayout v = addSection(context, column, "Version dependent");
                addDisabledRow(context, v, "Follow indicator", "Version dependent.");
                addDisabledRow(context, v, "Story mention visibility", "Version dependent.");
                addDisabledRow(context, v, "Mark-as-seen", "Version dependent.");
                addDisabledRow(context, v, "Chat background", "Version dependent.");
                sealGroup(context, v);
            });
    }

    private static void showDistractionPage(Context context) {
        showSubPage(context, "Distraction Free", "Network blocks + view hiders + swipe guard.",
            column -> {
                final Runnable[] landingSync = new Runnable[1];
                Runnable onNavChanged = () -> { if (landingSync[0] != null) landingSync[0].run(); };
                LinearLayout surfaces = addSection(context, column, "Blocked surfaces");
                addRow(context, surfaces, "Home feed", "block_feed", Config.isFeedBlocked());
                addRow(context, surfaces, "Explore", "block_explore", Config.isExploreBlocked());
                addRow(context, surfaces, "Reels", "block_reels", Config.isReelsBlocked());
                addRow(context, surfaces, "Friends in Reels", "block_friends_lane", Config.isFriendsLaneBlocked());
                addRow(context, surfaces, "Stories", "block_stories", Config.isStoriesBlocked());
                addRow(context, surfaces, "Instants", "block_instants", Config.isInstantsBlocked());
                addRow(context, surfaces, "Notes", "block_notes", Config.isNotesBlocked());
                addRow(context, surfaces, "Suggested accounts", "block_suggested", Config.isSuggestedBlocked());
                addRow(context, surfaces, "Ads", "block_ads", Config.isAdsBlocked());
                sealGroup(context, surfaces);
                LinearLayout nav = addSection(context, column, "Navigation bar");
                addRow(context, nav, "Search", "nav_show_search", Config.getBlocked("nav_show_search", true), onNavChanged);
                addRow(context, nav, "Reels", "nav_show_reels", Config.getBlocked("nav_show_reels", false), onNavChanged);
                addRow(context, nav, "Create", "nav_show_create", Config.getBlocked("nav_show_create", true), onNavChanged);
                addRow(context, nav, "Messages", "nav_show_direct", Config.getBlocked("nav_show_direct", true), onNavChanged);
                addRow(context, nav, "Profile", "nav_show_profile", Config.getBlocked("nav_show_profile", true), onNavChanged);
                sealGroup(context, nav);
            });
    }

    private static void showBackupPage(Context context) {
        showSubPage(context, "Backup & Restore", "JSON schema v1. No passwords or tokens.",
            column -> {
                Button exp = makeButton(context, "Export Backup", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
                exp.setOnClickListener(v -> exportBackup(context, false));
                column.addView(exp, stackedButtonParams(context, 4));
                Button imp = makeButton(context, "Import Backup (overwrite)", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
                imp.setOnClickListener(v -> confirmImport(context, true));
                column.addView(imp, stackedButtonParams(context, 10));
                Button merge = makeButton(context, "Merge without Overwrite", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
                merge.setOnClickListener(v -> confirmImport(context, false));
                column.addView(merge, stackedButtonParams(context, 10));
                Button clearDev = makeButton(context, "Clear Developer Mode Settings", 0, ON_SURFACE, false);
                clearDev.setBackground(outlined(context, OUTLINE_VARIANT));
                clearDev.setOnClickListener(v -> {
                    BackupManager.clearDeveloper(context);
                    android.widget.Toast.makeText(context, "Developer settings cleared.", android.widget.Toast.LENGTH_SHORT).show();
                });
                column.addView(clearDev, stackedButtonParams(context, 10));
                LinearLayout n = addSection(context, column, "Note");
                addDisabledRow(context, n, "Official backup", "Import accepts schema v1 from this file. Legacy FeurStagram keys migrate where safe.");
                sealGroup(context, n);
            });
    }

    private static void exportBackup(Context context, boolean unused) {
        try {
            org.json.JSONObject j = BackupManager.exportJson(context);
            java.io.File dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = context.getFilesDir();
            dir.mkdirs();
            java.io.File out = new java.io.File(dir, "instalume-backup.json");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            fos.write(j.toString(2).getBytes("UTF-8"));
            fos.close();
            android.widget.Toast.makeText(context, "Exported: " + out.getAbsolutePath(), android.widget.Toast.LENGTH_LONG).show();
            try {
                android.content.Intent share = new android.content.Intent(android.content.Intent.ACTION_SEND);
                share.setType("application/json");
                share.putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.fromFile(out));
                share.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(android.content.Intent.createChooser(share, "Share InstaLume backup"));
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            android.widget.Toast.makeText(context, "Export failed: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static void confirmImport(final Context context, final boolean overwrite) {
        showConfirm(context, overwrite ? "Import backup?" : "Merge backup?",
            overwrite ? "This overwrites matching InstaLume settings from instalume-backup.json. Unknown future keys are preserved."
                      : "This fills only missing settings from instalume-backup.json. Existing values stay.",
            overwrite ? "Import" : "Merge", PRIMARY, ON_PRIMARY,
            () -> doImport(context, overwrite));
    }

    private static void doImport(Context context, boolean overwrite) {
        try {
            java.io.File dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = context.getFilesDir();
            java.io.File in = new java.io.File(dir, "instalume-backup.json");
            if (!in.exists()) {
                android.widget.Toast.makeText(context, "No backup found at " + in.getAbsolutePath(), android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            byte[] b = new byte[(int) in.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(in);
            fis.read(b);
            fis.close();
            BackupManager.importJson(context, new String(b, "UTF-8"), overwrite);
            android.widget.Toast.makeText(context, "Backup applied. Restarting…", android.widget.Toast.LENGTH_SHORT).show();
            CacheCleaner.clearAndRestart(context);
        } catch (Throwable t) {
            android.widget.Toast.makeText(context, "Import failed: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static void showDevPage(Context context) {
        showSubPage(context, "Developer Options", "No security bypass controls.",
            column -> {
                LinearLayout d = addSection(context, column, "Debug");
                addRow(context, d, "Enable Debug Logging", "debug_logging", Config.isDebugLogging());
                sealGroup(context, d);
                Button clear = makeButton(context, "Clear Developer Mode Settings", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
                clear.setOnClickListener(v -> {
                    BackupManager.clearDeveloper(context);
                    android.widget.Toast.makeText(context, "Developer settings cleared.", android.widget.Toast.LENGTH_SHORT).show();
                });
                column.addView(clear, stackedButtonParams(context, 10));
                Button diag = makeButton(context, "Export Diagnostic Report", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
                diag.setOnClickListener(v -> exportDiagnostics(context));
                column.addView(diag, stackedButtonParams(context, 10));
            });
    }

    private static void showDiagnostics(Context context) {
        String ig = currentIg(context);
        String report = PatchRegistry.diagnose(ig);
        showConfirm(context, "Patch Diagnostics", report, "Copy IG version", PRIMARY, ON_PRIMARY,
            () -> {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("instalume-diag", report));
                    android.widget.Toast.makeText(context, "Diagnostics copied.", android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
            });
    }

    private static void exportDiagnostics(Context context) {
        try {
            String ig = currentIg(context);
            String report = PatchRegistry.diagnose(ig) + "\nFeatures:\n";
            for (FeatureRegistry.Feature f : FeatureRegistry.all()) {
                report += f.id + " | " + f.status + " | " + f.supportedVersions + "\n";
            }
            // Redaction: never include prefs dump, tokens, or paths.
            java.io.File dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = context.getFilesDir();
            dir.mkdirs();
            java.io.File out = new java.io.File(dir, "instalume-diagnostics.txt");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            fos.write(report.getBytes("UTF-8"));
            fos.close();
            android.widget.Toast.makeText(context, "Diagnostics: " + out.getAbsolutePath(), android.widget.Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            android.widget.Toast.makeText(context, "Diagnostics failed.", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static void showVersionPage(Context context) {
        String ig = currentIg(context);
        showSubPage(context, "Version Compatibility", "Fail-closed on untested IG.",
            column -> {
                LinearLayout p = addSection(context, column, "Patch status");
                addDisabledRow(context, p, "Instagram Version", ig);
                addDisabledRow(context, p, "InstaLume Version", BuildInfo.VERSION + " by " + BuildInfo.CREATOR);
                addDisabledRow(context, p, "Patch Compatibility", VersionCompatibility.statusFor(ig));
                addDisabledRow(context, p, "Patch Count", String.valueOf(PatchRegistry.all().size()));
                addDisabledRow(context, p, "Tested IG Base", BuildInfo.IG_BASE_TESTED);
                sealGroup(context, p);
            });
    }

    private static void showProjectInfo(Context context) {
        showConfirm(context, "InstaLume V1.0.0",
            "Created by Umaiz Sufiyan.\nSocial, reimagined.\n\nIndependent open-source Android customization project. Not affiliated with Meta or Instagram.\n\n" + BuildInfo.FOUNDATION + "\n\nRepo: " + BuildInfo.REPO_URL,
            "Open GitHub", PRIMARY, ON_PRIMARY,
            () -> openUrl(context, BuildInfo.REPO_URL));
    }

    private static void showLicenses(Context context) {
        showConfirm(context, "Open Source Licenses",
            "GPLv3 — see LICENSE.\n\nIncludes: FeurStagram contributors (foundation), Morphe (patcher, Section 7c name restriction), Piko (network/clone techniques). All other InstaLume code is original.",
            "Got it", PRIMARY, ON_PRIMARY, () -> {});
    }

    private static void showPrivacy(Context context) {
        showConfirm(context, "Privacy",
            "InstaLume does not collect, proxy, store, or transmit passwords, tokens, or cookies. Auth stays in Instagram. No analytics. Update check is off by default. Backups never include credentials. Build from source if you prefer.",
            "Got it", PRIMARY, ON_PRIMARY, () -> {});
    }

    private static void showUnavailable(Context context, String msg) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show();
    }

    private static void addDisabledRow(Context context, LinearLayout parent, String label, String support) {
        LinearLayout row = makeRow(context, parent, label, support);
        row.setAlpha(0.62f);
        row.setEnabled(false);
        TextView badge = new TextView(context);
        badge.setText("N/A");
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        badge.setTextColor(ON_SURFACE_VARIANT);
        badge.setContentDescription(label + ", unavailable");
        row.addView(badge);
    }

    private static LinearLayout.LayoutParams stackedButtonParams(Context context, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(context, topDp), 0, 0);
        return lp;
    }

    private static void onDone(Context context) {
        if (context == null) return;
        // A pending change restarts straight away (no dialog to back out of).
        if (Config.isRestartPending()) {
            CacheCleaner.clearAndRestart(context);
            return;
        }
        showConfirm(context, "Restart Instagram?",
                "InstaLume will clear the cache and restart the app to apply your changes.",
                "Restart", PRIMARY, ON_PRIMARY,
                () -> CacheCleaner.clearAndRestart(context));
    }

    private static void confirmHardcore(Context context) {
        if (context == null) return;
        if (Config.isHardcoreMode()) {
            Toast.makeText(context, "Permanent lock is already enabled", Toast.LENGTH_LONG).show();
            return;
        }
        showConfirm(context, "Enable permanent lock?",
                "This is permanent for this installation. You will no longer be able to "
                        + "re-enable Home feed, Explore, Reels or Stories without reinstalling the app.",
                "Enable lock", ERROR, ON_ERROR,
                () -> {
                    Config.enableHardcoreMode();
                    Toast.makeText(context,
                            "Permanent lock enabled. Reinstall the app to unlock content.",
                            Toast.LENGTH_LONG).show();
                });
    }

    // --- Confirmation cards ---------------------------------------------------

    /**
     * A centred card with a title, a body and two actions. Shared by the restart
     * and permanent-lock prompts, and shaped like {@link UpdateChecker}'s cards.
     */
    public static void showConfirm(Context context, String titleText, String bodyText,
                                   String confirmText, int confirmBg, int confirmFg,
                                   Runnable onConfirm) {
        try {
            Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            FrameLayout frame = cardFrame(context);
            LinearLayout card = addCard(context, frame);

            TextView title = new TextView(context);
            title.setText(titleText);
            titleLarge(title);
            card.addView(title);

            // Scrollable, and the one thing allowed to shrink: a long warning must
            // not push the buttons off a short screen or a landscape phone.
            ScrollView bodyScroll = new ScrollView(context);
            bodyScroll.setVerticalScrollBarEnabled(false);
            TextView body = new TextView(context);
            body.setText(bodyText);
            body(body);
            body.setPadding(0, dp(context, 10), 0, 0);
            bodyScroll.addView(body);
            card.addView(bodyScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            boundToFrame(frame, card, bodyScroll);

            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            buttonsLp.setMargins(0, dp(context, 24), 0, 0);
            card.addView(buttons, buttonsLp);

            Button cancel = makeButton(context, "Cancel", 0, ON_SURFACE, false);
            cancel.setBackground(outlined(context, OUTLINE_VARIANT));
            cancel.setOnClickListener(v -> dialog.dismiss());
            buttons.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            View spacer = new View(context);
            buttons.addView(spacer, new LinearLayout.LayoutParams(dp(context, 8), 1));

            Button confirm = makeButton(context, confirmText, confirmBg, confirmFg, true);
            confirm.setOnClickListener(v -> {
                dialog.dismiss();
                onConfirm.run();
            });
            buttons.addView(confirm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            dialog.setContentView(frame);
            dialog.setCanceledOnTouchOutside(true);
            styleWindow(dialog, 0, 0.6f);
            dialog.show();
        } catch (Throwable t) {
            Toast.makeText(context, "Unable to open confirmation", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * The full-window frame a centred card sits in: padded for the system bars and
     * the cutout, so a card never slips under them however the device is held.
     */
    public static FrameLayout cardFrame(Context context) {
        final FrameLayout frame = new FrameLayout(context);
        applyPageInsets(frame, 20, 20, CARD_MAX_WIDTH_DP);
        return frame;
    }

    /** M3 keeps dialogs narrow; past this they stop reading as a dialog. */
    private static final int CARD_MAX_WIDTH_DP = 520;

    /** Add the card itself, centred, to a {@link #cardFrame}. */
    public static LinearLayout addCard(Context context, FrameLayout frame) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(SURFACE_CONTAINER, CORNER_XL, context));
        int pad = dp(context, 24);
        card.setPadding(pad, pad, pad, pad);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        frame.addView(card, lp);
        return card;
    }

    /**
     * Keep {@code card} inside {@code frame} by shrinking {@code growable} — the one
     * child allowed to give up height — when the card would otherwise be taller than
     * the space actually available. This is what lets a long release-notes block
     * survive landscape, a short screen or a three-button navigation bar instead of
     * pushing the buttons off the bottom.
     */
    public static void boundToFrame(final FrameLayout frame, final View card, final View growable) {
        // A global-layout listener, not frame.addOnLayoutChangeListener: the frame
        // fills the window and so never changes bounds, while the things that
        // matter — insets arriving a frame late, the card growing once the notes
        // are measured, a rotation — all show up as ordinary layout passes.
        frame.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int available = frame.getHeight() - frame.getPaddingTop() - frame.getPaddingBottom();
            if (available <= 0 || card.getHeight() == 0) return;
            ViewGroup.LayoutParams lp = growable.getLayoutParams();
            int overflow = card.getHeight() - available;
            if (overflow > 0) {
                int height = Math.max(dp(frame.getContext(), 72), growable.getHeight() - overflow);
                if (lp.height != height) {
                    lp.height = height;
                    growable.setLayoutParams(lp);
                }
            } else if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT
                    && overflow < -dp(frame.getContext(), 8)) {
                // Room came back (rotation, bars hidden): let it grow and re-measure.
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                growable.setLayoutParams(lp);
            }
        });
    }

    private static void openUrl(Context context, String url) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(context, "No browser available", Toast.LENGTH_LONG).show();
        }
    }

    // --- Grouped lists --------------------------------------------------------

    /** A sentence-case section label, M3 Expressive style (not all-caps). */
    private static void addSectionLabel(Context context, LinearLayout parent, String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        label.setTextColor(ON_SURFACE_VARIANT);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setLetterSpacing(0.01f);
        label.setPadding(dp(context, 16), dp(context, 24), dp(context, 16), dp(context, 8));
        parent.addView(label);
    }

    /** Section label plus the container its rows go into. */
    private static LinearLayout addSection(Context context, LinearLayout parent, String text) {
        addSectionLabel(context, parent, text);
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        parent.addView(group);
        return group;
    }

    /**
     * Give a finished group its connected shape: round on the outside, nearly
     * square where rows meet, with a hairline gap between them.
     */
    private static void sealGroup(Context context, LinearLayout group) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View row = group.getChildAt(i);
            float top = i == 0 ? CORNER_L : CORNER_JOINT;
            float bottom = i == count - 1 ? CORNER_L : CORNER_JOINT;
            row.setBackground(ripple(RIPPLE,
                    roundedRect(SURFACE_CONTAINER, context, top, top, bottom, bottom)));
            if (i < count - 1) {
                ViewGroup.LayoutParams lp = row.getLayoutParams();
                if (lp instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) lp).bottomMargin = dp(context, ROW_GAP_DP);
                    row.setLayoutParams(lp);
                }
            }
        }
    }

    /** The shared skeleton of a list row: label, supporting text, trailing control. */
    private static LinearLayout makeRow(Context context, LinearLayout parent, String label, String support) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16));
        row.setMinimumHeight(dp(context, 64));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        labelView.setTextColor(ON_SURFACE);
        texts.addView(labelView);

        if (support != null) {
            TextView supportView = new TextView(context);
            supportView.setText(support);
            supportView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            supportView.setTextColor(ON_SURFACE_VARIANT);
            supportView.setLineSpacing(0f, 1.1f);
            supportView.setPadding(0, dp(context, 2), 0, 0);
            texts.addView(supportView);
        }

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static void addRow(Context context, LinearLayout parent, String label, String key, boolean value) {
        addRow(context, parent, label, key, value, null);
    }

    /**
     * @param onChanged run after the preference is written, for rows another
     *                  section depends on (the nav icons drive the landing picker)
     */
    private static void addRow(Context context, LinearLayout parent, String label, String key,
                               boolean value, Runnable onChanged) {
        LinearLayout row = makeRow(context, parent, label, supportText(key));

        Switch toggle = new Switch(context);
        toggle.setChecked(value);
        toggle.setShowText(false);
        // The platform track drawable is alpha-blended, so tinting it white gives
        // the grey M3 track; the thumb is what carries the accent.
        toggle.setTrackTintList(buildStateList(PRIMARY, OUTLINE_VARIANT));
        toggle.setThumbTintList(buildStateList(PRIMARY, OUTLINE));
        toggle.setOnCheckedChangeListener((btn, isChecked) -> {
            // Hardcore: a frozen surface cannot be revealed; snap it back to hidden.
            if (Config.isHardcoreMode() && Config.isLockable(key)
                    && !Config.hidesSurface(key, isChecked) && Config.wasHiddenAtBaseline(key)) {
                btn.setChecked(!isChecked);
                return;
            }
            Config.setBlocked(key, isChecked);
            Config.setNeedsRestart();
            if (onChanged != null) onChanged.run();
        });
        row.addView(toggle);
        row.setOnClickListener(v -> {
            if (toggle.isEnabled()) toggle.toggle();
        });

        // Freeze rows whose surface is already hidden under the permanent lock.
        if (Config.isLockable(key) && Config.isHardcoreMode() && Config.hidesSurface(key, value)) {
            toggle.setEnabled(false);
            row.setEnabled(false);
            row.setClickable(false);
            row.setAlpha(0.38f);
        }
    }

    private static String supportText(String key) {
        if (key.equals("auto_update")) return "Check GitHub for a new version on launch.";
        if (key.equals("block_ads")) return "Block sponsored ads across Instagram.";
        if (key.equals("limit_following_feed")) return "Show only accounts you follow (needs the feed unblocked).";
        if (key.equals("hide_toasts")) return "Hide every Instagram popup, including “couldn’t refresh feed”.";
        if (key.equals("force_sdr")) return "Keep blacks deep by stopping Instagram forcing HDR on the UI.";
        if (key.equals("block_friends_lane")) return "Hide the Friends tab and its avatars in the Reels header.";
        if (key.equals("block_notifications")) return "Hide the notifications (heart) button in the feed header.";
        if (key.startsWith("nav_show_")) return "Show this icon in the navigation bar.";
        return "Hide this surface in Instagram.";
    }

    // --- Landing page picker --------------------------------------------------

    private static final String[] LANDING_VALUES = {"home", "search", "direct", "profile"};
    private static final String[] LANDING_LABELS = {"Home feed", "Search", "Direct messages", "Profile"};

    /**
     * @param landingSync out-parameter: receives a runnable that re-checks which
     *                    options are still selectable, for the nav rows to call
     *                    when they hide or show a tab
     */
    private static void buildLandingOptions(Context context, LinearLayout group, Runnable[] landingSync) {
        final List<View> rows = new ArrayList<>();
        final List<RadioButton> marks = new ArrayList<>();

        for (int i = 0; i < LANDING_VALUES.length; i++) {
            final String value = LANDING_VALUES[i];
            LinearLayout row = makeRow(context, group, LANDING_LABELS[i], null);

            RadioButton mark = new RadioButton(context);
            mark.setClickable(false);
            mark.setFocusable(false);
            mark.setButtonTintList(buildStateList(PRIMARY, OUTLINE));
            row.addView(mark);

            rows.add(row);
            marks.add(mark);

            row.setOnClickListener(v -> {
                if (!Config.isLandingAvailable(value)) return;
                Config.setLandingPage(value);
                Config.setNeedsRestart();
                if (landingSync[0] != null) landingSync[0].run();
            });
        }

        // A surface whose nav icon is hidden is not a landing page you can choose:
        // it would hand back exactly what was hidden, and under the permanent lock
        // the picker was the one way left to reach a frozen surface (issue #116).
        landingSync[0] = () -> {
            String current = Config.getLandingPage();
            for (int i = 0; i < LANDING_VALUES.length; i++) {
                boolean available = Config.isLandingAvailable(LANDING_VALUES[i]);
                View row = rows.get(i);
                row.setEnabled(available);
                row.setAlpha(available ? 1f : 0.38f);
                marks.get(i).setChecked(LANDING_VALUES[i].equals(current));
            }
        };
        landingSync[0].run();
    }

    // --- Type, shape and control helpers --------------------------------------

    /** The page headline: large, tight-tracked, medium weight. */
    public static void headline(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f);
        view.setTextColor(ON_SURFACE);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(-0.02f);
    }

    /** A card or dialog title. */
    public static void titleLarge(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        view.setTextColor(ON_SURFACE);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(-0.01f);
    }

    /** Supporting text. */
    public static void body(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setTextColor(ON_SURFACE_VARIANT);
        view.setLineSpacing(0f, 1.15f);
    }

    public static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id == 0 ? 0 : context.getResources().getDimensionPixelSize(id);
    }

    public static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable roundedRect(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** Per-corner variant, for the connected list groups. */
    public static GradientDrawable roundedRect(int color, Context context,
                                               float topLeftDp, float topRightDp,
                                               float bottomRightDp, float bottomLeftDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float tl = dp(context, topLeftDp);
        float tr = dp(context, topRightDp);
        float br = dp(context, bottomRightDp);
        float bl = dp(context, bottomLeftDp);
        drawable.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        return drawable;
    }

    /** A transparent pill with a hairline border, for low-emphasis buttons. */
    public static Drawable outlined(Context context, int strokeColor) {
        GradientDrawable shape = roundedRect(Color.TRANSPARENT, CORNER_FULL, context);
        shape.setStroke(dp(context, 1), strokeColor);
        return ripple(RIPPLE, shape);
    }

    public static Drawable ripple(int color, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(color), content, null);
    }

    public static ColorStateList buildStateList(int checkedColor, int uncheckedColor) {
        int[][] states = {
                new int[]{android.R.attr.state_checked},
                new int[]{},
        };
        int[] colors = {checkedColor, uncheckedColor};
        return new ColorStateList(states, colors);
    }

    /** A pill button. Expressive metrics: 52dp tall, generous horizontal padding. */
    public static Button makeButton(Context context, String text, int bgColor, int textColor, boolean filled) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setLetterSpacing(0f);
        button.setMinHeight(dp(context, 52));
        button.setMinimumHeight(dp(context, 52));
        button.setPadding(dp(context, 20), 0, dp(context, 20), 0);
        button.setStateListAnimator(null);
        button.setBackground(filled
                ? ripple(RIPPLE, roundedRect(bgColor, CORNER_FULL, context))
                : ripple(RIPPLE, roundedRect(Color.TRANSPARENT, CORNER_FULL, context)));
        return button;
    }

    public static View makeDivider(Context context, int color) {
        View divider = new View(context);
        divider.setBackgroundColor(color);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return divider;
    }
}
