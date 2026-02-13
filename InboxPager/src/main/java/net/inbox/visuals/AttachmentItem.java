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

import net.inbox.db.Attachment;

public class AttachmentItem {

    private boolean picked = false;
    private int i_file_size;
    private String s_file_size;
    private String file_name;
    private String file_type;
    private String file_uuid;
    private Attachment attachment_object;

    public AttachmentItem(
        int i_file_size,
        String s_file_size,
        String file_name,
        String file_type,
        String file_uuid,
        Attachment att_obj
    ) {
        this.i_file_size = i_file_size;
        this.s_file_size = s_file_size;
        this.file_name = file_name;
        this.file_type = file_type;
        this.file_uuid = file_uuid;
        this.attachment_object = att_obj;
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

    Attachment get_attachment_object() {
        return attachment_object;
    }

    public void set_picked(boolean p) {
        this.picked = p;
    }
}
