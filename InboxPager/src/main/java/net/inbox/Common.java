/*
 * InboxPager, an android email client.
 * Copyright (C) 2024-2026  ITPROJECTS
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

import static net.inbox.InboxPager.vib;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.AndroidRuntimeException;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;

import net.inbox.pager.R;
import net.inbox.visuals.Dialogs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class Common {

    private final static int granted = PackageManager.PERMISSION_GRANTED;

    private final static int i_perm_r = Intent.FLAG_GRANT_READ_URI_PERMISSION;
    private final static int i_perm_w = Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private final static String s_perm_r = "android.permission.READ_EXTERNAL_STORAGE";
    private final static String s_perm_w = "android.permission.WRITE_EXTERNAL_STORAGE";

    public static int open_key_chain_status = -1; // -1 not set, 0 no gpg, 1 gpg available
    public static String open_key_chain_package = "org.sufficientlysecure.keychain";

    public static int webview_status = -1; // -1 not set, 0 no WebView, 1 WebView available

    private static SharedPreferences prefs;

    public static void notify_update(String msg, Context ctx) {
        if (prefs.getBoolean("vibrates", false))
            vib.vibrate(1000);

        if (prefs.getBoolean("beeps", false) && check_notification_give(ctx)) {
            if (ActivityCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
            ) return;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                ctx, ctx.getPackageName()
            ).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.application)
                .setContentTitle(ctx.getString(R.string.app_name))
                .setContentText(msg);

            // Notification with unique time-based id
            NotificationManagerCompat.from(ctx).notify(
                (int)(System.currentTimeMillis() % 100000), builder.build()
            );
        }
    }

    public static void set_prefs(SharedPreferences p) {
        prefs = p;
    }

    private static boolean check_notification_give(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android >= 33
            // Denied
            return ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED; // Granted
        } else {
            // Granted for Android API 23 to 32 at install time
            // Check if user-enabled
            return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
        }
    }

    // Check Uri has runtime permission to read file
    public static void check_read_give(AppCompatActivity act, Uri uri) {
        if (act.checkCallingOrSelfUriPermission(uri, i_perm_r) == granted) {
            act.grantUriPermission(act.getApplicationContext().getPackageName(), uri, i_perm_r);
        }
    }

    // Check Uri has runtime permission to read file
    public static void check_write_give(AppCompatActivity act, Uri uri) {
        if (act.checkCallingOrSelfUriPermission(uri, i_perm_w) == granted) {
            act.grantUriPermission(act.getApplicationContext().getPackageName(), uri, i_perm_w);
        } else {
            InboxPager.log = InboxPager.log.concat(
                act.getString(R.string.err_missing_permissions) + " " + uri + "\n\n"
            );
        }
    }

    // Check application storage permissions
    public static boolean check_permissions(AppCompatActivity act, boolean read_only) {
        if (Build.VERSION.SDK_INT >= 32) { // Android >= 12
            // Special MANAGE_EXTERNAL_STORAGE permission check
            return Environment.isExternalStorageManager();
        } else { // Android <= 10
            if (read_only) {
                return act.checkCallingOrSelfPermission(s_perm_r) == granted;
            } else {
                return ((act.checkCallingOrSelfPermission(s_perm_r) == granted))
                        && ((act.checkCallingOrSelfPermission(s_perm_w) == granted));
            }
        }
    }

    // Looks for available and supported encryption packages (OpenKeychain for GPG)
    public static boolean is_gpg_available(Context ctx) {
        if (open_key_chain_status == -1) {
            try {
                PackageInfo package_info = ctx.getPackageManager().getPackageInfo(
                    open_key_chain_package, 0
                );
                if (package_info.applicationInfo != null && package_info.applicationInfo.enabled)
                    open_key_chain_status = 1;
                else
                    open_key_chain_status = 0;
            } catch (PackageManager.NameNotFoundException e) {
                InboxPager.log = InboxPager.log.concat(
                    ctx.getString(R.string.err_missing_crypto_mime) + "\n\n"
                );
                Dialogs.toaster(false, ctx.getString(R.string.open_pgp_none_found), ctx);
                open_key_chain_status = 0;
            }
        }

        return open_key_chain_status == 1;
    }

    // Looks for Android WebView package
    public static boolean is_webview_available(Context ctx) {
        if (webview_status == -1) {
            try{
                CookieManager.getInstance(); // no cookies, no WebView!
                webview_status = 1;
            } catch(AndroidRuntimeException e) {
                InboxPager.log = InboxPager.log.concat(
                    ctx.getString(R.string.err_missing_webview) + "\n\n"
                );
                webview_status = 0;
            }
        }

        return webview_status == 1;
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
        web_settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web_settings.setSaveFormData(false);
        web_settings.setGeolocationEnabled(false);
        web_settings.setSupportZoom(true);
    }

    public static void set_activity_insets_listener(View main_root) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) { // Android API >= 35
            main_root.setOnApplyWindowInsetsListener(
                (v, window_insets) -> {
                    DisplayCutout cutout = window_insets.getDisplayCutout();
                    android.graphics.Insets sys_bars = window_insets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                    );
                    if (cutout != null) {
                        v.setPadding(
                            sys_bars.left + cutout.getSafeInsetLeft(),
                            sys_bars.top, // cutout.getSafeInsetTop()
                            sys_bars.right + cutout.getSafeInsetRight(),
                            sys_bars.bottom + cutout.getSafeInsetBottom()
                        );
                    } else {
                        v.setPadding(sys_bars.left, sys_bars.top, sys_bars.right, sys_bars.bottom);
                    }
                    return WindowInsets.CONSUMED;
                }
            );
        }
    }

    public static void fixed_or_rotating_orientation(boolean fixed_or_rotating, Activity act) {
        if (fixed_or_rotating)
            act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        else
            act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    public static String sha256(Context ctx, String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex_text = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hex_text.append('0');
                hex_text.append(hex);
            }
            return hex_text.toString();
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(
                ctx.getString(R.string.err_bad_hash) + "\n\n"
            );
            Dialogs.toaster(false, ctx.getString(R.string.err_bad_hash), ctx);
        }
        return "";
    }
}
