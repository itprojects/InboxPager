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

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.server.EndToEnd;
import net.inbox.server.Utils;
import net.inbox.visuals.Common;
import net.inbox.visuals.Dialogs;
import net.inbox.visuals.SendFilePicker;
import net.inbox.visuals.SpinningStatus;
import net.inbox.server.Handler;
import net.inbox.server.SMTP;

import java.util.ArrayList;
import java.util.HashMap;

public class InboxSend extends AppCompatActivity {

    // Prevents intent extras limit of < 1 MB
    protected static String msg_crypto;

    private Handler handler;

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
    private boolean good_incoming_server = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.send);

        try {
            // Restore existing state
            if (savedInstanceState != null) {
                crypto_locked = savedInstanceState.getBoolean("sv_crypto_locked");
                msg_contents = savedInstanceState.getString("sv_msg_contents");
                attachment_paths = savedInstanceState.getStringArrayList("sv_attachment_paths");
                if (attachment_paths == null) attachment_paths = new ArrayList<>();
                attachments_size = savedInstanceState.getLong("sv_attachments_size");
                total_size_limit = savedInstanceState.getLong("sv_total_size_limit");
                sending_active = savedInstanceState.getBoolean("sv_sending_active");
                good_incoming_server = savedInstanceState.getBoolean("sv_good_incoming_server");
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
            tv_send.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (sending_active) {
                        toaster(false, "");
                    } else {
                        send();
                    }
                }
            });

            et_to = findViewById(R.id.send_to);
            sw_cc = findViewById(R.id.send_cc_check);
            et_cc = findViewById(R.id.send_cc);
            sw_bcc = findViewById(R.id.send_bcc_check);
            et_bcc = findViewById(R.id.send_bcc);
            et_subject = findViewById(R.id.send_subject);
            et_contents = findViewById(R.id.send_contents);
            tv_attachments = findViewById(R.id.send_attachments_count);
            WebView webview = findViewById(R.id.send_contents_previous_webview);
            webview.setBackgroundColor(Color.TRANSPARENT);

            // Setting the correct size
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            float font_size = Float.parseFloat(prefs.getString("sett_msg_font_size", "100"));
            et_to.setTextSize(font_size);
            et_cc.setTextSize(font_size);
            et_bcc.setTextSize(font_size);
            et_subject.setTextSize(font_size);
            et_contents.setTextSize(font_size);
            tv_attachments.setTextSize(font_size);
            Common.setup_webview(webview.getSettings(), font_size);

            registerForContextMenu(et_contents);// Enables crypto ActionModes ContextMenus
            et_contents.setCustomSelectionActionModeCallback(new ActionMode.Callback() {

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
            });

            et_cc.setVisibility(View.GONE);
            et_bcc.setVisibility(View.GONE);
            sw_cc.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton v, boolean isChecked) {
                    if (isChecked) {
                        et_cc.setVisibility(View.VISIBLE);
                    } else {
                        et_cc.setVisibility(View.GONE);
                    }
                }
            });
            sw_bcc.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton v, boolean isChecked) {
                    if (isChecked) {
                        et_bcc.setVisibility(View.VISIBLE);
                    } else {
                        et_bcc.setVisibility(View.GONE);
                    }
                }
            });

            if (attachment_paths.size() > 0) {
                tv_attachments.setText(String.valueOf(attachment_paths.size()));
            } else {
                tv_attachments.setText("");
            }

            ImageView iv_attachments = findViewById(R.id.send_attachments_img);
            iv_attachments.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    pick_attachments();
                }
            });

            // Setting up the SSL authentication application
            iv_ssl_auth = findViewById(R.id.ssl_auth_img_vw);
            iv_ssl_auth.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    dialog_servers();
                }
            });

            // Starts encryption with AES
            ImageView iv_encryption_txt = findViewById(R.id.iv_encryption_txt);
            iv_encryption_txt.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (et_contents.getText().toString().isEmpty()) {
                        toaster(true, v.getContext().getString(R.string.crypto_no_text));
                    } else dialog_txt_crypto();
                }
            });

            // Starts encryption with PGP
            iv_encryption_pgp = findViewById(R.id.iv_encryption_pgp);
            iv_encryption_pgp.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    gpg_crypto_tests();
                }
            });

            tv_encryption_pgp_reset = findViewById(R.id.tv_encryption_pgp_reset);
            tv_encryption_pgp_reset.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    tv_encryption_pgp_reset.setVisibility(View.GONE);
                    current.set_contents_crypto(null);
                    et_contents.setText(msg_contents);
                    crypto_locked = false;
                    crypto_padlock();
                }
            });

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
                    if (buns.getString("previous_letter", "ERROR").equals("NO_TEXT")) {
                        llay_send_previous = findViewById(R.id.llay_send_previous);
                        llay_send_previous.setVisibility(View.GONE);
                        SwitchMaterial sw_previous = findViewById(R.id.send_sw_previous);
                        sw_previous.setVisibility(View.GONE);
                    } else {
                        llay_send_previous = findViewById(R.id.llay_send_previous);
                        llay_send_previous.setVisibility(View.VISIBLE);

                        String webview_charset = buns.getString("previous_letter_charset", "UTF-8");
                        webview.loadDataWithBaseURL(null,
                                buns.getString("previous_letter"),
                                buns.getBoolean("previous_letter_is_plain") ? "text/plain" : "text/html",
                                webview_charset, null);

                        SwitchMaterial sw_previous = findViewById(R.id.send_sw_previous);
                        sw_previous.setVisibility(View.VISIBLE);
                        sw_previous.setOnCheckedChangeListener(
                                new CompoundButton.OnCheckedChangeListener() {

                            @Override
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                if (isChecked) {
                                    llay_send_previous.setVisibility(View.VISIBLE);
                                } else {
                                    llay_send_previous.setVisibility(View.GONE);
                                }
                            }
                        });
                        sw_previous.setChecked(true);
                    }
                }
            }
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            finish();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            attachment_paths = data.getStringArrayListExtra("attachments");
            if (attachment_paths != null  && attachment_paths.size() > 0) {
                tv_attachments.setText(String.valueOf(attachment_paths.size()));
            } else {
                tv_attachments.setText("");
            }
        } else if (resultCode == 19091) {
            tv_encryption_pgp_reset.setVisibility(View.VISIBLE);
            crypto_locked = true;
            crypto_padlock();
            if (data.getIntExtra("ret-code", 0) != 0) {
                current.set_contents_crypto(msg_crypto);
                msg_crypto = "";
                if (current.get_contents_crypto() != null
                        && current.get_contents_crypto().length() > 500) {
                    et_contents.setText(current.get_contents_crypto().substring(0, 500));
                } else {
                    et_contents.setText(current.get_contents_crypto());
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle save) {
        super.onSaveInstanceState(save);
        save.putBoolean("sv_crypto_locked", crypto_locked);
        save.putString("sv_msg_contents", msg_contents);
        save.putStringArrayList("sv_attachment_paths", attachment_paths);
        save.putLong("sv_attachments_size", attachments_size);
        save.putLong("sv_total_size_limit", total_size_limit);
        save.putBoolean("sv_sending_active", sending_active);
        save.putBoolean("sv_good_incoming_server", good_incoming_server);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.right_in, R.anim.right_out);
    }

    private void pick_attachments() {
        if (!crypto_locked) {
            if (check_readable()) {
                // Prepare to write a reply message
                Intent pick_intent = new Intent(getApplicationContext(), SendFilePicker.class);
                Bundle b = new Bundle();
                b.putStringArrayList("str_array_paths", attachment_paths);
                b.putLong("l_attachment_size", attachments_size);
                b.putLong("l_total_size_limit", total_size_limit);
                if (!current_inbox.get_smtp_extensions().equals("-1")
                        && !current_inbox.smtp_check_extension("8BITMIME")) {
                    b.putBoolean("b_8_bit_absent", true);
                }
                startActivityForResult(pick_intent.putExtras(b), 19991);
                overridePendingTransition(0, 0);
            } else {
                // Missing permissions. Asking for read and write permissions.
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE }, 62999);
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

        if (to_count == 0) {
            // No recipient = no message
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
        if ((to_count + cc_count + bcc_count) > 100) {
            // Too many recipients
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
        handle_orientation(true);

        // Starting a spinning animation dialog
        SpinningStatus spt = new SpinningStatus(false, false, this, handler);
        spt.execute();
        spt.onProgressUpdate(getString(R.string.send_spin), "");

        // Starting SENDing message
        handler = new SMTP(this);
        handler.start();
        handler.sp = spt;
        if (attachment_paths.size() > 0) {
            handler.msg_action(current_inbox.get_id(), current, attachment_paths, false, this);
        } else {
            handler.msg_action(current_inbox.get_id(), current, null, false, this);
        }
    }

    public void handle_orientation(boolean fixed_or_rotating) {
        if (fixed_or_rotating) {
            InboxPager.orientation = getResources().getConfiguration().orientation;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else setRequestedOrientation(InboxPager.orientation);
    }

    public void connection_security() {
        if (handler == null) return;
        good_incoming_server = handler.get_hostname_verify();
        if (good_incoming_server) {
            if (handler != null && handler.get_last_connection_data() != null
                    && (handler.get_last_connection_data_id() == current_inbox.get_id())) {
                good_incoming_server = !handler.get_last_connection_data().isEmpty();
                iv_ssl_auth.setVisibility(View.VISIBLE);
                if (good_incoming_server) {
                    iv_ssl_auth.setImageResource(R.drawable.padlock_normal);
                } else {
                    iv_ssl_auth.setImageResource(R.drawable.padlock_error);
                }
            } else {
                good_incoming_server = false;
                iv_ssl_auth.setVisibility(View.GONE);
            }
        } else {
            iv_ssl_auth.setVisibility(View.VISIBLE);
            iv_ssl_auth.setImageResource(R.drawable.padlock_error);
            Dialogs.toaster(false, getString(R.string.err_action_failed), this);
        }
    }

    /**
     * Intermediaries' SSL certificates of the last live connection.
     **/
    private void dialog_servers() {
        Dialogs.dialog_view_ssl(this.good_incoming_server, this.handler, this);
    }

    /**
     * Looks for available and supported encryption packages.
     * OpenKeychain for GPG.
     **/
    private boolean crypto_package() {
        PackageManager pack_man = getPackageManager();
        try {
            pack_man.getPackageInfo(InboxPager.open_key_chain, PackageManager.GET_ACTIVITIES);
            return pack_man.getApplicationInfo(InboxPager.open_key_chain, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            toaster(true, getString(R.string.open_pgp_none_found));
            return false;
        }
    }

    /**
     * Tests message for required GPG parameters.
     **/
    private void gpg_crypto_tests() {
        // Testing OpenKeychain
        if (crypto_package()) {
            // Testing To
            String[] arr_to;
            String s_to = et_to.getText().toString().trim();
            if (s_to.isEmpty()) {
                toaster(true, getString(R.string.send_missing_rcpt_to));
                return;
            } else {
                arr_to = s_to.split(",");
                int to_count = 0;
                for (String s_to_name : arr_to) { if (!s_to_name.trim().isEmpty()) ++to_count; }
                if (to_count == 0) toaster(true, getString(R.string.send_missing_rcpt_to));
            }

            // Testing Subject
            String subject = et_subject.getText().toString();
            if (!subject.isEmpty()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.send_cleartext_subject));
                builder.setMessage(getString(R.string.send_cleartext_content));
                builder.setPositiveButton(getString(R.string.send_cleartext_delete),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            et_subject.setText("");
                            gpg_crypto_start();
                        }
                    }
                );
                builder.setNegativeButton(getString(R.string.send_cleartext_keep),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            gpg_crypto_start();
                        }
                    }
                );
                builder.show();
            } else {
                gpg_crypto_start();
            }
        }
    }

    /**
     * Starts GPG message work.
     **/
    private void gpg_crypto_start() {
        // Testing attachments
        boolean empty_contents = et_contents.getText().toString().isEmpty();
        boolean mime_attachments = attachment_paths.size() > 0;
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
            gpg = gpg.putExtras(b);
            startActivityForResult(gpg, 91, null);
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
            iv_encryption_pgp.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.padlock_pgp_closed));
        } else {
            et_to.setEnabled(true);
            et_cc.setEnabled(true);
            et_bcc.setEnabled(true);
            et_subject.setEnabled(true);
            et_contents.setEnabled(true);
            sw_cc.setEnabled(true);
            sw_bcc.setEnabled(true);
            tv_attachments.setEnabled(true);
            iv_encryption_pgp.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.padlock_pgp_open));
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
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
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
                });
        dialog_txt_crypto.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
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
                            msg_texts[1] = msg_texts[0].concat(msg_texts[1]
                                    .substring(0, msg_texts[1].length() - 1).concat(msg_texts[2]));
                            et_contents.setText(msg_texts[1]);
                            s_replace_start = s_replace_end = 0;
                        } catch (Exception e) {
                            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                            toaster(true, dialog_txt_crypto.getContext().getString(R.string.crypto_failure));
                            toaster(true, e.getMessage());
                        }
                    }
                });
    }

    private boolean check_readable() {
        return (checkCallingOrSelfPermission("android.permission.READ_EXTERNAL_STORAGE")
                == PackageManager.PERMISSION_GRANTED);
    }

    private void toaster(boolean use_s, String s) {
        if (!use_s) s = getString(R.string.send_wait);
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
