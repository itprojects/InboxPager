/*
 * InboxPager, an android email client.
 * Copyright (C) 2018-2024  ITPROJECTS
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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;

import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import net.inbox.InboxPager;
import net.inbox.db.Attachment;
import net.inbox.pager.R;
import net.inbox.server.Utils;

import java.util.ArrayList;

import static net.inbox.visuals.Dialogs.dialog_simple;

public class AttachmentDownloadPicker extends AppCompatActivity {

    private int temp_position = -1;

    private Uri cwd_path;

    private AttachmentsList attachments_adapter;
    private ArrayList<AttachmentItem> attachments_array = new ArrayList<>();
    private ArrayList<Attachment> attachments_obj = new ArrayList<>();

    private TextView tv_location;

    private View current_layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.folder_picker);

        try {
            // Restore existing state
            if (savedInstanceState != null) {
                temp_position = savedInstanceState.getInt("sv_temp_position", -1);
                cwd_path = Uri.parse(savedInstanceState.getString("sv_current_path"));
                attachments_obj = savedInstanceState.getParcelableArrayList("sv_attachments_obj");
            } else {
                // Animation
                current_layout = this.findViewById(R.id.picker_activity);
                current_layout.setVisibility(View.INVISIBLE);

                ViewTreeObserver viewTreeObserver = current_layout.getViewTreeObserver();
                if (viewTreeObserver.isAlive()) {
                    viewTreeObserver.addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {

                        @Override
                        public void onGlobalLayout() {
                            Common.animation_in((AppCompatActivity) current_layout.getContext(), current_layout);
                            current_layout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    });
                }

                // Activity first start
                if (getIntent().getExtras() != null) {
                    attachments_obj = getIntent().getParcelableArrayListExtra("msg_attachments");
                }
            }

            Toolbar tb = findViewById(R.id.picker_toolbar);
            setSupportActionBar(tb);

            // Find the title
            TextView pick_title = tb.findViewById(R.id.picker_title);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(false);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                pick_title.setText(getString(R.string.attch_title).toUpperCase());
            }

            attachments_adapter = new AttachmentsList(this, attachments_array);
            ListView list_view_attachments = findViewById(R.id.list_view_attachments);
            list_view_attachments.setAdapter(attachments_adapter);
            list_view_attachments.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    temp_position = position;
                    for (AttachmentItem a : attachments_array) a.set_picked(false);
                    attachments_array.get(temp_position).set_picked(true);
                    attachments_adapter.notifyDataSetChanged();
                }
            });
            load_attachments();

            TextView tv_picker_save = findViewById(R.id.tv_picker_save);
            tv_picker_save.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    prepare_save();
                }
            });

            TextView tv_picker_select = findViewById(R.id.tv_picker_select);
            tv_picker_select.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    pick_folder();
                }
            });

            tv_location = findViewById(R.id.tv_location);
            tv_location.setText(getString(R.string.folder_title));
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            cwd_path = data.getData();
            tv_location.setText(cwd_path.getLastPathSegment());
        }
    }

    @Override
    public void onSaveInstanceState(Bundle save) {
        super.onSaveInstanceState(save);
        save.putInt("sv_temp_position", temp_position);
        if (cwd_path == null) {
            save.putString("sv_current_path", "");
        } else {
            save.putString("sv_current_path", cwd_path.toString());
        }
        save.putParcelableArrayList("sv_attachments_obj", attachments_obj);
    }

    @Override
    public void onBackPressed() {
        Common.animation_out(this, current_layout);
    }

    private void load_attachments() {
        if (attachments_obj != null) {
            if  (attachments_obj.size() > 0) {
                attachments_array.clear();
                for (Attachment at : attachments_obj) {
                    try {
                        if (at.get_imap_uid() == null) {
                            attachments_array.add(0, new AttachmentItem(at.get_size(),
                                    Utils.s_file_size(at.get_size(),
                                            getString(R.string.attch_bytes),
                                            getString(R.string.attch_kilobytes),
                                            getString(R.string.attch_megabytes)),
                                    at.get_name(), at.get_mime_type(), at.get_pop_indx()));
                        } else {
                            attachments_array.add(0, new AttachmentItem(at.get_size(),
                                    Utils.s_file_size(at.get_size(),
                                            getString(R.string.attch_bytes),
                                            getString(R.string.attch_kilobytes),
                                            getString(R.string.attch_megabytes)),
                                    at.get_name(), at.get_mime_type(), at.get_imap_uid()));
                        }
                    } catch (Exception e) {
                        InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                    }
                }

                // Set known picked item
                if (temp_position != -1) attachments_array.get(temp_position).set_picked(true);
                attachments_adapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Checks for a series of conditions that may prevent file saving.
     **/
    private void prepare_save() {
        Common.check_write_give(this, cwd_path);
        DocumentFile df = DocumentFile.fromTreeUri(this, cwd_path);

        // Attachments
        boolean has_picked = false;
        int att_bytes = 0;
        String att_name = "";
        String att_mime_id = "";
        for (AttachmentItem att : attachments_array) {
            if (att.get_picked()) {
                has_picked = true;
                att_bytes = att.get_i_file_size();
                att_name = att.get_file_name();
                att_mime_id = att.get_file_uuid();
            }
        }
        if (has_picked) {
            // Cannot write to this folder, permissions on the device
            if (df != null &&  !df.canWrite()) {
                dialog_simple(getString(R.string.err_title_write_perms),
                        getString(R.string.err_msg_write_perms), this);
                return;
            }

            // Not enough space, and also not an exact calculation
            if (Utils.capacity_exists(att_bytes)) {
                dialog_simple(getString(R.string.err_title_no_space),
                        getString(R.string.err_msg_no_space), this);
                return;
            }
            save_and_end(att_name, att_mime_id);
        } else {
            Dialogs.toaster(true, getString(R.string.file_title), this);
        }
    }

    private void save_and_end(String name, String id) {
        Intent data = new Intent();
        data.putExtra("chosen_folder", cwd_path.toString());
        data.putExtra("chosen_name", name);
        data.putExtra("chosen_attachment", id);
        setResult(102, data);
        onBackPressed();
    }

    public void pick_folder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        startActivityForResult(Intent.createChooser(i, getString(R.string.folder_title)), 100);
    }
}
