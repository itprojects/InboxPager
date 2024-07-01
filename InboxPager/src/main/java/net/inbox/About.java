/*
 * InboxPager, an android email client.
 * Copyright (C) 2016-2024  ITPROJECTS
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

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import net.inbox.pager.R;
import net.inbox.visuals.Dialogs;
import net.sqlcipher.BuildConfig;

public class About extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.about);

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
        tv_ver.setText(BuildConfig.VERSION_NAME);

        TextView tv_app_license = findViewById(R.id.tv_app_license);
        tv_app_license.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Dialogs.dialog_simple(
                        getString(R.string.dialog_license),
                        getString(R.string.license_long),
                        (AppCompatActivity) v.getContext());
            }
        });

        TextView tv_app_icon = findViewById(R.id.tv_app_icon);
        tv_app_icon.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Dialogs.dialog_simple(
                        getString(R.string.dialog_license),
                        getString(R.string.cc_by_sa_link),
                        (AppCompatActivity) v.getContext());
            }
        });

        TextView tv_font = findViewById(R.id.tv_font);
        tv_font.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Dialogs.dialog_simple(
                        getString(R.string.dialog_license),
                        getString(R.string.license_long_font),
                        (AppCompatActivity) v.getContext());
            }
        });

        TextView tv_open_keychain = findViewById(R.id.tv_open_keychain);
        tv_open_keychain.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Dialogs.dialog_simple(
                        getString(R.string.dialog_license),
                        getString(R.string.license_long_open_keychain),
                        (AppCompatActivity) v.getContext());
            }
        });

        TextView tv_apache_foundation = findViewById(R.id.tv_apache_foundation);
        tv_apache_foundation.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Dialogs.dialog_simple(
                        getString(R.string.dialog_license),
                        getString(R.string.license_long_apache_foundation),
                        (AppCompatActivity) v.getContext());
            }
        });

        TextView tv_sql_cipher_java = findViewById(R.id.tv_sql_cipher_java);
        tv_sql_cipher_java.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Dialogs.dialog_simple(
                        getString(R.string.dialog_license),
                        getString(R.string.license_long_sql_cipher_java),
                        (AppCompatActivity) v.getContext());
            }
        });

        TextView tv_sql_cipher_other = findViewById(R.id.tv_sql_cipher_other);
        tv_sql_cipher_other.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Dialogs.dialog_simple(
                        getString(R.string.dialog_license),
                        getString(R.string.license_long_sql_cipher_other),
                        (AppCompatActivity) v.getContext());
            }
        });

        TextView tv_gnu_crypto = findViewById(R.id.tv_gnu_crypto);
        tv_gnu_crypto.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Dialogs.dialog_simple(
                        getString(R.string.dialog_license),
                        getString(R.string.license_long_gnu_cryoto),
                        (AppCompatActivity) v.getContext());
            }
        });

        TextView tv_stack_of = findViewById(R.id.tv_stack_of);
        tv_stack_of.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Dialogs.dialog_simple(
                        getString(R.string.dialog_license),
                        getString(R.string.cc_by_sa_link),
                        (AppCompatActivity) v.getContext());
            }
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.left_in, R.anim.left_out);
    }
}
