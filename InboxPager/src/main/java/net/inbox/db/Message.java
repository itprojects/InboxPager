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
package net.inbox.db;

public class Message {

    private int id = -2;
    private int account = -2;
    private String to;
    private String cc;
    private String bcc;
    private String from;
    private String content_type;
    private String content_transfer_encoding;
    private String charset_plain;
    private String charset_html;
    private String subject;
    private String message_id;
    private String uid;
    private String date;
    private String received;
    private String structure;
    private String contents_plain;
    private String contents_html;
    private String contents_other;
    private String contents_crypto;
    private String full_msg;
    private int size = 0;// octets = 8 bits
    private int attachments = -2;
    private boolean seen = true;

    public int get_id() {
        return id;
    }

    public int get_account() {
        return account;
    }

    public String get_to() {
        return to;
    }

    public String get_cc() {
        return cc;
    }

    public String get_bcc() {
        return bcc;
    }

    public String get_from() {
        return from;
    }

    public String get_content_type() {
        return content_type;
    }

    public String get_content_transfer_encoding() {
        return content_transfer_encoding;
    }

    public String get_charset_plain() {
        return charset_plain;
    }

    public String get_charset_html() {
        return charset_html;
    }

    public String get_subject() {
        return subject;
    }

    public String get_message_id() {
        return message_id;
    }

    public String get_uid() {
        return uid;
    }

    public String get_date() {
        return date;
    }

    public String get_received() {
        return received;
    }

    public String get_structure() {
        return structure;
    }

    public String get_contents_plain() {
        return contents_plain;
    }

    public String get_contents_html() {
        return contents_html;
    }

    public String get_contents_other() {
        return contents_other;
    }

    public String get_contents_crypto() {
        return contents_crypto;
    }

    public String get_full_msg() {
        return full_msg;
    }

    public int get_size() {
        return size;
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

    public void set_account(int i) {
        account = i;
    }

    public void set_to(String s) {
        to = s;
    }

    public void set_cc(String s) {
        cc = s;
    }

    public void set_bcc(String s) {
        bcc = s;
    }

    public void set_from(String s) {
        from = s;
    }

    public void set_content_type(String s) {
        content_type = s;
    }

    public void set_content_transfer_encoding(String s) {
        content_transfer_encoding = s;
    }

    public void set_charset_plain(String s) {
        charset_plain = s;
    }

    public void set_charset_html(String s) {
        charset_html = s;
    }

    public void set_subject(String s) {
        subject = s;
    }

    public void set_message_id(String s) {
        message_id = s;
    }

    public void set_uid(String s) {
        uid = s;
    }

    public void set_date(String s) {
        date = s;
    }

    public void set_received(String s) {
        received = s;
    }

    public void set_structure(String s) {
        structure = s;
    }

    public void set_contents_plain(String s) {
        contents_plain = s;
    }

    public void set_contents_html(String s) {
        contents_html = s;
    }

    public void set_contents_other(String s) {
        contents_other = s;
    }

    public void set_contents_crypto(String s) {
        contents_crypto = s;
    }

    public void set_full_msg(String s) {
        full_msg = s;
    }

    public void set_size(int i) {
        size = i;
    }

    public void set_attachments(int i) {
        attachments = i;
    }

    public void set_seen(boolean b) {
        seen = b;
    }
}
