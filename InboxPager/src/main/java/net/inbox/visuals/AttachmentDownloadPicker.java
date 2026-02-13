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

import android.app.Dialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.documentfile.provider.DocumentFile;

import android.widget.ListView;
import android.widget.TextView;

import net.inbox.Common;
import net.inbox.InboxMessage;
import net.inbox.db.Attachment;
import net.inbox.pager.R;
import net.inbox.server.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static net.inbox.visuals.Dialogs.dialog_simple;

public class AttachmentDownloadPicker extends Dialog {

    private AttachmentsList attachments_adapter;
    private ArrayList<AttachmentItem> attachment_items;

    private TextView tv_location;

    protected WeakReference<AppCompatActivity> act;

    public AttachmentDownloadPicker(
        final AppCompatActivity at,
        ArrayList<AttachmentItem> att_array
    ) {
        super(at);
        act = new WeakReference<>(at);
        attachment_items = att_array;

        // Rounded Dialog window corners
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(
                AppCompatResources.getDrawable(at, R.drawable.dialog_background)
            );
        }

        setContentView(R.layout.folder_picker);

        ((TextView) findViewById(R.id.picker_title)).setText(
            at.getString(R.string.attch_title).toUpperCase()
        );

        attachments_adapter = new AttachmentsList(at, attachment_items);
        ListView list_view_attachments = findViewById(R.id.list_view_attachments);
        list_view_attachments.setAdapter(attachments_adapter);
        list_view_attachments.setOnItemClickListener(
            (parent, view, position, id) -> {
                for (AttachmentItem a : attachment_items) a.set_picked(false);
                attachment_items.get(position).set_picked(true);
                attachments_adapter.notifyDataSetChanged();
            }
        );

        tv_location = findViewById(R.id.tv_location);
        if (((InboxMessage) act.get()).uri_chosen_folder != null)
            tv_location.setText(((InboxMessage) act.get()).uri_chosen_folder.getLastPathSegment());

        findViewById(R.id.tv_picker_select_folder).setOnClickListener(
            view -> ((InboxMessage) at).open_folder_picker(false)
        );
        findViewById(R.id.tv_picker_save).setOnClickListener(view -> prepare_save());
    }

    public void set_tv_location(String s_uri) {
        tv_location.setText(s_uri);
    }

    /**
     * Checks for a series of conditions that may prevent file saving.
     **/
    private void prepare_save() {
        if (((InboxMessage) act.get()).uri_chosen_folder == null) {
            Dialogs.toaster(true, act.get().getString(R.string.picker_no_selection), act.get());
            dismiss();
        } else {
            Common.check_write_give(act.get(), ((InboxMessage) act.get()).uri_chosen_folder);
            DocumentFile df = DocumentFile.fromTreeUri(
                act.get(), ((InboxMessage) act.get()).uri_chosen_folder
            );

            // Attachments
            boolean has_picked = false;
            int att_bytes = 0;
            String att_name = "";
            String att_mime_id = "";
            Attachment att_obj = null;
            for (AttachmentItem att : attachment_items) {
                if (att.get_picked()) {
                    has_picked = true;
                    att_bytes = att.get_i_file_size();
                    att_obj = att.get_attachment_object();
                    break;
                }
            }
            if (has_picked) {
                // Cannot write to this folder, permissions on the device
                if (df != null && !df.canWrite()) {
                    dialog_simple(
                        act.get().getString(R.string.err_title_write_perms),
                        act.get().getString(R.string.err_msg_write_perms),
                        act.get()
                    );
                    return;
                }

                // Not enough space, and also not an exact calculation
                if (Utils.capacity_exists(att_bytes)) {
                    dialog_simple(
                        act.get().getString(R.string.err_title_no_space),
                        act.get().getString(R.string.err_msg_no_space),
                        act.get()
                    );
                    return;
                }

                dismiss();
                ((InboxMessage) act.get()).close_attachment_dialog_save(att_obj);
            } else {
                Dialogs.toaster(true, act.get().getString(R.string.picker_no_selection), act.get());
                dismiss();
            }
        }
    }
}