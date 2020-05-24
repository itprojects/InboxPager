/*
 * InboxPager, an android email client.
 * Copyright (C) 2018-2020  ITPROJECTS
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import net.inbox.R;

import java.util.ArrayList;

public class AttachmentsList extends BaseAdapter {

    private Context ctx;
    private ArrayList<AttachmentItem> attachment_items;

    AttachmentsList(Context ctx, ArrayList<AttachmentItem> attachment_items) {
        this.ctx = ctx;
        this.attachment_items = attachment_items;
    }

    @Override
    public int getCount() {
        return attachment_items.size();
    }

    @Override
    public Object getItem(int position) {
        return attachment_items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View v, ViewGroup parent) {
        if (v == null) {
            v = (LayoutInflater.from(this.ctx)).inflate(R.layout.folder_picker_attachment_list_row, parent, false);
        }
        final AttachmentItem itm = (AttachmentItem) getItem(position);
        RadioButton rb_attachments = v.findViewById(R.id.rb_attachments);
        rb_attachments.setChecked(itm.get_picked());
        TextView tv_pick_file_name = v.findViewById(R.id.attachments_name);
        tv_pick_file_name.setText(itm.get_file_name());
        TextView tv_pick_file_type = v.findViewById(R.id.attachment_type);
        tv_pick_file_type.setText(itm.get_file_type());
        if (itm.get_file_type() == null) {
            tv_pick_file_type.setVisibility(View.GONE);
        } else {
            tv_pick_file_type.setVisibility(View.VISIBLE);
        }
        TextView tv_pick_size = v.findViewById(R.id.attachment_size);
        tv_pick_size.setText(itm.get_s_file_size());

        return v;
    }
}

class AttachmentItem {

    private boolean picked = false;
    private int i_file_size;
    private String s_file_size;
    private String file_name;
    private String file_type;
    private String file_uuid;

    AttachmentItem(int i_file_size, String s_file_size, String file_name, String file_type,
                   String file_uuid) {
        this.i_file_size = i_file_size;
        this.s_file_size = s_file_size;
        this.file_name = file_name;
        this.file_type = file_type;
        this.file_uuid = file_uuid;
    }

    int get_i_file_size() {
        return i_file_size;
    }

    String get_s_file_size() {
        return s_file_size;
    }

    String get_file_name() {
        return file_name;
    }

    String get_file_type() {
        return file_type;
    }

    String get_file_uuid() {
        return file_uuid;
    }

    boolean get_picked() {
        return picked;
    }

    void set_picked(boolean p) {
        this.picked = p;
    }
}
