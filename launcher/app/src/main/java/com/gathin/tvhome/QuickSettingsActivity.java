package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService):
 * a few glass rows under the Settings pill, over Home or whatever app is playing. Translucent and in a
 * task of its own, so the app behind keeps running and Back returns to it. Pressing Settings again
 * closes the panel.
 */
public final class QuickSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);
        if (FireHomeService.isFireOs()) {
            // Fire OS 7's top Settings grid is a pane of Amazon's home (see FireHomeService); its pages are
            // their own activities in com.amazon.tv.settings.v2, so list those instead.
            addRow(rows, R.drawable.ic_wifi, R.string.quick_network, 0, new Intent(Settings.ACTION_WIFI_SETTINGS));
            addRow(rows, R.drawable.ic_picture, R.string.fire_display_sounds, 0,
                    new Intent("com.amazon.device.settings.action.DISPLAY_AND_SOUNDS"));
            addRow(rows, R.drawable.ic_apps, R.string.fire_applications, 0,
                    new Intent("com.amazon.device.settings.action.APPLICATIONS"));
            addRow(rows, R.drawable.ic_input, R.string.fire_controllers, 0,
                    new Intent("com.amazon.device.settings.action.CONTROLLERS"));
            addRow(rows, R.drawable.ic_settings, R.string.fire_device, 0,
                    new Intent("com.amazon.device.settings.action.DEVICE"));
            addRow(rows, R.drawable.ic_lock, R.string.fire_preferences, 0,
                    new Intent("com.amazon.device.settings.action.PREFERENCES"));
            addRow(rows, R.drawable.ic_av, R.string.fire_accessibility, 0,
                    new Intent("com.amazon.device.settings.action.ACCESSIBILITY"));
            addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                    new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
            rows.getChildAt(0).requestFocus();
            return;
        }
        addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, new Intent(Settings.ACTION_SETTINGS));
        addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        addRow(rows, R.drawable.ic_wifi, R.string.quick_network, 0, new Intent(Settings.ACTION_WIFI_SETTINGS));
        addRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        rows.getChildAt(0).requestFocus();
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hint = row.findViewById(R.id.status);
        if (status != 0) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            if (Settings.ACTION_SETTINGS.equals(target.getAction())) FireHomeService.allowAmazonSettings();
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        rows.addView(row);
    }
}
