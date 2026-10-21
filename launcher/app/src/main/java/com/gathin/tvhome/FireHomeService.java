package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS keeps Home: com.amazon.tv.launcher is protected (pm disable-user is refused) and
 * set-home-activity is ignored. This service makes this launcher the Home people see anyway: whenever
 * Amazon's home activity comes to the front (boot, the Home button, an app exiting), it brings this
 * launcher straight back. Only the home activity itself is covered, so voice search results and
 * content pages that the Amazon launcher opens in other activities still show.
 *
 * On Fire OS 7 the top Settings grid is a pane of that same home activity (android.settings.SETTINGS
 * goes through com.amazon.tv.settings.v2/.compliance.CTSDummySettingsHandler and resumes
 * HomeActivity_vNext). So opening Settings from this launcher calls {@link #allowAmazonSettings()},
 * and the grid stays reachable while the user moves between it and the Settings pages
 * (com.amazon.tv.settings.v2).
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.FireHomeService
 *   settings put secure accessibility_enabled 1
 */
public class FireHomeService extends AccessibilityService {

    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    private static final String AMAZON_SETTINGS = "com.amazon.tv.settings.v2";
    /** How long after Settings was opened here the Amazon home activity may come up as the Settings grid. */
    private static final long SETTINGS_GRACE_MS = 8000;

    private static volatile long settingsAllowedUntil;

    /** Packages whose windows sit on top of whatever is in front without replacing it. */
    private static boolean isOverlay(String pkg) {
        return pkg.equals("com.android.systemui") || pkg.equals("com.amazon.tv.ime") || pkg.equals("android");
    }

    /** Call right before opening the system Settings, which on Fire OS 7 is the Amazon launcher's grid. */
    static void allowAmazonSettings() {
        settingsAllowedUntil = SystemClock.uptimeMillis() + SETTINGS_GRACE_MS;
    }

    private boolean inSettings;
    private long lastLaunch;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkgName = event.getPackageName();
        if (pkgName == null) return;
        String pkg = pkgName.toString();
        if (isOverlay(pkg) || pkg.equals(AMAZON_SETTINGS)) return;
        if (!pkg.equals(AMAZON_LAUNCHER)) {
            // Any app (this launcher included) in front ends a Settings visit.
            inSettings = false;
            return;
        }
        if (SystemClock.uptimeMillis() < settingsAllowedUntil) {
            inSettings = true;
            return;
        }
        // Back from a Settings page lands on the grid again; that's still the Settings visit.
        if (inSettings) return;
        CharSequence cls = event.getClassName();
        if (cls != null && AMAZON_HOME.contentEquals(cls)) openHome();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Fire OS may not pass Home to accessibility services at all; where it does, catch it here so
        // Amazon's home never shows, including from inside the Settings grid.
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            inSettings = false;
            openHome();
        }
        return true;
    }

    private void openHome() {
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < 600) return;   // one launch per Home press or launcher flash
        lastLaunch = now;
        startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    @Override
    public void onInterrupt() {
    }
}
