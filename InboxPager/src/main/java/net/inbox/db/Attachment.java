/*
 * InboxPager, an android email client.
 * Copyright (C) 2016-2024  ITPROJECTS
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
package net.inbox.db;

import android.os.Parcel;
import android.os.Parcelable;

public class Attachment implements Parcelable {

    private int id = -2;
    private int account = -2;
    private int message = -2;
    private String imap_uid;
    private String pop_indx;
    private String mime_type;
    private String boundary;
    private String name;
    private String transfer_encoding;
    private int size;// octet = 8 bits

    public Attachment() {}

    public int get_id() {
        return id;
    }

    public int get_account() {
        return account;
    }

    public int get_message() {
        return message;
    }

    public String get_imap_uid() {
        return imap_uid;
    }

    public String get_pop_indx() {
        return pop_indx;
    }

    public String get_mime_type() {
        return mime_type;
    }

    public String get_boundary() {
        return boundary;
    }

    public String get_name() {
        return name;
    }

    public String get_transfer_encoding() {
        return transfer_encoding;
    }

    public int get_size() {
        return size;
    }

    public void set_id(int i) {
        id = i;
    }

    public void set_account(int i) {
        account = i;
    }

    public void set_message(int i) {
        message = i;
    }

    public void set_imap_uid(String s) {
        imap_uid = s;
    }

    public void set_pop_indx(String s) {
        pop_indx = s;
    }

    public void set_mime_type(String s) {
        mime_type = s;
    }

    public void set_boundary(String s) {
        boundary = s;
    }

    public void set_name(String s) {
        name = s;
    }

    public void set_transfer_encoding(String s) {
        transfer_encoding = s;
    }

    public void set_size(int i) {
        size = i;
    }

    protected Attachment(Parcel in) {
        id = in.readInt();
        account = in.readInt();
        message = in.readInt();
        imap_uid = in.readString();
        pop_indx = in.readString();
        mime_type = in.readString();
        boundary = in.readString();
        name = in.readString();
        transfer_encoding = in.readString();
        size = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeInt(account);
        dest.writeInt(message);
        dest.writeString(imap_uid);
        dest.writeString(pop_indx);
        dest.writeString(mime_type);
        dest.writeString(boundary);
        dest.writeString(name);
        dest.writeString(transfer_encoding);
        dest.writeInt(size);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Attachment> CREATOR = new Parcelable.Creator<Attachment>() {
        @Override
        public Attachment createFromParcel(Parcel in) {
            return new Attachment(in);
        }

        @Override
        public Attachment[] newArray(int size) {
            return new Attachment[size];
        }
    };
}
