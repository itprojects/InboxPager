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
package net.inbox.dialogs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import net.inbox.R;

import java.util.ArrayList;

public class FileDownloadList extends BaseAdapter {

    private Context ctx;
    private ArrayList<FileDownloadItem> file_folders;

    FileDownloadList(Context ctx, ArrayList<FileDownloadItem> file_folders) {
        this.ctx = ctx;
        this.file_folders = file_folders;
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
            v = (LayoutInflater.from(this.ctx)).inflate(R.layout.folder_picker_list_row, parent, false);
        }
        final FileDownloadItem itm = (FileDownloadItem) getItem(position);
        TextView tv_pick_file_name = v.findViewById(R.id.pick_file_name);
        tv_pick_file_name.setText(itm.get_file_name());
        ImageView iv_pick_type = v.findViewById(R.id.pick_type);
        if (itm.get_file_or_directory()) {
            iv_pick_type.setImageDrawable(this.ctx.getDrawable(R.drawable.pick_file));
        } else {
            // Folder item
            iv_pick_type.setImageDrawable(this.ctx.getDrawable(R.drawable.pick_folder));
        }

        return v;
    }
}

class FileDownloadItem {

    private boolean file_or_directory;
    private String file_name;
    private String file_uri;

    FileDownloadItem(boolean file_or_directory, String file_name, String file_uri) {
        this.file_or_directory = file_or_directory;
        this.file_name = file_name;
        this.file_uri = file_uri;
    }

    boolean get_file_or_directory() {
        return file_or_directory;
    }

    String get_file_name() {
        return file_name;
    }

    String get_file_uri() {
        return file_uri;
    }
}
