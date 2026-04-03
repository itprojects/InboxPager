/*
 * InboxPager, an Android e-mail client.
 * Copyright (C) 2026  ITPROJECTS
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

import static net.inbox.Common.set_activity_insets_listener;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;

import net.inbox.Common;
import net.inbox.InboxPager;
import net.inbox.pager.R;
import net.inbox.server.OAuth2;
import net.inbox.server.OAuth2AuthServer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class OAuth2Preferences extends AppCompatActivity {

    public boolean account_is_modified = false;

    public SwitchMaterial sw_oauth2_use_extras;
    public TextView tv_oauth2_request_auth_server;
    public TextView tv_oauth2_request_auth_note;
    public TextView tv_oauth2_request_auth_copy_url;

    private String username = null;

    private int selected_tab = 0; // 0 local, 1 remote

    private LinearLayout llay_oauth2_preferences_local;
    private LinearLayout llay_oauth2_preferences_remote;

    private EditText et_oauth2_client_id;
    private EditText et_oauth2_client_secret;
    private EditText et_oauth2_refresh_token;
    private EditText et_oauth2_auth_endpoint;
    private EditText et_oauth2_token_endpoint;
    private EditText et_oauth2_scopes;
    private EditText et_oauth2_extra_parameters;

    private String[] initial_et_values = new String[6]; // EditText parameters

    private int current_inbox_id = -2;

    private OAuth2AuthServer auth_thread;

    @Override
    protected void onCreate(Bundle saved_instance_state) {
        super.onCreate(saved_instance_state);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );

        // For camera cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) // Android API >= 15
            EdgeToEdge.enable(this); // run before setContentView()

        setContentView(R.layout.oauth2_preferences);
        LinearLayout main_root = findViewById(R.id.root_view_oauth2_preferences);

        try {
            // Prepare help dialogs
            Map<Integer, String> help_texts = new HashMap<Integer, String>() {{
                put(R.id.tv_oauth2_client_id, getString(R.string.oauth2_tv_client_id_info));
                put(R.id.tv_oauth2_client_secret, getString(R.string.oauth2_tv_client_secret_info));
                put(R.id.tv_oauth2_refresh_token, getString(R.string.oauth2_tv_refresh_token_info));
                put(R.id.tv_oauth2_auth_endpoint, getString(R.string.oauth2_tv_auth_endpoint_info));
                put(R.id.tv_oauth2_token_endpoint, getString(R.string.oauth2_tv_token_endpoint_info));
                put(R.id.tv_oauth2_scopes, getString(R.string.oauth2_tv_scopes_info));
                put(R.id.tv_oauth2_extra_parameters, getString(R.string.oauth2_tv_extra_parameters_info));
                put(R.id.tv_oauth2_help, getString(R.string.oauth2_help_long));
            }};
            for (Map.Entry<Integer, String> entry : help_texts.entrySet()) {
                int key = entry.getKey();
                String value = entry.getValue();
                set_help_information(key, value);
            }

            et_oauth2_client_id = findViewById(R.id.et_oauth2_client_id);
            et_oauth2_client_secret = findViewById(R.id.et_oauth2_client_secret);
            et_oauth2_refresh_token = findViewById(R.id.et_oauth2_refresh_token);
            et_oauth2_auth_endpoint = findViewById(R.id.et_oauth2_auth_endpoint);
            et_oauth2_token_endpoint = findViewById(R.id.et_oauth2_token_endpoint);
            et_oauth2_scopes = findViewById(R.id.et_oauth2_scopes);
            et_oauth2_extra_parameters = findViewById(R.id.et_oauth2_extra_parameters);

            sw_oauth2_use_extras = findViewById(R.id.sw_oauth2_use_extras);
            sw_oauth2_use_extras.setOnClickListener(
                view -> {
                    if (((SwitchMaterial) view).isChecked())
                        et_oauth2_extra_parameters.setVisibility(View.VISIBLE);
                    else
                        et_oauth2_extra_parameters.setVisibility(View.GONE);
                }
            );

            Button btn_oauth2_request_auth = findViewById(R.id.btn_oauth2_request_auth);
            btn_oauth2_request_auth.setOnClickListener(view -> on_request_authentication());
            Button btn_oauth2_cancel_request_auth = findViewById(R.id.btn_oauth2_cancel_request_auth);
            btn_oauth2_cancel_request_auth.setOnClickListener(
                view -> {
                    if (auth_thread != null && auth_thread.isAlive())
                        auth_thread.cancel_action();
                    tv_oauth2_request_auth_copy_url.setText("");
                }
            );
            tv_oauth2_request_auth_server = findViewById(R.id.tv_oauth2_request_auth_server);
            tv_oauth2_request_auth_note = findViewById(R.id.tv_oauth2_request_auth_note);
            tv_oauth2_request_auth_copy_url = findViewById(R.id.tv_oauth2_request_auth_copy_url);

            // Restore existing state
            if (saved_instance_state != null) {
                account_is_modified = saved_instance_state.getBoolean("sv_account_is_modified");
                selected_tab = saved_instance_state.getInt("sv_selected_tab");
                current_inbox_id = saved_instance_state.getInt("sv_current_inbox_id");
                username = saved_instance_state.getString("sv_username");
                if (saved_instance_state.getBoolean("sv_sw_extras_state")) {
                    et_oauth2_extra_parameters.setVisibility(View.VISIBLE);
                } else {
                    et_oauth2_extra_parameters.setVisibility(View.GONE);
                }
                initial_et_values = saved_instance_state.getStringArray("sv_initial_et_values");
            } else {
                selected_tab = 0;
                if (getIntent().getExtras() != null) {
                    current_inbox_id = getIntent().getExtras().getInt("current_inbox_id");
                    username = getIntent().getExtras().getString("username");

                    // Get Inbox OAuth2 parameters
                    // client_id, client_secret, refresh_token, auth_endpoint, token_endpoint, scopes
                    initial_et_values = InboxPager.get_db().get_account_oauth2_preferences(
                        current_inbox_id
                    );

                    // Set Inbox OAuth2 parameters in EditText View's
                    et_oauth2_client_id.setText(initial_et_values[0]);
                    et_oauth2_client_secret.setText(initial_et_values[1]);
                    et_oauth2_refresh_token.setText(initial_et_values[2]);
                    et_oauth2_auth_endpoint.setText(initial_et_values[3]);
                    et_oauth2_token_endpoint.setText(initial_et_values[4]);
                    et_oauth2_scopes.setText(initial_et_values[5]);
                }
            }

            Toolbar tb = findViewById(R.id.oauth2_preferences_toolbar);
            setSupportActionBar(tb);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(false);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                TextView pick_title = tb.findViewById(R.id.oauth2_preferences_title); // Find the title
                pick_title.setText(getString(R.string.oauth2_preferences_title).toUpperCase());
            }

            llay_oauth2_preferences_local = findViewById(R.id.llay_oauth2_preferences_local);
            llay_oauth2_preferences_remote = findViewById(R.id.llay_oauth2_preferences_remote);

            TabLayout tl_oauth2_preferences = findViewById(R.id.tl_oauth2_preferences);
            tl_oauth2_preferences.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {

                    @Override
                    public void onTabSelected(TabLayout.Tab tab) {
                        selected_tab = tab.getPosition();
                        set_tab(tab.getPosition());
                    }

                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {}

                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {}
                }
            );
            tl_oauth2_preferences.selectTab(tl_oauth2_preferences.getTabAt(selected_tab));
        } catch (Exception e) {
            String s_error = e.getMessage();
            InboxPager.log = InboxPager.log.concat(s_error + "\n\n");
            Dialogs.toaster(true, s_error, this);
            finish();
        }

        // Handle insets for cutout and system bars
        set_activity_insets_listener(main_root);
    }

    @Override
    public void finish() {
        // Run before super() to correctly set result code
        String[] changed_et_values = new String[]{
            et_oauth2_client_id.getText().toString(), // str_cid
            et_oauth2_client_secret.getText().toString(), // str_csecret
            et_oauth2_refresh_token.getText().toString(), // str_refresh
            et_oauth2_auth_endpoint.getText().toString(), // str_auth
            et_oauth2_token_endpoint.getText().toString(), // str_token
            et_oauth2_scopes.getText().toString() // str_scopes
        };

        if (!account_is_modified) { // more guaranteed to indicate changes
            for (int i = 0;i < initial_et_values.length;++i) {
                if (!Arrays.equals(initial_et_values, changed_et_values)) {
                    account_is_modified = true;
                    break;
                }
            }
        }

        if (account_is_modified) { // Account has changed
            // Prevents screen rotation crash
            Common.fixed_or_rotating_orientation(true, this); // ROTATING

            // Save to database
            InboxPager.get_db().set_oauth2_to_account(
                current_inbox_id,
                changed_et_values[0], // str_cid
                changed_et_values[1], // str_csecret
                changed_et_values[2], // str_refresh
                changed_et_values[3], // str_auth
                changed_et_values[4], // str_token
                changed_et_values[5]  // str_scopes
            );

            // Declare result, request ListView re-flow
            setResult(
                101, // Edited Account for OAuth2
                new Intent().putExtra("account_is_modified", true)
            );

            Dialogs.toaster(true, getString(R.string.edit_account_saved), this);
        }

        super.finish();

        // Stop OAuth2 thread by interruption
        if (auth_thread != null && auth_thread.isAlive() && !auth_thread.over)
            auth_thread.cancel_action();

        // Prevents screen rotation crash, ensure proper restoration
        Common.fixed_or_rotating_orientation(false, this); // ROTATING

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.right_in, R.anim.right_out);
        } else { // Android API <= 33
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle saved_instance_state) {
        super.onSaveInstanceState(saved_instance_state);
        saved_instance_state.putBoolean("sv_account_is_modified", account_is_modified);
        saved_instance_state.putInt("sv_selected_tab", selected_tab);
        saved_instance_state.putString("sv_username", username);
        saved_instance_state.putBoolean("sv_sw_extras_state", sw_oauth2_use_extras.isChecked());
        saved_instance_state.putStringArray("sv_initial_et_values", initial_et_values);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.log_action_btns, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.log_menu) {
            Dialogs.dialog_view_log(this);
        }

        return true;
    }

    // Using manual switching is more convenient than
    // the alternative Android adapter mechanism
    private void set_tab(int which_tab) {
        if (which_tab == 0) { // local
            llay_oauth2_preferences_local.setVisibility(View.VISIBLE);
            llay_oauth2_preferences_remote.setVisibility(View.GONE);
        } else { // remote
            llay_oauth2_preferences_local.setVisibility(View.GONE);
            llay_oauth2_preferences_remote.setVisibility(View.VISIBLE);
        }
    }

    private void set_help_information(int id, String s) {
        findViewById(id).setOnClickListener(
            (tv) -> Dialogs.dialog_simple(
                getString(R.string.oauth2_information_title), s, this
            )
        );
    }

    private void on_request_authentication() {
        String str_error = null;
        // Check and pre-existing Thread and OAuth2 parameters
        if (auth_thread != null && auth_thread.isAlive()) {
            str_error = getString(R.string.oauth2_server_still_operating);
        } else if (et_oauth2_client_id.getText().toString().isEmpty()) {
            str_error = getString(R.string.oauth2_bad_parameter) +
                    getString(R.string.oauth2_client_id);
        }
        //else if (et_oauth2_client_secret.getText().toString().isEmpty()) { // often required
        //    str_error = getString(R.string.oauth2_bad_parameter) +
        //            getString(R.string.oauth2_client_secret);
        //}
        else if (et_oauth2_auth_endpoint.getText().toString().isEmpty()) {
            str_error = getString(R.string.oauth2_bad_parameter) +
                    getString(R.string.oauth2_auth_endpoint);
        } else if (et_oauth2_token_endpoint.getText().toString().isEmpty()) {
            str_error = getString(R.string.oauth2_bad_parameter) +
                    getString(R.string.oauth2_token_endpoint);
        } else if (et_oauth2_scopes.getText().toString().isEmpty()) {
            str_error = getString(R.string.oauth2_bad_parameter) +
                    getString(R.string.oauth2_scopes);
        } else {
            // Check for available local ports for server
            int available_port = OAuth2AuthServer.get_free_port();
            if (available_port > -1) {
                // Creat textual verifier, used in S256
                String verifier = OAuth2.oauth2_code_verifier_string();
                String extra_params = et_oauth2_extra_parameters.getText().toString();
                String auth_url = OAuth2.create_auth_url(
                    available_port,
                    username,
                    verifier,
                    et_oauth2_client_id.getText().toString(),
                    et_oauth2_scopes.getText().toString(),
                    et_oauth2_auth_endpoint.getText().toString(),
                    (sw_oauth2_use_extras.isChecked() && !extra_params.isEmpty()) ? extra_params : null
                );

                tv_oauth2_request_auth_copy_url.setText(auth_url);

                String request_refresh_token = OAuth2.auth_code_for_refresh_token(
                    available_port,
                    et_oauth2_client_id.getText().toString(),
                    et_oauth2_client_secret.getText().toString(),
                    et_oauth2_scopes.getText().toString(),
                    verifier
                );

                // Prevents screen rotation crash
                Common.fixed_or_rotating_orientation(true, this); // FIXED

                // Indicate that server is active
                tv_oauth2_request_auth_server.setVisibility(View.VISIBLE);

                // Create URL to request permissions from server
                tv_oauth2_request_auth_note.setVisibility(View.VISIBLE);
                tv_oauth2_request_auth_copy_url.setVisibility(View.VISIBLE);

                // Run a server waiting for email (server) client to connect
                auth_thread = new OAuth2AuthServer(
                    this,
                    current_inbox_id,
                    available_port,
                    et_oauth2_token_endpoint.getText().toString(),
                    request_refresh_token
                );
                auth_thread.start();
            } else { // no ports, no authentication
                str_error = this.getString(R.string.oauth2_no_available_ports);
                InboxPager.log = InboxPager.log.concat(
                    this.getString(R.string.oauth2_no_available_ports)
                );
            }
        }

        if (str_error != null) { // When authorisation should not begin
            Dialogs.dialog_simple(
                getString(R.string.err_error),
                getString(R.string.oauth2_bad_parameter) + str_error,
                this
            );
        }
    }
}
