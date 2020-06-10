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
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import android.util.Base64;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;

import net.inbox.db.Attachment;
import net.inbox.db.DBAccess;
import net.inbox.db.Message;
import net.inbox.server.EndToEnd;
import net.inbox.visuals.Common;
import net.inbox.visuals.Dialogs;
import net.inbox.visuals.AttachmentDownloadPicker;
import net.inbox.visuals.SpinningStatus;
import net.inbox.server.Handler;
import net.inbox.server.IMAP;
import net.inbox.server.POP;
import net.inbox.server.Utils;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.inbox.visuals.Dialogs.dialog_simple;

public class InboxMessage extends AppCompatActivity {

    // Prevents intent extras limit of < 1 MB
    protected static String msg_clear_text;// PGP usage

    private DBAccess db;
    private Handler handler;

    private WebView webview;
    private Spinner message_spinner_texts;
    private ImageView iv_ssl_auth;
    private ImageView iv_gpg_crypto;

    private Date msg_date;
    private PopupWindow tv_date_raw_popup;// Original RFC 2822
    private TextView tv_date;
    private TextView tv_contents;
    private ImageView iv_decryption_txt;

    // Text message encryption
    private AlertDialog dialog_txt_crypto;
    private Spinner spin_cipher_type;
    private Spinner spin_cipher_mode;
    private Spinner spin_cipher_padding;
    private EditText et_key;
    private int s_replace_start = 0;
    private int s_replace_end = 0;

    // Hold decrypted cryptogram texts TXT/PLAIN, TXT/HTML
    private String plain_decrypts;
    private String html_decrypts;

    // GPG variables
    private boolean crypto_locked = false;
    private boolean msg_encrypted = false;
    private boolean msg_signed = false;
    private String msg_contents;
    private String msg_signature;

    private boolean imap_or_pop = false;
    private boolean no_send = false;

    private boolean good_incoming_server = false;

    private int current_inbox = -2;

    private Message current;
    private Attachment chosen_att;
    private ArrayList<Attachment> attachments = new ArrayList<>();
    private LinkedHashMap<String, Integer> text_types;// PLAIN, HTML, UNSUPPORTED

    // Folder picker variables
    private boolean save_in_db;
    private DocumentFile chosen_folder;
    private TextView tv_page_attachments;

    @SuppressLint("SimpleDateFormat")
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
            TextView tv_to = findViewById(R.id.message_to);
            TextView tv_cc = findViewById(R.id.message_cc);
            TextView tv_bcc = findViewById(R.id.message_bcc);
            TextView tv_subject = findViewById(R.id.message_subject);
            TextView tv_date_title = findViewById(R.id.message_date_title);
            tv_date = findViewById(R.id.message_date);
            message_spinner_texts = findViewById(R.id.message_spinner_texts);
            tv_contents = findViewById(R.id.message_contents);
            webview = findViewById(R.id.message_contents_webview);
            webview.setBackgroundColor(Color.TRANSPARENT);

            // Setting the correct size
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            float font_size = Float.parseFloat(prefs.getString("sett_msg_font_size", "100"));
            tv_from.setTextSize(font_size);
            tv_to.setTextSize(font_size);
            tv_cc.setTextSize(font_size);
            tv_bcc.setTextSize(font_size);
            tv_subject.setTextSize(font_size);
            tv_date.setTextSize(font_size);
            tv_contents.setTextSize(font_size);
            Common.setup_webview(webview.getSettings(), font_size);
            registerForContextMenu(webview);// Enables crypto ActionModes

            // Insert the data
            TextView tv_from_title = findViewById(R.id.message_from_title);
            tv_from_title.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // Dialog showing the IP or server of the message sender
                    dialog_simple(getString(R.string.message_dialog), current.get_received(),
                            (AppCompatActivity) v.getContext());
                }
            });

            // Sender may have multiple visible recipients
            tv_from.setText(current.get_from());
            if (current.get_to().contains(",")) {
                tv_to.setText(current.get_to());
            } else {
                tv_to.setVisibility(View.GONE);
                findViewById(R.id.message_to_title).setVisibility(View.GONE);
            }
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

            // Handling Date/Calendar
            msg_date = Utils.parse_date(current.get_date());
            if (msg_date != null) {
                tv_date.setText(new SimpleDateFormat("EEEE d, MMMM yyyy, H:mm")
                        .format(msg_date));
            } else tv_date.setText(current.get_date());
            tv_date_title.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (msg_date == null) return;
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(msg_date);
                    new DatePickerDialog (v.getContext(), null, cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
                }
            });
            tv_date.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (tv_date_raw_popup != null) tv_date_raw_popup.dismiss();
                    LayoutInflater layoutInflater = (LayoutInflater) InboxMessage.
                            this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                    TextView tv = (TextView) layoutInflater.inflate(R.layout.popup,null);

                    String s_popup_msg = v.getContext().getString(R.string.message_date_original) +
                            current.get_date();
                    tv.setText(s_popup_msg);

                    tv_date_raw_popup = new PopupWindow(tv,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT, true);

                    tv_date_raw_popup.showAsDropDown(tv_date);
                    tv_date_raw_popup.setOutsideTouchable(true);
                    tv_date_raw_popup.setFocusable(true);
                    tv_date_raw_popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }
            });

            // Prepare different message text types
            set_spinner_texts();

            message_spinner_texts.setOnItemSelectedListener(
                    new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                    call_contents_update();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            registerForContextMenu(tv_contents);// Enables crypto ActionModes ContextMenus
            tv_contents.setCustomSelectionActionModeCallback(new ActionMode.Callback() {

                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    // Adding crypto options
                    MenuInflater inflater = getMenuInflater();
                    inflater.inflate(R.menu.crypto_action_btns, menu);
                    menu.findItem(R.id.menu_gnu_encrypt).setVisible(false);

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
                        s_replace_start = tv_contents.getSelectionStart();
                        s_replace_end = tv_contents.getSelectionEnd();

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

            // Text decryption button, working with html
            iv_decryption_txt = findViewById(R.id.iv_decryption_txt);
            iv_decryption_txt.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (tv_contents.getVisibility() == View.GONE) {
                        tv_contents.setVisibility(View.VISIBLE);
                        tv_contents.setText(html_decrypts == null ? current.get_contents_html() :
                                html_decrypts);
                    } else {
                        if (html_decrypts != null && !html_decrypts.isEmpty()) {
                            set_contents(false, html_decrypts);
                        } else {
                            // Set original
                            set_contents(false, current.get_contents_html());
                        }
                    }
                }
            });
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
        if (requestCode == 100 && resultCode == RESULT_OK) {
            if (data.getData() != null) {
                Uri uri = Uri.parse(String.valueOf(data.getData()));
                Common.check_write_give(this, uri);
                chosen_folder = DocumentFile.fromTreeUri(this, uri);

                // Cannot write to this folder, permissions on the device
                if (chosen_folder != null && !chosen_folder.canWrite()) {
                    dialog_simple(getString(R.string.err_title_write_perms),
                            getString(R.string.err_msg_write_perms), this);
                    return;
                }

                // Not enough space, and also not an exact calculation
                if (Utils.capacity_exists(current.get_size())) {
                    dialog_simple(getString(R.string.err_title_no_space),
                            getString(R.string.err_msg_no_space), this);
                    return;
                }

                if (current.get_full_msg() == null || current.get_full_msg().isEmpty()) {
                    start_saving_full_message(false);
                } else {
                    write_full_message();
                }
            }
        }
        switch (resultCode) {
            case 19091:// From InboxGPG
                crypto_locked = true;
                iv_gpg_crypto.setImageResource(R.drawable.padlock_pgp_open_inverted);
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
            case 102:// Download called from InboxMessage Message Attachment
                Uri uri = Uri.parse(String.valueOf(data.getStringExtra("chosen_folder")));
                Common.check_write_give(this, uri);
                chosen_folder = DocumentFile.fromTreeUri(this, uri);
                chosen_att = null;
                String att_which_uuid = data.getStringExtra("chosen_attachment");
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
                chosen_att.set_name(data.getStringExtra("chosen_name"));

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
        save.putBoolean("sv_save_in_db", save_in_db);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.right_in, R.anim.right_out);
    }

    /**
     * Creates/refreshes the list of readable message texts.
     * PLAIN, HTML, UNSUPPORTED, CRYPTO/TXT, PGP, NO TEXT
     **/
    private void set_spinner_texts() {
        text_types = new LinkedHashMap<>();

        if (current.get_contents_plain() != null)//PLAIN
            text_types.put(getString(R.string.message_contents_loop_plain), 1);

        if (current.get_contents_html() != null)//HTML
            text_types.put(getString(R.string.message_contents_loop_html), 2);

        // Unsupported message format, ex: markdown, pdf, rtf
        if (current.get_contents_other() != null)//UNSUPPORTED
            text_types.put(getString(R.string.crypto_unsupported), 3);

        // Still empty, NO TEXT
        if (text_types.size() == 0)//"NO TEXT"
            text_types.put(getString(R.string.message_contents_loop_no), 0);

        String[] types = text_types.keySet().toArray(new String[0]);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this,
                R.layout.spinner_item, types);
        message_spinner_texts.setAdapter(adapter);
    }

    private int get_selected_spin() {
        return text_types.get(message_spinner_texts.getSelectedItem().toString());
    }

    private void call_contents_update() {
        populate_contents(get_selected_spin(), false);
    }

    /**
     * Sets refreshed message texts.
     **/
    private void populate_contents(int type, boolean isCryptogram) {
        switch (type) {
            case 1://PLAIN
            case 3://UNSUPPORTED
                iv_decryption_txt.setVisibility(View.GONE);
                if (isCryptogram && (plain_decrypts == null)) {
                    set_contents(true, "");
                } else if (isCryptogram) {
                    set_contents(true, plain_decrypts);
                } else {
                    set_contents(true, current.get_contents_plain() == null ?
                            "" : current.get_contents_plain());
                }
                break;
            case 2://HTML
                iv_decryption_txt.setVisibility(View.VISIBLE);
                if (isCryptogram && (html_decrypts == null)) {
                    set_contents(false, "");
                } else if (isCryptogram) {
                    set_contents(false, html_decrypts);
                } else {
                    set_contents(false, current.get_contents_html() == null ?
                            "" : current.get_contents_html());
                }
                break;
            case 0:
                // NO TEXT
                break;
        }
    }

    // plain mime-type: text/plain, text/html
    private void set_contents(boolean plain, String data) {
        tv_contents.setText("");
        if (plain) {
            webview.setVisibility(View.GONE);
            webview.loadDataWithBaseURL(null, "", "text/html", "UTF-8", null);
            tv_contents.setVisibility(View.VISIBLE);
            tv_contents.setText(data);
        } else {
            tv_contents.setVisibility(View.GONE);
            webview.setVisibility(View.VISIBLE);
            webview.loadDataWithBaseURL(null, data, "text/html", current.get_charset_html(), null);
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
            ret_intent = ret_intent.putExtra("previous_letter_is_plain", true);
            ret_intent = ret_intent.putExtra("previous_letter_charset", current.get_charset_plain());
            ret_intent = ret_intent.putExtra("previous_letter", current.get_contents_plain());
        } else if (current.get_contents_html() != null && !current.get_contents_html().trim().isEmpty()) {
            ret_intent = ret_intent.putExtra("previous_letter_is_plain", false);
            ret_intent = ret_intent.putExtra("previous_letter_charset", current.get_charset_html());
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

        if (Common.check_permissions(this)) {
            if (full_message) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addCategory(Intent.CATEGORY_DEFAULT);
                startActivityForResult(Intent.createChooser(i, getString(R.string.folder_title)), 100);
            } else {
                // Prepare to write a reply message
                Intent i = new Intent(getApplicationContext(), AttachmentDownloadPicker.class);
                i.putParcelableArrayListExtra("msg_attachments", attachments);
                startActivityForResult(i, 19991);
                overridePendingTransition(0, 0);
            }
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
        handler.attachment_action(current.get_account(), chosen_att, chosen_folder, this);
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
                InboxPager.log = InboxPager.log.concat(getString(
                        R.string.err_imap_attachment_saving) + "\n\n");
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
            DocumentFile new_file = chosen_folder.createFile(
                    "application/octet-stream", chosen_att.get_name());
            OutputStream os = getContentResolver().openOutputStream(new_file.getUri(), "rw");

            // Converting transfer encoding
            if (chosen_att != null && os != null) {
                // Parsing file download
                try {
                    if (chosen_att.get_transfer_encoding().equalsIgnoreCase("BASE64")) {
                        byte[] data = Base64.decode(att.getBytes(), Base64.DEFAULT);
                        os.write(data);
                    } else if (chosen_att.get_transfer_encoding()
                            .equalsIgnoreCase("QUOTED-PRINTABLE")) {
                        //UTF-8 is a guess
                        os.write(Utils.parse_quoted_printable(att, "UTF-8").getBytes());
                    } else {
                        // 7BIT, 8BIT, BINARY, QUOTED-PRINTABLE
                        os.write(att.getBytes());
                    }
                    os.close();
                    Dialogs.toaster(true, getString(R.string.message_action_done), this);
                } catch (Exception e) {
                    InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                    Dialogs.dialog_exception(e, this);
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
            handler.msg_action(current.get_account(), current, chosen_folder, save_in_db, this);
        }
    }

    /**
     * Available full messages are directly written to file.
     **/
    private void write_full_message() {
        // Prepare for direct file write
        Common.check_write_give(this, chosen_folder.getUri());
        try {
            DocumentFile new_file = chosen_folder.createFile(
                    "application/octet-stream", current.get_subject() + ".eml");
            OutputStream os = getContentResolver().openOutputStream(new_file.getUri());
            if (os != null) {
                os.write(current.get_full_msg().getBytes());
                os.flush();
                os.close();
                Dialogs.toaster(true, getString(R.string.progress_download_complete), this);
            }
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
     * Looks for available and supported encryption packages. OpenKeychain for PGP.
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
     * Starts PGP message work.
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
     * PGP MIME to message conversion after decryption.
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

                // Clean-up and display of texts
                msg_contents = "";
                plain_decrypts = "";
                html_decrypts = "";
                set_spinner_texts();
                call_contents_update();

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

    /**
     * Text message cryptography. AES, Twofish, not PGP. Does not decrypt any attachments.
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

        dialog_txt_crypto = builder.show();
        spin_cipher_type = dialog_txt_crypto.findViewById(R.id.spin_cipher);
        spin_cipher_mode = dialog_txt_crypto.findViewById(R.id.spin_cipher_mode);
        spin_cipher_padding = dialog_txt_crypto.findViewById(R.id.spin_cipher_padding);
        et_key = dialog_txt_crypto.findViewById(R.id.et_key);

        dialog_txt_crypto.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        decrypt_cryptogram();
                    }
                });
    }

    /**
     * Text message cryptography. AES, Twofish, not PGP. Does not decrypt any attachments.
     **/
    private void decrypt_cryptogram() {
        try {
            // [1] head, [2] middle to replace or full if note replacing, [3] tail
            String[] msg_texts = new String[]{ "", null, ""};
            if (s_replace_start != 0 || s_replace_end != 0) {
                msg_texts[0] = tv_contents.getText().toString()
                        .substring(0, s_replace_start);
                msg_texts[1] = tv_contents.getText().toString()
                        .substring(s_replace_start, s_replace_end);
                msg_texts[2] = tv_contents.getText().toString()
                        .substring(s_replace_end, tv_contents.length());
            } else {
                msg_texts[1] = tv_contents.toString();
            }
            msg_texts[1] = EndToEnd.decrypt(
                    dialog_txt_crypto.getContext(),
                    et_key.getText().toString(),
                    msg_texts[1],
                    (String) spin_cipher_type.getSelectedItem(),
                    (String) spin_cipher_mode.getSelectedItem(),
                    (String) spin_cipher_padding.getSelectedItem()
            );
            Dialogs.toaster(true, dialog_txt_crypto.getContext()
                    .getString(R.string.crypto_success), this);
            dialog_txt_crypto.cancel();

            // Rebuilding complete text
            msg_texts[1] = msg_texts[0].concat(msg_texts[1]).concat(msg_texts[2]);

            // Setting buffers
            if (get_selected_spin() == 1) {// PLAIN
                plain_decrypts = msg_texts[1];
            } else if (get_selected_spin() == 2) {// HTML
                html_decrypts = msg_texts[1];
            }
            populate_contents(get_selected_spin(), true);
            s_replace_start = s_replace_end = 0;
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            Dialogs.toaster(true, dialog_txt_crypto.getContext()
                    .getString(R.string.crypto_failure), this);
            Dialogs.toaster(true, e.getMessage(), this);
        }
    }
}
