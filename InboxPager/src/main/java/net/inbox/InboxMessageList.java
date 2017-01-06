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

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

class InboxMessageList extends BaseAdapter {

    private ArrayList<InboxMessageListItem> msg_s;
    private Context ctx;
    private Typeface tf;

    InboxMessageList(Context ct, ArrayList<InboxMessageListItem> messages) {
        ctx = ct;
        msg_s = messages;
        tf = Pager.tf;
    }

    @Override
    public int getCount() {
        return msg_s.size();
    }

    @Override
    public Object getItem(int position) {
        return msg_s.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View v, ViewGroup parent) {
        if (v == null) {
            v = (LayoutInflater.from(this.ctx)).inflate(R.layout.message_list_row, parent, false);
        }

        InboxMessageListItem itm = msg_s.get(position);
        TextView tv_title = (TextView) v.findViewById(R.id.message_list_title);
        if (itm.get_seen()) {
            (v.findViewById(R.id.message_list_title_unseen_mark)).setVisibility(View.GONE);
        } else {
            (v.findViewById(R.id.message_list_title_unseen_mark)).setVisibility(View.VISIBLE);
        }
        tv_title.setText(itm.get_subject());
        tv_title.invalidate();
        TextView tv_subtitle = (TextView) v.findViewById(R.id.message_list_subtitle);
        tv_subtitle.setText(itm.get_subtitle());
        ImageView iv_att = (ImageView) v.findViewById(R.id.message_list_attachments_img);
        TextView tv_att = (TextView) v.findViewById(R.id.message_list_attachments);
        tv_att.setTypeface(tf);
        if (itm.get_attachments() < 1) {
            iv_att.setVisibility(View.GONE);
            tv_att.setVisibility(View.GONE);
        } else {
            iv_att.setVisibility(View.VISIBLE);
            tv_att.setVisibility(View.VISIBLE);
        }
        tv_att.setText(String.valueOf(itm.get_attachments()));

        return v;
    }
}

class InboxMessageListItem {

    private int id;
    private int inbox;
    private String subject;
    private String sender;
    private int attachments;
    private boolean seen;

    InboxMessageListItem(int i, int ia, String si, String sii, int ic, boolean sn) {
        id = i;
        inbox = ia;
        subject = si;
        sender = sii;
        attachments = ic;
        seen = sn;
    }

    public int get_id() {
        return id;
    }

    int get_inbox() {
        return inbox;
    }

    String get_subject() {
        return subject;
    }

    String get_subtitle() {
        return sender;
    }

    int get_attachments() {
        return attachments;
    }

    boolean get_seen() {
        return seen;
    }
}
