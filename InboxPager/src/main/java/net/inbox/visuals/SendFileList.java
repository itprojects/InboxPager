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

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import net.inbox.pager.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class SendFileList extends BaseAdapter {

    private boolean picked_mode; // picked files or browser
    private ArrayList<SendFileItem> file_folders;
    private WeakReference<Context> ctx;

    SendFileList(boolean picked_m, Context ct, ArrayList<SendFileItem> f_folders) {
        picked_mode = picked_m;
        ctx = new WeakReference<>(ct);
        file_folders = f_folders;
    }

    @Override
    public int getCount() {
        return file_folders.size();
    }

    @Override
    public Object getItem(int position) {
        return file_folders.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View v, ViewGroup parent) {
        if (v == null) {
            v = (LayoutInflater.from(ctx.get())).inflate(
                R.layout.file_picker_list_row, parent, false
            );
        }
        final SendFileItem itm = (SendFileItem) getItem(position);
        ImageView iv_pick_action = v.findViewById(R.id.pick_action);
        if (!itm.get_file_or_directory()) iv_pick_action.setVisibility(View.GONE);
        iv_pick_action.setOnClickListener(view -> {
            if (itm.get_file_or_directory()) {
                ((SendFilePicker) ctx.get()).add_or_remove_attachment(!picked_mode, itm.get_file_uri());
            }
        });
        TextView tv_pick_file_name = v.findViewById(R.id.pick_file_name);
        tv_pick_file_name.setText(itm.get_file_name());
        TextView tv_pick_file_type = v.findViewById(R.id.pick_file_type);
        TextView tv_pick_size = v.findViewById(R.id.pick_size);
        ImageView iv_pick_type = v.findViewById(R.id.pick_type);
        if (itm.get_file_or_directory()) {
            iv_pick_type.setImageDrawable(
                AppCompatResources.getDrawable(ctx.get(), R.drawable.pick_file)
            );
            iv_pick_action.setVisibility(View.VISIBLE);
            tv_pick_size.setText(itm.get_file_size_s());
            tv_pick_size.setVisibility(View.VISIBLE);

            if (picked_mode) {
                iv_pick_action.setImageDrawable(
                    AppCompatResources.getDrawable(ctx.get(), R.drawable.remove_item)
                );
            } else {
                iv_pick_action.setImageDrawable(
                    AppCompatResources.getDrawable(ctx.get(), R.drawable.attachment)
                );
            }

            // Content-type determination
            if (itm.get_file_type() == null || itm.get_file_type().isEmpty()) {
                tv_pick_file_type.setVisibility(View.GONE);
            } else {
                tv_pick_file_type.setVisibility(View.VISIBLE);
                tv_pick_file_type.setText(itm.get_file_type());
            }
        } else {
            // Folder item
            iv_pick_type.setImageDrawable(
                AppCompatResources.getDrawable(
                    ctx.get(), R.drawable.pick_folder
                )
            );
            iv_pick_action.setVisibility(View.GONE);
            tv_pick_size.setVisibility(View.GONE);
            tv_pick_file_type.setVisibility(View.GONE);
        }

        // Set item click listener
        v.setOnClickListener(
            view -> {
                SendFileItem item = file_folders.get(position);
                Dialogs.dialog_simple(
                    parent.getContext().getString(R.string.attch_title_generic),
                    item.get_file_name() + "\n\n" + Uri.decode(item.get_file_uri()),
                    ((AppCompatActivity) ctx.get())
                );
            }
        );

        return v;
    }
}

class SendFileItem {

    private boolean file_or_directory;
    private long file_size_l;
    private String file_size_s;
    private String file_name;
    private String file_uri;
    private String file_type;

    SendFileItem(
        boolean file_or_directory,
        long file_size_l,
        String file_size_s,
        String file_name,
        String file_uri,
        String file_type
    ) {
        this.file_or_directory = file_or_directory;
        this.file_size_l = file_size_l;
        this.file_size_s = file_size_s;
        this.file_name = file_name;
        this.file_uri = file_uri;
        this.file_type = file_type;
    }

    boolean get_file_or_directory() {
        return file_or_directory;
    }

    long get_file_size_l() {
        return file_size_l;
    }

    String get_file_size_s() {
        return file_size_s;
    }

    String get_file_name() {
        return file_name;
    }

    String get_file_uri() {
        return file_uri;
    }

    String get_file_type() {
        return file_type;
    }
}
