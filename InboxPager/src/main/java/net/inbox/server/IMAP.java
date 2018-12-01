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
package net.inbox.server;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;

import net.inbox.InboxMessage;
import net.inbox.InboxPager;
import net.inbox.R;
import net.inbox.db.Attachment;
import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.dialogs.Dialogs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IMAP extends Handler {

    private class DataIMAP extends Data {
        // Command sequence index
        int cmd_indx = 0;

        // Used with EXAMINE
        String[] examine_flags;

        // Used with FETCH (message uid from server, unique)
        ArrayList<String> message_uids = new ArrayList<>();

        // Used with SEARCH UNSEEN
        ArrayList<String> unseen_uids = new ArrayList<>();

        // Used with delegation
        int msg_indx = 0;

        // Used with BODYSTRUCTURE
        // Message attachments, (uid, mime-type, name, transfer-encoding, size)
        ArrayList<String[]> msg_structure = new ArrayList<>();

        // PLAIN, 1.1, UTF-8, QUOTED-PRINTABLE
        String[] msg_text_plain = new String[] { "-1", "-1", "-1", "-1" };
        String[] msg_text_html = new String[] { "-1", "-1", "-1", "-1" };
        String[] msg_text_other = new String[] { "-1" };

        // Used in saving attachments and text
        Attachment att_item;
        FileOutputStream fstream;

        /**
         * Resets all message data, for a new instance.
         **/
        void msg_reset() {
            msg_current = new Message();
            msg_structure = new ArrayList<>();
            msg_text_plain = new String[] { "-1", "-1", "-1", "-1" };
            msg_text_html = new String[] { "-1", "-1", "-1", "-1" };
            msg_text_other = new String[] { "-1" };
        }
    }

    private DataIMAP data;

    // Command sent index
    private int num = 0;

    public IMAP(Context ct) {
        super(ct);
    }

    @Override
    public void reset() {
        over = false;
        ready = false;
        excepted = false;
        num = 0;
        tag = "";
        stat = 0;
        io_sock = null;
        data = null;
        current_inbox = null;
    }

    @Override
    public void reply(String l) {
        if (l.isEmpty()) {
            data.sbuffer.append("\r\n");
            return;
        }
        if (l.startsWith(tag + " OK") || l.startsWith(tag + " NO") || l.startsWith(tag + " BAD")) {
            // Line is a tagged response: "a1 OK", "a1 BAD", "a1 NO"
            if (l.startsWith(tag + " OK")) {
                data.cmd_return = l;
                stat = 1;
                if (data.delegate) {
                    imap_delegation(false);
                } else {
                    imap_conductor(false);
                }
            } else if (l.startsWith(tag + " NO")) {
                data.cmd_return = l;
                stat = 2;
                data.sequence.clear();
                data.sequence.add("logout");

                String str_tmp = tag + " NO";
                error_dialog(current_inbox.get_email() + "\n\n" + l.substring(str_tmp.length()));
            } else if (l.startsWith(tag + " BAD")) {
                data.cmd_return = l;
                stat = 3;
                data.sequence.clear();
                data.sequence.add("LOGOUT");
                error_dialog(current_inbox.get_email() + "\n\n" + l);
            }
        } else if (l.charAt(0) != '*' && data.fstream != null) {
            l += "\r\n";
            data.sbuffer.append(l);
        } else if (l.charAt(0) == '*' || l.charAt(0) == '+')  {
            // Catching stray data
            if (l.length() > 2 && l.charAt(1) != ' ') {
                // Message data
                data.sbuffer.append(l);
                data.sbuffer.append("\r\n");
                return;
            }

            // Parsing command response
            if (ready) {
                // Server response parameters
                data.cmd_return_pars += l + "\r\n";
            } else {
                // Server initial 'ready' response
                ready = l.startsWith(l.charAt(0) + " OK");
                if (ready) imap_conductor(true);
            }
        } else {
            // Message data
            data.sbuffer.append(l);
            data.sbuffer.append("\r\n");
        }
    }

    /**
     * Test remote server for correct operation.
     **/
    @Override
    public void test_server(Inbox inn, Context ct) {
        current_inbox = inn;
        ctx = ct;

        // A partial reset of variables
        over = false;
        ready = false;
        excepted = false;

        // Data storage
        data = new DataIMAP();

        // Testing
        data.test_mode = true;

        // Refresh messages sequence
        data.sequence.add("CAPABILITY");
        data.sequence.add("LOGOUT");

        socket_start_imap(this);

        try {
            sleep(1000);
        } catch (InterruptedException e) {
            InboxPager.log += ctx.getString(R.string.ex_field) + e.getMessage() + "\n\n";
        }

        if (!excepted) {
            // Prepare a callback for results
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        sleep(3000);
                    } catch (InterruptedException e) {
                        InboxPager.log += ctx.getString(R.string.ex_field) + e.getMessage() + "\n\n";
                    }

                    if (current_inbox.get_imap_or_pop_extensions() != null
                            && !current_inbox.get_imap_or_pop_extensions().isEmpty()) {
                        String tested;
                        if (current_inbox.get_imap_or_pop_extensions()
                                .equals(ctx.getString(R.string.err_no_capability))) {
                            tested = ctx.getString(R.string.err_no_capability);
                        } else {
                            // Preparing the dialog message
                            load_extensions();
                            tested = ctx.getString(R.string.edit_account_check_login_types) + "\n\n";
                            for (int i = 0;i < data.auths.size();++i) {
                                if (i == (data.auths.size() - 1)) {
                                    tested += data.auths.get(i).toUpperCase();
                                } else {
                                    tested = tested.concat(data.auths.get(i).toUpperCase() + ", ");
                                }
                            }
                            tested += "\n\n" + ctx.getString(R.string.edit_account_check_other) + "\n\n";
                            for (int i = 0;i < data.general.size();++i) {
                                if (i == (data.general.size() - 1)) {
                                    tested += data.general.get(i).toUpperCase();
                                } else {
                                    tested = tested.concat(data.general.get(i).toUpperCase() + ", ");
                                }
                            }
                        }
                        Dialogs.dialog_server_ext(ctx.getString(R.string.edit_account_check_incoming),
                                tested, (AppCompatActivity) ctx);
                    } else {
                        Dialogs.dialog_server_ext(ctx.getString(R.string.edit_account_check_incoming),
                                ctx.getString(R.string.edit_account_check_fail),
                                (AppCompatActivity) ctx);
                    }
                    reset();
                }
            }).start();
        } else {
            reset();
        }
    }

    @Override
    public void default_action(boolean multi, Inbox inn, Context ct) {
        multiple = multi;
        current_inbox = inn;
        ctx = ct;

        // A partial reset of variables
        over = false;
        ready = false;
        excepted = false;

        // Data storage
        data = new DataIMAP();

        // Check server capabilities
        if (current_inbox.get_imap_or_pop_extensions().equals("-1")) {
            data.sequence.add("CAPABILITY");
        }

        if (multiple) {
            on_ui_thread(current_inbox.get_email(),
                    ctx.getString(R.string.progress_refreshing));
        } else {
            on_ui_thread(ctx.getString(R.string.progress_title),
                    ctx.getString(R.string.progress_refreshing));
        }

        // Refresh messages sequence
        data.sequence.add("LOGIN");
        data.sequence.add("STATUS");
        data.sequence.add("PREP_REFRESH");
        data.sequence.add("LOGOUT");

        socket_start_imap(this);
    }

    @Override
    public void attachment_action(int account_id, Attachment att, String save_path, Context ct) {
        current_inbox = db.get_account(account_id);
        ctx = ct;

        // A partial reset of variables
        over = false;
        ready = false;
        excepted = false;

        // Data storage
        data = new DataIMAP();

        // Check server capabilities
        if (current_inbox.get_imap_or_pop_extensions().equals("-1")) {
            data.sequence.add("CAPABILITY");
        }

        data.att_item = att;
        data.a_file = new File(save_path + "/" + data.att_item.get_name());

        on_ui_thread(ctx.getString(R.string.progress_downloading), data.att_item.get_name());

        // Refresh messages sequence
        data.sequence.add("LOGIN");
        data.sequence.add("EXAMINE");
        data.sequence.add("SAVE_ATTACHMENT");
        data.sequence.add("LOGOUT");

        socket_start_imap(this);
    }

    @Override
    public void msg_action(int aid, Message msg, String save_path, boolean sv, Context ct) {
        current_inbox = db.get_account(aid);
        ctx = ct;

        // A partial reset of variables
        over = false;
        ready = false;
        excepted = false;

        // Data storage
        data = new DataIMAP();

        // Check server capabilities
        if (current_inbox.get_imap_or_pop_extensions().equals("-1")) {
            data.sequence.add("CAPABILITY");
        }

        data.msg_current = msg;
        data.save_in_db = sv;
        if (save_path == null) {
            data.a_file = null;
        } else {
            data.a_file = new File(save_path);
        }

        on_ui_thread(ctx.getString(R.string.progress_downloading), msg.get_subject());

        // Refresh messages sequence
        data.sequence.add("LOGIN");
        data.sequence.add("EXAMINE");
        data.sequence.add("SAVE_MSG");
        data.sequence.add("LOGOUT");

        socket_start_imap(this);
    }

    @Override
    public void move_action(int aid, Message msg, Context ct) {
        current_inbox = db.get_account(aid);
        ctx = ct;

        // A partial reset of variables
        over = false;
        ready = false;
        excepted = false;

        // Data storage
        data = new DataIMAP();

        // Check server capabilities
        if (current_inbox.get_imap_or_pop_extensions().equals("-1")) {
            data.sequence.add("CAPABILITY");
        }

        data.msg_current = msg;

        on_ui_thread(ctx.getString(R.string.progress_deleting),
                ctx.getString(R.string.progress_deleting_msg) + " " + msg.get_subject());

        // Refresh messages sequence
        data.sequence.add("LOGIN");
        data.sequence.add("SELECT");// RW
        data.sequence.add("DELETE_MSG");
        data.sequence.add("EXPUNGE");
        data.sequence.add("LOGOUT");

        socket_start_imap(this);
    }

    @Override
    public void load_extensions() {
        String str = current_inbox.get_imap_or_pop_extensions();
        if (str != null && !str.isEmpty()) {
            String[] arr = str.toUpperCase().split("\n");
            for (String s : arr) {
                if (s.startsWith("AUTH=")) {
                    Collections.addAll(data.auths, s.substring(5).split(" "));
                } else {
                    data.general.add(s);
                }
            }
        }
    }

    @Override
    public void cancel_action() {
        over = true;
        if (multiple) continue_pager();
    }

    @Override
    public void clear_buff() {
        data.cmd_return_pars = "";
        data.sbuffer.setLength(0);
        stat = 0;
    }

    private void continue_pager() {
        over = true;
        on_ui_thread_continue_refresh();
    }

    /**
     * A que of commands towards a goal, ex. imap_refresh inbox.
     **/
    private void imap_conductor(boolean cmd_start) {
        if (over) return;
        try {
            if (data.sequence.size() < 1) return;
            switch (data.sequence.get(0)) {
                case "CAPABILITY":
                    imap_capability(cmd_start);
                    break;
                case "LOGIN":
                    imap_login(cmd_start);
                    if (!cmd_start) {
                        last_connection_data_id = current_inbox.get_id();
                        last_connection_data = io_sock.print();
                    }
                    break;
                case "LOGOUT":
                    imap_logout(cmd_start);
                    if (!cmd_start) {
                        if (io_sock != null) {
                            io_sock.closing();
                            io_sock = null;
                            io_sock_thread = null;
                        }
                        over = true;
                        ready = false;
                    }
                    break;
                case "STATUS":
                    imap_status_inbox(cmd_start);
                    break;
                case "PREP_REFRESH":
                    // Prepare an inbox refresh
                    if (cmd_start) {
                        if (current_inbox.get_to_be_refreshed()) {
                            data.sequence.add(1, "SET_UNSEEN");
                            data.sequence.add(1, "CHECK_REFRESH");
                            data.sequence.add(1, "FIND_UNSEEN");
                            data.sequence.add(1, "UIDS");
                            data.sequence.add(1, "SELECT");
                        }
                        imap_conductor(false);
                    }
                    break;
                case "EXAMINE":
                    imap_examine(cmd_start);
                    break;
                case "SELECT":
                    imap_select(cmd_start);
                    break;
                case "UIDS":
                    imap_message_uids(cmd_start);
                    break;
                case "FIND_UNSEEN":
                    imap_find_unseen(cmd_start);
                    break;
                case "CHECK_REFRESH":
                    // Refresh INBOX
                    if (cmd_start) imap_check_refresh();
                    break;
                case "SET_UNSEEN":
                    // Setting the unseen
                    if (cmd_start && data.unseen_uids != null) {
                        for (String uid_unseen : data.unseen_uids) {
                            db.seen_unseen_message(current_inbox.get_id(), uid_unseen, false);
                        }
                        imap_conductor(false);
                    }
                    break;
                case "SAVE_ATTACHMENT":
                    // Downloading an attachment
                    imap_fetch_msg_body_attachment(cmd_start);
                    break;
                case "SAVE_MSG":
                    // Download the message with embedded attachments
                    imap_save_full_msg(cmd_start);
                    break;
                case "DELETE_MSG":
                    // Moves a message on server to the deleted folder.
                    imap_delete_msg(cmd_start);
                    break;
                case "EXPUNGE":
                    // Deletes all messages in \Deleted trash mailbox folder.
                    imap_expunge(cmd_start);
                    break;
            }
            if (!cmd_start) {
                data.sequence.remove(0);
                if (data.sequence.size() > 0) {
                    imap_conductor(true);
                } else {
                    db.refresh_total_size(current_inbox.get_id());
                    current_inbox.set_total_size(db.get_total_size(current_inbox.get_id()));
                    if (!data.test_mode) reset();
                    if (!multiple && sp != null) {
                        sp.unblock = true;
                    } else if (multiple) {
                        continue_pager();
                    }
                }
            }
        } catch (Exception e) {
            if (multiple) {
                continue_pager();
            } else {
                error_dialog(e);
            }
        }
    }

    /**
     * A que of delegate (extra) commands towards a goal, i.e. multiple download.
     **/
    private void imap_delegation(boolean cmd_start) {
        if (!data.delegate) return;
        if (data.delegation.size() < 1) return;
        switch (data.delegation.get(data.cmd_indx)) {
            case "ALL_MSGS":
                // Adding ALL MESSAGES
                if (cmd_start) {
                    // Prepare the variables for the message
                    data.msg_reset();
                    data.msg_current.set_account(current_inbox.get_id());

                    // End imap_delegation if no more messages
                    if (data.msg_indx >= data.message_uids.size()) {
                        data.delegate = false;
                        // End this imap_delegation also in the sequence
                        data.sequence.remove(0);
                        imap_conductor(true);
                        return;
                    }

                    data.msg_current.set_uid(data.message_uids.get(data.msg_indx));

                    // Update spinning dialog message
                    if (sp != null) {
                        on_ui_thread("-1", (ctx.getString(R.string.progress_fetch_msg) + " "
                                + (data.msg_indx + 1) + " / " + (data.message_uids.size())));
                    }
                    ++data.msg_indx;
                }
                imap_bodystructure(cmd_start);
                break;
            case "MSG_SIZE":
                imap_message_size(cmd_start);
                break;
            case "MSG_HDR":
                imap_fetch_msg_hdr(cmd_start);
                break;
            case "MSG_STRUCTURE":
                imap_bodystructure(cmd_start);
                break;
            case "MSG_TEXT_PLAIN":
                // Get plain text of the message
                    if (data.msg_text_plain[0] == null || data.msg_text_plain[0].equals("-1")) {
                        cmd_start = false;
                    } else imap_fetch_msg_body_text(cmd_start, 1);
                break;
            case "MSG_TEXT_HTML":
                // Get html text of the message
                    if (data.msg_text_html == null || data.msg_text_html[0].equals("-1")) {
                        cmd_start = false;
                    } else imap_fetch_msg_body_text(cmd_start, 2);
                break;
            case "MSG_TEXT_OTHER":
                // Get (other type of) text of the message
                if (!data.crypto_contents) {
                    if (data.msg_text_other[0].equals("-1")) {
                        cmd_start = false;
                    } else imap_fetch_msg_body_text(cmd_start, 3);
                } else cmd_start = false;
                break;
            case "MSG_CRYPTO_MIME":
                if (data.crypto_contents) {
                    imap_fetch_msg_body_full(cmd_start, true);
                } else cmd_start = false;
                break;
            case "MSG_FULL":
                imap_fetch_msg_body_full(cmd_start, false);
                break;
            case "ADD_MSG":
                // Add the message to database
                db.add_message(data.msg_current);

                // Add the message's attachments to database
                if (data.msg_structure != null) {
                    for (int ii = 0;ii < data.msg_structure.size();++ii) {
                        Attachment att = new Attachment();
                        att.set_account(current_inbox.get_id());
                        att.set_message(data.msg_current.get_id());

                        // Mime part, i.e. - BODY[1], BODY[1.1], for attachment
                        att.set_imap_uid(data.msg_structure.get(ii)[0]);
                        att.set_mime_type(data.msg_structure.get(ii)[1]);
                        att.set_name(data.msg_structure.get(ii)[2]);
                        att.set_transfer_encoding(data.msg_structure.get(ii)[3]);
                        att.set_size(Integer.parseInt(data.msg_structure.get(ii)[4]));
                        db.add_attachment(att);
                    }
                }
                cmd_start = false;
                break;
        }
        if (!cmd_start) {
            ++data.cmd_indx;
            if (data.cmd_indx >= data.delegation.size()) data.cmd_indx = 0;
            imap_delegation(true);
        }
    }

    private void tag() {
        tag = "a" + String.valueOf(num);
        ++num;
    }

    private void imap_capability(boolean go) {
        if (go) {
            tag();
            write(tag + " CAPABILITY");
        } else {
            data.cmd_return_pars = data.cmd_return_pars.substring(12);
            current_inbox.set_imap_or_pop_extensions
                    (data.cmd_return_pars.trim().replaceAll(" ", "\n"));
            db.update_account(current_inbox);
            clear_buff();
        }
    }

    private void imap_login(boolean go) {
        if (go) {
            tag();
            load_extensions();
            if (data.auths.contains("PLAIN")) {
                data.auth = "PLAIN";
                write(tag + " LOGIN " + current_inbox.get_username()
                        + " " + current_inbox.get_pass());
            } else {
                error_dialog(ctx.getString(R.string.err_no_authentication));
                data.sequence.clear();
                imap_logout(true);
            }
        } else {
           clear_buff();
        }
    }

    private void imap_logout(boolean go) {
        if (go) {
            tag();
            write(tag + " LOGOUT");
        } else clear_buff();
    }

    private void imap_status_inbox(boolean go) {
        if (go) {
            tag();
            write(tag + " STATUS INBOX (MESSAGES UNSEEN RECENT UIDNEXT UIDVALIDITY)");
        } else {
            data.cmd_return_pars = data.cmd_return_pars.trim();

            // Update account message statistics
            int messages = -1;
            int recent = -1;
            int uidnext = -1;
            int uidvalidity = -1;
            int unseen = -1;

            String reg = ".*MESSAGES (\\d+) RECENT (\\d+) UIDNEXT (\\d+)";
            reg += " UIDVALIDITY (\\d+) UNSEEN (\\d+).*";
            pat = Pattern.compile(reg, Pattern.CASE_INSENSITIVE);
            mat = pat.matcher(data.cmd_return_pars);
            if (mat.matches()) {
                messages = Integer.parseInt(mat.group(1));
                recent = Integer.parseInt(mat.group(2));
                uidnext = Integer.parseInt(mat.group(3));
                uidvalidity  = Integer.parseInt(mat.group(4));
                unseen = Integer.parseInt(mat.group(5));
            }

            // Is the current inbox to be refreshed?
            if (db.get_messages_count(current_inbox.get_id()) != messages) {
                current_inbox.set_to_be_refreshed(true);
            } else if (uidnext != current_inbox.get_uidnext()
                    && uidvalidity != current_inbox.get_uidvalidity()) {
                current_inbox.set_to_be_refreshed(true);
            }

            if (messages != current_inbox.get_messages()
                    || recent != current_inbox.get_recent()
                    || uidnext != current_inbox.get_uidnext()
                    || uidvalidity != current_inbox.get_uidvalidity()
                    || unseen != current_inbox.get_unseen()) {
                current_inbox.set_messages(messages);
                current_inbox.set_recent(recent);
                current_inbox.set_uidnext(uidnext);
                current_inbox.set_uidvalidity(uidvalidity);
                current_inbox.set_unseen(unseen);
                db.update_account(current_inbox);
            }

            clear_buff();
        }
    }

    /**
     * A read-only way of working with an INBOX.
     **/
    private void imap_examine(boolean go) {
        if (go) {
            tag();
            write(tag + " EXAMINE INBOX");
        } else {
            String[] rows = new String[1];
            if (data.cmd_return_pars.charAt(0) == '*') {
                rows = data.cmd_return_pars.split("\\*");
            } else if (data.cmd_return_pars.charAt(0) == '+') {
                rows = data.cmd_return_pars.split("\\+");
            }

            data.examine_flags = new String[1];

            for (String row : rows) {
                row = row.trim();
                if (row.toLowerCase().startsWith("flags (")) {
                    pat = Pattern.compile("FLAGS \\((.+)\\)", Pattern.CASE_INSENSITIVE);
                    mat = pat.matcher(row);
                    if (mat.matches()) {
                        String b = mat.group(1);
                        String[] tmp_ = b.split(" ");
                        for (int i = 0;i < tmp_.length;++i) {
                            if (tmp_[i].startsWith("\\")) {
                                tmp_[i] = tmp_[i].substring(1).trim();
                            }
                        }
                        data.examine_flags = tmp_;
                    }
                }
            }
            clear_buff();
        }
    }

    /**
     * A read-write way of working with an INBOX.
     * All fetched messages SHOULD be marked \Seen
     **/
    private void imap_select(boolean go) {
        if (go) {
            tag();
            write(tag + " SELECT INBOX");
        } else {
            String[] rows = new String[1];
            if (data.cmd_return_pars.charAt(0) == '*') {
                rows = data.cmd_return_pars.split("\\*");
            } else if (data.cmd_return_pars.charAt(0) == '+') {
                rows = data.cmd_return_pars.split("\\+");
            }

            data.examine_flags = new String[1];

            for (String row : rows) {
                row = row.trim();
                if (row.toLowerCase().startsWith("flags (")) {
                    pat = Pattern.compile("FLAGS \\((.+)\\)", Pattern.CASE_INSENSITIVE);
                    mat = pat.matcher(row);
                    if (mat.matches()) {
                        String b = mat.group(1);
                        String[] tmp_ = b.split(" ");
                        for (int i = 0;i < tmp_.length;++i) {
                            if (tmp_[i].startsWith("\\")) {
                                tmp_[i] = tmp_[i].substring(1).trim();
                            }
                        }
                        data.examine_flags = tmp_;
                    }
                }
            }
            clear_buff();
        }
    }

    private void imap_message_uids(boolean go) {
        if (go) {
            tag();
            write(tag + " UID SEARCH ALL");
        } else {
            String str = data.cmd_return_pars.substring(8).trim();
            if (!str.isEmpty()) {
                data.message_uids = new ArrayList<>(Arrays.asList(str.split(" ")));
            }
            clear_buff();
        }
    }

    private void imap_find_unseen(boolean go) {
        if (go) {
            tag();
            write(tag + " UID SEARCH UNSEEN");
        } else {
            String str = data.cmd_return_pars.substring(8).trim();
            if (!str.isEmpty()) {
                data.unseen_uids = new ArrayList<>(Arrays.asList(str.split(" ")));
            }
            clear_buff();
        }
    }

    /**
     * Compare server and local messages, after STATUS INBOX.
     **/
    private void imap_check_refresh() {
        // Messages in DB
        HashMap<Integer, String> local_msgs = db.get_all_message_uids(current_inbox.get_id());
        Iterator<HashMap.Entry<Integer, String>> local_msgs_iterator;
        int local_msgs_num = local_msgs.size();

        if (current_inbox.get_messages() == 0 && local_msgs_num == 0) {
            // Update spinning dialog message
            on_ui_thread("-1", ctx.getString(R.string.progress_nothing));
            if (!multiple && sp != null) sp.unblock = true;
        } else if (current_inbox.get_messages() == 0 && local_msgs_num > 0) {
            // Update spinning dialog message
            if (sp != null) {
                on_ui_thread("-1", local_msgs_num + " " + ctx.getString(R.string.progress_deleted_msgs));
            }
            db.delete_all_messages(local_msgs);
            if (!multiple && sp != null) sp.unblock = true;
        } else if (current_inbox.get_messages() > 0 && local_msgs_num == 0) {
            // Notify the user of the new message(s)
            InboxPager.notify_update();

            // Adding all new messages
            data.delegate = true;
            data.delegation.clear();
            data.delegation.add("ALL_MSGS");
            data.delegation.add("MSG_SIZE");
            data.delegation.add("MSG_HDR");
            data.delegation.add("MSG_STRUCTURE");
            data.delegation.add("MSG_TEXT_PLAIN");
            data.delegation.add("MSG_TEXT_HTML");
            data.delegation.add("MSG_TEXT_OTHER");
            data.delegation.add("MSG_CRYPTO_MIME");
            if (current_inbox.get_auto_save_full_msgs()) {
                data.delegation.add("MSG_FULL");
            }
            data.delegation.add("ADD_MSG");
            data.msg_indx = 0;
            imap_delegation(true);
        } else {
            // Notify - new message(s)
            InboxPager.notify_update();

            // Remove (obsolete) messages from local database
            int deleted_msgs = 0;
            local_msgs_iterator = local_msgs.entrySet().iterator();
            while (local_msgs_iterator.hasNext()) {
                HashMap.Entry<Integer, String> entry = local_msgs_iterator.next();
                if (!data.message_uids.contains(entry.getValue())) {
                    db.delete_message(entry.getKey());
                    local_msgs_iterator.remove();
                    ++deleted_msgs;
                }
            }

            if (deleted_msgs > 0) {
                on_ui_thread("-1", deleted_msgs + " " + ctx.getString(R.string.progress_deleted_msgs));
            }

            // Looking for new messages, and adding
            ArrayList<String> new_list = new ArrayList<>();
            for (String new_one : data.message_uids) {
                if (!local_msgs.containsValue(new_one)) {
                    new_list.add(new_one);
                }
            }

            data.message_uids = new_list;

            if (new_list.size() > 0) {
                // Adding all new messages
                data.delegate = true;
                data.delegation.clear();
                data.delegation.add("ALL_MSGS");
                data.delegation.add("MSG_SIZE");
                data.delegation.add("MSG_HDR");
                data.delegation.add("MSG_STRUCTURE");
                data.delegation.add("MSG_TEXT_PLAIN");
                data.delegation.add("MSG_TEXT_HTML");
                data.delegation.add("MSG_TEXT_OTHER");
                data.delegation.add("MSG_CRYPTO_MIME");
                if (current_inbox.get_auto_save_full_msgs()) {
                    data.delegation.add("MSG_FULL");
                }
                data.delegation.add("ADD_MSG");
                data.msg_indx = 0;
                imap_delegation(true);
            }
        }

        // Start the imap_delegation
        if (!data.delegate) imap_conductor(false);
    }

    /**
     * Obtains the octet (8 bits) size of the message.
     **/
    private void imap_message_size(boolean go) {
        if (go) {
            tag();
            write(tag + " UID FETCH " + data.msg_current.get_uid() + " (FLAGS RFC822.SIZE)");
        } else {
            Pattern pat = Pattern.compile(".*RFC822.SIZE (\\d+)\\)", Pattern.CASE_INSENSITIVE);
            Matcher mat = pat.matcher(data.cmd_return_pars.trim());
            if (mat.matches()) {
                data.msg_current.set_size(Integer.parseInt(mat.group(1)));
            } else {
                data.msg_current.set_size(-1);
            }

            clear_buff();
        }
    }

    private void imap_fetch_msg_hdr(boolean go) {
        if (go) {
            tag();
            write(tag + " UID FETCH " + data.msg_current.get_uid() + " BODY[HEADER]");
        } else {
            data.msg_current.set_account(current_inbox.get_id());
            String received = "";
            String str_tmp;
            String[] rows = data.sbuffer.toString().split("\r\n");
            for (int i = 0;i < rows.length;++i) {
                String sto = rows[i].trim().toLowerCase();
                if (sto.startsWith("received:")) {
                    pat = Pattern.compile("Received:(.*)",
                            Pattern.CASE_INSENSITIVE);
                    mat = pat.matcher(rows[i]);
                    if (mat.matches()) received = received.concat(rows[i].trim() + "\n");
                } else if (sto.startsWith("to:")) {
                    str_tmp = rows[i].substring(3).trim();
                    if (Utils.validate_B64_QP(str_tmp)) str_tmp = Utils.split_B64_QP(str_tmp);
                    data.msg_current.set_to(str_tmp);
                } else if (sto.startsWith("subject:")) {
                    str_tmp = rows[i].substring(8).trim();
                    if (Utils.validate_B64_QP(str_tmp)) str_tmp = Utils.split_B64_QP(str_tmp);
                    data.msg_current.set_subject(str_tmp);
                } else if (sto.startsWith("message-id:")) {
                    data.msg_current.set_message_id(rows[i].substring(11).trim());
                } else if (sto.startsWith("from:")) {
                    str_tmp = rows[i].substring(5).trim();
                    if (Utils.validate_B64_QP(str_tmp)) str_tmp = Utils.split_B64_QP(str_tmp);
                    data.msg_current.set_from(str_tmp);
                } else if (sto.startsWith("cc:")) {
                    str_tmp = rows[i].substring(3).trim();
                    if (Utils.validate_B64_QP(str_tmp)) str_tmp = Utils.split_B64_QP(str_tmp);
                    data.msg_current.set_cc(str_tmp);
                } else if (sto.startsWith("bcc:")) {
                    str_tmp = rows[i].substring(4).trim();
                    if (Utils.validate_B64_QP(str_tmp)) str_tmp = Utils.split_B64_QP(str_tmp);
                    data.msg_current.set_bcc(str_tmp);
                } else if (sto.startsWith("date:")) {
                    data.msg_current.set_date(rows[i].substring(5).trim());
                } else if (sto.startsWith("content-type:")) {
                    String str = rows[i].substring(13);
                    if (str.trim().endsWith(";")) {
                        if ((i + 1) < rows.length) {
                            str += rows[i + 1];
                            if (str.trim().endsWith(";")) {
                                if ((i + 2) < rows.length) {
                                    str += rows[i + 2];
                                }
                            }
                        }
                    }
                    str = str.replaceAll("\r", "").replaceAll("\n", "");
                    data.msg_current.set_content_type(str);

                    // Checking for signed and/or encrypted i.e. PGP/MIME
                    str = data.msg_current.get_content_type().toLowerCase();
                    data.crypto_contents = str.contains("multipart/encrypted")
                            || str.contains("multipart/signed");
                }
            }
            data.msg_current.set_received(received);
            clear_buff();
        }
    }

    /**
     * Obtains and builds the structure of the message.
     * Prepares the work for text and attachments download.
     **/
    private void imap_bodystructure(boolean go) {
        if (go) {
            tag();
            write(tag + " UID FETCH " + data.msg_current.get_uid() + " BODYSTRUCTURE");
        } else {
            // Collecting the bodystructure response
            String body_structure = data.cmd_return_pars.trim();
            if (data.sbuffer.length() > 0) {
                body_structure += data.sbuffer.toString().replaceAll("\r", "").replaceAll("\n", "");
            }

            // Serialization
            data.msg_current.set_structure(body_structure);

            // Structural parsing
            data.msg_structure = Utils.imap_parse_bodystructure(data.msg_current.get_structure());

            // Preparing texts and attachments
            data.msg_structure = Utils.imap_parse_nodes(data.msg_structure, data.msg_text_plain,
                    data.msg_text_html, data.msg_text_other);

            // Declare the number of attachments for later
            if (data.msg_structure != null) data.msg_current.set_attachments(data.msg_structure.size());

            clear_buff();
        }
    }

    /**
     * Obtains textual part of the message.
     **/
    private void imap_fetch_msg_body_text(boolean go, int indx) {
        if (go) {
            tag();
            String cmd_tmp;
            String[] arr_tmp;
            switch (indx) {
                case 1:
                    cmd_tmp = "BODY[" + data.msg_text_plain[1] + "]";
                    write(tag + " UID FETCH " + data.msg_current.get_uid() + " " +  cmd_tmp);
                    break;
                case 2:
                    cmd_tmp = "BODY[" +  data.msg_text_html[1] + "]";
                    write(tag + " UID FETCH " + data.msg_current.get_uid() + " " +  cmd_tmp);
                    break;
                case 3:
                    arr_tmp = data.msg_text_other[2].split(",");
                    cmd_tmp = "(";
                    for (int k = 0;k < arr_tmp.length;++k) {
                        if ((k + 1) == arr_tmp.length) {
                            cmd_tmp = cmd_tmp.concat("BODY[" + arr_tmp[k] + "])");
                        } else {
                            cmd_tmp = cmd_tmp.concat("BODY[" + arr_tmp[k] + "] ");
                        }
                    }
                    write(tag + " UID FETCH " + data.msg_current.get_uid() + " " +  cmd_tmp);
                    break;
            }
        } else {
            // Removing trailing ')' IMAP closing bracket
            if (data.sbuffer.length() > 0) {
                if (data.sbuffer.charAt(data.sbuffer.length() - 1) == ')') {
                    data.sbuffer.deleteCharAt(data.sbuffer.length() - 1);
                } else if (data.sbuffer.charAt(data.sbuffer.length() - 2) == ')') {
                    data.sbuffer.deleteCharAt(data.sbuffer.length() - 2);
                } else if (data.sbuffer.charAt(data.sbuffer.length() - 3) == ')') {
                    data.sbuffer.deleteCharAt(data.sbuffer.length() - 3);
                }
            }

            switch (indx) {
                case 1:
                    String str_plain = data.sbuffer.toString();
                    if (!data.msg_text_plain[2].equals("-1")) {
                        data.msg_current.set_charset_plain(data.msg_text_plain[2]);
                    }

                    // Convert to text from BASE64 or QUOTED-PRINTABLE
                    if (data.msg_text_plain[3].equalsIgnoreCase("BASE64")) {
                        str_plain = Utils.parse_BASE64_encoding(str_plain,
                                data.msg_current.get_charset_plain());
                        data.msg_current.set_content_transfer_encoding("BASE64");
                    } else if (data.msg_text_plain[3].equalsIgnoreCase("QUOTED-PRINTABLE")) {
                        str_plain = Utils.parse_quoted_printable(str_plain, data.msg_current.get_charset_plain());
                        data.msg_current.set_content_transfer_encoding("QUOTED-PRINTABLE");
                    }
                    data.msg_current.set_contents_plain(str_plain);
                    data.msg_text_plain = null;
                    break;
                case 2:
                    String str_html = data.sbuffer.toString();
                    if (!data.msg_text_html[2].equals("-1")) {
                        data.msg_current.set_charset_html(data.msg_text_html[2]);
                    }

                    // Convert to text from BASE64 or QUOTED-PRINTABLE
                    if (data.msg_text_html[3].equalsIgnoreCase("BASE64")) {
                        str_html = Utils.parse_BASE64_encoding(str_html,
                                data.msg_current.get_charset_html());
                        data.msg_current.set_content_transfer_encoding("BASE64");
                    } else if (data.msg_text_html[3].equalsIgnoreCase("QUOTED-PRINTABLE")) {
                        str_html = Utils.parse_quoted_printable(str_html, data.msg_current.get_charset_html());
                        data.msg_current.set_content_transfer_encoding("QUOTED-PRINTABLE");
                    }
                    data.msg_current.set_contents_html(str_html);
                    data.msg_text_html = null;
                    break;
                case 3:
                    if (data.sbuffer.length() > 0) {
                        data.msg_current.set_contents_other(data.sbuffer.toString());
                    }
                    data.msg_text_other = null;
                    break;
                default:
                    break;
            }

            clear_buff();
        }
    }

    /**
     * Obtains the full message.
     **/
    private void imap_fetch_msg_body_full(boolean go, boolean crypto) {
        if (go) {
            tag();
            write(tag + " UID FETCH " + data.msg_current.get_uid() + " BODY[]");
        } else {
            // Removing trailing ')'
            for (int i = data.sbuffer.length() - 1;i >= 0 ;i--) {
                if (data.sbuffer.charAt(i) == ')') {
                    data.sbuffer.deleteCharAt(i);
                    break;
                } else {
                    data.sbuffer.deleteCharAt(i);
                }
            }

            if (crypto) {
                // Boundary search
                String bounds = data.msg_current.get_content_type()
                        .replaceAll("\r", "").replaceAll("\n", "");
                int nn = bounds.toLowerCase().indexOf("boundary=");
                if (nn != -1) {
                    bounds = bounds.substring(nn + 9);
                    int n_semi = 0;
                    for (int k = 0;k < bounds.length();++k) {
                        if (bounds.charAt(k) == ';') {
                            n_semi = k;
                            break;
                        }
                    }
                    bounds = bounds.substring(0, n_semi).replaceAll("\"", "");
                    int cut_off = data.sbuffer.indexOf("--" + bounds);
                    if (cut_off > 0) {
                        // Reduction to MIME
                        data.sbuffer = data.sbuffer.delete(0, cut_off);
                        data.msg_current.set_contents_crypto(data.sbuffer.toString().trim());
                    } else {
                        // On error - save full message
                        data.msg_current.set_full_msg(data.sbuffer.toString());
                    }
                } else {
                    // On error - save full message
                    data.msg_current.set_full_msg(data.sbuffer.toString());
                }
            } else {
                // Loading full message
                data.msg_current.set_full_msg(data.sbuffer.toString());
                Dialogs.toaster(true, ctx.getString(R.string.message_action_done),
                        (AppCompatActivity) ctx);
            }

            clear_buff();
        }
    }

    private void imap_fetch_msg_body_attachment(boolean go) {
        if (go) {
            // Prepare for direct file write
            try {
                data.fstream = new FileOutputStream(data.a_file);
            } catch (FileNotFoundException fnf) {
                error_dialog(fnf);
            }

            // Set the message
            data.msg_current = db.get_message(data.att_item.get_message());

            tag();
            write(tag + " UID FETCH " + data.msg_current.get_uid()
                    + " BODY[" + String.valueOf(data.att_item.get_imap_uid()) + "]");
        } else {
            // Removing trailing ')'
            for (int i = data.sbuffer.length() - 1;i >= 0 ;i--) {
                if (data.sbuffer.charAt(i) == ')') {
                    data.sbuffer.deleteCharAt(i);
                    break;
                } else {
                    data.sbuffer.deleteCharAt(i);
                }
            }

            try {
                // Converting transfer encoding
                if (data.att_item != null) {
                    // Parsing file download
                    if (data.att_item.get_transfer_encoding().equalsIgnoreCase("BASE64")) {
                        boolean CR = false;
                        StringBuilder sb_tmp = new StringBuilder(0);
                        for (int i = 0;i < data.sbuffer.length();++i) {
                            if (data.sbuffer.charAt(i) == '\n') {
                                if (CR) {
                                    data.fstream.write(Base64.decode(sb_tmp.toString().getBytes(),
                                            Base64.DEFAULT));
                                    sb_tmp.setLength(0);
                                    CR = false;
                                }
                            } else if (data.sbuffer.charAt(i) == '\r') {
                                CR = true;
                            } else if (data.sbuffer.charAt(i) != '=') {
                                sb_tmp.append(data.sbuffer.charAt(i));
                                CR = false;
                            }
                        }
                        if (sb_tmp.length() > 0) {
                            data.fstream.write(Base64.decode(sb_tmp.toString().getBytes(),
                                    Base64.DEFAULT));
                        }
                        if (data.fstream != null) data.fstream.close();
                    } else {
                        data.fstream.write(data.sbuffer.toString().getBytes());
                        if (data.fstream != null) data.fstream.close();
                    }
                }
                if (sp != null) {
                    on_ui_thread("-1", ctx.getString(R.string.progress_download_complete));
                    sp.unblock = true;
                    Dialogs.toaster(true, ctx.getString(R.string.message_action_done),
                            (AppCompatActivity) ctx);
                }
            } catch (IOException e) {
                InboxPager.log += e.getMessage() + "\n\n";
                error_dialog(e);
                if (sp != null) {
                    on_ui_thread("-1", ctx.getString(R.string.err_not_saved));
                    sp.unblock = true;
                }
            }

            clear_buff();
        }
    }

    private void imap_save_full_msg(boolean go) {
        if (go) {
            // Prepare for direct file write
            if (data.a_file == null) {
                data.fstream = null;
            } else {
                try {
                    data.fstream = new FileOutputStream(data.a_file);
                } catch (FileNotFoundException fnf) {
                    error_dialog(fnf);
                }
            }

            // Also saving to DB
            if (data.save_in_db) data.sbuffer.setLength(0);

            tag();
            write(tag + " UID FETCH " + data.msg_current.get_uid() + " BODY[]");
        } else {
            // Removing trailing ')'
            for (int i = data.sbuffer.length() - 1;i >= 0 ;i--) {
                if (data.sbuffer.charAt(i) == ')') {
                    data.sbuffer.deleteCharAt(i);
                    break;
                } else {
                    data.sbuffer.deleteCharAt(i);
                }
            }

            if (data.fstream != null) {
                try {
                    data.fstream.write(data.sbuffer.toString().getBytes());
                    data.fstream.close();
                } catch (IOException ioe) {
                    InboxPager.log += ioe.getMessage() + "\n\n";
                    error_dialog(ioe);
                    if (sp != null) {
                        on_ui_thread("-1", ctx.getString(R.string.err_not_saved));
                        sp.unblock = true;
                    }
                }
            }

            if (sp != null) {
                on_ui_thread("-1", ctx.getString(R.string.progress_download_complete));
                sp.unblock = true;
                Dialogs.toaster(true, ctx.getString(R.string.message_action_done),
                        (AppCompatActivity) ctx);
            }

            // Save to DB
            if (data.save_in_db || data.fstream == null) {
                data.msg_current.set_full_msg(data.sbuffer.toString());
                db.update_message(data.msg_current);
            }

            clear_buff();
        }
    }

    /**
     * Moves a message to the \Deleted mailbox folder.
     **/
    private void imap_delete_msg(boolean go) {
        if (go) {
            tag();
            write(tag + " UID STORE " + data.msg_current.get_uid() + " +FLAGS (\\Deleted)");
        } else {
            db.delete_message(data.msg_current.get_id());
            clear_buff();
        }
    }

    /**
     * Deletes ALL messages in \Deleted mailbox folder.
     **/
    private void imap_expunge(boolean go) {
        if (go) {
            tag();
            write(tag + " EXPUNGE");
        } else {
            on_ui_thread("-1", ctx.getString(R.string.progress_deleted_msg));
            ((InboxMessage) ctx).delete_message_ui();
            clear_buff();
        }
    }
}
