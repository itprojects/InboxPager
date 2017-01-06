/**
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

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import net.inbox.db.DBAccess;
import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.dialogs.DialogsCerts;
import net.inbox.dialogs.Dialogs;
import net.inbox.dialogs.SpinningStatus;
import net.inbox.server.Handler;
import net.inbox.server.SMTP;

import java.io.File;
import java.util.ArrayList;

public class InboxSend extends AppCompatActivity {

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

    // GPG variables
    private boolean crypto_locked = false;
    private String msg_contents;

    private ListView attachments_list;
    private AlertDialog attachments_dialog;
    private ArrayList<String> attachment_paths = new ArrayList<>();
    private long attachments_size = 0;

    private Handler handler;
    private Message current = new Message();
    private Inbox current_inbox;

    private boolean warned_8_bit_absent = false;
    private boolean sending_active;
    private boolean good_incoming_server = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.send);

        // Get the database
        DBAccess db = Pager.get_db();

        current_inbox = db.get_account(getIntent().getExtras().getInt("db_id"));

        Toolbar tb = (Toolbar) findViewById(R.id.send_toolbar);
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

        if (getSupportActionBar() != null) {
            String s_title = getIntent().getExtras().getString("title");
            if (s_title != null) getSupportActionBar().setTitle(s_title.toUpperCase());
        }

        TextView tv_send = (TextView) findViewById(R.id.tv_send);
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

        et_to = (EditText) findViewById(R.id.send_to);
        sw_cc = (Switch) findViewById(R.id.send_cc_check);
        et_cc = (EditText) findViewById(R.id.send_cc);
        sw_bcc = (Switch) findViewById(R.id.send_bcc_check);
        et_bcc = (EditText) findViewById(R.id.send_bcc);
        et_subject = (EditText) findViewById(R.id.send_subject);
        et_contents = (EditText) findViewById(R.id.send_contents);
        tv_attachments = (TextView) findViewById(R.id.send_attachments_count);

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
        tv_attachments.setTypeface(Pager.tf);

        ImageView iv_attachments = (ImageView) findViewById(R.id.send_attachments_img);
        iv_attachments.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog_attachments();
            }
        });

        // Setting up the SSL authentication application
        iv_ssl_auth = (ImageView) findViewById(R.id.ssl_auth_img_vw);
        iv_ssl_auth.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog_servers();
            }
        });

        // Starts encryption
        iv_encryption = (ImageView) findViewById(R.id.iv_encryption);
        iv_encryption.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                gpg_crypto_tests();
            }
        });

        tv_encryption_reset = (TextView) findViewById(R.id.tv_encryption_reset);
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

        // If replying to a message
        String reply_to;
        String subject_of = "";
        if (getIntent().getExtras().containsKey("subject")) {
            subject_of = getIntent().getExtras().getString("subject");
        }
        if (getIntent().getExtras().containsKey("reply-to")) {
            reply_to = getIntent().getExtras().getString("reply-to");
            et_to.setText(reply_to);
            et_subject.setText(subject_of);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();
            File f = new File(uri.getPath());
            if (f.canRead()) {
                if (f.isFile()) {
                    attachment_paths.add(uri.getPath());
                    attachments_size += f.length();
                } else {
                    Dialogs.dialog_error_line(getString(R.string.send_folder_not_file), this);
                }
            } else {
                Dialogs.dialog_error_line(getString(R.string.err_read_file) + "\n\n"
                        + getString(R.string.err_read_file_storage), this);
            }
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
                current.set_contents_crypto(data.getStringExtra("message-crypto"));
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
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.right_in, R.anim.right_out);
    }

    private void dialog_attachments() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.send_attachments));
        populate_list_view();
        builder.setView(attachments_list);
        builder.setCancelable(true);
        builder.setPositiveButton(getString(R.string.attch_add_attachment),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("file:///*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(intent, "Pick Attachment"), 0);
                } catch (android.content.ActivityNotFoundException e) {
                    dialog_no_fm();
                }
            }
        });

        attachments_dialog = builder.show();

        if (!warned_8_bit_absent && !current_inbox.get_smtp_extensions().equals("-1")
                && !current_inbox.smtp_check_extension("8BITMIME")) {
            warned_8_bit_absent = true;
            Dialogs.dialog_error_line(getString(R.string.err_no_8_bit_mime), this);
        }
    }

    private void populate_list_view() {
        attachments_size = 0;
        attachments_list = new ListView(this);
        String[] values = new String[attachment_paths.size()];
        for (int i = 0;i < attachment_paths.size();++i) {
            String val = "[ " + (Uri.parse(attachment_paths.get(i))).getLastPathSegment() + " ], ";
            long sz = (new File(attachment_paths.get(i))).length();
            attachments_size += sz;
            if (sz < 1024) {
                val += sz + " " + getString(R.string.attch_bytes);
            } else if (sz >= 1024 && sz < 1048576) {
                val += (sz/1024) + " " + getString(R.string.attch_kilobytes);
            } else {
                val += (sz/1048576) + " " + getString(R.string.attch_megabytes);
            }
            values[i] = val;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                android.R.id.text1, values);
        attachments_list.setAdapter(adapter);
        attachments_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dialog_remove_attachment(position);
            }

        });
    }

    private void dialog_no_fm() {
        Dialogs.dialog_error_line(getString(R.string.err_no_file_manager), this);
    }

    private void dialog_remove_attachment(final int i) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage(getString(R.string.send_remove_attachment) + " "
                + (new File(attachment_paths.get(i))).getName() + "?");
        builder.setPositiveButton(getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                attachment_paths.remove(i);
                if (attachment_paths.size() > 0) {
                    tv_attachments.setText(String.valueOf(attachment_paths.size()));
                } else {
                    tv_attachments.setText("");
                }
                attachments_dialog.dismiss();
                dialog_attachments();
            }
        });
        builder.setCancelable(true);
        builder.show();
    }

    /**
     * Error checking before sending.
     **/
    private void send() {
        // Wait for the checks to complete
        sending_active = true;

        // Maximum SMTP message octet (byte) size
        String str1 = current_inbox.smtp_check_extension_return("SIZE");
        int total_size_limit = (str1 == null) ? 0 : Integer.parseInt(str1.substring(4).trim());

        // Default SMTP message size 64000 octets (bytes)
        if (total_size_limit == 0) total_size_limit = 64000;

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
                    s_to += new_to.get(i) + ",";
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
                        s_cc += new_to.get(i) + ",";
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
                        s_bcc += new_to.get(i) + ",";
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

        // Starting a spinning animation dialog
        SpinningStatus spt = new SpinningStatus(false, this, handler);
        spt.execute();
        spt.onProgressUpdate(getString(R.string.send_spin), "");

        // Starting SENDing message
        handler = new SMTP(this);
        handler.start();
        handler.sp = spt;
        if (attachment_paths.size() > 0) {
            String str = "";
            for (String st: attachment_paths) { str += st + "\uD83D\uDCCE"; }
            handler.msg_action(current_inbox.get_id(), current, str, false, this);
        } else {
            handler.msg_action(current_inbox.get_id(), current, null, false, this);
        }
    }

    public void connection_security() {
        good_incoming_server = handler.get_hostname_verify();
        if (good_incoming_server) {
            if (handler != null && handler.get_last_connection_data() != null
                    && (handler.get_last_connection_data_id() == current_inbox.get_id())) {
                good_incoming_server = handler.get_last_connection_data().size() > 0;
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
            DialogsCerts.dialog_certs(this, handler.get_last_connection_data());
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
            pack_man.getPackageInfo(Pager.open_key_chain, PackageManager.GET_ACTIVITIES);
            return pack_man.getApplicationInfo(Pager.open_key_chain, 0).enabled;
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

    private void toaster(boolean use_s, String s) {
        if (!use_s) s = getString(R.string.send_wait);
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}