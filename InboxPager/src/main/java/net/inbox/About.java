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

import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.inbox.pager.R;
import net.inbox.visuals.Dialogs;

public class About extends AppCompatActivity {

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

        setContentView(R.layout.about);

        LinearLayout main_root = findViewById(R.id.root_view_about);

        Toolbar tb = findViewById(R.id.about_toolbar);
        setSupportActionBar(tb);

        // Find the title
        TextView about_title = tb.findViewById(R.id.about_title);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(false);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            about_title.setText(getString(R.string.menu_about).toUpperCase());
        }

        TextView tv_ver = findViewById(R.id.about_version);
        tv_ver.setText(net.inbox.pager.BuildConfig.VERSION_NAME);

        TextView tv_app_license = findViewById(R.id.tv_app_license);
        tv_app_license.setOnClickListener(
            v -> Dialogs.dialog_simple(
                getString(R.string.dialog_license),
                getString(R.string.license_long),
                (AppCompatActivity) v.getContext()
            )
        );

        TextView tv_app_icon = findViewById(R.id.tv_app_icon);
        tv_app_icon.setOnClickListener(
            v -> Dialogs.dialog_simple(
                getString(R.string.dialog_license),
                getString(R.string.cc_by_sa_link),
                (AppCompatActivity) v.getContext()
            )
        );

        TextView tv_font = findViewById(R.id.tv_font);
        tv_font.setOnClickListener(
            v -> Dialogs.dialog_simple(
                getString(R.string.dialog_license),
                getString(R.string.license_long_font),
                (AppCompatActivity) v.getContext()
            )
        );

        TextView tv_open_keychain = findViewById(R.id.tv_open_keychain);
        tv_open_keychain.setOnClickListener(
            v -> Dialogs.dialog_simple(
                getString(R.string.dialog_license),
                getString(R.string.license_long_open_keychain),
                (AppCompatActivity) v.getContext()
            )
        );

        TextView tv_apache_foundation = findViewById(R.id.tv_apache_foundation);
        tv_apache_foundation.setOnClickListener(
            v -> Dialogs.dialog_simple(
                getString(R.string.dialog_license),
                getString(R.string.license_long_apache_foundation),
                (AppCompatActivity) v.getContext()
            )
        );

        TextView tv_sql_cipher_java = findViewById(R.id.tv_sql_cipher_java);
        tv_sql_cipher_java.setOnClickListener(
            v -> Dialogs.dialog_simple(
                getString(R.string.dialog_license),
                getString(R.string.license_long_sql_cipher_java),
                (AppCompatActivity) v.getContext()
            )
        );

        TextView tv_sql_cipher_other = findViewById(R.id.tv_sql_cipher_other);
        tv_sql_cipher_other.setOnClickListener(
            v -> Dialogs.dialog_simple(
                getString(R.string.dialog_license),
                getString(R.string.license_long_sql_cipher_other),
                (AppCompatActivity) v.getContext()
            )
        );

        TextView tv_gnu_crypto = findViewById(R.id.tv_gnu_crypto);
        tv_gnu_crypto.setOnClickListener(
            v -> Dialogs.dialog_simple(
                getString(R.string.dialog_license),
                getString(R.string.license_long_gnu_cryoto),
                (AppCompatActivity) v.getContext()
            )
        );

        TextView tv_stack_of = findViewById(R.id.tv_stack_of);
        tv_stack_of.setOnClickListener(
            v -> Dialogs.dialog_simple(
                getString(R.string.dialog_license),
                getString(R.string.cc_by_sa_link),
                (AppCompatActivity) v.getContext()
            )
        );

        // Handle insets for cutout and system bars
        set_activity_insets_listener(main_root);
    }

    @Override
    public void finish() {
        super.finish();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE, R.anim.left_in, R.anim.left_out
            );
        } else { // Android API <= 33
            overridePendingTransition(R.anim.left_in, R.anim.left_out);
        }
    }
}
