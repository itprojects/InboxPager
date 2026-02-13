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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import net.inbox.pager.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class AttachmentsList extends BaseAdapter {

    private ArrayList<AttachmentItem> attachment_items;
    private WeakReference<Context> ctx;

    AttachmentsList(Context ct, ArrayList<AttachmentItem> att_items) {
        ctx = new WeakReference<>(ct);
        attachment_items = att_items;
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
            v = (LayoutInflater.from(ctx.get())).inflate(
                R.layout.folder_picker_attachment_list_row, parent, false
            );
        }
        final AttachmentItem itm = (AttachmentItem) getItem(position);
        RadioButton rb_attachments = v.findViewById(R.id.rb_attachments);
        rb_attachments.setChecked(itm.get_picked());
        TextView tv_pick_file_name = v.findViewById(R.id.attachments_name);
        tv_pick_file_name.setText(itm.get_file_name());
        tv_pick_file_name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
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

