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

public class Inbox {

    private int id = -2;
    private int messages;
    private int recent;
    private int unseen;
    private int uidnext;
    private int uidvalidity;
    private int total_size;
    private boolean auto_refresh;
    private boolean imap_or_pop;
    private String email;
    private String username;
    private String pass;
    private String imap_or_pop_extensions = "-1";
    private String imap_or_pop_server;
    private int imap_or_pop_port;
    private String smtp_extensions = "-1";
    private String smtp_server;
    private int smtp_port;
    private boolean always_ask_pass;
    private boolean auto_save_full_msgs;

    // Non-db params
    private boolean ehlo;// If unsupported, old SMTP
    private boolean to_be_refreshed = false;

    public int get_id() {
        return id;
    }

    public int get_messages() {
        return messages;
    }

    public int get_recent() {
        return recent;
    }

    public int get_unseen() {
        return unseen;
    }

    public int get_uidnext() {
        return uidnext;
    }

    public int get_uidvalidity() {
        return uidvalidity;
    }

    public boolean get_auto_refresh() {
        return this.auto_refresh;
    }

    public int get_total_size() {
        return total_size;
    }

    public boolean get_imap_or_pop() {
        return imap_or_pop;
    }

    public String get_email() {
        return email;
    }

    public String get_username() {
        return username;
    }

    public String get_pass() {
        return pass;
    }

    public String get_imap_or_pop_extensions() {
        return imap_or_pop_extensions;
    }

    public String get_imap_or_pop_server() {
        return imap_or_pop_server;
    }

    public int get_imap_or_pop_port() {
        return imap_or_pop_port;
    }

    public String get_smtp_extensions() {
        return smtp_extensions;
    }

    public String get_smtp_server() {
        return smtp_server;
    }

    public int get_smtp_port() {
        return smtp_port;
    }

    public boolean get_always_ask_pass() {
        return always_ask_pass;
    }

    public boolean get_auto_save_full_msgs() {
        return auto_save_full_msgs;
    }

    public boolean get_ehlo() {
        return ehlo;
    }

    public boolean get_to_be_refreshed() {
        return to_be_refreshed;
    }

    public void set_id(int i) {
        id = i;
    }

    public void set_messages(int i) {
        messages = i;
    }

    public void set_recent(int i) {
        recent = i;
    }

    public void set_unseen(int i) {
        unseen = i;
    }

    public void set_uidnext(int i) {
        uidnext  = i;
    }

    public void set_uidvalidity(int i) {
        uidvalidity = i;
    }

    public void set_auto_refresh(boolean b) {
        auto_refresh = b;
    }

    public void set_total_size(int i) {
        total_size = i;
    }

    public void set_imap_or_pop(boolean b) {
        imap_or_pop = b;
    }

    public void set_email(String s) {
        email = s;
    }

    public void set_username(String s) {
        username = s;
    }

    public void set_pass(String s) {
        pass = s;
    }

    public void set_imap_or_pop_extensions(String s) {
        imap_or_pop_extensions = s;
    }

    public void set_imap_or_pop_server(String s) {
        imap_or_pop_server = s;
    }

    public void set_imap_or_pop_port(int i) {
        imap_or_pop_port = i;
    }

    public void set_smtp_extensions(String s) {
        smtp_extensions = s;
    }

    public void set_smtp_server(String s) {
        smtp_server = s;
    }

    public void set_smtp_port(int i) {
        smtp_port = i;
    }

    public void set_always_ask_pass(boolean b) {
        always_ask_pass = b;
    }

    public void set_auto_save_full_msgs(boolean b) {
        auto_save_full_msgs = b;
    }

    public void set_ehlo(boolean b) {
        ehlo = b;
    }

    public void set_to_be_refreshed(boolean b) {
        to_be_refreshed = b;
    }

    public boolean smtp_check_extension(String s) {
        for (String t : smtp_extensions.toUpperCase().split("\n")) {
            if (t.contains(s)) return true;
        }
        return false;
    }

    public String smtp_check_extension_return(String s) {
        for (String t : smtp_extensions.toUpperCase().split("\n")) {
            if (t.startsWith(s)) return t;
        }
        return null;
    }
}
