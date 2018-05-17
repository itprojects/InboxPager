/*
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

public class InboxList extends BaseAdapter {

    private Context ctx;
    private ArrayList<InboxListItem> inboxes;
    private Typeface tf;

    InboxList(Context ctx, ArrayList<InboxListItem> inboxes) {
        this.ctx = ctx;
        this.inboxes = inboxes;
        this.tf = Pager.tf;
    }

    @Override
    public int getCount() {
        return inboxes.size();
    }

    @Override
    public Object getItem(int position) {
        return inboxes.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View v, ViewGroup parent) {
        if (v == null) {
            v = (LayoutInflater.from(this.ctx)).inflate(R.layout.inbox_list_row, parent, false);
        }

        InboxListItem itm = (InboxListItem) getItem(position);
        TextView tv_title = v.findViewById(R.id.inbox_list_title);
        tv_title.setText(itm.get_inbox());
        TextView tv_count = v.findViewById(R.id.inbox_list_count);
        tv_count.setTypeface(tf);
        if (Integer.valueOf(itm.get_count()) < 1) {
            tv_count.setVisibility(View.GONE);
        } else {
            tv_count.setVisibility(View.VISIBLE);
        }
        tv_count.setText(itm.get_count());

        return v;
    }
}

class InboxListItem {

    private int id;
    private String inbox_name;
    private String count;

    InboxListItem(int i, String si, int ic) {
        this.id = i;
        this.inbox_name = si;
        set_count(ic);
    }

    public int get_id() {
        return id;
    }

    public String get_inbox() {
        return inbox_name;
    }

    public String get_count() {
        return count;
    }

    /*
    public void set_id(int i) {
        id = i;
    }
    */

    /*
    public void set_inbox(String s) {
        inbox_name = s;
    }
    */

    private void set_count(int i) {
        if (i < 1) {
            count = "000";
        } else {
            if (i < 10) {
                count = "00" + String.valueOf(i);
            } if (i > 9 && i < 100) {
                count = "0" + String.valueOf(i);
            } else if (i > 999) {
                count = "+" + String.valueOf(i);
            }
        }
    }
}
