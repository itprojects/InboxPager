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

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.text.Html;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.pager.R;
import net.inbox.server.EndToEnd;
import net.inbox.server.Utils;
import net.inbox.visuals.Dialogs;
import net.inbox.visuals.SpinningStatus;
import net.inbox.visuals.SendFilePicker;
import net.inbox.server.NetworkThread;
import net.inbox.server.SMTP;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

public class InboxSend extends AppCompatActivity {

    // Prevents intent extras limit of < 1 MB
    protected static String msg_crypto;

    private NetworkThread network_thread;

    private ImageView iv_ssl_auth;
    private EditText et_subject;
    private EditText et_to;
    private SwitchMaterial sw_cc;
    private EditText et_cc;
    private SwitchMaterial sw_bcc;
    private EditText et_bcc;
    private EditText et_contents;
    private TextView tv_attachments;
    private ImageView iv_encryption_pgp;
    private TextView tv_encryption_pgp_reset;
    private LinearLayout llay_send_previous;

    // Text message encryption dialog.
    private AlertDialog dialog_txt_crypto;
    private Spinner spin_cipher_type;
    private Spinner spin_cipher_mode;
    private Spinner spin_cipher_padding;
    private EditText et_key;
    private int s_replace_start = 0;
    private int s_replace_end = 0;

    // GPG variables
    private boolean crypto_locked = false;
    private String msg_contents;

    private ArrayList<String> attachment_paths = new ArrayList<>();
    private long attachments_size = 0;
    private long total_size_limit = 0;

    private Message current = new Message();
    private Inbox current_inbox;

    private boolean sending_active = false;

    private int last_connection_data_id = -1;
    private String last_connection_data = null;

    private ActivityResultLauncher<Intent> start_activity_for_result;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(),
        isGranted -> {/* true or false */}
        );

    @Override
    public void onCreate(Bundle saved_instance_state) {
        super.onCreate(saved_instance_state);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );

        // For camera cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) // Android API >= 35
            EdgeToEdge.enable(this); // run before setContentView()

        setContentView(R.layout.send);
        LinearLayout main_root = findViewById(R.id.root_view_send);

        try {
            // Restore existing state
            if (saved_instance_state != null) {
                crypto_locked = saved_instance_state.getBoolean("sv_crypto_locked");
                msg_contents = saved_instance_state.getString("sv_msg_contents");
                attachment_paths = saved_instance_state.getStringArrayList("sv_attachment_paths");
                if (attachment_paths == null) attachment_paths = new ArrayList<>();
                attachments_size = saved_instance_state.getLong("sv_attachments_size");
                total_size_limit = saved_instance_state.getLong("sv_total_size_limit");
                sending_active = saved_instance_state.getBoolean("sv_sending_active");
                last_connection_data_id = saved_instance_state.getInt("sv_last_connection_data_id");
                last_connection_data = saved_instance_state.getString("sv_last_connection_data");
            }

            // Check for extras
            Bundle buns = getIntent().getExtras();

            // Get the database
            current_inbox = InboxPager.get_db().get_account(getIntent().getIntExtra("db_id", -99));

            Toolbar tb = findViewById(R.id.send_toolbar);
            setSupportActionBar(tb);

            // Find the title
            TextView send_title = tb.findViewById(R.id.send_title);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(false);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                if (buns != null) {
                    String s_title = buns.getString("title");
                    if (s_title != null) send_title.setText(s_title.toUpperCase());
                }
            }

            TextView tv_send = findViewById(R.id.tv_send);
            tv_send.setOnClickListener(
                v -> {
                    if (sending_active) {
                        toaster(false, "");
                    } else {
                        send();
                    }
                }
            );

            et_to = findViewById(R.id.send_to);
            sw_cc = findViewById(R.id.send_cc_check);
            et_cc = findViewById(R.id.send_cc);
            sw_bcc = findViewById(R.id.send_bcc_check);
            et_bcc = findViewById(R.id.send_bcc);
            et_subject = findViewById(R.id.send_subject);
            et_contents = findViewById(R.id.send_contents);
            tv_attachments = findViewById(R.id.send_attachments_count);
            llay_send_previous = findViewById(R.id.llay_send_previous);
            TextView tv_send_contents_previous = findViewById(R.id.tv_send_contents_previous);

            // Setting the correct size
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            float font_size = Float.parseFloat(prefs.getString("sett_msg_font_size", "100"));
            et_to.setTextSize(font_size);
            et_cc.setTextSize(font_size);
            et_bcc.setTextSize(font_size);
            et_subject.setTextSize(font_size);
            et_contents.setTextSize(font_size);
            tv_attachments.setTextSize(font_size);
            tv_send_contents_previous.setTextSize(font_size); // (html or plain) of previous message

            registerForContextMenu(tv_send_contents_previous); // Crypto ActionModes ContextMenus
            tv_send_contents_previous.setCustomSelectionActionModeCallback(
                    new ActionMode.Callback() {
                        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                            // Adding crypto options
                            MenuInflater inflater = getMenuInflater();
                            inflater.inflate(R.menu.crypto_action_btns, menu);

                            return true;
                        }

                        @Override
                        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                            return false;
                        }

                        @Override
                        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                            if (item.getGroupId() == R.id.gnu_crypts) {
                                // Set selection parameters
                                s_replace_start = tv_send_contents_previous.getSelectionStart();
                                s_replace_end = tv_send_contents_previous.getSelectionEnd();

                                // Open dialog for decryption
                                dialog_txt_crypto();

                                mode.finish();
                                return true;
                            }
                            return false;
                        }

                        @Override
                        public void onDestroyActionMode(ActionMode mode) {}
                    }
            );

            registerForContextMenu(et_contents);
            et_contents.setCustomSelectionActionModeCallback(
                new ActionMode.Callback() {
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        // Adding crypto options
                        MenuInflater inflater = getMenuInflater();
                        inflater.inflate(R.menu.crypto_action_btns, menu);

                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        if (item.getGroupId() == R.id.gnu_crypts) {
                            // Set selection parameters
                            s_replace_start = et_contents.getSelectionStart();
                            s_replace_end = et_contents.getSelectionEnd();

                            // Open dialog for decryption
                            dialog_txt_crypto();

                            mode.finish();
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {}
                }
            );

            et_cc.setVisibility(View.GONE);
            et_bcc.setVisibility(View.GONE);
            sw_cc.setOnCheckedChangeListener(
                (v, isChecked) -> {
                    if (isChecked) {
                        et_cc.setVisibility(View.VISIBLE);
                    } else {
                        et_cc.setVisibility(View.GONE);
                    }
                }
            );
            sw_bcc.setOnCheckedChangeListener(
                (v, isChecked) -> {
                    if (isChecked) {
                        et_bcc.setVisibility(View.VISIBLE);
                    } else {
                        et_bcc.setVisibility(View.GONE);
                    }
                }
            );

            if (!attachment_paths.isEmpty()) {
                tv_attachments.setText(String.valueOf(attachment_paths.size()));
            } else {
                tv_attachments.setText("");
            }

            TextView tv_attachments = findViewById(R.id.send_attachments_count);
            tv_attachments.setOnClickListener(v -> pick_attachments());

            // Setting up the SSL authentication application
            iv_ssl_auth = findViewById(R.id.ssl_auth_img_vw);
            iv_ssl_auth.setOnClickListener(v -> dialog_servers());
            if (last_connection_data_id > -1 && last_connection_data_id == current_inbox.get_id()) {
                iv_ssl_auth.setVisibility(View.VISIBLE); // restore connection security icon
                if (last_connection_data != null && !last_connection_data.isEmpty())
                    iv_ssl_auth.setImageResource(R.drawable.padlock_normal);
                else
                    iv_ssl_auth.setImageResource(R.drawable.padlock_error);
            } else {
                iv_ssl_auth.setVisibility(View.GONE);
            }

            // Starts encryption with PGP
            iv_encryption_pgp = findViewById(R.id.iv_encryption_pgp);
            iv_encryption_pgp.setOnClickListener(v -> gpg_crypto_tests());

            tv_encryption_pgp_reset = findViewById(R.id.tv_encryption_pgp_reset);
            tv_encryption_pgp_reset.setOnClickListener(
                v -> {
                    tv_encryption_pgp_reset.setVisibility(View.GONE);
                    current.set_contents_crypto(null);
                    et_contents.setText(msg_contents);
                    crypto_locked = false;
                    crypto_padlock();
                }
            );

            // Maximum Server (SMTP) message octet (byte) size
            String str1 = current_inbox.smtp_check_extension_return("SIZE");
            total_size_limit = (str1 == null) ? 0 : Long.parseLong(str1.substring(4).trim());

            // Default SMTP message size 64000 octets (bytes)
            if (total_size_limit == 0) total_size_limit = 64000;

            // If replying to a message
            String reply_to;
            String subject_of;

            if (buns != null) {
                if (buns.containsKey("subject")) {
                    subject_of = buns.getString("subject");
                    et_subject.setText(subject_of);
                }
                if (buns.containsKey("reply-to")) {
                    reply_to = buns.getString("reply-to");
                    et_to.setText(reply_to);
                }
                if (buns.containsKey("reply-cc")) {
                    et_cc.setText(buns.getString("reply-cc"));
                    sw_cc.setChecked(true);
                }
                if (buns.containsKey("previous_letter")) {
                    if (
                        buns.getString("previous_letter", "ERROR").equals("NO_TEXT")
                    ) {
                        llay_send_previous.setVisibility(View.GONE);
                        SwitchMaterial sw_previous = findViewById(R.id.send_sw_previous);
                        sw_previous.setVisibility(View.GONE);
                    } else {
                        llay_send_previous.setVisibility(View.VISIBLE);
                        String text_charset = buns.getString("previous_letter_charset", "UTF-8");
                        String text_data;
                        if (text_charset != null
                            && !text_charset.equalsIgnoreCase("UTF-8")
                            && !text_charset.equalsIgnoreCase("utf8")
                        ) {
                            text_data = new String(
                                buns.getString("previous_letter").getBytes(StandardCharsets.UTF_8),
                                StandardCharsets.UTF_8
                            );
                        } else text_data = buns.getString("previous_letter");
                        if (buns.getBoolean("previous_letter_is_plain")) {
                            tv_send_contents_previous.setText(text_data);
                        } else {
                            tv_send_contents_previous.setText(Html.fromHtml(text_data));
                        }

                        SwitchMaterial sw_previous = findViewById(R.id.send_sw_previous);
                        sw_previous.setVisibility(View.VISIBLE);
                        sw_previous.setOnCheckedChangeListener(
                            (buttonView, isChecked) -> {
                                if (isChecked) {
                                    llay_send_previous.setVisibility(View.VISIBLE);
                                } else {
                                    llay_send_previous.setVisibility(View.GONE);
                                }
                            }
                        );
                        sw_previous.setChecked(true);
                    }
                }
            }


        } catch (Exception e) {
            String s_error = e.getMessage();
            InboxPager.log = InboxPager.log.concat(s_error + "\n\n");
            Dialogs.toaster(true, s_error, this);
            finish();
        }

        // Handle insets for cutout and system bars
        set_activity_insets_listener(main_root);

        // Prepare Activity for result
        start_activity_for_result = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                int result_code = result.getResultCode();
                Intent data = result.getData();
                if (result_code == Activity.RESULT_OK) {
                    if (data != null) {
                        attachment_paths = data.getStringArrayListExtra("attachments");
                        if (attachment_paths != null && !attachment_paths.isEmpty()) {
                            tv_attachments.setText(String.valueOf(attachment_paths.size()));
                        } else {
                            tv_attachments.setText("");
                        }
                    }
                } else if (result_code == 19091) {
                    tv_encryption_pgp_reset.setVisibility(View.VISIBLE);
                    crypto_locked = true;
                    crypto_padlock();
                    if (data != null && data.getIntExtra("ret-code", 0) != 0) {
                        current.set_contents_crypto(msg_crypto);
                        msg_crypto = "";
                        if (current.get_contents_crypto() != null
                            && current.get_contents_crypto().length() > 500
                        ) {
                            et_contents.setText(current.get_contents_crypto().substring(0, 500));
                        } else {
                            et_contents.setText(current.get_contents_crypto());
                        }
                    }
                }
            }
        );
    }

    @Override
    public void finish() {
        super.finish();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.right_in, R.anim.right_out);
        } else { // Android API <= 33
            overridePendingTransition(R.anim.right_in, R.anim.right_out);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.send_action_btns, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.log_menu) {
            Dialogs.dialog_view_log(this);
        }

        return true;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle saved_instance_state) {
        super.onSaveInstanceState(saved_instance_state);
        saved_instance_state.putBoolean("sv_crypto_locked", crypto_locked);
        saved_instance_state.putString("sv_msg_contents", msg_contents);
        saved_instance_state.putStringArrayList("sv_attachment_paths", attachment_paths);
        saved_instance_state.putLong("sv_attachments_size", attachments_size);
        saved_instance_state.putLong("sv_total_size_limit", total_size_limit);
        saved_instance_state.putBoolean("sv_sending_active", sending_active);
        saved_instance_state.putInt("sv_last_connection_data_id", last_connection_data_id);
        saved_instance_state.putString("sv_last_connection_data", last_connection_data);
    }

    private void pick_attachments() {
        if (!crypto_locked) {
            if (Common.check_permissions(this, true)) {
                // Prepare to write a reply message
                Intent pick_intent = new Intent(getApplicationContext(), SendFilePicker.class);
                Bundle b = new Bundle();
                b.putStringArrayList("str_array_paths", attachment_paths);
                b.putLong("l_attachment_size", attachments_size);
                b.putLong("l_total_size_limit", total_size_limit);
                if (!current_inbox.get_smtp_extensions().equals("-1")
                    && !current_inbox.smtp_check_extension("8BITMIME")
                ) {
                    b.putBoolean("b_8_bit_absent", true);
                }
                pick_intent.putExtras(b);
                start_activity_for_result.launch(pick_intent);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
                    overrideActivityTransition(
                        OVERRIDE_TRANSITION_OPEN,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    );
                } else { // Android API <= 33
                    // Animation
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }
            } else {
                // Asking for read and manage external storage permissions, to add an attachment
                if (Build.VERSION.SDK_INT >= 32) { // Android API >= 12
                    Dialogs.toaster(
                        false,
                        getString(R.string.err_missing_permissions) + "\nMANAGE_EXTERNAL_STORAGE ❌",
                        this
                    );
                    Intent ask_intent = new Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                    );
                    ask_intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(ask_intent);
                } else { // Android API <= 10
                    Dialogs.toaster(
                        false,
                        getString(R.string.err_missing_permissions) + "\nREAD_EXTERNAL_STORAGE ❌",
                        this
                    );
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            }
        } else {
            Dialogs.toaster(true, getString(R.string.send_unlock_first), this);
        }
    }

    /**
     * Error checking before sending.
     **/
    private void send() {
        // Wait for the checks to complete
        sending_active = true;

        int to_count = 0;
        int cc_count = 0;
        int bcc_count = 0;

        String s_to = et_to.getText().toString().trim();
        String s_cc = "";
        String s_bcc = "";
        if (sw_cc.isChecked()) s_cc = et_cc.getText().toString().trim();
        if (sw_bcc.isChecked()) s_bcc = et_bcc.getText().toString().trim();

        // Testing To
        if (s_to.isEmpty()) {
            // No recipient = no message
            Dialogs.dialog_simple(null, getString(R.string.err_no_rcpt), this);
            InboxPager.log = InboxPager.log.concat(getString(R.string.err_no_rcpt) + "\n\n");
            sending_active = false;
            return;
        }

        HashMap<String, String> adrs = Utils.parse_addresses(new String[]{ s_to, s_cc, s_bcc });

        // Testing TO
        if (!s_to.trim().isEmpty() && (adrs.get("TO_") != null)) {
            int ind = adrs.get("TO_").indexOf("|");
            to_count = Integer.parseInt(adrs.get("TO_").substring(0, ind));
            s_to = adrs.get("TO_").substring(ind + 1);
        }
        int to_size = s_to.getBytes().length;

        if (to_count == 0) { // No recipient = no message
            Dialogs.dialog_simple(null, getString(R.string.err_no_rcpt), this);
            InboxPager.log = InboxPager.log.concat(getString(R.string.err_no_rcpt) + "\n\n");
            sending_active = false;
            return;
        }

        // Testing CC
        if (!s_cc.trim().isEmpty() && (adrs.get("CC_") != null)) {
            int ind = adrs.get("CC_").indexOf("|");
            cc_count = Integer.parseInt(adrs.get("CC_").substring(0, ind));
            s_cc = adrs.get("CC_").substring(ind + 1);
        }
        int cc_size = s_cc.getBytes().length;

        // Testing BCC
        if (!s_bcc.trim().isEmpty() && (adrs.get("BCC") != null)) {
            int ind = adrs.get("BCC").indexOf("|");
            bcc_count = Integer.parseInt(adrs.get("BCC").substring(0, ind));
            s_bcc = adrs.get("BCC").substring(ind + 1);
        }
        int bcc_size = s_bcc.getBytes().length;

        // Testing if there are more than 100 recipients
        if ((to_count + cc_count + bcc_count) > 100) { // Too many recipients
            Dialogs.dialog_simple(null, getString(R.string.err_too_many_rcpt), this);
            InboxPager.log = InboxPager.log.concat(getString(R.string.err_too_many_rcpt) + "\n\n");
            sending_active = false;
            return;
        }

        String subject_line = et_subject.getText().toString().trim();
        int subject_size = subject_line.getBytes().length;

        String text_contents = et_contents.getText().toString();
        int text_size = text_contents.getBytes().length;

        // Testing attachments size
        if (attachments_size >= total_size_limit) {
            // Server will refuse the message, attachments too big
            Dialogs.dialog_simple(null, getString(R.string.err_size_attachments), this);
            InboxPager.log = InboxPager.log.concat(getString(R.string.err_size_attachments) + "\n\n");
            sending_active = false;
            return;
        }

        long current_total_size = subject_size + to_size + text_size + attachments_size;

        if (sw_cc.isChecked()) current_total_size += cc_size;

        if (sw_bcc.isChecked()) current_total_size += bcc_size;

        if (current_total_size >= total_size_limit) {
            // Server will refuse the message, it's too big
            Dialogs.dialog_simple(null, getString(R.string.err_msg_too_heavy), this);
            InboxPager.log = InboxPager.log.concat(getString(R.string.err_msg_too_heavy) + "\n\n");
            sending_active = false;
            return;
        }

        // Preparing the message
        current.set_subject(subject_line);
        current.set_to(s_to);
        if (sw_cc.isChecked()) current.set_cc(s_cc);
        if (sw_bcc.isChecked()) current.set_bcc(s_bcc);
        current.set_contents_plain(text_contents);
        current.set_charset_plain("UTF-8");
        current.set_attachments(attachment_paths.size());

        // Real sending action
        send_action();
    }

    private void send_action() {
        sending_active = false;

        // Prevents screen rotation crash
        Common.fixed_or_rotating_orientation(true, this);

        last_connection_data_id = -1;
        last_connection_data = null;

        // Starting an animated dialog
        SpinningStatus spt = new SpinningStatus(false, false, this, network_thread);
        spt.set_progress(getString(R.string.send_spin), "");

        // Starting SENDing message
        network_thread = new SMTP(this);
        network_thread.start();
        network_thread.sp = spt;
        if (!attachment_paths.isEmpty()) {
            network_thread.msg_action(current_inbox.get_id(), current, attachment_paths, false, this);
        } else {
            network_thread.msg_action(current_inbox.get_id(), current, null, false, this);
        }
    }

    /**
     * Tests message for required GPG parameters.
     **/
    private void gpg_crypto_tests() {
        // Testing OpenKeychain
        if (Common.is_gpg_available(this)) {
            // Testing TO
            String[] arr_to;
            String s_to = et_to.getText().toString().trim();
            if (s_to.isEmpty()) {
                toaster(true, getString(R.string.send_missing_rcpt_to));
                return;
            } else {
                arr_to = s_to.split(",");
                int to_count = 0;
                for (String s_to_name : arr_to) {
                    if (!s_to_name.trim().isEmpty()) ++to_count;
                }
                if (to_count == 0) toaster(true, getString(R.string.send_missing_rcpt_to));
            }

            // Testing Subject
            String subject = et_subject.getText().toString();
            if (!subject.isEmpty()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.send_cleartext_subject));
                builder.setMessage(getString(R.string.send_cleartext_content));
                builder.setPositiveButton(
                    getString(R.string.send_cleartext_delete),
                    (dialog, which) -> {
                        et_subject.setText("");
                        gpg_crypto_start();
                    }
                );
                builder.setNegativeButton(
                    getString(R.string.send_cleartext_keep),
                    (dialog, which) -> gpg_crypto_start()
                );
                builder.show();
            } else {
                gpg_crypto_start();
            }
        } else {
            Dialogs.toaster(false, getString(R.string.open_pgp_none_found), this);
        }
    }

    /**
     * Starts GPG message work.
     **/
    private void gpg_crypto_start() {
        try {
            // Testing attachments
            boolean empty_contents = et_contents.getText().toString().isEmpty();
            boolean mime_attachments = !attachment_paths.isEmpty();
            if (empty_contents && !mime_attachments) {
                // No encryption required
                crypto_locked = true;
                crypto_padlock();
                tv_encryption_pgp_reset.setVisibility(View.VISIBLE);
            } else {
                // Encryption required
                Intent gpg = new Intent(this, InboxGPG.class);
                Bundle b = new Bundle();
                String s_rcpt = et_to.getText().toString();
                if (sw_cc.isChecked() && et_cc.getText().length() > 0) {
                    s_rcpt += "," + et_cc.getText().toString();
                }
                if (sw_bcc.isChecked() && et_bcc.getText().length() > 0) {
                    s_rcpt += "," + et_bcc.getText().toString();
                }
                b.putString("recipients", s_rcpt);
                if (mime_attachments) b.putStringArrayList("attachments", attachment_paths);

                // Message text
                current.set_contents_crypto(null);
                msg_contents = et_contents.getText().toString();
                b.putString("message-data", msg_contents);
                b.putInt("request-code", 91);
                start_activity_for_result.launch(gpg.putExtras(b));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
                    overrideActivityTransition(
                        OVERRIDE_TRANSITION_OPEN,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    );
                } else { // Android API <= 33
                    // Animation
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }
            }
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(
                getString(R.string.open_pgp_none_found) + "\n\n"
            );
            toaster(true, getString(R.string.open_pgp_none_found));
        }
    }

    private void crypto_padlock() {
        if (crypto_locked) {
            et_to.setEnabled(false);
            et_cc.setEnabled(false);
            et_bcc.setEnabled(false);
            et_subject.setEnabled(false);
            et_contents.setEnabled(false);
            sw_cc.setEnabled(false);
            sw_bcc.setEnabled(false);
            tv_attachments.setEnabled(false);
            iv_encryption_pgp.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.padlock_pgp_closed)
            );
        } else {
            et_to.setEnabled(true);
            et_cc.setEnabled(true);
            et_bcc.setEnabled(true);
            et_subject.setEnabled(true);
            et_contents.setEnabled(true);
            sw_cc.setEnabled(true);
            sw_bcc.setEnabled(true);
            tv_attachments.setEnabled(true);
            iv_encryption_pgp.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.padlock_pgp_open)
            );
        }
    }

    /**
     * Text message cryptography. AES, Twofish, not PGP.
     * Does not encrypt any attachments.
     **/
    private void dialog_txt_crypto() {
        // Clean-up previous
        dialog_txt_crypto = null;
        spin_cipher_type = null;
        spin_cipher_mode = null;
        spin_cipher_padding = null;
        et_key = null;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Build cryptogram dialog
        Dialogs.dialog_pw_txt(builder, this);
        builder.setNeutralButton(getString(R.string.crypto_encrypt), null);

        dialog_txt_crypto = builder.show();
        spin_cipher_type = dialog_txt_crypto.findViewById(R.id.spin_cipher);
        spin_cipher_mode = dialog_txt_crypto.findViewById(R.id.spin_cipher_mode);
        spin_cipher_padding = dialog_txt_crypto.findViewById(R.id.spin_cipher_padding);
        et_key = dialog_txt_crypto.findViewById(R.id.et_key);

        dialog_txt_crypto.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
            v -> {
                try {
                    // [1] head, [2] middle to replace or full if note replacing, [3] tail
                    String[] msg_texts = new String[]{ "", null, ""};
                    if (s_replace_start != 0 || s_replace_end != 0) {
                        msg_texts[0] = et_contents.getText().toString()
                            .substring(0, s_replace_start);
                        msg_texts[1] = et_contents.getText().toString()
                            .substring(s_replace_start, s_replace_end);
                        msg_texts[2] = et_contents.getText().toString()
                            .substring(s_replace_end, et_contents.length());
                    } else {
                        msg_texts[1] = et_contents.getText().toString();
                    }
                    msg_texts[1] = EndToEnd.decrypt(
                        dialog_txt_crypto.getContext(),
                        et_key.getText().toString(),
                        msg_texts[1],
                        (String) spin_cipher_type.getSelectedItem(),
                        (String) spin_cipher_mode.getSelectedItem(),
                        (String) spin_cipher_padding.getSelectedItem()
                    );
                    toaster(true, dialog_txt_crypto.getContext().getString(R.string.crypto_success));
                    dialog_txt_crypto.cancel();

                    // Rebuilding complete text
                    msg_texts[1] = msg_texts[0].concat(msg_texts[1]).concat(msg_texts[2]);
                    et_contents.setText(msg_texts[1]);
                    s_replace_start = s_replace_end = 0;
                } catch (Exception e) {
                    InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                    toaster(true, dialog_txt_crypto.getContext().getString(R.string.crypto_failure));
                    toaster(true, e.getMessage());
                }
            }
        );
        dialog_txt_crypto.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(
            v -> {
                try {
                    // [1] head, [2] middle to replace or full if note replacing, [3] tail
                    String[] msg_texts = new String[]{ "", null, ""};
                    if (s_replace_start != 0 || s_replace_end != 0) {
                        msg_texts[0] = et_contents.getText().toString()
                            .substring(0, s_replace_start);
                        msg_texts[1] = et_contents.getText().toString()
                            .substring(s_replace_start, s_replace_end);
                        msg_texts[2] = et_contents.getText().toString()
                            .substring(s_replace_end, et_contents.length());
                    } else {
                        msg_texts[1] = et_contents.getText().toString();
                    }
                    msg_texts[1] = EndToEnd.encrypt(
                        dialog_txt_crypto.getContext(),
                        et_key.getText().toString(),
                        msg_texts[1],
                        (String) spin_cipher_type.getSelectedItem(),
                        (String) spin_cipher_mode.getSelectedItem(),
                        (String) spin_cipher_padding.getSelectedItem()
                    );
                    toaster(true, dialog_txt_crypto.getContext().getString(R.string.crypto_success));
                    dialog_txt_crypto.cancel();

                    // Rebuilding the total text
                    msg_texts[1] = msg_texts[0].concat(
                        msg_texts[1].substring(0, msg_texts[1].length() - 1).concat(msg_texts[2])
                    );
                    et_contents.setText(msg_texts[1]);
                    s_replace_start = s_replace_end = 0;
                } catch (Exception e) {
                    InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                    toaster(true, dialog_txt_crypto.getContext().getString(R.string.crypto_failure));
                    toaster(true, e.getMessage());
                }
            }
        );
    }

    private void toaster(boolean use_s, String s) {
        if (!use_s) s = getString(R.string.send_wait);
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    public void connection_security() {
        if (network_thread == null) return;
        last_connection_data_id = network_thread.last_connection_data_id;
        last_connection_data = network_thread.last_connection_data;
        if (last_connection_data_id > -1) {
            if (network_thread.last_connection_data_id == current_inbox.get_id()) {
                iv_ssl_auth.setVisibility(View.VISIBLE);
                if (!network_thread.last_connection_data.isEmpty()) {
                    iv_ssl_auth.setImageResource(R.drawable.padlock_normal);
                } else {
                    iv_ssl_auth.setImageResource(R.drawable.padlock_error);
                }
            } else {
                iv_ssl_auth.setVisibility(View.GONE);
            }
        } else {
            iv_ssl_auth.setVisibility(View.GONE);
            iv_ssl_auth.setImageResource(R.drawable.padlock_error);
            Dialogs.toaster(false, getString(R.string.err_action_failed), this);
        }
    }

    /**
     * Intermediaries' SSL certificates of the last live connection.
     **/
    private void dialog_servers() {
        Dialogs.dialog_simple(
            getString(R.string.ssl_auth_popup_title),
            last_connection_data == null ? getString(R.string.ssl_auth_popup_bad_connection)
                : last_connection_data,
            this
        );
    }
}
