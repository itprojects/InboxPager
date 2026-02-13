/*
 * InboxPager, an android email client.
 * Copyright (C) 2018-2026  ITPROJECTS
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
import androidx.documentfile.provider.DocumentFile;

import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import net.inbox.Common;
import net.inbox.InboxPager;
import net.inbox.pager.R;
import net.inbox.server.Utils;

import java.net.URLConnection;
import java.util.ArrayList;

public class SendFilePicker extends AppCompatActivity {

    private boolean warned_8_bit_absent = false;
    private boolean warned_files_too_big = false;

    private long attachments_size = 0;
    private long total_size_limit = 0;

    private String s_chosen_folder;
    private String s_temporary_uri;

    private ArrayList<String> s_attachment_paths = new ArrayList<>();
    private SendFileList picked_adapter;
    private ArrayList<SendFileItem> picked_attachments = new ArrayList<>();

    private ActivityResultLauncher<Intent> start_activity_for_result;

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

        setContentView(R.layout.file_picker);
        LinearLayout main_root = findViewById(R.id.root_view_file_picker_activity);

        try {
            // Restore existing state
            if (saved_instance_state != null) {
                warned_8_bit_absent = saved_instance_state.getBoolean("sv_warned_8_bit_absent");
                warned_files_too_big = saved_instance_state.getBoolean("sv_warned_files_too_big");
                attachments_size = saved_instance_state.getLong("sv_attachments_size");
                total_size_limit = saved_instance_state.getLong("sv_total_size_limit");
                s_attachment_paths = saved_instance_state.getStringArrayList("sv_s_attachment_paths");
                s_temporary_uri = saved_instance_state.getString("sv_s_temporary_uri");
                s_chosen_folder = saved_instance_state.getString("sv_s_chosen_folder");
            } else {
                // Activity first start
                if (getIntent().getExtras() != null) {
                    if (getIntent().getExtras().containsKey("b_8_bit_absent") && !warned_8_bit_absent) {
                        warned_8_bit_absent = true;
                        getIntent().getExtras().getBoolean("b_8_bit_absent");
                        Dialogs.dialog_simple(null, getString(R.string.err_no_8_bit_mime), this);
                    }
                    attachments_size = getIntent().getExtras().getLong("l_attachment_size", 0);
                    total_size_limit = getIntent().getExtras().getLong("l_total_size_limit", 0);
                    s_attachment_paths = getIntent().getExtras().getStringArrayList("str_array_paths");
                }
            }

            Toolbar tb = findViewById(R.id.picker_toolbar);
            setSupportActionBar(tb);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(false);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                TextView pick_title = tb.findViewById(R.id.picker_title); // Find the title
                pick_title.setText(getString(R.string.send_attachments).toUpperCase());
            }

            picked_adapter = new SendFileList(true, this, picked_attachments);
            ListView list_view_picked_attachments = findViewById(R.id.list_view_picked_attachments);
            list_view_picked_attachments.setAdapter(picked_adapter);
            load_picked();

            findViewById(R.id.tv_picker_save).setOnClickListener(view -> {
                Intent data = new Intent();
                data.putStringArrayListExtra("attachments", s_attachment_paths);
                setResult(RESULT_OK, data);
                finish();
            });

            TextView tv_picker_select = findViewById(R.id.tv_picker_select);
            tv_picker_select.setText(getString(R.string.file_title));
            tv_picker_select.setOnClickListener(view -> pick_files());
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            finish();
        }

        // Handle insets for cutout and system bars
        set_activity_insets_listener(main_root);

        // Prepare InboxGPG Activity for result
        start_activity_for_result = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                int result_code = result.getResultCode();
                Intent data = result.getData();
                if (result_code == RESULT_OK) {
                    // No files to add
                    if (data.getData() == null && data.getClipData() == null) return;

                    // Comparing and adding files, if necessary
                    if (s_attachment_paths == null) s_attachment_paths = new ArrayList<>();

                    if (data.getData() != null
                            && !s_attachment_paths.contains(data.getData().toString())) {
                        s_attachment_paths.add(data.getData().toString());
                    }

                    if (data.getClipData() != null) {
                        for (int ii = 0;ii < data.getClipData().getItemCount();++ii) {
                            String s = data.getClipData().getItemAt(ii).getUri().toString();
                            if (!s_attachment_paths.contains(s)) s_attachment_paths.add(s);
                        }
                    }
                    load_picked();
                }
            }
        );
    }

    @Override
    public void finish() {
        super.finish();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android APi >= 34
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.right_in, R.anim.right_out);
        } else { // Android API <= 33
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle saved_instance_state) {
        super.onSaveInstanceState(saved_instance_state);
        saved_instance_state.putBoolean("sv_warned_8_bit_absent", warned_8_bit_absent);
        saved_instance_state.putBoolean("sv_warned_files_too_big", warned_files_too_big);
        saved_instance_state.putLong("sv_attachments_size", attachments_size);
        saved_instance_state.putLong("sv_total_size_limit", total_size_limit);
        saved_instance_state.putStringArrayList("sv_s_attachment_paths", s_attachment_paths);
        saved_instance_state.putString("sv_s_temporary_uri", s_temporary_uri);
        saved_instance_state.putString("sv_s_chosen_folder", s_chosen_folder);
    }

    private void calc_total_files_size() {
        attachments_size = 0;
        for (SendFileItem ffi : picked_attachments) attachments_size += ffi.get_file_size_l();
    }

    private void load_picked() {
        picked_attachments.clear();
        if (s_attachment_paths != null) {
            for (String s : s_attachment_paths) {
                try {
                    Uri uri = Uri.parse(s);
                    Common.check_read_give(this, uri);
                    DocumentFile df = DocumentFile.fromSingleUri(this, uri);
                    picked_attachments.add(
                        0,
                        new SendFileItem(
                            true,
                            df.length(),
                            Utils.s_file_size(
                                df.length(),
                                getString(R.string.attch_bytes),
                                getString(R.string.attch_kilobytes),
                                getString(R.string.attch_megabytes)
                            ),
                            df.getName(),
                            s,
                            URLConnection.guessContentTypeFromName(df.getName())
                        )
                    );
                } catch (Exception e) {
                    InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                }
            }
        }
        picked_adapter.notifyDataSetChanged();
    }

    public void add_or_remove_attachment(boolean add_or_remove, String uri) {
        if (add_or_remove) {
            // Adding file attachment
            // Missing read permission check
            DocumentFile df_add = DocumentFile.fromSingleUri(this, Uri.parse(uri));
            if (!df_add.canRead()) {
                Dialogs.dialog_simple(
                    getString(R.string.err_title_android_permission),
                    getString(R.string.err_read_file),
                    this
                );
                return;
            }
            // File can be read
            // Check file is not duplicate
            for (SendFileItem ffi: picked_attachments) {
                if (ffi.get_file_uri().equals(uri)) {
                    Dialogs.dialog_simple(null, getString(R.string.err_title_file_is_duplicate), this);
                    return;
                }
            }
            // File is not a duplicate
            // Over-sized SMTP server message quota check
            calc_total_files_size();
            if (((attachments_size + df_add.length()) >= total_size_limit)
                && !warned_files_too_big
            ) {
                // Server will refuse the message, attachments too big
                s_temporary_uri = uri;
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.err_size_attachments_title));
                builder.setMessage(getString(R.string.err_size_attachments));
                builder.setPositiveButton(
                    getString(R.string.err_size_attachments_continue),
                        (dialog, which) -> {
                            warned_files_too_big = true;
                            add_or_remove_attachment_directly(true, s_temporary_uri);
                        }
                );
                builder.setNegativeButton(getString(android.R.string.cancel), null);
                builder.show();
            } else add_or_remove_attachment_directly(true, uri);
        } else {
            add_or_remove_attachment_directly(false, uri);
        }
    }

    public void add_or_remove_attachment_directly(boolean adding, String uri) {
        if (adding) {
            DocumentFile df = DocumentFile.fromSingleUri(this, Uri.parse(uri));
            picked_attachments.add(
                new SendFileItem(
                    false,
                    df.length(),
                    Utils.s_file_size(
                        df.length(),
                        getString(R.string.attch_bytes),
                        getString(R.string.attch_kilobytes),
                        getString(R.string.attch_megabytes)
                    ),
                    df.getName(),
                    uri,
                    URLConnection.guessContentTypeFromName(df.getName())
                )
            );
            s_attachment_paths.add(uri);
            Dialogs.toaster(
                true,
                getString(R.string.attch_added_attachment) + " " + df.getName(),
                this
            );
        } else {
            if (s_attachment_paths != null && !s_attachment_paths.isEmpty()) {
                s_attachment_paths.remove(uri);
            }
            if (picked_attachments != null && !picked_attachments.isEmpty()) {
                for (int i = picked_attachments.size() - 1;i >= 0;i--) {
                    if (picked_attachments.get(i).get_file_uri().equals(uri)) {
                        picked_attachments.remove(picked_attachments.get(i));
                        picked_attachments.trimToSize();
                    }
                }
            }
        }
        picked_adapter.notifyDataSetChanged();
    }

    public void pick_files() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        Intent ii = Intent.createChooser(i, getString(R.string.file_title));
        start_activity_for_result.launch(ii);
    }
}
