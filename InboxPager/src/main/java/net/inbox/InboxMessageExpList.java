/*
 * InboxPager, an android email client.
 * Copyright (C) 2019-2026  ITPROJECTS
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.SortedMap;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import net.inbox.db.Message;
import net.inbox.pager.R;

public class InboxMessageExpList extends BaseExpandableListAdapter {

    private ArrayList<String> heads; // of grouped messages
    private SortedMap<String, ArrayList<Message>> msgs;
    private WeakReference<Context> ctx;

    InboxMessageExpList(
        Context ct,
        ArrayList<String> head,
        SortedMap<String,
        ArrayList<Message>> mesgs
    ) {
        ctx = new WeakReference<>(ct);
        heads = head;
        msgs = mesgs;
    }

    @Override
    public Object getGroup(int list_pos) {
        return heads.get(list_pos);
    }

    @Override
    public int getGroupCount() {
        return heads.size();
    }

    @Override
    public long getGroupId(int list_pos) {
        return list_pos;
    }

    @Override
    public View getGroupView(int list_pos, boolean isExpanded, View v, ViewGroup parent) {
        final String head_mail = (String) getGroup(list_pos);

        if (v == null) {
            LayoutInflater layoutInflater = (LayoutInflater) ctx.get().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE
            );
            v = layoutInflater.inflate(R.layout.message_list_group, null);
        }
        TextView message_group_title = v.findViewById(R.id.message_group_title);
        message_group_title.setText(head_mail);

        // Unread messages from sender
        ArrayList<Message> ms = msgs.get(head_mail);
        boolean has_unread = false;
        if (ms != null) {
            for (int i = 0;i < ms.size();++i) {
                if (!ms.get(i).get_seen()) {
                    has_unread = true;
                    break;
                }
            }
        }

        (v.findViewById(R.id.message_group_unseen_mark)).setVisibility(
            has_unread ? View.VISIBLE : View.GONE
        );

        return v;
    }

    @Override
    public int getChildrenCount(int list_pos) {
        return msgs.get(heads.get(list_pos)).size();
    }

    @Override
    public Object getChild(int list_pos, int exp_list_pos) {
        return msgs.get(this.heads.get(list_pos)).get(exp_list_pos);
    }

    @Override
    public long getChildId(int list_pos, int exp_list_pos) {
        return exp_list_pos;
    }

    @Override
    public View getChildView(
        int list_pos,
        final int exp_list_pos,
        boolean isLastChild,
        View v,
        ViewGroup parent
    ) {
        Message m = (Message) getChild(list_pos, exp_list_pos);
        if (v == null) {
            LayoutInflater layoutInflater = (LayoutInflater) ctx.get().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE
            );
            v = layoutInflater.inflate(R.layout.message_list_row, null);
        }

        TextView tv_title = v.findViewById(R.id.message_list_title);
        if (m.get_seen()) {
            (v.findViewById(R.id.message_list_title_unseen_mark)).setVisibility(View.GONE);
        } else {
            (v.findViewById(R.id.message_list_title_unseen_mark)).setVisibility(View.VISIBLE);
        }
        tv_title.setText(m.get_subject());
        tv_title.invalidate();
        TextView tv_att = v.findViewById(R.id.message_list_attachments);
        if (m.get_attachments() < 1) {
            tv_att.setVisibility(View.GONE);
        } else {
            tv_att.setVisibility(View.VISIBLE);
        }
        tv_att.setText(String.valueOf(m.get_attachments()));

        return v;
    }

    @Override
    public boolean isChildSelectable(int list_pos, int exp_list_pos) {
        return true;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }
}
