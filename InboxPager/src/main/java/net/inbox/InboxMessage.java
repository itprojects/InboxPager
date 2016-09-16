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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import net.inbox.db.Attachment;
import net.inbox.db.DBAccess;
import net.inbox.db.Message;
import net.inbox.dialogs.Dialogs;
import net.inbox.dialogs.SpinningStatus;
import net.inbox.server.IMAP;
import net.inbox.server.POP;
import net.inbox.server.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InboxMessage extends AppCompatActivity {

    private DBAccess db;

    private TextView tv_contents;
    private TextView tv_texts;

    private boolean imap_or_pop;
    private boolean no_send;

    private boolean btn_texts_ready = true;
    private int btn_texts_state = 0;
    private ArrayList<String[]> btn_texts_states;

    private Message current;
    private Attachment chosen_att;
    private List<Attachment> attachments;

    // Folder picker variables
    private boolean save_in_db;
    private AlertDialog dialog_folder_picker;
    private ArrayAdapter lv_adapter;
    private File chosen_folder;
    private File current_path = new File("/");
    private List<File> f_folders = new ArrayList<>();
    private List<String> s_folders = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.message);

        // Get the database
        db = Pager.get_db();

        current = db.get_message(getIntent().getExtras().getInt("db_id"));

        imap_or_pop = getIntent().getExtras().getBoolean("imap_or_pop");

        no_send = getIntent().getExtras().getBoolean("no_send");

        Toolbar tb = (Toolbar) findViewById(R.id.message_toolbar);
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

        // Message Attachments Counter
        TextView tv_page_attachments = (TextView) findViewById(R.id.message_attachments);
        tv_page_attachments.setTypeface(Pager.tf);
        tv_page_attachments.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog_list_attachments();
            }
        });

        // Setting the number of attachments
        if (current.get_attachments() > 0) {
            tv_page_attachments.setVisibility(View.VISIBLE);
        } else {
            tv_page_attachments.setVisibility(View.GONE);
        }

        TextView tv_from = (TextView) findViewById(R.id.message_from);
        TextView tv_cc = (TextView) findViewById(R.id.message_cc);
        TextView tv_bcc = (TextView) findViewById(R.id.message_bcc);
        TextView tv_subject = (TextView) findViewById(R.id.message_subject);
        TextView tv_date = (TextView) findViewById(R.id.message_date);
        tv_contents = (TextView) findViewById(R.id.message_contents);
        tv_texts = (TextView) findViewById(R.id.message_loop);

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

        TextView tv_from_title = (TextView) findViewById(R.id.message_from_title);
        tv_from_title.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog_originating();
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
        btn_texts_states = new ArrayList<>();
        if (current.get_contents_plain() != null && !current.get_contents_plain().isEmpty()) {
            btn_texts_states.add(new String[] { getString(R.string.message_contents_loop_plain), "1"});
        }
        if (current.get_contents_html() != null && !current.get_contents_html().isEmpty()) {
            btn_texts_states.add(new String[] { getString(R.string.message_contents_loop_html), "2"});
        }
        if (current.get_contents_other() != null && !current.get_contents_other().isEmpty()) {
            btn_texts_states.add(new String[] { getString(R.string.message_contents_loop_other), "3"});
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

        // Set unseen -> seen
        if (!(current.get_seen())) {
            db.seen_unseen_message(current.get_account(), current.get_uid(), true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.message_action_btns, menu);
        if (no_send) {
            menu.findItem(R.id.write_reply_menu).setVisible(false);
        } else {
            menu.findItem(R.id.write_reply_menu).setVisible(true);
        }
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
                Dialogs.toaster(getString(R.string.message_no_full_message), this);
                break;
            case R.id.download_message_menu:
                // Starting a spinning animation dialog
                start_saving_full_message(true);
                break;
            case R.id.save_message_menu:
                // Save a message to device
                if (current.get_full_msg() == null || current.get_full_msg().isEmpty()) {
                    dialog_download_prompt();
                } else {
                    save_in_db = false;
                    dialog_folder_picker();
                }
                break;
            case R.id.write_reply_menu:
                if (no_send) {
                    Dialogs.toaster(getString(R.string.err_server_not_configured), this);
                } else {
                    write_reply();
                }
                break;
            case R.id.details_menu:
                // Display the full message
                if (current.get_full_msg() == null || current.get_full_msg().isEmpty()) {
                    Dialogs.toaster(getString(R.string.err_no_full_msg), this);
                } else {
                    Dialogs.dialog_view_message(current.get_full_msg(), this);
                }
                break;
        }

        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.right_in, R.anim.right_out);
    }

    private void populate_contents() {
        tv_contents.setText("");
        switch (btn_texts_states.get(btn_texts_state)[1]) {
            case "0":
                break;
            case "1":
                if (current.get_contents_plain() != null) {
                    if (!current.get_charset_plain().isEmpty()
                            && !current.get_charset_plain().equalsIgnoreCase("UTF-8")
                            && !current.get_charset_plain().equalsIgnoreCase("NIL")) {
                        try {
                            current.set_contents_plain(new String(current.get_contents_plain()
                                    .getBytes(current.get_charset_plain())));
                        } catch (UnsupportedEncodingException e) {
                            System.out.println("Exception: " + e.getMessage());
                            Pager.log += getString(R.string.ex_field) + e.getMessage() + "\n";
                        }
                    }
                    tv_contents.setText(current.get_contents_plain());
                } else {
                    tv_contents.setText("");
                }
                tv_texts.setText(btn_texts_states.get(btn_texts_state)[0]);
                break;
            case "2":
                if (current.get_contents_html() != null) {
                    if (!current.get_charset_html().isEmpty()
                            && !current.get_charset_html().equalsIgnoreCase("UTF-8")
                            && !current.get_charset_html().equalsIgnoreCase("NIL")) {
                        try {
                            html_text(new String(current.get_contents_html()
                                    .getBytes(current.get_charset_html())));
                        } catch (UnsupportedEncodingException e) {
                            System.out.println("Exception: " + e.getMessage());
                            Pager.log += getString(R.string.ex_field) + e.getMessage() + "\n";
                        }
                    }
                    html_text(current.get_contents_html());
                } else {
                    tv_contents.setText("");
                }
                tv_texts.setText(btn_texts_states.get(btn_texts_state)[0]);
                break;
            case "3":
                if (current.get_contents_other() != null) {
                    tv_contents.setText(current.get_contents_other());
                } else {
                    tv_contents.setText("");
                }
                tv_texts.setText(btn_texts_states.get(btn_texts_state)[0]);
                break;
        }
        btn_texts_ready = true;
    }

    private void html_text(String html_txt) {
        Spanned spn;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            spn = Html.fromHtml(html_txt, Html.FROM_HTML_MODE_LEGACY);
        } else {
            spn = Html.fromHtml(html_txt);
        }
        tv_contents.setText(spn);
    }

    /**
     * Finishes activity to start a new message.
     **/
    private void write_reply() {
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
        ret_intent = ret_intent.putExtra("subject", current.get_subject());
        setResult(10101, ret_intent);

        // End activity
        finish();
    }

    /**
     * Dialog showing the IP of the message sender.
     **/
    private void dialog_originating() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.message_dialog));
        builder.setMessage(current.get_received());
        builder.setPositiveButton(getString(android.R.string.ok), null);
        builder.show();
    }

    private void dialog_list_attachments() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.attch_title));

        attachments = db.get_all_attachments_of_msg(current.get_id());
        String[] str_temp = new String[attachments.size()];
        if (str_temp.length > 0) {
            for (int i = 0;i < str_temp.length;++i) {
                int bytes = attachments.get(i).get_size();
                if (attachments.get(i).get_name().trim().isEmpty()) {
                    attachments.get(i).set_name(getString(R.string.attch_no_name)
                            + UUID.randomUUID().toString());
                    str_temp[i] = "[ " + attachments.get(i).get_name() + " ]\n";
                } else {
                    str_temp[i] = attachments.get(i).get_name() + "\n";
                }
                str_temp[i] += attachments.get(i).get_mime_type().toLowerCase() + ", ";
                if (bytes < 1024) {
                    str_temp[i] += bytes + " " + getString(R.string.attch_bytes);
                } else if (bytes >= 1024 && bytes < 1048576) {
                    str_temp[i] += (bytes/1024) + " " + getString(R.string.attch_kilobytes);
                } else {
                    str_temp[i] += (bytes/1048576) + " " + getString(R.string.attch_megabytes);
                }
            }
        }

        builder.setItems(str_temp, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int choice) {
                dialog.dismiss();
                chosen_att = attachments.get(choice);
                dialog_folder_picker();
            }
        });
        builder.show();
    }

    private void dialog_download_prompt() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.progress_downloading));
        builder.setMessage(getString(R.string.message_empty_go_download));
        builder.setCancelable(true);
        builder.setPositiveButton(getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog_download_and_keep();
                    }
                });
        builder.show();
    }

    /**
     * Dialog choose to keep a DB copy of the full message.
     **/
    private void dialog_download_and_keep() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.progress_keep_msg));
        builder.setMessage(getString(R.string.message_keep_in_database));
        builder.setCancelable(false);
        builder.setPositiveButton(getString(R.string.btn_yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        save_in_db = true;
                        dialog_folder_picker();
                    }
                });
        builder.setNegativeButton(getString(R.string.btn_no),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        save_in_db = false;
                        dialog_folder_picker();
                    }
                });
        builder.show();
    }

    /**
     * Dialog picking the folder for the attachment download.
     **/
    private void dialog_folder_picker() {
        chosen_folder = null;
        refresh_folders(1);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.folder_title));
        builder.setPositiveButton(getString(R.string.btn_up_one_level), null);

        // Populating folders
        ListView lv_folders = new ListView(this);
        lv_folders.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                // Obtain the index of the chosen folder
                String str = (String) parent.getItemAtPosition(position);
                for (File f : f_folders) {
                    if (f.getName().equals(str)) {
                        current_path = f;
                        break;
                    }
                }
                refresh_folders(2);
                lv_adapter.notifyDataSetChanged();
            }
        });

        // Continuing download
        lv_folders.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
                // Obtain the index of the chosen folder
                String str = (String) parent.getItemAtPosition(position);
                for (File f : f_folders) {
                    if (f.getName().equals(str)) {
                        chosen_folder = f;
                        break;
                    }
                }
                dialog_folder_picker.dismiss();
                if (chosen_att == null) {
                    full_message_tests();
                } else {
                    attachment_tests();
                }
                return true;
            }
        });

        lv_adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, s_folders);
        lv_folders.setAdapter(lv_adapter);

        builder.setView(lv_folders);
        dialog_folder_picker = builder.show();

        // Reassigning button to prevent early dialog ending
        dialog_folder_picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (current_path.getParent() != null) {
                    refresh_folders(3);
                    lv_adapter.notifyDataSetChanged();
                }
            }
        });
    }

    /**
     * Reconfigures the folder picker with new items.
     **/
    private void refresh_folders(int level_up) {
        f_folders.clear();
        s_folders.clear();

        switch (level_up) {
            case 1:
                // Add SDCARD directory
                if (Environment.getExternalStorageDirectory().exists()) {
                    current_path = Environment.getExternalStorageDirectory();
                }
                break;
            case 2:
                break;
            case 3:
                // Listing folder, one level up
                current_path = new File(current_path.getParent());
                break;
        }

        if(current_path.exists() && current_path.listFiles() != null) {
            // Add all path [array] to folder_list
            f_folders.addAll(Arrays.asList(current_path.listFiles()));
        }

        // Remove files, keep directories
        for (int i = f_folders.size() - 1;i >= 0;i--) {
            if (!f_folders.get(i).isDirectory()) {
                f_folders.remove(f_folders.get(i));
            } else {
                s_folders.add(f_folders.get(i).getName());
            }
        }

        // Alphabetic sort;
        Collections.sort(s_folders, new Comparator<String>() {
            public int compare(String s1, String s2) {
                return s1.compareTo(s2);
            }
        });
    }

    /**
     * Checks for a series of conditions that may prevent file saving.
     **/
    private void attachment_tests() {
        // Cannot write to this folder, android permissions not set to enabled
        if (!check_writable()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.err_title_android_permission));
            builder.setMessage(getString(R.string.err_msg_android_permission));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.show();
            return;
        }

        // Cannot write to this folder, permissions on the device
        if (!chosen_folder.canWrite()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.err_title_write_perms));
            builder.setMessage(getString(R.string.err_msg_write_perms));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.show();
            return;
        }

        // Not enough space, and also not an exact calculation
        int file_size;
        if (chosen_att != null && chosen_att.get_size() != -1) {
            file_size = chosen_att.get_size();
        } else {
            file_size = current.get_size();
        }
        if (chosen_folder.getUsableSpace() < file_size) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.err_title_no_space));
            builder.setMessage(getString(R.string.err_msg_no_space));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.show();
            return;
        }

        // Quoted-printable warning
        if (chosen_att.get_transfer_encoding().equalsIgnoreCase("quoted-printable")) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.err_title_qp_warn));
            builder.setMessage(getString(R.string.err_msg_qp_warn));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.show();
            return;
        }

        // File already exists, overwrite?
        String full_url = chosen_folder.getPath() + "/";
        if (chosen_att != null) {
            full_url += chosen_att.get_name();
        } else {
            full_url += current.get_subject();
        }
        boolean already_exists = (new File(full_url)).exists();
        if (already_exists) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.err_title_already_exists));
            builder.setMessage(getString(R.string.err_msg_already_exists));
            builder.setPositiveButton(getString(android.R.string.yes),
                    new AlertDialog.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            if (current.get_full_msg() == null || current.get_full_msg().isEmpty()) {
                                start_saving_attachment();
                            } else {
                                write_attachment_from_db();
                            }
                        }
                    });
            builder.setNegativeButton(getString(android.R.string.no), null);
            builder.show();
        } else {
            if (current.get_full_msg() == null || current.get_full_msg().isEmpty()) {
                start_saving_attachment();
            } else {
                write_attachment_from_db();
            }
        }
    }

    /**
     * Checks for a series of conditions that may prevent file saving.
     **/
    private void full_message_tests() {
        // Cannot write to this folder, android permissions not set to enabled
        if (!check_writable()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.err_title_android_permission));
            builder.setMessage(getString(R.string.err_msg_android_permission));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.show();
            return;
        }

        // Cannot write to this folder, permissions on the device
        if (!chosen_folder.canWrite()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.err_title_write_perms));
            builder.setMessage(getString(R.string.err_msg_write_perms));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.show();
            return;
        }

        // Not enough space, and also not an exact calculation
        int file_size = current.get_size();
        if (chosen_folder.getUsableSpace() < file_size) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.err_title_no_space));
            builder.setMessage(getString(R.string.err_msg_no_space));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.show();
            return;
        }

        // File already exists, overwrite?
        String full_url = chosen_folder.getPath() + "/";
        if (chosen_att != null) {
            full_url += chosen_att.get_name();
        } else {
            full_url += current.get_subject();
        }
        boolean already_exists = (new File(full_url)).exists();
        if (already_exists) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.err_title_already_exists));
            builder.setMessage(getString(R.string.err_msg_already_exists));
            builder.setPositiveButton(getString(android.R.string.yes),
                    new AlertDialog.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            if (current.get_full_msg() == null || current.get_full_msg().isEmpty()) {
                                start_saving_full_message(false);
                            } else {
                                write_full_message();
                            }
                        }
                    });
            builder.setNegativeButton(getString(android.R.string.no), null);
            builder.show();
        } else {
            if (current.get_full_msg() == null || current.get_full_msg().isEmpty()) {
                start_saving_full_message(false);
            } else {
                write_full_message();
            }
        }
    }

    private boolean check_writable() {
        String permission = "android.permission.WRITE_EXTERNAL_STORAGE";
        int res = checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    private void start_saving_attachment() {
        // Starting a spinning animation dialog
        SpinningStatus spt = new SpinningStatus(false, this);
        spt.execute();
        spt.onProgressUpdate(getString(R.string.progress_downloading),
                getString(R.string.progress_fetch_attachment));

        // Starting Handler
        if (imap_or_pop) {
            Pager.handler = new IMAP(this);
        } else {
            Pager.handler = new POP(this);
        }
        Pager.handler.sp = spt;
        Pager.handler.start();
        Pager.handler.attachment_action
                (current.get_account(), chosen_att, chosen_folder.getPath(), this);
    }

    /**
     * Continues the file saving process.
     * Convert attachments from BASE64 to normal.
     **/
    public void write_attachment_from_db() {
        // Offline IMAP attachment file can give errors
        if (imap_or_pop) {
            Pattern pat = Pattern.compile(".*boundary=\"(.*)\".*", Pattern.CASE_INSENSITIVE);
            Matcher mat = pat.matcher(current.get_content_type());
            if (mat.matches()) {
                chosen_att.set_boundary("--" + mat.group(1));
                chosen_att.set_pop_indx(chosen_att.get_imap_uid());
            } else {
                Dialogs.dialog_error_line(getString(R.string.err_imap_attachment_saving), this);
                return;
            }
        }

        String att = Utils.mime_part_section(current.get_full_msg(), chosen_att.get_pop_indx(),
                chosen_att.get_boundary());

        try {
            FileOutputStream fstream = new FileOutputStream(new File
                    (chosen_folder.getAbsoluteFile() + "/" + chosen_att.get_name()));

            // Converting transfer encoding
            if (chosen_att != null) {
                // Parsing file download
                if (chosen_att.get_transfer_encoding().equalsIgnoreCase("BASE64")) {
                    boolean CR = false;
                    StringBuilder sb_tmp = new StringBuilder(0);
                    for (int i = 0;i < att.length();++i) {
                        if (att.charAt(i) == '\n') {
                            if (CR) {
                                fstream.write(Base64.decode
                                        (sb_tmp.toString().getBytes(), Base64.DEFAULT));
                                sb_tmp.setLength(0);
                                CR = false;
                            }
                        } else if (att.charAt(i) == '\r') {
                            CR = true;
                        } else if (att.charAt(i) != '=') {
                            sb_tmp.append(att.charAt(i));
                            CR = false;
                        }
                    }
                    if (sb_tmp.length() > 0) {
                        fstream.write(Base64.decode(sb_tmp.toString().getBytes(),
                                Base64.DEFAULT));
                    }
                    fstream.close();
                } else if (chosen_att.get_transfer_encoding().equalsIgnoreCase("7BIT")
                        || chosen_att.get_transfer_encoding().equalsIgnoreCase("8BIT")
                        || chosen_att.get_transfer_encoding().equalsIgnoreCase("BINARY")
                        || chosen_att.get_transfer_encoding()
                        .equalsIgnoreCase("QUOTED-PRINTABLE")) {
                    fstream.write(att.getBytes());
                    fstream.close();
                } else {
                    Pager.log += getString(R.string.err_unknown_transfer_encoding);
                    Dialogs.dialog_error_line
                            (getString(R.string.err_unknown_transfer_encoding), this);
                }
            }
        } catch (IOException e) {
            Pager.log += e.getMessage();
            Dialogs.dialog_exception(e, this);
        }
    }

    private void start_saving_full_message(boolean in_db_only) {
        // Starting a spinning animation dialog
        SpinningStatus sp = new SpinningStatus(true, this);
        sp.execute();
        sp.onProgressUpdate(getString(R.string.progress_downloading),
                getString(R.string.progress_fetch_msg) + ".");

        // Starting refresh INBOX
        if (imap_or_pop) {
            Pager.handler = new IMAP(this);
        } else {
            Pager.handler = new POP(this);
        }
        Pager.handler.sp = sp;
        Pager.handler.start();
        if (in_db_only) {
            Pager.handler.msg_action
                    (current.get_account(), current, null, save_in_db, this);
        } else {
            Pager.handler.msg_action
                    (current.get_account(), current, chosen_folder.getPath(), save_in_db, this);
        }
    }

    /**
     * Available full messages are directly written to file.
     **/
    private void write_full_message() {
        // Prepare for direct file write
        try {
            FileOutputStream fstream = new FileOutputStream(new File
                    (chosen_folder.getAbsoluteFile() + "/" + current.get_subject() + ".eml"));
            fstream.write(current.get_full_msg().getBytes());
            fstream.close();
            Dialogs.toaster(getString(R.string.progress_download_complete), this);
        } catch (IOException ioe) {
            Pager.log += ioe.getMessage();
            Dialogs.dialog_exception(ioe, this);
        }
    }

    /**
     * Deletes the current message from server and local database.
     **/
    private void delete_message() {
        // Starting a spinning animation dialog
        SpinningStatus spt = new SpinningStatus(false, this);
        spt.execute();
        spt.onProgressUpdate(getString(R.string.progress_deleting),
                getString(R.string.progress_deleting_msg) + " " + current.get_subject());

        // Starting Handler
        if (imap_or_pop) {
            Pager.handler = new IMAP(this);
        } else {
            Pager.handler = new POP(this);
        }
        Pager.handler.sp = spt;
        Pager.handler.start();
        Pager.handler.move_action(current.get_account(), current, this);
    }
}
