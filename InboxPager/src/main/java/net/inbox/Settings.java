/*
 * InboxPager, an android email client.
 * Copyright (C) 2016-2020  ITPROJECTS
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
import androidx.fragment.app.FragmentActivity;
import android.view.WindowManager;
import android.webkit.WebSettings;

public class Settings extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.left_in, R.anim.left_out);
    }

    // Sandbox WebView, prepare for use.
    public static void setup_webview(WebSettings web_settings) {
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
