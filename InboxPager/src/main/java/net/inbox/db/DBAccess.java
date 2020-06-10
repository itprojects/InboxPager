/*
 * InboxPager, an android email client.
 * Copyright (C) 2016-2020  ITPROJECTS
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import android.content.ContentValues;
import android.content.Context;

import net.inbox.InboxPager;
import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

public class DBAccess extends SQLiteOpenHelper {

    private SQLiteDatabase dbw;

    private static final int db_version = 1;// increment if db fields change
    private static final String db_name = "pages";

    // Tables
    private final String table_accounts = "accounts";
    private final String table_messages = "messages";
    private final String table_attachments = "attachments";

    // Table columns of accounts
    private final String key_id = "id";
    private final String key_messages = "messages";
    private final String key_recent = "recent";
    private final String key_unseen = "unseen";
    private final String key_uidnext = "uidnext";
    private final String key_uidvalidity = "uidvalidity";
    private final String key_total_size = "total_size";
    private final String key_auto_refresh = "auto_refresh";
    private final String key_imap_or_pop = "imap_or_pop";
    private final String key_email = "email";
    private final String key_username = "username";
    private final String key_pass = "pass";
    private final String key_imap_or_pop_extensions = "imap_or_pop_extensions";
    private final String key_imap_or_pop_server = "imap_or_pop_server";
    private final String key_imap_or_pop_port = "imap_or_pop_port";
    private final String key_smtp_extensions = "smtp_extensions";
    private final String key_smtp_server = "smtp_server";
    private final String key_smtp_port = "smtp_port";
    private final String key_always_ask_pass = "always_ask_pass";
    private final String key_auto_save_full_msgs = "auto_save_full_msgs";

    // Table columns of messages
    private final String key_account = "account";
    private final String key_to = "to_";// Hidden
    private final String key_cc = "cc";
    private final String key_bcc = "bcc";
    private final String key_from = "from_";
    private final String key_content_type = "content_type";
    private final String key_content_transfer_encoding = "content_transfer_encoding";
    private final String key_charset_plain = "charset_plain";
    private final String key_charset_html = "charset_html";
    private final String key_subject = "subject";
    private final String key_message_id = "message_id";// On server
    private final String key_uid = "uid";
    private final String key_date = "date";
    private final String key_received = "received";
    private final String key_structure = "structure";// MIME structure
    private final String key_contents_plain = "contents";
    private final String key_contents_html = "contents_html";
    private final String key_contents_other = "contents_other";
    private final String key_contents_crypto = "contents_crypto";
    private final String key_full_msg = "full_msg";
    private final String key_attachments = "attachments";
    private final String key_seen = "seen";

    // Table columns of attachments
    private final String key_message = "message";
    private final String key_imap_uid = "imap_uid";// On server
    private final String key_pop_indx = "pop_indx";// In full message
    private final String key_mime_type = "mime_type";
    private final String key_boundary = "boundary";
    private final String key_name = "name";
    private final String key_transfer_encoding = "transfer_encoding";// 7BIT, 8BIT, BASE64
    private final String key_size = "size";// OCTETS (= 8bits)

    // Debug: hexdump -C pages > myfile
    public DBAccess(Context context) {
        super(context, db_name, null, db_version);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);

        // auto_vacuum helps reduce the size of the database,
        // alternatively the database will bloat.
        db.execSQL("PRAGMA auto_vacuum = INCREMENTAL");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String create_table_accounts = "CREATE TABLE IF NOT EXISTS accounts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "messages INTEGER, recent INTEGER, unseen INTEGER,"
                + "uidnext INTEGER, uidvalidity INTEGER, total_size INTEGER,"
                + "auto_refresh BOOLEAN, imap_or_pop BOOLEAN, email TEXT,"
                + "username TEXT, pass TEXT, imap_or_pop_extensions TEXT,"
                + "imap_or_pop_server TEXT, imap_or_pop_port TEXT, smtp_extensions TEXT,"
                + "smtp_server TEXT, smtp_port TEXT, always_ask_pass INTEGER,"
                + "auto_save_full_msgs INTEGER"
                + " )";
        db.execSQL(create_table_accounts);

        String create_table_messages = "CREATE TABLE IF NOT EXISTS messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, account INTEGER, to_ TEXT, cc TEXT,"
                + "bcc TEXT, from_ TEXT, content_type TEXT, content_transfer_encoding TEXT,"
                + "charset_plain TEXT, charset_html TEXT, subject TEXT, message_id TEXT,"
                + "uid TEXT, date TEXT, received TEXT, structure TEXT, contents TEXT,"
                + "contents_html TEXT, contents_other TEXT, contents_crypto TEXT, full_msg TEXT,"
                + "size INTEGER, attachments INTEGER, seen BOOLEAN )";
        db.execSQL(create_table_messages);

        String create_table_attachments = "CREATE TABLE IF NOT EXISTS attachments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, account INTEGER, message INTEGER,"
                + "imap_uid TEXT, pop_indx TEXT, mime_type TEXT, boundary TEXT, name TEXT,"
                + "transfer_encoding TEXT, size INTEGER"
                + " )";
        db.execSQL(create_table_attachments);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int old_ver, int new_ver) {
        // Delete previous tables from database
        db.execSQL("DROP TABLE IF EXISTS accounts");
        db.execSQL("DROP TABLE IF EXISTS messages");
        db.execSQL("DROP TABLE IF EXISTS attachments");

        // Create a new upgraded database
        this.onCreate(db);
    }

    public void activate_db(String s) {
        dbw = getWritableDatabase(s);
    }

    public void rekey_db(String s) {
        // Previous Method
        dbw.query(String.format("PRAGMA rekey = '%s'", s));
        System.gc();
    }

    /**
     * Checks then cleans and defragments database, improving overall speed.
     * This function does not need to be run everyday, so checking for date.
     **/
    public void vacuum_db() {
        if (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == 1) {
            dbw.execSQL("VACUUM");
            System.gc();
        }
    }

    public int add_account(Inbox current) {
        ContentValues values = new ContentValues();
        values.put(key_messages, current.get_messages());
        values.put(key_recent, current.get_recent());
        values.put(key_unseen, current.get_unseen());
        values.put(key_uidnext, current.get_uidnext());
        values.put(key_uidvalidity, current.get_uidvalidity());
        values.put(key_total_size, current.get_total_size());
        values.put(key_auto_refresh, current.get_auto_refresh());
        values.put(key_imap_or_pop, current.get_imap_or_pop());
        values.put(key_email, current.get_email());
        values.put(key_username, current.get_username());
        values.put(key_pass, current.get_pass());
        values.put(key_imap_or_pop_extensions, current.get_imap_or_pop_extensions());
        values.put(key_imap_or_pop_server, current.get_imap_or_pop_server());
        values.put(key_imap_or_pop_port, current.get_imap_or_pop_port());
        values.put(key_smtp_extensions, current.get_smtp_extensions());
        values.put(key_smtp_server, current.get_smtp_server());
        values.put(key_smtp_port, current.get_smtp_port());
        values.put(key_always_ask_pass, current.get_always_ask_pass());
        values.put(key_auto_save_full_msgs, current.get_auto_save_full_msgs());

        // ID of the newly created row
        int ret = (int) dbw.insert(table_accounts, null, values);

        // Setting the ID to the newly added item
        current.set_id(ret);

        return ret;
    }

    public void update_account(Inbox current) {
        ContentValues values = new ContentValues();
        values.put(key_messages, current.get_messages());
        values.put(key_recent, current.get_recent());
        values.put(key_unseen, current.get_unseen());
        values.put(key_uidnext, current.get_uidnext());
        values.put(key_uidvalidity, current.get_uidvalidity());
        values.put(key_total_size, current.get_total_size());
        values.put(key_auto_refresh, current.get_auto_refresh());
        values.put(key_imap_or_pop, current.get_imap_or_pop());
        values.put(key_email, current.get_email());
        values.put(key_username, current.get_username());
        values.put(key_pass, current.get_pass());
        values.put(key_imap_or_pop_extensions, current.get_imap_or_pop_extensions());
        values.put(key_imap_or_pop_server, current.get_imap_or_pop_server());
        values.put(key_imap_or_pop_port, current.get_imap_or_pop_port());
        values.put(key_smtp_extensions, current.get_smtp_extensions());
        values.put(key_smtp_server, current.get_smtp_server());
        values.put(key_smtp_port, current.get_smtp_port());
        values.put(key_always_ask_pass, current.get_always_ask_pass());
        values.put(key_auto_save_full_msgs, current.get_auto_save_full_msgs());

        dbw.update(table_accounts, values, key_id + " = " + current.get_id(), null);
    }

    public Inbox get_account(int id) {
        // Query database
        Cursor cursor = dbw.query(table_accounts, new String[] { "*" }, "id = " + id,
                null, null, null, null);

        // If the account (id) is found
        if (cursor != null) {
            cursor.moveToFirst();

            Inbox current = new Inbox();
            current.set_id(cursor.getInt(0));
            current.set_messages(cursor.getInt(1));
            current.set_recent(cursor.getInt(2));
            current.set_unseen(cursor.getInt(3));
            current.set_uidnext(cursor.getInt(4));
            current.set_uidvalidity(cursor.getInt(5));
            current.set_total_size(cursor.getInt(6));
            current.set_auto_refresh(cursor.getInt(7) == 1);
            current.set_imap_or_pop(cursor.getInt(8) == 1);
            current.set_email(cursor.getString(9));
            current.set_username(cursor.getString(10));
            current.set_pass(cursor.getString(11));
            current.set_imap_or_pop_extensions(cursor.getString(12));
            current.set_imap_or_pop_server(cursor.getString(13));
            current.set_imap_or_pop_port(cursor.getInt(14));
            current.set_smtp_extensions(cursor.getString(15));
            current.set_smtp_server(cursor.getString(16));
            current.set_smtp_port(cursor.getInt(17));
            current.set_always_ask_pass(cursor.getInt(18) == 1);
            current.set_auto_save_full_msgs(cursor.getInt(19) == 1);

            // Prevent memory issues
            cursor.close();

            return current;
        } else return null;
    }

    // Legacy code
    public int get_global_unseen_count() {
        Cursor cursor = dbw.query(table_accounts, new String[] { key_unseen }, null,
                null, null, null, null);

        int count = 0;
        if (cursor.moveToFirst()) {
            do {
                count += cursor.getInt(0);
            } while (cursor.moveToNext());
        }

        // Prevent memory issues
        cursor.close();

        return count;
    }

    public int update_account_unseen_count(int id) {
        int count = count_unseen_account_messages(id);

        int current_count = -1;

        Cursor cursor = dbw.query(table_accounts, new String[] { key_unseen }, "id = " + id,
                null, null, null, null);

        if (cursor.moveToFirst()) current_count = cursor.getInt(0);

        // Updating unread messages count for account
        if (current_count != -1 && current_count != count) dbw.execSQL("UPDATE " + table_accounts
                + " SET unseen=" + count + " WHERE id = " + id);

        // Prevent memory issues
        cursor.close();

        return count;
    }

    /**
     * Count unseen messages for account.
     **/
    public int count_unseen_account_messages(int account_id) {
        int count = -1;
        try {
            Cursor cursor = dbw.query(table_messages, new String[] { "COUNT(1)" },
                    "account = " + account_id + " AND seen = 0", null, null, null, null);
            if (!cursor.moveToFirst()) {
                count = -1000;
            } else {
                count = cursor.getInt(0);
            }
            cursor.close();
        } catch (Exception e) {
            // A rare exception.
            InboxPager.log = InboxPager.log.concat(e.getMessage() == null ? "!DB!" : e.getMessage());
        }
        return count;
    }

    public ArrayList<Inbox> get_all_accounts() {
        Cursor cursor = dbw.query(table_accounts, new String[] { "*" }, null,
                null, null, null, null);

        ArrayList<Inbox> accounts = new ArrayList<>();
        Inbox current;
        if (cursor.moveToFirst()) {
            do {
                current = new Inbox();
                current.set_id(cursor.getInt(0));
                current.set_messages(cursor.getInt(1));
                current.set_recent(cursor.getInt(2));
                current.set_unseen(cursor.getInt(3));
                current.set_uidnext(cursor.getInt(4));
                current.set_uidvalidity(cursor.getInt(5));
                current.set_total_size(cursor.getInt(6));
                current.set_auto_refresh(cursor.getInt(7) == 1);
                current.set_imap_or_pop(cursor.getInt(8) == 1);
                current.set_email(cursor.getString(9));
                current.set_always_ask_pass(cursor.getInt(18) == 1);
                current.set_auto_save_full_msgs(cursor.getInt(19) == 1);

                accounts.add(current);
            } while (cursor.moveToNext());
        }

        // Prevent memory issues
        cursor.close();

        return accounts;
    }

    public ArrayList<Integer> get_all_accounts_id() {
        Cursor cursor = dbw.query(table_accounts, new String[] { "*" }, null,
                null, null, null, null);

        ArrayList<Integer> accounts = new ArrayList<>();
        Integer current;
        if (cursor.moveToFirst()) {
            do {
                current = cursor.getInt(0);
                accounts.add(current);
            } while (cursor.moveToNext());
        }

        // Prevent memory issues
        cursor.close();

        return accounts;
    }

    public void delete_account(int id) {
        if (table_exists(table_attachments)) dbw.delete(table_attachments, key_account + " = " + id, null);

        if (table_exists(table_messages)) dbw.delete(table_messages, key_account + " = " + id, null);

        if (table_exists(table_accounts)) dbw.delete(table_accounts, key_id + " = " + id, null);
    }

    public void add_message(Message current) {
        ContentValues values = new ContentValues();
        values.put(key_account, current.get_account());
        values.put(key_to, current.get_to());
        values.put(key_cc, current.get_cc());
        values.put(key_bcc, current.get_bcc());
        values.put(key_from, current.get_from());
        values.put(key_content_type, current.get_content_type());
        values.put(key_content_transfer_encoding, current.get_content_transfer_encoding());
        values.put(key_charset_plain, current.get_charset_plain());
        values.put(key_charset_html, current.get_charset_html());
        values.put(key_subject, current.get_subject());
        values.put(key_message_id, current.get_message_id());
        values.put(key_uid, current.get_uid());
        values.put(key_date, current.get_date());
        values.put(key_received, current.get_received());
        values.put(key_structure, current.get_structure());
        values.put(key_contents_plain, current.get_contents_plain());
        values.put(key_contents_html, current.get_contents_html());
        values.put(key_contents_other, current.get_contents_other());
        values.put(key_contents_crypto, current.get_contents_crypto());
        values.put(key_full_msg, current.get_full_msg());
        values.put(key_size, current.get_size());
        values.put(key_attachments, current.get_attachments());
        values.put(key_seen, current.get_seen());

        // ID of the newly created row
        int ret = (int) dbw.insert(table_messages, null, values);

        // Setting the ID to the newly added item
        current.set_id(ret);
    }

    /**
     * Used in testing.
     **/
    public int update_message(Message current) {
        ContentValues values = new ContentValues();
        values.put(key_account, current.get_account());
        values.put(key_to, current.get_to());
        values.put(key_cc, current.get_cc());
        values.put(key_bcc, current.get_bcc());
        values.put(key_from, current.get_from());
        values.put(key_content_type, current.get_content_type());
        values.put(key_content_transfer_encoding, current.get_content_transfer_encoding());
        values.put(key_charset_plain, current.get_charset_plain());
        values.put(key_charset_html, current.get_charset_html());
        values.put(key_subject, current.get_subject());
        values.put(key_message_id, current.get_message_id());
        values.put(key_uid, current.get_uid());
        values.put(key_date, current.get_date());
        values.put(key_received, current.get_received());
        values.put(key_structure, current.get_structure());
        values.put(key_contents_plain, current.get_contents_plain());
        values.put(key_contents_html, current.get_contents_html());
        values.put(key_contents_other, current.get_contents_other());
        values.put(key_contents_crypto, current.get_contents_crypto());
        values.put(key_full_msg, current.get_full_msg());
        values.put(key_size, current.get_size());
        values.put(key_attachments, current.get_attachments());
        values.put(key_seen, current.get_seen());

        return dbw.update(table_messages, values, key_id + " = " + current.get_id(), null);
    }

    public Message get_message(int id) {
        Cursor cursor = dbw.query(table_messages, new String[] { "*" }, "id = " + id,
                null, null, null, null);

        // If the message (id) is found
        if (cursor != null) {
            cursor.moveToFirst();

            Message current = new Message();
            current.set_id(cursor.getInt(0));
            current.set_account(cursor.getInt(1));
            current.set_to(cursor.getString(2));
            current.set_cc(cursor.getString(3));
            current.set_bcc(cursor.getString(4));
            current.set_from(cursor.getString(5));
            current.set_content_type(cursor.getString(6));
            current.set_content_transfer_encoding(cursor.getString(7));
            current.set_charset_plain(cursor.getString(8));
            current.set_charset_html(cursor.getString(9));
            current.set_subject(cursor.getString(10));
            current.set_message_id(cursor.getString(11));
            current.set_uid(cursor.getString(12));
            current.set_date(cursor.getString(13));
            current.set_received(cursor.getString(14));
            current.set_structure(cursor.getString(15));
            current.set_contents_plain(cursor.getString(16));
            current.set_contents_html(cursor.getString(17));
            current.set_contents_other(cursor.getString(18));
            current.set_contents_crypto(cursor.getString(19));
            current.set_full_msg(cursor.getString(20));
            current.set_size(cursor.getInt(21));
            current.set_attachments(cursor.getInt(22));
            current.set_seen(cursor.getInt(23) == 1);

            // Prevent memory issues
            cursor.close();

            return current;
        } else return null;
    }

    /**
     * Changes message seen/unseen status.
     **/
    public void seen_unseen_message(int account_id, String uid, boolean seen) {
        int i_seen = 0;
        if (seen) i_seen = 1;
        dbw.execSQL("UPDATE " + table_messages + " SET seen='" + i_seen
                + "' WHERE (uid='" + uid + "' AND account='" + account_id + "')");
    }

    /**
     * Changes all messages' status to seen.
     **/
    public void mark_all_seen(int id) {
        Cursor cursor = dbw.query(table_messages, new String[] { "*" }, "account = " + id,
                null, null, null, null);

        if (cursor.moveToFirst()) {
            do {
                boolean b_set = cursor.getInt(23) == 1;
                if (!b_set) {
                    seen_unseen_message(id, cursor.getString(12), true);
                }
            } while (cursor.moveToNext());
        }

        // Prevent memory issues
        cursor.close();
    }

    /**
     * Counts the messages for an account.
     **/
    public int get_messages_count(int id) {
        Cursor cursor = dbw.query(table_messages, new String[] { "COUNT(*)" }, "account = " + id,
                null, null, null, null);

        int result = 0;
        if (cursor.moveToFirst()) result = cursor.getInt(0);

        // Prevent memory issues
        cursor.close();

        return result;
    }

    /**
     * Used with inbox statistical dialog.
     **/
    public int get_total_size(int id) {
        Cursor cursor = dbw.query(table_accounts, new String[] { key_total_size }, "id = " + id,
                null, null, null, null);

        int result = 0;
        if (cursor.moveToFirst()) result = cursor.getInt(0);

        // Prevent memory issues
        cursor.close();

        return result;
    }

    /**
     * Computes the total message size on server for an account.
     * Used with IMAP.
     **/
    public void refresh_total_size(int id) {
        Cursor cursor = dbw.query(table_messages, new String[] { key_size }, "account = " + id,
                null, null, null, null);

        int result = 0;
        if (cursor.moveToFirst()) {
            do {
                result += cursor.getInt(0);
            } while (cursor.moveToNext());
        }

        // Updating total size of (server) inbox
        dbw.execSQL("UPDATE " + table_accounts + " SET total_size=" + result + " WHERE id = " + id);

        // Prevent memory issues
        cursor.close();
    }

    /**
     * Get the DB id and uid of a message. Every row is a message.
     **/
    public HashMap<Integer, String> get_all_message_uids(int id) {
        Cursor cursor = dbw.query(table_messages, new String[] { "*" }, "account = " + id,
                null, null, null, null);

        HashMap<Integer, String> messages = new HashMap<>();

        if (cursor.moveToFirst()) {
            do {
                // DB ID and message-uid
                messages.put(cursor.getInt(0), cursor.getString(12));
            } while (cursor.moveToNext());
        }

        // Prevent memory issues
        cursor.close();

        return messages;
    }

    public SortedMap<String, ArrayList<Message>> get_all_messages(int id) {
        Cursor cursor = dbw.query(table_messages, new String[] {"*"}, "account = " + id,
                null, null, null, null);

        ArrayList<Message> msgs = new ArrayList<>();
        HashMap<String, String> msg_set_unique_addr = new HashMap<>();

        // Sender email@example.com has a Message list
        SortedMap<String, ArrayList<Message>> messages = new TreeMap<>(new Comparator<String>() {
            @Override public int compare(String s1, String s2) {
                return s1.compareToIgnoreCase(s2);
            }
        });

        int ind;
        String addr;
        Message current;
        if (cursor.moveToFirst()) {
            do {
                current = new Message();
                current.set_id(cursor.getInt(0));
                current.set_account(cursor.getInt(1));
                current.set_from(cursor.getString(5));
                current.set_subject(cursor.getString(10));
                current.set_uid(cursor.getString(12));
                current.set_attachments(cursor.getInt(22));
                current.set_seen(cursor.getInt(23) == 1);

                // Splitting John Doe <john.doe@example.com>
                addr = current.get_from();
                ind = addr.indexOf("<");
                if (ind > -1) {
                    current.set_from_basic(addr.substring(ind + 1, addr.indexOf(">")));
                    current.set_from(addr);
                    msg_set_unique_addr.put(current.get_from_basic(), current.get_from());
                } else {
                    current.set_from_basic(addr);
                    if (!msg_set_unique_addr.containsKey(current.get_from_basic())) {
                        msg_set_unique_addr.put(current.get_from_basic(), null);
                    }
                }

                msgs.add(current);
            } while (cursor.moveToNext());
        }

        // Prevent memory issues
        cursor.close();

        // Grouping. Note: code beyond this line needs a functional programming update.

        // Removing null values for addresses such as email@example.com, no <>
        for (Map.Entry<String, String> e : msg_set_unique_addr.entrySet()) {
            if (e.getValue() == null) e.setValue(e.getKey());
        }

        // Add messages to SortedMap for email address
        for (Message msg : msgs) {
            if (messages.containsKey(msg.get_from_basic())) {
                ArrayList<Message> message_list = messages.get(msg.get_from_basic());
                try {
                    message_list.add(0, msg);
                } catch (Exception e) {
                    InboxPager.log = InboxPager.log.concat(e.getMessage() == null ? "!MSG!" : e.getMessage());
                }
            } else {
                ArrayList<Message> message_list = new ArrayList<>();
                message_list.add(0, msg);
                messages.put(msg.get_from_basic(), message_list);
            }
        }

        // Renaming keys/branches of SortedMap
        for (Map.Entry<String, String> e : msg_set_unique_addr.entrySet()) {
            if (!e.getValue().equals(e.getKey())) {
                messages.put(e.getValue(), messages.remove(e.getKey()));
            }
        }

        return messages;
    }

    public void delete_message(int id) {
        dbw.delete(table_attachments, key_message + " = " + id, null);
        dbw.delete(table_messages, key_id + " = " + id, null);
    }

    /**
     * Delete all messages, for an account.
     **/
    public void delete_all_messages(HashMap<Integer, String> m) {
        Iterator<HashMap.Entry<Integer, String>> m_iterate = m.entrySet().iterator();
        while (m_iterate.hasNext()) {
            HashMap.Entry<Integer, String> entry = m_iterate.next();
            dbw.delete(table_attachments, key_message + " = " + entry.getKey(), null);
            dbw.delete(table_messages, key_id + " = " + entry.getKey(), null);
            m_iterate.remove();
        }
    }

    /**
     * Deletes the full message copy from local database.
     * Uses message ID.
     **/
    public void delete_full_message(int id) {
        dbw.execSQL("UPDATE " + table_messages + " SET full_msg='' WHERE id = " + id);
    }

    /**
     * Deletes all the full message copies from local database.
     * Uses account ID.
     **/
    public void delete_all_full_messages(int id) {
        dbw.execSQL("UPDATE " + table_messages + " SET full_msg='' WHERE account = " + id);
    }

    public void add_attachment(Attachment current) {
        ContentValues values = new ContentValues();
        values.put(key_account, current.get_account());
        values.put(key_message, current.get_message());
        values.put(key_imap_uid, current.get_imap_uid());
        values.put(key_pop_indx, current.get_pop_indx());
        values.put(key_mime_type, current.get_mime_type());
        values.put(key_boundary, current.get_boundary());
        values.put(key_name, current.get_name());
        values.put(key_transfer_encoding, current.get_transfer_encoding());
        values.put(key_size, current.get_size());

        // ID of the newly created row
        int ret = (int) dbw.insert(table_attachments, null, values);

        // Setting the ID to the newly added item
        current.set_id(ret);
    }

    public int update_attachment(Attachment current) {
        ContentValues values = new ContentValues();
        values.put(key_account, current.get_account());
        values.put(key_message, current.get_message());
        values.put(key_imap_uid, current.get_imap_uid());
        values.put(key_pop_indx, current.get_pop_indx());
        values.put(key_mime_type, current.get_mime_type());
        values.put(key_boundary, current.get_boundary());
        values.put(key_name, current.get_name());
        values.put(key_transfer_encoding, current.get_transfer_encoding());
        values.put(key_size, current.get_size());

        return dbw.update(table_attachments, values, key_id + " = " + current.get_id(), null);
    }

    /**
     * Get a list of attachments for a specific message of an account.
     **/
    public ArrayList<Attachment> get_all_attachments_of_msg(int mid) {
        Cursor cursor = dbw.query(table_attachments, new String[] { "*" }, "message = " + mid,
                null, null, null, null);

        ArrayList<Attachment> attachments = new ArrayList<>();
        Attachment current;
        if (cursor.moveToFirst()) {
            do {
                current = new Attachment();
                current.set_id(cursor.getInt(0));
                current.set_account(cursor.getInt(1));
                current.set_message(cursor.getInt(2));
                current.set_imap_uid(cursor.getString(3));
                current.set_pop_indx(cursor.getString(4));
                current.set_mime_type(cursor.getString(5));
                current.set_boundary(cursor.getString(6));
                current.set_name(cursor.getString(7));
                current.set_transfer_encoding(cursor.getString(8));
                current.set_size(cursor.getInt(9));
                attachments.add(current);
            } while (cursor.moveToNext());
        }

        // Prevent memory issues
        cursor.close();

        return attachments;
    }

    private boolean table_exists(String s) {
        if (s == null) return false;
        Cursor cursor = dbw.query("sqlite_master", new String[] { "COUNT(1)" },
                "type = 'table' AND name = '" + s + "'", null, null, null, null);
        if (!cursor.moveToFirst()) return false;
        boolean ret = cursor.getInt(0) > 0;
        cursor.close();
        return ret;
    }
}
