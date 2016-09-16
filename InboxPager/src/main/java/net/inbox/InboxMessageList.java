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
import android.widget.TextView;

import java.util.ArrayList;

public class InboxMessageList extends BaseAdapter {

    private ArrayList<InboxMessageListItem> msgs;
    private Context ctx;
    private Typeface tf;

    public InboxMessageList(Context ctx, ArrayList<InboxMessageListItem> msgs) {
        this.ctx = ctx;
        this.msgs = msgs;
        this.tf = Pager.tf;
    }

    @Override
    public int getCount() {
        return msgs.size();
    }

    @Override
    public Object getItem(int position) {
        return msgs.get(position);
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

        InboxMessageListItem itm = msgs.get(position);
        TextView tv_title = (TextView) v.findViewById(R.id.message_list_title);
        if (itm.get_seen()) {
            (v.findViewById(R.id.message_list_title_unseen_mark)).setVisibility(View.GONE);
        } else {
            (v.findViewById(R.id.message_list_title_unseen_mark)).setVisibility(View.VISIBLE);
        }
        tv_title.setText(itm.get_subject());tv_title.invalidate();
        TextView tv_subtitle = (TextView) v.findViewById(R.id.message_list_subtitle);
        tv_subtitle.setText(itm.get_subtitle());
        TextView tv_att = (TextView) v.findViewById(R.id.message_list_attachments);
        tv_att.setTypeface(tf);
        if (itm.get_attachments() < 1) {
            tv_att.setVisibility(View.GONE);
        } else {
            tv_att.setVisibility(View.VISIBLE);
        }
        tv_att.setText(String.valueOf(itm.get_attachments()));

        return v;
    }
}

class InboxMessageListItem {

    private int id;
    private String subject;
    private String sender;
    private int attachments;
    private boolean seen;

    public InboxMessageListItem(int i, String si, String sii, int ic, boolean sn) {
        this.id = i;
        this.subject = si;
        this.sender = sii;
        this.attachments = ic;
        this.seen = sn;
    }

    public int get_id() {
        return id;
    }

    public String get_subject() {
        return subject;
    }

    public String get_subtitle() {
        return sender;
    }

    public int get_attachments() {
        return attachments;
    }

    public boolean get_seen() {
        return seen;
    }

    public void set_id(int i) {
        id = i;
    }

    public void set_subject(String s) {
        subject = s;
    }

    public void set_subtitle(String s) {
        sender = s;
    }

    public void set_attachments(int i) {
        attachments = i;
    }

    public void set_seen(boolean b) {
        seen = b;
    }
}
