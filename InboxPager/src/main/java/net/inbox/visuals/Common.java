/*
 * InboxPager, an android email client.
 * Copyright (C) 2020  ITPROJECTS
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
package net.inbox.visuals;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateInterpolator;
import android.webkit.WebSettings;

import androidx.appcompat.app.AppCompatActivity;

public class Common {

    private final static int granted = PackageManager.PERMISSION_GRANTED;

    private final static int i_perm_r = Intent.FLAG_GRANT_READ_URI_PERMISSION;
    private final static int i_perm_w = Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private final static String s_perm_r = "android.permission.READ_EXTERNAL_STORAGE";
    private final static String s_perm_w = "android.permission.WRITE_EXTERNAL_STORAGE";

    // Circular reveal animation of activity intent.
    public static void animation_in(AppCompatActivity a, View current_layout) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        float circle_radius = (float) (Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels));
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(current_layout,
                displayMetrics.widthPixels/2,
                displayMetrics.heightPixels/2,
                0, circle_radius);
        circularReveal.setDuration(420);
        circularReveal.setInterpolator(new AccelerateInterpolator());
        current_layout.setVisibility(View.VISIBLE);
        circularReveal.start();
    }

    // Circular reveal animation of activity intent.
    public static void animation_out(final AppCompatActivity a, final View current_layout) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        float circle_radius = (float) (Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels));
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(current_layout,
                displayMetrics.widthPixels/2,
                displayMetrics.heightPixels/2,
                circle_radius, 0);
        circularReveal.setDuration(420);
        circularReveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                current_layout.setVisibility(View.INVISIBLE);
                a.finish();
            }
        });
        circularReveal.start();
    }

    // Check Uri has runtime permission to read file
    public static void check_read_give(AppCompatActivity act, Uri uri) {
        if (act.checkCallingOrSelfUriPermission(uri, i_perm_r) == granted)
            act.grantUriPermission(act.getCallingPackage(), uri, i_perm_r);
    }

    // Check Uri has runtime permission to read file
    public static void check_write_give(AppCompatActivity act, Uri uri) {
        if (act.checkCallingOrSelfUriPermission(uri, i_perm_w) == granted)
            act.grantUriPermission(act.getCallingPackage(), uri, i_perm_w);
    }

    // Check application storage permissions
    public static boolean check_permissions(AppCompatActivity act) {
        return ((act.checkCallingOrSelfPermission(s_perm_r) == granted)) &&
                ((act.checkCallingOrSelfPermission(s_perm_w) == granted));
    }

    // Sandbox WebView, prepare for use.
    public static void setup_webview(WebSettings web_settings, float font_size) {
        web_settings.setDefaultFontSize((int) font_size);
        web_settings.setDefaultFixedFontSize((int) font_size);
        web_settings.setAllowFileAccess(false);
        web_settings.setLoadsImagesAutomatically(false);
        web_settings.setDatabaseEnabled(false);
        web_settings.setBlockNetworkImage(true);
        web_settings.setBlockNetworkLoads(true);
        web_settings.setJavaScriptEnabled(false);
        web_settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web_settings.setAppCacheEnabled(false);
        web_settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web_settings.setSaveFormData(false);
        web_settings.setGeolocationEnabled(false);
        web_settings.setSupportZoom(true);
    }
}
