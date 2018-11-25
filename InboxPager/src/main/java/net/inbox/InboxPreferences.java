/*
 * InboxPager, an android email client.
 * Copyright (C) 2016  ITPROJECTS
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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import net.inbox.db.DBAccess;
import net.inbox.db.Inbox;
import net.inbox.dialogs.Dialogs;
import net.inbox.server.Handler;
import net.inbox.server.IMAP;
import net.inbox.server.POP;
import net.inbox.server.SMTP;
import net.inbox.server.Test;

import java.util.HashMap;

public class InboxPreferences extends AppCompatActivity {

    private DBAccess db;

    // Adding or editing account
    private boolean add_mode = false;

    // Used in switching IMAP/POP
    private boolean initial_switch_value = false;
    private int initial_imap_or_pop_port = -1;

    private EditText et_email;
    private EditText et_username;
    private EditText et_pass;
    private Switch sw_imap_or_pop;
    private TextView tv_imap_or_pop;
    private EditText et_imap_or_pop_server;
    private EditText et_imap_or_pop_server_port;
    private EditText et_smtp_server;
    private EditText et_smtp_server_port;
    private CheckBox cb_auto_refresh;
    private CheckBox cb_always_ask_pass;
    private CheckBox cb_auto_save_full_msgs;

    private int current_inbox = -2;

    private Inbox current = new Inbox();

    private AppCompatActivity ctx;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.edit_account);

        ctx = this;

        try {
            // Get the database
            db = Pager.get_db();
            prefs = PreferenceManager.getDefaultSharedPreferences(this);

            // Restore existing state
            if (savedInstanceState != null) {
                add_mode = savedInstanceState.getBoolean("sv_add_mode");
                initial_switch_value = savedInstanceState.getBoolean("sv_initial_switch_value");
                initial_imap_or_pop_port = savedInstanceState.getInt("sv_initial_imap_or_pop_port");
                current_inbox = savedInstanceState.getInt("sv_current_inbox");
            } else {
                // Launching the corresponding ADD or EDIT operation
                add_mode = getIntent().getExtras().getBoolean("add");
                current_inbox = getIntent().getExtras().getInt("db_id");
            }

            current.set_id(current_inbox);

            Toolbar tb = findViewById(R.id.edit_account_toolbar);
            setSupportActionBar(tb);

            // Find the title
            TextView tv_t;
            for (int i = 0; i < tb.getChildCount(); ++i) {
                int idd = tb.getChildAt(i).getId();
                if (idd == -1) {
                    tv_t = (TextView) tb.getChildAt(i);
                    tv_t.setTextColor(ContextCompat.getColor(this, R.color.color_title));
                    tv_t.setTypeface(Pager.tf);
                    break;
                }
            }

            String title;
            if (add_mode) {
                title = getString(R.string.activity_add_account_title);
            } else {
                title = getString(R.string.activity_edit_account_title);
            }

            if (getSupportActionBar() != null) getSupportActionBar().setTitle(title.toUpperCase());

            // Get the visual elements
            et_email = findViewById(R.id.et_email);
            et_username = findViewById(R.id.et_username);
            et_pass = findViewById(R.id.et_pass);
            et_imap_or_pop_server = findViewById(R.id.et_imap_or_pop_server);
            et_imap_or_pop_server_port = findViewById(R.id.et_imap_or_pop_server_port);
            et_smtp_server = findViewById(R.id.et_smtp_server);
            et_smtp_server_port = findViewById(R.id.et_smtp_server_port);
            cb_auto_refresh = findViewById(R.id.cb_auto_refresh);
            cb_always_ask_pass = findViewById(R.id.cb_always_ask_pass);
            cb_auto_save_full_msgs = findViewById(R.id.cb_auto_save_full_msgs);
            tv_imap_or_pop = findViewById(R.id.tv_imap_or_pop);
            sw_imap_or_pop = findViewById(R.id.sw_imap_or_pop);
            sw_imap_or_pop.setOnCheckedChangeListener((v, isChecked) -> {
                if (isChecked) {
                    sw_imap_or_pop.setText(getString(R.string.edit_account_imap_or_pop_switchOn));
                    tv_imap_or_pop.setText(getString(R.string.edit_account_imap_or_pop_on));
                    et_imap_or_pop_server.setHint(getString
                            (R.string.edit_account_incoming_server_hint_imap));
                    et_imap_or_pop_server_port.setHint(getString
                            (R.string.edit_account_incoming_server_port_hint_imap));
                } else {
                    sw_imap_or_pop.setText(getString(R.string.edit_account_imap_or_pop_switchOff));
                    tv_imap_or_pop.setText(getString
                            (R.string.edit_account_imap_or_pop_off));
                    et_imap_or_pop_server.setHint(getString
                            (R.string.edit_account_incoming_server_hint_pop));
                    et_imap_or_pop_server_port.setHint(getString
                            (R.string.edit_account_incoming_server_port_hint_pop));
                }
            });

            et_imap_or_pop_server_port.addTextChangedListener(new TextWatcher() {

                int n = 0;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() > 0) {
                        n = Integer.parseInt(s.toString());
                        if (n < 1 || n > 65535) {
                            // override bad setting
                            et_imap_or_pop_server_port.setText("");
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            et_smtp_server_port.addTextChangedListener(new TextWatcher() {

                int n = 0;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() > 0) {
                        n = Integer.parseInt(s.toString());
                        if (n < 1 || n > 65535) {
                            // override bad setting
                            et_smtp_server_port.setText("");
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            Button btn_nc_check = findViewById(R.id.btn_nc_check);
            btn_nc_check.setOnClickListener(v -> btn_nc_check_action());

            Button btn_check_incoming = findViewById(R.id.btn_check_incoming);
            Button btn_check_smtp = findViewById(R.id.btn_check_smtp);
            TextView btn_save = findViewById(R.id.tv_save);
            TextView btn_delete = findViewById(R.id.tv_delete);

            btn_check_incoming.setOnClickListener(v -> btn_check_action(false));
            btn_check_smtp.setOnClickListener(v -> btn_check_action(true));
            btn_save.setOnClickListener(v -> {
                if (initial_switch_value == sw_imap_or_pop.isChecked()) {
                    btn_save_action();
                } else {
                    int i_port = 0;
                    String s_port = et_imap_or_pop_server_port.getText().toString();
                    if (!s_port.isEmpty()) {
                        i_port = Integer.valueOf(s_port);
                    }
                    if (initial_imap_or_pop_port == i_port && i_port != 0) {
                        // Changed protocol, but not port!
                        dialog_port_consideration();
                    } else {
                        if (db.get_messages_count(current.get_id()) > 0) {
                            dialog_deletion_messages();
                        } else {
                            btn_save_action();
                        }
                    }
                }
            });

            btn_delete.setOnClickListener(v -> dialog_deletion());

            // Delete cache full messages
            Button btn_delete_full_msgs = findViewById(R.id.btn_delete_all_full_msgs);
            btn_delete_full_msgs.setOnClickListener(v -> {
                db.delete_all_full_messages(current.get_id());
                Dialogs.toaster(false, getString(R.string.message_no_full_messages), ctx);
            });

            if (add_mode) {
                // New account is being added
                prepare_add();
            } else {
                // Existing account is being edited
                prepare_edit();
            }

            // Used in IMAP <-> POP
            initial_switch_value = current.get_imap_or_pop();
            initial_imap_or_pop_port = current.get_imap_or_pop_port();
        } catch (Exception e) {
            Pager.log += e.getMessage() + "\n\n";
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle save) {
        super.onSaveInstanceState(save);
        save.putBoolean("sv_add_mode", add_mode);
        save.putBoolean("sv_initial_switch_value", initial_switch_value);
        save.putInt("sv_initial_imap_or_pop_port", initial_imap_or_pop_port);
        save.putInt("sv_current_inbox", current_inbox);
    }

    @Override
    public void finish() {
        super.finish();
        if (add_mode) {
            overridePendingTransition(R.anim.left_in, R.anim.left_out);
        } else {
            overridePendingTransition(R.anim.right_in, R.anim.right_out);
        }
    }

    private void prepare_add() {
        // Assign user data parameters
        current.set_auto_refresh(prefs.getBoolean("auto_refresh", false));
        current.set_imap_or_pop(prefs.getBoolean("imap_or_pop", false));

        // Assign the default parameters
        cb_auto_refresh.setChecked(prefs.getBoolean("auto_refresh", true));
        cb_always_ask_pass.setChecked(prefs.getBoolean("always_ask_pass", false));
        cb_auto_save_full_msgs.setChecked(prefs.getBoolean("auto_save_full_msgs", false));
        sw_imap_or_pop.setChecked(prefs.getBoolean("imap_or_pop", false));
    }

    private void prepare_edit() {
        current = db.get_account(current.get_id());
        et_email.setText(current.get_email());
        et_username.setText(current.get_username());
        et_pass.setText(current.get_pass());
        et_imap_or_pop_server.setText(current.get_imap_or_pop_server());
        et_imap_or_pop_server_port.setText(String.valueOf(current.get_imap_or_pop_port()));
        et_smtp_server.setText(current.get_smtp_server());
        et_smtp_server_port.setText(String.valueOf(current.get_smtp_port()));
        cb_auto_refresh.setChecked(current.get_auto_refresh());
        cb_always_ask_pass.setChecked(current.get_always_ask_pass());
        cb_auto_save_full_msgs.setChecked(current.get_auto_save_full_msgs());
        sw_imap_or_pop.setChecked(current.get_imap_or_pop());
        if (sw_imap_or_pop.isChecked()) {
            sw_imap_or_pop.setText(getString(R.string.edit_account_imap_or_pop_switchOn));
            tv_imap_or_pop.setText(getString(R.string.edit_account_imap_or_pop_on));
            et_imap_or_pop_server.setHint(getString
                    (R.string.edit_account_incoming_server_hint_imap));
            et_imap_or_pop_server_port.setHint(getString
                    (R.string.edit_account_incoming_server_port_hint_imap));
        } else {
            sw_imap_or_pop.setText(getString(R.string.edit_account_imap_or_pop_switchOff));
            tv_imap_or_pop.setText(getString(R.string.edit_account_imap_or_pop_off));
            et_imap_or_pop_server.setHint(getString
                    (R.string.edit_account_incoming_server_hint_pop));
            et_imap_or_pop_server_port.setHint(getString
                    (R.string.edit_account_incoming_server_port_hint_pop));
        }
    }

    private void btn_nc_check_action() {
        String server_name = et_imap_or_pop_server.getText().toString();
        if (server_name.isEmpty()) {
            Dialogs.toaster(true, getString(R.string.edit_account_bad_params)
                    + getString(R.string.edit_account_bad_server) + ".", this);
        } else {
            // Starts the test
            Test server = new Test(server_name, this);
            server.execute();
        }
    }

    private void btn_check_action(boolean smtp) {
        Dialogs.toaster(true, getString(R.string.edit_account_checking), this);

        if (current.get_id() <= 0) {
            Dialogs.toaster(true, getString(R.string.edit_account_check_save_first), this);
            return;
        }

        String email = et_email.getText().toString();
        String server_incoming = et_imap_or_pop_server.getText().toString();
        String port_incoming = et_imap_or_pop_server_port.getText().toString();
        String server_outgoing = et_smtp_server.getText().toString();
        String port_outgoing = et_smtp_server_port.getText().toString();

        // Check for empty text field value
        if (email.isEmpty()) {
            String err = getString(R.string.edit_account_bad_params)
                    + getString(R.string.edit_account_bad_email);
            Dialogs.toaster(true, err, this);
            return;
        }
        if (smtp) {
            if (server_outgoing.isEmpty() || port_outgoing.isEmpty()) {
                String err = getString(R.string.edit_account_bad_params);
                if (server_outgoing.isEmpty()) {
                    err += getString(R.string.edit_account_bad_server);
                }
                if (port_outgoing.isEmpty()) {
                    err += getString(R.string.edit_account_bad_port);
                }
                Dialogs.toaster(true, err, this);
                return;
            }
        } else {
            if (server_incoming.isEmpty() || port_incoming.isEmpty()) {
                String err = getString(R.string.edit_account_bad_params);
                if (server_incoming.isEmpty()) {
                    err += getString(R.string.edit_account_bad_server);
                }
                if (port_incoming.isEmpty()) {
                    err += getString(R.string.edit_account_bad_port);
                }
                Dialogs.toaster(true, err, this);
                return;
            }
        }

        // Testing remote server
        Handler handler;
        if (smtp) {
            handler = new SMTP(this);
            handler.start();
        } else {
            if (current.get_imap_or_pop()) {
                handler = new IMAP(this);
                handler.start();
            } else {
                handler = new POP(this);
                handler.start();
            }
        }
        handler.test_server(current, this);
    }

    private void btn_save_action() {
        current.set_auto_refresh(cb_auto_refresh.isChecked());
        current.set_always_ask_pass(cb_always_ask_pass.isChecked());
        current.set_auto_save_full_msgs(cb_auto_save_full_msgs.isChecked());
        current.set_imap_or_pop(sw_imap_or_pop.isChecked());
        current.set_email(et_email.getText().toString());
        current.set_username(et_username.getText().toString());
        current.set_pass(et_pass.getText().toString());
        current.set_imap_or_pop_server(et_imap_or_pop_server.getText().toString());
        String pt_imap_or_pop = et_imap_or_pop_server_port.getText().toString();
        if (pt_imap_or_pop.isEmpty()) {
            if (sw_imap_or_pop.isChecked()) {
                current.set_imap_or_pop_port(993);
            } else {
                current.set_imap_or_pop_port(995);
            }
        } else current.set_imap_or_pop_port(Integer.parseInt(pt_imap_or_pop));
        current.set_smtp_server(et_smtp_server.getText().toString());
        String pt_smtp = et_smtp_server_port.getText().toString();
        if (pt_smtp.isEmpty()) {
            current.set_smtp_port(465);
        } else current.set_smtp_port(Integer.parseInt(pt_smtp));
        if (et_email.getText().toString().isEmpty()) {
            Dialogs.toaster(true, getString(R.string.edit_account_no_saving), this);
            return;
        } else {
            Dialogs.toaster(true, getString(R.string.edit_account_saving), this);
        }

        if (add_mode) {
            if (current.get_email().isEmpty()) {
                Dialogs.toaster(false, getString(R.string.edit_account_not_saved), this);
                return;
            }

            // If the account is new - add to database
            db.add_account(current);
        } else {
            // If the account exists - update in database
            db.update_account(current);
        }

        // Declaring the result of the activity
        Intent ret_intent = new Intent();

        // Request ListView re-flow
        setResult(Activity.RESULT_OK, ret_intent.putExtra("status", true));

        // End activity
        finish();
    }

    /**
     * Dialog asking for wrong port consideration.
     **/
    public void dialog_port_consideration() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.menu_port_consideration_title));
        builder.setMessage(getString(R.string.menu_port_consideration_dialog));
        builder.setCancelable(true);
        builder.setPositiveButton(getString(R.string.btn_yes), (dialog, id) -> {
            if (db.get_messages_count(current.get_id()) > 0) {
                dialog_deletion_messages();
            } else {
                btn_save_action();
            }
        });
        builder.setNegativeButton(getString(android.R.string.cancel), null);
        builder.show();
    }

    /**
     * Dialog asking for account deletion confirmation.
     **/
    public void dialog_deletion() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.menu_delete_account_dialog_title));
        builder.setMessage(getString(R.string.menu_delete_account_dialog));
        builder.setCancelable(true);
        builder.setPositiveButton(getString(android.R.string.ok), (dialog, id) -> delete_account());
        builder.setNegativeButton(getString(android.R.string.cancel), null);
        builder.show();
    }

    public void delete_account() {
        db.delete_account(current.get_id());
        Intent ret_intent = new Intent();
        setResult(Activity.RESULT_OK, ret_intent);
        finish();
    }

    /**
     * Dialog asking for account deletion confirmation.
     **/
    public void dialog_deletion_messages() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.menu_switch_pop_imap_title));
        builder.setMessage(getString(R.string.menu_switch_pop_imap_dialog));
        builder.setCancelable(true);
        builder.setPositiveButton(getString(android.R.string.ok), (dialog, id) -> {
            dialog.dismiss();
            delete_all_messages();
            current.set_imap_or_pop_extensions("-1");
            current.set_messages(0);
            current.set_recent(0);
            current.set_unseen(0);
            current.set_uidvalidity(0);
            current.set_total_size(0);
            db.update_account(current);
            btn_save_action();
        });
        builder.setNegativeButton(getString(android.R.string.cancel), (dialog, id) -> {
            dialog.dismiss();
            sw_imap_or_pop.setChecked(initial_switch_value);
            btn_save_action();
        });
        builder.show();
    }

    public void delete_all_messages() {
        HashMap<Integer, String> local_msgs = db.get_all_message_uids(current.get_id());
        if (local_msgs.size() > 0) db.delete_all_messages(local_msgs);
    }
}
