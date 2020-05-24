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
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Base64;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import net.inbox.db.Attachment;
import net.inbox.db.DBAccess;
import net.inbox.db.Message;
import net.inbox.visuals.Dialogs;
import net.inbox.visuals.FileDownloadPicker;
import net.inbox.visuals.SpinningStatus;
import net.inbox.server.Handler;
import net.inbox.server.IMAP;
import net.inbox.server.POP;
import net.inbox.server.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.inbox.visuals.Dialogs.dialog_simple;

public class InboxMessage extends AppCompatActivity {

    // Prevents intent extras limit of < 1 MB
    protected static String msg_clear_text;

    private DBAccess db;
    private Handler handler;

    private WebView webview;
    private TextView tv_contents;
    private TextView tv_texts;
    private ImageView iv_ssl_auth;
    private ImageView iv_gpg_crypto;

    // GPG variables
    private boolean crypto_locked = false;
    private boolean msg_encrypted = false;
    private boolean msg_signed = false;
    private String msg_contents;
    private String msg_signature;

    private boolean imap_or_pop = false;
    private boolean no_send = false;

    private boolean good_incoming_server = false;

    private boolean btn_texts_ready = true;
    private int btn_texts_state = 0;
    private ArrayList<String[]> btn_texts_states;

    private int current_inbox = -2;

    private Message current;
    private Attachment chosen_att;
    private ArrayList<Attachment> attachments = new ArrayList<>();

    // Folder picker variables
    private boolean save_in_db;
    private String save_name_override = "";
    private File chosen_folder;
    private TextView tv_page_attachments;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.message);

        try {
            // Restore existing state
            if (savedInstanceState != null) {
                current_inbox = savedInstanceState.getInt("sv_current_inbox");
                crypto_locked = savedInstanceState.getBoolean("sv_crypto_locked");
                msg_encrypted = savedInstanceState.getBoolean("sv_msg_encrypted");
                msg_signed = savedInstanceState.getBoolean("sv_msg_signed");
                msg_contents = savedInstanceState.getString("sv_msg_contents");
                msg_signature = savedInstanceState.getString("sv_msg_signature");
                imap_or_pop = savedInstanceState.getBoolean("sv_imap_or_pop");
                no_send = savedInstanceState.getBoolean("sv_no_send");
                good_incoming_server = savedInstanceState.getBoolean("sv_good_incoming_server");
                btn_texts_ready = savedInstanceState.getBoolean("sv_btn_texts_ready");
                btn_texts_state = savedInstanceState.getInt("sv_btn_texts_state");
                save_in_db = savedInstanceState.getBoolean("sv_save_in_db");
            }

            // Get the database
            db = InboxPager.get_db();

            // Check for extras
            Bundle buns = getIntent().getExtras();

            if (buns != null) {
                current = db.get_message(buns.getInt("db_id"));
                current_inbox = buns.getInt("db_inbox");
                imap_or_pop = buns.getBoolean("imap_or_pop");
                no_send = buns.getBoolean("no_send");
            }

            // Crypto information
            if (current.get_contents_crypto() != null) {
                // GPG Encrypted
                msg_encrypted = current.get_content_type().toLowerCase()
                        .contains("application/pgp-encrypted");

                // GPG Signed
                msg_signed = current.get_content_type().toLowerCase()
                        .contains("application/pgp-signature");
            }

            Toolbar tb = findViewById(R.id.message_toolbar);
            setSupportActionBar(tb);

            // Find the title
            TextView message_title = tb.findViewById(R.id.message_title);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(false);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                if (buns != null) {
                    String s_title = buns.getString("title");
                    if (s_title != null) message_title.setText(s_title.toUpperCase());
                }
            }

            TextView tv_reply = findViewById(R.id.tv_reply);
            tv_reply.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    write_reply();
                }
            });
            if (no_send) {
                tv_reply.setVisibility(View.GONE);
            } else {
                tv_reply.setVisibility(View.VISIBLE);
            }

            // Message Attachments Counter
            tv_page_attachments = findViewById(R.id.message_attachments);
            tv_page_attachments.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    open_folder_picker(false);
                }
            });

            // Setting the number of attachments
            if (current.get_attachments() > 0) {
                tv_page_attachments.setVisibility(View.VISIBLE);
            } else {
                tv_page_attachments.setVisibility(View.GONE);
            }

            TextView tv_from = findViewById(R.id.message_from);
            TextView tv_cc = findViewById(R.id.message_cc);
            TextView tv_bcc = findViewById(R.id.message_bcc);
            TextView tv_subject = findViewById(R.id.message_subject);
            TextView tv_date = findViewById(R.id.message_date);
            tv_contents = findViewById(R.id.message_contents);
            tv_texts = findViewById(R.id.message_loop);

            webview = findViewById(R.id.message_contents_webview);
            webview.setBackgroundColor(Color.TRANSPARENT);
            Settings.setup_webview(webview.getSettings());

            // Setting the correct size
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            float sz = Float.parseFloat(prefs.getString("sett_msg_font_size", "100"));
            tv_from.setTextSize(sz);
            tv_cc.setTextSize(sz);
            tv_bcc.setTextSize(sz);
            tv_subject.setTextSize(sz);
            tv_date.setTextSize(sz);
            tv_contents.setTextSize(sz);
            tv_texts.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (btn_texts_states.size() > 1 && btn_texts_ready) {
                        btn_texts_ready = false;
                        if (btn_texts_state == (btn_texts_states.size() - 1)) {
                            btn_texts_state = 0;
                        } else {
                            ++btn_texts_state;
                        }
                        populate_contents();
                    }
                }
            });

            TextView tv_from_title = findViewById(R.id.message_from_title);
            tv_from_title.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // Dialog showing the IP or server of the message sender.
                    dialog_simple(getString(R.string.message_dialog), current.get_received(),
                            (AppCompatActivity) v.getContext());
                }
            });

            // Insert the data
            tv_from.setText(current.get_from());
            if (current.get_cc() == null) {
                tv_cc.setVisibility(View.GONE);
                findViewById(R.id.message_cc_title).setVisibility(View.GONE);
            } else {
                tv_cc.setText(current.get_cc());
            }
            if (current.get_bcc() == null) {
                tv_bcc.setVisibility(View.GONE);
                findViewById(R.id.message_bcc_title).setVisibility(View.GONE);
            } else {
                tv_bcc.setText(current.get_bcc());
            }
            tv_subject.setText(current.get_subject());
            tv_date.setText(current.get_date());

            // Counting the states of the texts' button
            set_btn_texts();

            // Setting up the SSL authentication application
            iv_ssl_auth = findViewById(R.id.ssl_auth_img_vw);
            iv_ssl_auth.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    dialog_servers();
                }
            });

            // GPG crypto activity
            iv_gpg_crypto = findViewById(R.id.iv_gpg_crypto);
            iv_gpg_crypto.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (!crypto_locked && crypto_package()) {
                        gpg_crypto_start();
                    }
                }
            });
            if (current.get_contents_crypto() != null) {
                iv_gpg_crypto.setVisibility(View.VISIBLE);
            } else {
                iv_gpg_crypto.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.message_action_btns, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.log_menu:
                Dialogs.dialog_view_log(this);
                break;
            case R.id.delete_from_server_menu:
                delete_message();
                break;
            case R.id.delete_from_local_menu:
                db.delete_full_message(current.get_id());
                current.set_full_msg(null);
                Dialogs.toaster(false, getString(R.string.message_no_full_message), this);
                break;
            case R.id.download_message_menu:
                // Save a message to internal database
                start_saving_full_message(true);
                break;
            case R.id.save_message_menu:
                // Save a message to permanent device memory
                open_folder_picker(true);
                break;
            case R.id.details_menu:
                // Display the full message
                if (current.get_full_msg() == null || current.get_full_msg().isEmpty()) {
                    Dialogs.toaster(false, getString(R.string.err_no_full_msg), this);
                } else {
                    Dialogs.dialog_view_message(current.get_full_msg(), this);
                }
                break;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (resultCode) {
            case 19091:// From InboxGPG
                crypto_locked = true;
                iv_gpg_crypto.setImageResource(R.drawable.padlock_open_inverse);
                msg_signature = data.getStringExtra("msg-signature");
                if (data.getIntExtra("ret-code", 0) == 92) {
                    msg_contents = msg_clear_text;
                    msg_clear_text = "";

                    // MIME parsing
                    gpg_mime_parsing();
                }
                if (msg_signature != null && msg_signature.length() > 0) {
                    final AppCompatActivity ct = this;
                    ImageView iv_gpg_signature = findViewById(R.id.iv_gpg_signature);
                    iv_gpg_signature.setVisibility(View.VISIBLE);
                    iv_gpg_signature.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            dialog_simple(getString(R.string.open_pgp_message_signature),
                                    msg_signature, ct);
                        }
                    });
                }
                break;
            case 101:// Download called from InboxMessage Full Message
                chosen_folder = new File(data.getStringExtra("chosen_folder"), "/");
                save_name_override = data.getStringExtra("chosen_name");
                if (current.get_full_msg() == null || current.get_full_msg().isEmpty()) {
                    dialog_download_and_keep_full_msg();
                } else {
                    write_full_message();
                }
                break;
            case 102:// Download called from InboxMessage Message Attachment
                chosen_att = null;
                String att_which_uuid = data.getStringExtra("chosen_attachment");
                chosen_folder = new File(data.getStringExtra("chosen_folder"), "/");
                save_name_override = data.getStringExtra("chosen_name");
                if (crypto_locked) {
                    for (Attachment at : attachments) {
                        if (at.get_pop_indx().equals(att_which_uuid)) {
                            chosen_att = at;
                            break;
                        }
                    }
                } else {
                    for (Attachment at : attachments) {
                        if (at.get_imap_uid() != null) {
                            // IMAP protocol
                            if (at.get_imap_uid().equals(att_which_uuid)) {
                                chosen_att = at;
                                break;
                            }
                        } else {
                            // POP protocol
                            if (at.get_pop_indx().equals(att_which_uuid)) {
                                chosen_att = at;
                                break;
                            }
                        }
                    }
                }

                if (chosen_att == null) return;
                chosen_att.set_name(save_name_override);

                if (crypto_locked) {
                    write_attachment_from_db();
                } else if (current.get_full_msg() == null || current.get_full_msg().isEmpty()) {
                    save_an_online_attachment();
                } else {
                    write_attachment_from_db();
                }
                break;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle save) {
        super.onSaveInstanceState(save);
        save.putInt("sv_current_inbox", current_inbox);
        save.putBoolean("sv_crypto_locked", crypto_locked);
        save.putBoolean("sv_msg_encrypted", msg_encrypted);
        save.putBoolean("sv_msg_signed", msg_signed);
        save.putString("sv_msg_contents", msg_contents);
        save.putString("sv_msg_signature", msg_signature);
        save.putBoolean("sv_imap_or_pop", imap_or_pop);
        save.putBoolean("sv_no_send", no_send);
        save.putBoolean("sv_good_incoming_server", good_incoming_server);
        save.putBoolean("sv_btn_texts_ready", btn_texts_ready);
        save.putInt("sv_btn_texts_state", btn_texts_state);
        save.putBoolean("sv_save_in_db", save_in_db);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.right_in, R.anim.right_out);
    }

    /**
     * Creates/refreshes the list of readable message texts.
     **/
    private void set_btn_texts() {
        btn_texts_states = new ArrayList<>();
        if (current.get_contents_plain() != null && !current.get_contents_plain().isEmpty()) {
            btn_texts_states.add(new String[] { getString(R.string.message_contents_loop_plain), "1"});
        }
        if (current.get_contents_html() != null && !current.get_contents_html().isEmpty()) {
            btn_texts_states.add(new String[] { getString(R.string.message_contents_loop_html), "2"});
        }
        if (btn_texts_states.size() < 1) {
            tv_texts.setVisibility(View.GONE);
        } else if (btn_texts_states.size() == 1) {
            btn_texts_state = 0;
            populate_contents();
        } else {
            for (int i = 0;i < btn_texts_states.size();++i) {
                btn_texts_states.get(i)[0] += " " + (i+1) + "/" + btn_texts_states.size();
            }
            btn_texts_state = 0;
            populate_contents();
        }
    }

    /**
     * Sets refreshed message texts.
     **/
    private void populate_contents() {
        tv_contents.setText("");
        switch (btn_texts_states.get(btn_texts_state)[1]) {
            case "0":
                break;
            case "1":
                if (current.get_contents_plain() == null) {
                    set_contents(true, "");
                } else {
                    set_contents(true, current.get_contents_plain());
                }
                tv_texts.setText(btn_texts_states.get(btn_texts_state)[0]);
                break;
            case "2":
                if (current.get_contents_html() == null) {
                    set_contents(false, "");
                } else {
                    set_contents(false, current.get_contents_html());
                }
                tv_texts.setText(btn_texts_states.get(btn_texts_state)[0]);
                break;
        }
        btn_texts_ready = true;
    }

    // plain is true for plaintext
    private void set_contents(boolean plain, String txt_html) {
        if (plain) {
            tv_contents.setVisibility(View.VISIBLE);
            webview.setVisibility(View.GONE);
            tv_contents.setText(txt_html);
        } else {
            tv_contents.setVisibility(View.GONE);
            webview.setVisibility(View.VISIBLE);
            webview.loadDataWithBaseURL(null, txt_html, "text/html", current.get_charset_html(), null);
        }
    }

    /**
     * Finishes activity to start a new message.
     **/
    private void write_reply() {
        if (no_send) {
            Dialogs.toaster(false, getString(R.string.err_server_not_configured), this);
            return;
        }

        String ret;
        Pattern pat = Pattern.compile(".*<(.*)>.*", Pattern.CASE_INSENSITIVE);
        Matcher mat = pat.matcher(current.get_from());
        if (mat.matches()) {
            ret = mat.group(1);
        } else {
            ret = current.get_from().trim();
        }

        // Declaring the result of the activity
        Intent ret_intent = new Intent();

        // Request ListView re-flow
        ret_intent = ret_intent.putExtra("reply-to", ret);
        if (current.get_cc() != null && !current.get_cc().trim().isEmpty()) {
            ret_intent = ret_intent.putExtra("reply-cc", current.get_cc());
        }
        ret_intent = ret_intent.putExtra("subject", current.get_subject());
        if (current.get_contents_plain() != null && !current.get_contents_plain().trim().isEmpty()) {
            ret_intent = ret_intent.putExtra("previous_letter", current.get_contents_plain());
        } else if (current.get_contents_html() != null && !current.get_contents_html().trim().isEmpty()) {
            ret_intent = ret_intent.putExtra("previous_letter", current.get_contents_html());
        } else {
            ret_intent = ret_intent.putExtra("previous_letter", "NO_TEXT");
        }
        setResult(10101, ret_intent);

        // End activity
        finish();
    }

    private void open_folder_picker(boolean full_message) {
        // Decrypted Attachments
        if (!crypto_locked) attachments = db.get_all_attachments_of_msg(current.get_id());

        if (check_read_and_writable()) {
            // Prepare to write a reply message
            Intent pick_intent = new Intent(getApplicationContext(), FileDownloadPicker.class);
            Bundle b = new Bundle();
            if (full_message) {
                b.putBoolean("full_msg_download", true);
                if (current.get_subject() == null || current.get_subject().isEmpty()) {
                    b.putString("full_msg_title", "E-mail.eml");
                } else {
                    b.putString("full_msg_title", current.get_subject() + ".eml");
                }
            } else {
                b.putBoolean("full_msg_download", false);
                pick_intent.putParcelableArrayListExtra("msg_attachments", attachments);
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
                        Manifest.permission.WRITE_EXTERNAL_STORAGE }, 61999);
            }
        }
    }

    /**
     * Dialog choose to keep a DB copy of the full message.
     **/
    private void dialog_download_and_keep_full_msg() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.progress_keep_msg));
        builder.setMessage(getString(R.string.message_keep_in_database));
        builder.setCancelable(false);
        builder.setPositiveButton(getString(R.string.btn_yes),

                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        start_saving_full_message(true);
                    }
                });
        builder.setNegativeButton(getString(R.string.btn_no),

                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        start_saving_full_message(false);
                    }
                });
        builder.show();
    }

    private boolean check_read_and_writable() {
        return ((checkCallingOrSelfPermission("android.permission.READ_EXTERNAL_STORAGE")
                == PackageManager.PERMISSION_GRANTED))
                && ((checkCallingOrSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                == PackageManager.PERMISSION_GRANTED));
    }

    private void save_an_online_attachment() {
        // Prevents screen rotation crash
        handle_orientation(true);

        // Starting a spinning animation dialog
        SpinningStatus spt = new SpinningStatus(false, false, this, handler);
        spt.execute();
        spt.onProgressUpdate(getString(R.string.progress_downloading),
                getString(R.string.progress_fetch_attachment));

        // Starting Handler
        if (imap_or_pop) {
            handler = new IMAP(this);
        } else {
            handler = new POP(this);
        }
        handler.sp = spt;
        handler.start();
        handler.attachment_action(current.get_account(), chosen_att, chosen_folder.getPath(), this);
    }

    /**
     * Continues the file saving process.
     * Convert attachments from BASE64 to normal.
     **/
    public void write_attachment_from_db() {
        if (imap_or_pop && !crypto_locked) {
            Pattern pat = Pattern.compile(".*boundary=\"(.*)\".*", Pattern.CASE_INSENSITIVE);
            Matcher mat = pat.matcher(current.get_content_type());
            if (mat.matches()) {
                chosen_att.set_boundary("--" + mat.group(1));
            } else {
                InboxPager.log = InboxPager.log.concat(getString(R.string.err_imap_attachment_saving) + "\n\n");
                dialog_simple(null, getString(R.string.err_imap_attachment_saving), this);
                return;
            }
        }

        if (chosen_att.get_imap_uid() == null) chosen_att.set_pop_indx(chosen_att.get_pop_indx());
        if (chosen_att.get_pop_indx() == null) chosen_att.set_pop_indx(chosen_att.get_imap_uid());

        String att;
        if (crypto_locked) {
            att = Utils.mime_part_section(current.get_contents_crypto(),
                    chosen_att.get_pop_indx(), chosen_att.get_boundary());
        } else {
            att = Utils.mime_part_section(current.get_full_msg(), chosen_att.get_pop_indx(),
                    chosen_att.get_boundary());
        }

        try {
            FileOutputStream f_stream = new FileOutputStream(new File(
                    chosen_folder.getAbsoluteFile() + "/" + chosen_att.get_name()));

            // Converting transfer encoding
            if (chosen_att != null) {
                // Parsing file download
                if (chosen_att.get_transfer_encoding().equalsIgnoreCase("BASE64")) {
                    try {
                        byte[] data = Base64.decode(att.getBytes(), Base64.DEFAULT);
                        f_stream.write(data);
                        f_stream.close();
                    } catch (Exception e) {
                        InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                        Dialogs.dialog_exception(e, this);
                    }
                } else {
                    // 7BIT, 8BIT, BINARY, QUOTED-PRINTABLE
                    f_stream.write(att.getBytes());
                    f_stream.close();
                }
            }
        } catch (IOException e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            Dialogs.dialog_exception(e, this);
        }
    }

    private void start_saving_full_message(boolean in_db_only) {
        // Prevents screen rotation crash
        handle_orientation(true);

        // Starting a spinning animation dialog
        SpinningStatus sp = new SpinningStatus(true, false, this, handler);
        sp.execute();
        sp.onProgressUpdate(getString(R.string.progress_downloading),
                getString(R.string.progress_fetch_msg) + ".");

        // Starting refresh INBOX
        if (imap_or_pop) {
            handler = new IMAP(this);
        } else {
            handler = new POP(this);
        }
        handler.sp = sp;
        handler.start();
        if (in_db_only) {
            handler.msg_action(current.get_account(), current, null, save_in_db, this);
        } else {
            handler.msg_action(current.get_account(), current,
                    chosen_folder.getPath() + "/" + save_name_override, save_in_db, this);
        }
    }

    /**
     * Available full messages are directly written to file.
     **/
    private void write_full_message() {
        // Prepare for direct file write
        try {
            FileOutputStream f_stream = new FileOutputStream(new File
                    (chosen_folder.getAbsoluteFile() + "/" + save_name_override));
            f_stream.write(current.get_full_msg().getBytes());
            f_stream.close();
            Dialogs.toaster(true, getString(R.string.progress_download_complete), this);
        } catch (IOException ioe) {
            InboxPager.log = InboxPager.log.concat(ioe.getMessage() + "\n\n");
            Dialogs.dialog_exception(ioe, this);
        }
    }

    /**
     * Deletes the current message from server and local database.
     **/
    private void delete_message() {
        // Prevents screen rotation crash
        handle_orientation(true);

        // Starting a spinning animation dialog
        SpinningStatus spt = new SpinningStatus(false, false, this, handler);
        spt.execute();
        spt.onProgressUpdate(getString(R.string.progress_deleting),
                getString(R.string.progress_deleting_msg) + " " + current.get_subject());

        // Starting Handler
        if (imap_or_pop) {
            handler = new IMAP(this);
        } else {
            handler = new POP(this);
        }
        handler.sp = spt;
        handler.start();
        handler.move_action(current.get_account(), current, this);
    }

    /**
     * Deletes the current message from UI.
     **/
    public void delete_message_ui() {
        // Request ListView re-flow
        setResult(1010101);

        // End activity
        finish();
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
                    && (handler.get_last_connection_data_id() == current_inbox)) {
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
            Dialogs.toaster(true, getString(R.string.err_action_failed), this);
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
            Dialogs.toaster(false, getString(R.string.open_pgp_none_found), this);
            return false;
        }
    }

    /**
     * Starts GPG message work.
     **/
    private void gpg_crypto_start() {
        // Starting decryption and verification intent
        Intent gpg = new Intent(this, InboxGPG.class);
        Bundle b = new Bundle();
        int request_code;
        if (msg_encrypted) {
            request_code = 92;

            // Extracting the encrypted part
            attachments = db.get_all_attachments_of_msg(current.get_id());
            boolean pgp_content = false;
            for (Attachment a : attachments) {
                if (a.get_mime_type().toLowerCase().contains("pgp-encrypted")) {
                    pgp_content = true;
                } else if (pgp_content) {
                    msg_contents = current.get_contents_crypto();
                    if (msg_contents == null) {
                        InboxPager.log = InboxPager.log.concat(getString(R.string.err_missing_crypto_mime) + "\n\n");
                        Dialogs.toaster(false, getString(R.string.err_missing_crypto_mime), this);
                        return;
                    }

                    int start_index = -1;
                    int end_index = -1;

                    Pattern p = Pattern.compile(".*(-----BEGIN PGP MESSAGE-----.*)",
                            Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
                    Matcher m = p.matcher(msg_contents);

                    if (m.matches()) start_index = m.start(1);

                    p = Pattern.compile(".*-----END PGP MESSAGE-----(.*)",
                            Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
                    m = p.matcher(msg_contents);

                    if (m.matches()) end_index = m.start(1);

                    if ((start_index > -1 && end_index > -1) && start_index < end_index) {
                        msg_contents = msg_contents.substring(start_index, end_index);
                    } else {
                        InboxPager.log = InboxPager.log.concat(getString(R.string.err_missing_crypto_mime) + "\n\n");
                        Dialogs.toaster(false, getString(R.string.err_missing_crypto_mime), this);
                        return;
                    }
                    break;
                }
            }
        } else if (msg_signed) {
            request_code = 93;

            // Extracting the signed part
            String[] ct_bonds = Utils.content_type_boundary(current.get_content_type());
            msg_contents = current.get_contents_crypto();
            int split_index = msg_contents.indexOf(ct_bonds[1]);
            if (split_index != -1) {
                msg_contents = msg_contents.substring(split_index + ct_bonds[1].length());

                // Twice to remove \r\n
                if (msg_contents.charAt(0) == '\r' || msg_contents.charAt(0) == '\n') {
                    msg_contents = msg_contents.substring(1);
                }
                if (msg_contents.charAt(0) == '\r' || msg_contents.charAt(0) == '\n') {
                    msg_contents = msg_contents.substring(1);
                }
            }
            split_index = msg_contents.indexOf(ct_bonds[1]);
            if (split_index != -1) {
                msg_contents = msg_contents.substring(0, split_index - 1).trim() + "\r\n";
            }

            msg_signature = Utils.mime_part_section(current.get_contents_crypto(), "2", ct_bonds[1]);
            b.putString("signature", msg_signature);// Get from pgp-mime part
        } else return;
        msg_clear_text = msg_contents;
        b.putInt("request-code", request_code);
        gpg = gpg.putExtras(b);

        startActivityForResult(gpg, request_code, null);
    }

    /**
     * MIME to message conversion after decryption.
     **/
    private void gpg_mime_parsing() {
        String c_type;
        if (msg_contents.length() > 500) {
            c_type = msg_contents.substring(0, 500);
        } else {
            c_type = msg_contents;
        }

        // Decrypted inner MIME Content-type & boundary
        String[] ct_bound = Utils.content_type_boundary(c_type);
        try {
            Pattern p = Pattern.compile(".*(" + ct_bound[1] + "(.*)" + ct_bound[1] + "--).*",
                    Pattern.DOTALL);
            Matcher m = p.matcher(msg_contents);
            if (m.matches()) {
                msg_contents = m.group(1);
                current.set_contents_crypto(msg_contents);
                String m_type = ct_bound[0].trim();

                // Structural parsing
                ArrayList<String[]> msg_structure = Utils.mime_bodystructure
                        (msg_contents, ct_bound[1], m_type);

                ArrayList<String[]> msg_texts = new ArrayList<>();
                Utils.mime_parse_full_msg_into_texts
                        (current.get_contents_crypto(), msg_structure, msg_texts, current);

                msg_contents = "";
                set_btn_texts();

                // Inner attachments assignment
                attachments = new ArrayList<>();
                if (msg_structure.size() > 0) {
                    for (int ii = 0;ii < msg_structure.size();++ii) {
                        // Mime part, i.e. - BODY[1], BODY[1.1], ...
                        Attachment att = new Attachment();
                        att.set_pop_indx(msg_structure.get(ii)[0]);
                        att.set_mime_type(msg_structure.get(ii)[1]);
                        att.set_boundary(msg_structure.get(ii)[2]);
                        att.set_name(msg_structure.get(ii)[3]);
                        att.set_transfer_encoding(msg_structure.get(ii)[4]);
                        att.set_size(-1);
                        attachments.add(att);
                    }
                } else {
                    tv_page_attachments.setVisibility(View.GONE);
                }
            } else {
                InboxPager.log = InboxPager.log.concat(getString(R.string.err_missing_crypto_mime) + "\n\n");
                dialog_simple(null, getString(R.string.err_missing_crypto_mime), this);
            }
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            Dialogs.dialog_exception(e, this);
        }
    }
}
