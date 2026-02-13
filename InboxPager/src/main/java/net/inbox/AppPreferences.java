/*
 * InboxPager, an android email client.
 * Copyright (C) 2016-2026  ITPROJECTS
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **/
package net.inbox;

import static net.inbox.Common.set_activity_insets_listener;

import androidx.appcompat.widget.Toolbar;

import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.fragment.app.FragmentActivity;

import android.view.View;
import android.view.WindowManager;

import net.inbox.pager.R;

public class AppPreferences extends FragmentActivity {

    @Override
    protected void onCreate(Bundle saved_instance_state) {
        super.onCreate(saved_instance_state);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );

        // For camera cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) // Android API >= 35
            EdgeToEdge.enable(this); // run before setContentView()

        setContentView(R.layout.app_preferences);

        Toolbar toolbar = findViewById(R.id.app_preferences_toolbar);
        //setSupportActionBar(toolbar);
        //getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Load the Preferences Fragment
        if (saved_instance_state == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.app_preferences_container, new AppPreferencesFragment()).commit();
        }

        View main_root = getWindow().getDecorView().getRootView(); // after commit above

        // Handle insets for system bars
        set_activity_insets_listener(main_root);
    }

    @Override
    public void finish() {
        super.finish();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.left_in, R.anim.left_out);
        } else { // Android <= 33
            overridePendingTransition(R.anim.left_in, R.anim.left_out);
        }
    }
}
