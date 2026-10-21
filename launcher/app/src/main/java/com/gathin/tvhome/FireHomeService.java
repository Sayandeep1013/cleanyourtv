package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;

/**
 * Fire OS keeps Home: com.amazon.tv.launcher is protected (pm disable-user is refused) and
 * set-home-activity is ignored. This service makes this launcher the Home people see anyway: whenever
 * Amazon's home activity comes to the front (boot, the Home button, an app exiting), it brings this
 * launcher straight back. Only the home activity itself is covered, so voice search results and
 * content pages that the Amazon launcher opens in other activities still show.
 *
 * After the Home button Android 9 holds back activity starts from apps in the background for about
 * five seconds (app switch protection; only the system may lift it), so the launcher arrives late.
 * A curtain in this launcher's colours covers Amazon's home at once and eats remote presses until
 * the launcher is in front; accessibility overlays are not held back.
 *
 * On Fire OS 7 the top Settings grid is a pane of that same home activity (android.settings.SETTINGS
 * goes through com.amazon.tv.settings.v2/.compliance.CTSDummySettingsHandler and resumes
 * HomeActivity_vNext). The Settings pill opens QuickSettingsActivity's Fire OS pages instead; where
 * the grid is still asked for, {@link #allowAmazonSettings()} lets it through, and it stays reachable
 * while the user moves between it and the Settings pages (com.amazon.tv.settings.v2).
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.FireHomeService
 *   settings put secure accessibility_enabled 1
 */
public class FireHomeService extends AccessibilityService {

    private static final String TAG = "TvHome.FireHome";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    private static final String AMAZON_SETTINGS = "com.amazon.tv.settings.v2";
    /** How long after Settings was opened here the Amazon home activity may come up as the Settings grid. */
    private static final long SETTINGS_GRACE_MS = 8000;
    /** The curtain comes down by itself after this, in case the launcher never arrives. */
    private static final long CURTAIN_MAX_MS = 8000;
    /** Time for the launcher to draw its first frame before the curtain lifts. */
    private static final long CURTAIN_LIFT_DELAY_MS = 300;

    private static volatile long settingsAllowedUntil;

    /** True on Fire OS, where the Amazon launcher keeps Home. */
    static boolean isFireOs() {
        return "Amazon".equalsIgnoreCase(Build.MANUFACTURER);
    }

    /** Packages whose windows sit on top of whatever is in front without replacing it. */
    private static boolean isOverlay(String pkg) {
        return pkg.equals("com.android.systemui") || pkg.equals("com.amazon.tv.ime") || pkg.equals("android");
    }

    /** Call right before opening the system Settings, which on Fire OS 7 is the Amazon launcher's grid. */
    static void allowAmazonSettings() {
        settingsAllowedUntil = SystemClock.uptimeMillis() + SETTINGS_GRACE_MS;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable liftCurtain = this::removeCurtain;
    private View curtain;
    private boolean inSettings;
    private long lastLaunch;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkgName = event.getPackageName();
        if (pkgName == null) return;
        String pkg = pkgName.toString();
        if (isOverlay(pkg)) return;
        if (pkg.equals(AMAZON_SETTINGS)) {
            removeCurtain();
            return;
        }
        if (!pkg.equals(AMAZON_LAUNCHER)) {
            // Any app (this launcher included) in front ends a Settings visit and lifts the curtain.
            inSettings = false;
            if (curtain != null) {
                handler.removeCallbacks(liftCurtain);
                handler.postDelayed(liftCurtain, pkg.equals(getPackageName()) ? CURTAIN_LIFT_DELAY_MS : 0);
            }
            return;
        }
        if (SystemClock.uptimeMillis() < settingsAllowedUntil) {
            inSettings = true;
            return;
        }
        // Back from a Settings page lands on the grid again; that's still the Settings visit.
        if (inSettings) return;
        CharSequence cls = event.getClassName();
        if (cls != null && AMAZON_HOME.contentEquals(cls)) {
            showCurtain();
            openHome();
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Fire OS 7 does not pass Home to accessibility services (checked on AFTMV00); where a build
        // does, catch it here so Amazon's home never shows, including from inside the Settings grid.
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
        Log.i(TAG, "Amazon home in front, opening this launcher");
        startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    /** Covers the screen with the launcher's background and clock, and takes the remote's keys. */
    private void showCurtain() {
        handler.removeCallbacks(liftCurtain);
        handler.postDelayed(liftCurtain, CURTAIN_MAX_MS);
        if (curtain != null) return;
        Config cfg = Config.load(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(cfg.backgroundColor | 0xFF000000);
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView time = new TextView(this);
        time.setText(DateFormat.getTimeFormat(this).format(new Date()));
        time.setTextColor(0xFFFFFFFF);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 96);
        time.setTypeface(getResources().getFont(R.font.outfit_extralight));
        TextView date = new TextView(this);
        date.setText(DateFormat.format("EEEE d MMMM", new Date()).toString().toUpperCase());
        date.setTextColor(0xB3FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        date.setLetterSpacing(0.2f);
        date.setTypeface(getResources().getFont(R.font.outfit_light));
        clock.addView(time);
        clock.addView(date);
        root.addView(clock, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        // Focusable, so remote presses during the wait land here instead of on Amazon's tiles.
        root.setFocusable(true);
        root.setOnKeyListener((v, keyCode, e) -> true);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE);
        try {
            getSystemService(WindowManager.class).addView(root, lp);
            curtain = root;
            root.requestFocus();
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not show the curtain", e);
        }
    }

    private void removeCurtain() {
        handler.removeCallbacks(liftCurtain);
        if (curtain == null) return;
        try {
            getSystemService(WindowManager.class).removeView(curtain);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not remove the curtain", e);
        }
        curtain = null;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        removeCurtain();
        super.onDestroy();
    }
}
