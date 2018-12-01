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

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.dialogs.Dialogs;
import net.inbox.dialogs.SendFilePicker;
import net.inbox.dialogs.SpinningStatus;
import net.inbox.server.Handler;
import net.inbox.server.SMTP;

import java.util.ArrayList;

public class InboxSend extends AppCompatActivity {

    // Prevents intent extras limit of < 1 MB
    protected static String msg_crypto;

    private Handler handler;

    private ImageView iv_ssl_auth;
    private EditText et_subject;
    private EditText et_to;
    private Switch sw_cc;
    private EditText et_cc;
    private Switch sw_bcc;
    private EditText et_bcc;
    private EditText et_contents;
    private TextView tv_attachments;
    private ImageView iv_encryption;
    private TextView tv_encryption_reset;
    private TextView tv_previous;

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

            // Get the database
            current_inbox = InboxPager.get_db().get_account(getIntent().getExtras().getInt("db_id"));

            Toolbar tb = findViewById(R.id.send_toolbar);
            setSupportActionBar(tb);

            // Find the title
            TextView send_title = tb.findViewById(R.id.send_title);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(false);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                String s_title = getIntent().getExtras().getString("title");
                if (s_title != null) send_title.setText(s_title.toUpperCase());
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

            // Starts encryption
            iv_encryption = findViewById(R.id.iv_encryption);
            iv_encryption.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    gpg_crypto_tests();
                }
            });

            tv_encryption_reset = findViewById(R.id.tv_encryption_reset);
            tv_encryption_reset.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    tv_encryption_reset.setVisibility(View.GONE);
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
            String subject_of = "";
            if (getIntent().getExtras().containsKey("subject")) {
                subject_of = getIntent().getExtras().getString("subject");
                et_subject.setText(subject_of);
            }
            if (getIntent().getExtras().containsKey("reply-to")) {
                reply_to = getIntent().getExtras().getString("reply-to");
                et_to.setText(reply_to);
            }
            if (getIntent().getExtras().containsKey("reply-cc")) {
                et_cc.setText(getIntent().getExtras().getString("reply-cc"));
                sw_cc.setChecked(true);
            }
            if (getIntent().getExtras().containsKey("previous_letter")) {
                if (getIntent().getExtras().getString("previous_letter").equals("NO_TEXT")) {
                    tv_previous = findViewById(R.id.send_previous);
                    tv_previous.setVisibility(View.GONE);
                    Switch sw_previous = findViewById(R.id.send_sw_previous);
                    sw_previous.setVisibility(View.GONE);
                } else {
                    tv_previous = findViewById(R.id.send_previous);
                    tv_previous.setText(getIntent().getExtras().getString("previous_letter"));
                    Switch sw_previous = findViewById(R.id.send_sw_previous);
                    sw_previous.setVisibility(View.VISIBLE);
                    sw_previous.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (isChecked) {
                                tv_previous.setVisibility(View.VISIBLE);
                            } else {
                                tv_previous.setVisibility(View.GONE);
                            }
                        }
                    });
                    sw_previous.setChecked(true);
                }
            }
        } catch (Exception e) {
            InboxPager.log += e.getMessage() + "\n\n";
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            attachment_paths = data.getStringArrayListExtra("attachments");
            if (attachment_paths.size() > 0) {
                tv_attachments.setText(String.valueOf(attachment_paths.size()));
            } else {
                tv_attachments.setText("");
            }
        } else if (resultCode == 19091) {
            tv_encryption_reset.setVisibility(View.VISIBLE);
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
                // Permissions to read files missing
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.err_title_android_permission));
                builder.setMessage(getString(R.string.err_msg_android_permission));
                builder.setPositiveButton(getString(android.R.string.ok), null);
                builder.show();
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

        // Testing To
        String s_to = et_to.getText().toString().trim();
        if (s_to.isEmpty()) {
            // No recipient = no message
            Dialogs.dialog_error_line(getString(R.string.err_no_rcpt), this);
            sending_active = false;
            return;
        }
        String[] arr_to = s_to.split(",");
        int to_count = 0;
        for (String s_to_name : arr_to) { if (!s_to_name.trim().isEmpty()) ++to_count; }
        if (to_count == 0) {
            // No recipient = no message
            Dialogs.dialog_error_line(getString(R.string.err_no_rcpt), this);
            sending_active = false;
            return;
        }
        if (arr_to.length != to_count) {
            ArrayList<String> new_to = new ArrayList<>();
            for (String s_to_name : arr_to) {
                if (!s_to_name.trim().isEmpty()) {
                    new_to.add(s_to_name.trim());
                }
            }
            s_to = "";
            for (int i = 0;i < new_to.size();++i) {
                if (i == (new_to.size() - 1)) {
                    s_to += new_to.get(i);
                } else {
                    s_to = s_to.concat(new_to.get(i) + ",");
                }
            }
        }
        int to_size = s_to.getBytes().length;

        // Testing Carbon Copy
        String s_cc = "";
        int cc_count = 0;
        if (sw_cc.isChecked()) s_cc = et_cc.getText().toString().trim();
        if (!s_cc.isEmpty()) {
            String[] arr_cc = s_cc.split(",");
            for (String s_to_name : arr_cc) { if (!s_to_name.trim().isEmpty()) ++cc_count; }
            if (arr_cc.length != to_count) {
                ArrayList<String> new_to = new ArrayList<>();
                for (String s_to_name : arr_cc) {
                    if (!s_to_name.trim().isEmpty()) {
                        new_to.add(s_to_name.trim());
                    }
                }
                s_cc = "";
                for (int i = 0;i < new_to.size();++i) {
                    if (i == (new_to.size() - 1)) {
                        s_cc += new_to.get(i);
                    } else {
                        s_cc = s_cc.concat(new_to.get(i) + ",");
                    }
                }
            }
        }
        int cc_size = s_cc.getBytes().length;

        // Testing Blind Carbon Copy
        String s_bcc = "";
        int bcc_count = 0;
        if (sw_bcc.isChecked()) s_bcc = et_bcc.getText().toString().trim();
        if (!s_bcc.isEmpty()) {
            String[] arr_bcc = s_bcc.split(",");
            for (String s_to_name : arr_bcc) { if (!s_to_name.trim().isEmpty()) ++bcc_count; }
            if (arr_bcc.length != to_count) {
                ArrayList<String> new_to = new ArrayList<>();
                for (String s_to_name : arr_bcc) {
                    if (!s_to_name.trim().isEmpty()) {
                        new_to.add(s_to_name.trim());
                    }
                }
                s_bcc = "";
                for (int i = 0;i < new_to.size();++i) {
                    if (i == (new_to.size() - 1)) {
                        s_bcc += new_to.get(i);
                    } else {
                        s_bcc = s_bcc.concat(new_to.get(i) + ",");
                    }
                }
            }
        }
        int bcc_size = s_bcc.getBytes().length;

        // Testing if there are more than 100 recipients
        if ((to_count + cc_count + bcc_count) > 100) {
            // Too many recipients
            Dialogs.dialog_error_line(getString(R.string.err_too_many_rcpt), this);
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
            Dialogs.dialog_error_line(getString(R.string.err_size_attachments), this);
            sending_active = false;
            return;
        }

        long current_total_size = subject_size + to_size + text_size + attachments_size;

        if (sw_cc.isChecked()) current_total_size += cc_size;

        if (sw_bcc.isChecked()) current_total_size += bcc_size;

        if (current_total_size >= total_size_limit) {
            // Server will refuse the message, it's too big
            Dialogs.dialog_error_line(getString(R.string.err_msg_too_heavy), this);
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
            String str = "";
            for (String st: attachment_paths) { str = str.concat(st + "\uD83D\uDCCE"); }
            handler.msg_action(current_inbox.get_id(), current, str, false, this);
        } else {
            handler.msg_action(current_inbox.get_id(), current, null, false, this);
        }
    }

    @SuppressLint("WrongConstant")
    public void handle_orientation(boolean fixed_or_rotating) {
        if (fixed_or_rotating) {
            InboxPager.orientation = getResources().getConfiguration().orientation;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else setRequestedOrientation(InboxPager.orientation);
    }

    public void connection_security() {
        good_incoming_server = handler.get_hostname_verify();
        if (good_incoming_server) {
            if (handler != null && handler.get_last_connection_data() != null
                    && (handler.get_last_connection_data_id() == current_inbox.get_id())) {
                good_incoming_server = !handler.get_last_connection_data().isEmpty();
                iv_ssl_auth.setVisibility(View.VISIBLE);
                if (good_incoming_server) {
                    good_incoming_server = true;
                    iv_ssl_auth.setImageResource(R.drawable.padlock_normal);
                } else {
                    good_incoming_server = false;
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
        if (good_incoming_server) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.ssl_auth_popup_title));
            builder.setCancelable(true);
            builder.setMessage(handler.get_last_connection_data());
            builder.show();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.ssl_auth_popup_title));
            builder.setMessage(getString(R.string.ssl_auth_popup_bad_connection));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.show();
        }
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
            tv_encryption_reset.setVisibility(View.VISIBLE);
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
            overridePendingTransition(R.anim.left_in, R.anim.left_out);
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
            iv_encryption.setImageResource(R.drawable.padlock_closed);
        } else {
            et_to.setEnabled(true);
            et_cc.setEnabled(true);
            et_bcc.setEnabled(true);
            et_subject.setEnabled(true);
            et_contents.setEnabled(true);
            sw_cc.setEnabled(true);
            sw_bcc.setEnabled(true);
            tv_attachments.setEnabled(true);
            iv_encryption.setImageResource(R.drawable.padlock_open);
        }
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
