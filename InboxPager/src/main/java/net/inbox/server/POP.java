/*
 * InboxPager, an android email client.
 * Copyright (C) 2016-2026  ITPROJECTS
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import android.util.Base64;

import net.inbox.InboxMessage;
import net.inbox.InboxPager;
import net.inbox.db.Attachment;
import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.pager.R;
import net.inbox.Common;
import net.inbox.visuals.Dialogs;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

public class POP extends NetworkThread {

    private static class DataPOP extends Data {
        // Command sequence index
        int cmd_index = 0;

        // Command que, the numbers correspond with methods
        ArrayList<String> sequence = new ArrayList<>();

        // Used with UIDL
        ArrayList<String> message_nums = new ArrayList<>();
        ArrayList<Integer> message_size = new ArrayList<>();
        ArrayList<String> message_uids = new ArrayList<>();

        // Used with delegation
        int msg_index = 0;

        // Line count
        int line_count = 0;

        // Size of the header in chars
        int hdr = 0;

        // Message attachments, (uid, mime-type, name, transfer-encoding, size)
        ArrayList<String[]> msg_structure = new ArrayList<>();

        // Message texts
        ArrayList<String[]>  msg_texts = new ArrayList<>();

        // Used in saving attachments and text
        Attachment att_item;
        OutputStream os;

        /**
         * Resets all message data, for a new instance.
         **/
        void msg_reset() {
            hdr = 0;
            msg_current = new Message();
            msg_structure = new ArrayList<>();
            msg_texts = new ArrayList<>();
        }
    }

    private DataPOP data;

    public POP(AppCompatActivity at) {
        super(at);
    }

    @Override
    public void reset() {
        over = false;
        ready = false;
        tag = "";
        stat = 0;
        io_sock = null;
        data = null;
        current_inbox = null;
    }

    @Override
    public void reply(String l) {
        if(data == null) return;
        if (l.equals(".")) {
            // Multi-line ending
            if (data.delegate) {
                if (tag.equals("RETR")) {
                    // Byte count
                    int bytes = 0;
                    int sz = data.sbuffer.length();
                    for (int i = 0;i < sz;i += 1000) {
                        if ((i + 1000) >= sz) {
                            bytes += data.sbuffer.substring(i).getBytes().length;
                            continue;
                        }
                        bytes += data.sbuffer.substring(i, i + 1000).getBytes().length;
                    }
                    if ((bytes + data.line_count) >= data.msg_current.get_size()) {
                        pop_delegation(false);
                    } else {
                        data.sbuffer.append(l);
                        data.sbuffer.append("\n");
                    }
                } else {
                    pop_delegation(false);
                }
            } else {
                pop_conductor(false);
            }
        } else if (l.startsWith("+") && tag.matches("AUTH (LOGIN|PLAIN).*")) {
            // A server challenge
            data.cmd_return = l;
            pop_conductor(false);
        } else if (l.startsWith("+OK")) {
            // +OK server response
            data.cmd_return = l;
            stat = 1;
            if (ready) {
                // Checking for a multi-line command
                if (!data.sequence.isEmpty()
                    && !data.sequence.get(0).matches("CAPA|LIST|UIDL")
                    && !tag.matches("RETR|TOP")
                ) {
                    if (data.delegate) {
                        pop_delegation(false);
                    } else {
                        pop_conductor(false);
                    }
                }
            } else {
                ready = true;
                pop_conductor(true);
            }
        } else if (l.startsWith("-ERR")) {
            // -ERR server response
            data.cmd_return = l;
            stat = 2;
            if (!data.sequence.isEmpty()
                && data.sequence.get(0).equalsIgnoreCase("CAPA")
            ) {
                data.sbuffer.setLength(0);
                data.sbuffer.append(act.get().getString(R.string.err_no_capa));
                pop_conductor(false);
            } else {
                cancel_action();
            }
        } else {
            // Message data
            data.sbuffer.append(l);
            data.sbuffer.append('\n');
            ++data.line_count;
        }
    }

    @Override
    public void test_server(Inbox inn, AppCompatActivity at) {
        current_inbox = inn;
        act = new WeakReference<>(at);

        // A partial reset of variables
        over = false;
        ready = false;
        excepted = false;

        // Data storage
        data = new DataPOP();

        // Testing
        data.test_mode = true;

        // Check server capabilities
        data.sequence.add("CAPA");
        data.sequence.add("QUIT");

        socket_start_pop(this);

        try {
            sleep(1000);
        } catch (InterruptedException e) {
            InboxPager.log = InboxPager.log.concat(
                act.get().getString(R.string.ex_field) + e.getMessage() + "\n\n"
            );
        }

        if (!excepted) {
            // Prepare a callback for results
            new Thread(
                () -> {
                    try {
                        sleep(3000);
                    } catch (InterruptedException e) {
                        InboxPager.log = InboxPager.log.concat(
                            act.get().getString(R.string.ex_field) + e.getMessage() + "\n\n"
                        );
                    }

                    if (current_inbox.get_imap_or_pop_extensions() != null
                        && !current_inbox.get_imap_or_pop_extensions().isEmpty()
                    ) {
                        String tested;
                        if (current_inbox.get_imap_or_pop_extensions()
                            .equals(act.get().getString(R.string.err_no_capa))
                        ) {
                            tested = act.get().getString(R.string.err_no_capa);
                        } else {
                            // Preparing the dialog message
                            data.auths = new ArrayList<>();
                            data.general = new ArrayList<>();
                            String[] parts = current_inbox.get_imap_or_pop_extensions().split("\n");
                            for (int g = 0;g < parts.length;++g) {
                                if (parts[g] != null) {
                                    parts[g] = parts[g].toLowerCase().trim();
                                    if (parts[g].contains("sasl")) {
                                        String[] au = parts[g].substring(5).split(" ");
                                        Collections.addAll(data.auths, au);
                                        continue;
                                    }
                                    data.general.add(parts[g]);
                                }
                            }
                            tested = act.get().getString(R.string.edit_account_check_login_types) + "\n\n";
                            for (int i = 0;i < data.auths.size();++i) {
                                if (i == (data.auths.size() - 1)) {
                                    tested += data.auths.get(i).toUpperCase();
                                } else {
                                    tested = tested.concat(data.auths.get(i).toUpperCase() + ", ");
                                }
                            }
                            tested += "\n\n" + act.get().getString(R.string.edit_account_check_other) + "\n\n";
                            for (int i = 0;i < data.general.size();++i) {
                                if (i == (data.general.size() - 1)) {
                                    tested += data.general.get(i).toUpperCase();
                                } else {
                                    tested = tested.concat(data.general.get(i).toUpperCase() + ", ");
                                }
                            }
                        }
                        Dialogs.dialog_simple(
                            act.get().getString(R.string.edit_account_check_incoming),
                            tested,
                            act.get()
                        );
                    } else {
                        Dialogs.dialog_simple(
                            act.get().getString(R.string.edit_account_check_incoming),
                            act.get().getString(R.string.edit_account_check_fail),
                            act.get()
                        );
                    }
                    reset();
                }
            ).start();
        }
    }

    @Override
    public void default_action(boolean multi, Inbox inn, AppCompatActivity at) {
        multiple = multi;
        current_inbox = inn;
        act = new WeakReference<>(at);

        // A partial reset of variables
        over = false;
        ready = false;
        excepted = false;

        // Data storage
        data = new DataPOP();

        // Check server capabilities
        if (current_inbox.get_imap_or_pop_extensions().equals("-1")) {
            data.sequence.add("CAPA");
        }

        if (multiple) {
            on_ui_thread(
                current_inbox.get_email(),
                act.get().getString(R.string.progress_refreshing)
            );
        } else {
            on_ui_thread(
                act.get().getString(R.string.progress_title),
                act.get().getString(R.string.progress_refreshing)
            );
        }

        // Refresh messages sequence
        data.sequence.add("AUTH");
        data.sequence.add("STAT");
        data.sequence.add("PREP_REFRESH");
        data.sequence.add("QUIT");

        socket_start_pop(this);
    }

    @Override
    public void attachment_action(int aid, Attachment att, Object doc_file, AppCompatActivity at) {
        current_inbox = db.get_account(aid);
        act = new WeakReference<>(at);

        // A partial reset of variables
        over = false;
        ready = false;
        excepted = false;

        // Data storage
        data = new DataPOP();

        // Check server capabilities
        if (current_inbox.get_imap_or_pop_extensions().equals("-1")) {
            data.sequence.add("CAPA");
        }

        data.att_item = att;
        data.msg_current = db.get_message(att.get_message());
        if (doc_file == null) {
            data.a_file = null;
        } else {
            Common.check_write_give(at, ((DocumentFile) doc_file).getUri());
            data.a_file = ((DocumentFile) doc_file).createFile(
                "message/rfc822", data.att_item.get_name()
            );
        }

        on_ui_thread(act.get().getString(R.string.progress_downloading), data.att_item.get_name());

        // Refresh messages sequence
        data.sequence.add("AUTH");
        data.sequence.add("STAT");
        data.sequence.add("UIDL");
        data.sequence.add("DOWNLOAD_FULL_MESSAGE");
        data.sequence.add("SAVE_ATTACHMENT");
        data.sequence.add("QUIT");

        socket_start_pop(this);
    }

    @Override
    public void msg_action(int aid, Message msg, Object doc_file, boolean sv, AppCompatActivity at) {
        current_inbox = db.get_account(aid);
        act = new WeakReference<>(at);

        // A partial reset of variables
        over = false;
        ready = false;
        excepted = false;

        // Data storage
        data = new DataPOP();

        // Check server capabilities
        if (current_inbox.get_imap_or_pop_extensions().equals("-1")) {
            data.sequence.add("CAPA");
        }

        data.msg_current = msg;
        data.save_in_db = sv;
        if (doc_file == null) {
            data.a_file = null;
        } else {
            Common.check_write_give(act.get(), ((DocumentFile) doc_file).getUri());
            String attachment_file_name;
            if (data.msg_current.get_subject() == null || data.msg_current.get_subject().isEmpty())
                attachment_file_name = act.get().getString(R.string.progress_unknown_eml);
            else
                attachment_file_name = data.msg_current.get_subject() + ".eml";
            data.a_file = ((DocumentFile) doc_file).createFile(
                "application/octet-stream", attachment_file_name
            );
        }

        on_ui_thread(act.get().getString(R.string.progress_downloading), msg.get_subject());

        // Refresh messages sequence
        data.sequence.add("AUTH");
        data.sequence.add("STAT");
        data.sequence.add("UIDL");
        data.sequence.add("DOWNLOAD_FULL_MESSAGE");
        data.sequence.add("SAVE_MSG");
        data.sequence.add("QUIT");

        socket_start_pop(this);
    }

    @Override
    public void move_action(int aid, Message msg, AppCompatActivity at) {
        current_inbox = db.get_account(aid);
        act = new WeakReference<>(at);

        // A partial reset of variables
        over = false;
        ready = false;
        excepted = false;

        // Data storage
        data = new DataPOP();
        data.msg_current = msg;

        on_ui_thread(
            act.get().getString(R.string.progress_deleting),
            act.get().getString(R.string.progress_deleting_msg) + " " + msg.get_subject()
        );

        // Refresh messages sequence
        data.sequence.add("AUTH");
        data.sequence.add("UIDL");
        data.sequence.add("DELETE_MSG");
        data.sequence.add("QUIT");

        socket_start_pop(this);
    }

    @Override
    public void load_extensions() {
        String str = current_inbox.get_imap_or_pop_extensions();
        if (str != null && !str.isEmpty()) {
            String[] arr = str.toUpperCase().split("\n");
            for (String s : arr) {
                if (s.startsWith("SASL ")) {
                    Collections.addAll(data.auths, s.substring(5).split(" "));
                } else {
                    data.general.add(s);
                }
            }
        }
    }

    @Override
    public void cancel_action() {
        try {
            if (io_sock != null) write("RSET");
            if (io_sock != null) write("QUIT");
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
        }
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
     * A que of commands towards a goal, ex. pop_refresh inbox.
     **/
    private void pop_conductor(boolean cmd_start) {
        if (over) return;
        try {
            if (data.sequence.isEmpty()) return;
            switch (data.sequence.get(0)) {
                case "CAPA":
                    pop_capability(cmd_start);
                    break;
                case "AUTH":
                    if (!current_inbox.get_imap_or_pop_extensions().contains("UIDL")
                        || !current_inbox.get_imap_or_pop_extensions().contains("SASL")
                    ) {
                        Dialogs.dialog_simple(
                            null,
                            act.get().getString(R.string.err_no_extension),
                            act.get()
                        );
                        data.sequence.clear();
                        data.sequence.add("QUIT");
                        pop_logout(cmd_start);
                    }
                    switch (tag) {
                        case "AUTH LOGIN":
                        case "AUTH LOGIN password":
                            pop_login(cmd_start);
                            return;
                        default:
                            pop_login(cmd_start);
                            break;
                    }
                    if (!cmd_start) {
                        last_connection_data_id = current_inbox.get_id();
                        last_connection_data = io_sock.print();
                    }
                    break;
                case "QUIT":
                    pop_logout(cmd_start);
                    break;
                case "STAT":
                    pop_stat(cmd_start);
                    break;
                case "PREP_REFRESH":
                    // Prepare an inbox refresh
                    if (cmd_start) {
                        if (current_inbox.get_to_be_refreshed()) {
                            data.sequence.add(1, "CHECK_REFRESH");
                            data.sequence.add(1, "UIDL");
                            data.sequence.add(1, "LIST");
                        }
                        pop_conductor(false);
                    }
                    break;
                case "LIST":
                    pop_list(cmd_start);
                    break;
                case "UIDL":
                    pop_uidl(cmd_start);
                    break;
                case "CHECK_REFRESH":
                    // Refresh INBOX
                    if (cmd_start) pop_check_refresh();
                    break;
                case "DOWNLOAD_FULL_MESSAGE":
                    if (cmd_start) {
                        // Picking out a particular message
                        for (int i = data.message_uids.size() - 1;i >= 0;i--) {
                            if (!data.message_uids.get(i).equals(data.msg_current.get_uid())) {
                                data.message_nums.remove(i);
                                data.message_uids.remove(i);
                            }
                        }
                        if (data.message_uids.isEmpty()) {
                            // Message not present
                            on_ui_thread("-1", act.get().getString(R.string.progress_not_found));
                            InboxPager.log = InboxPager.log.concat(
                                act.get().getString(R.string.progress_not_found) + "\n\n"
                            );
                            cmd_start = false;
                        } else {
                            pop_retr_full_msg(true);
                        }
                    } else {
                        pop_retr_full_msg(false);
                    }
                    break;
                case "SAVE_ATTACHMENT":
                    // Saving an attachment
                    cmd_start = false;
                    pop_save_attachment();
                    break;
                case "SAVE_MSG":
                    // Saving the message
                    cmd_start = false;
                    pop_save_full_msg();
                    break;
                case "DELETE_MSG":
                    if (cmd_start) {
                        // Picking out a particular message
                        for (int i = data.message_uids.size() - 1;i >= 0;i--) {
                            if (!data.message_uids.get(i).equals(data.msg_current.get_uid())) {
                                data.message_nums.remove(i);
                                data.message_uids.remove(i);
                            }
                        }
                        if (data.message_uids.isEmpty()) {
                            // Message not present
                            on_ui_thread("-1", act.get().getString(R.string.progress_not_found));
                            InboxPager.log = InboxPager.log.concat(
                                act.get().getString(R.string.progress_not_found) + "\n\n"
                            );
                            cmd_start = false;
                        } else {
                            pop_delete_msg(true);
                        }
                    } else {
                        pop_delete_msg(false);
                    }
                    break;
            }
            if (!cmd_start) {
                data.sequence.remove(0);
                if (!data.sequence.isEmpty()) {
                    pop_conductor(true);
                } else {
                    if (!data.test_mode) {
                        db.update_account_unseen_count(current_inbox.get_id());
                        reset();
                    }
                    if (!multiple && sp != null) {
                        sp.do_after();
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
    private void pop_delegation(boolean cmd_start) {
        if (!data.delegate) return;
        if (data.delegation.isEmpty()) return;
        switch (data.delegation.get(data.cmd_index)) {
            case "ALL_MSGS":
                // Adding ALL MESSAGES
                if (cmd_start) {
                    // Prepare the variables for the message
                    data.msg_reset();
                    data.msg_current.set_account(current_inbox.get_id());

                    // End pop_delegation if no more messages
                    if (data.msg_index >= data.message_uids.size()) {
                        data.delegate = false;
                        // End this pop_delegation also in the sequence
                        data.sequence.remove(0);
                        pop_conductor(true);
                        return;
                    }

                    data.msg_current.set_uid(data.message_uids.get(data.msg_index));

                    // Update spinning dialog message
                    if (sp != null) {
                        on_ui_thread(
                            "-1",
                            act.get().getString(R.string.progress_fetch_msg) + " "
                                + (data.msg_index + 1) + " / " + (data.message_uids.size())
                        );
                    }
                    ++data.msg_index;
                    cmd_start = false;
                }
                break;
            case "TOP_HDR":
                pop_msg_top(cmd_start);
                break;
            case "RETR_MSG":
                pop_retr_msg(cmd_start);
                break;
            case "ADD_MSG":
                // Keep or free, full message
                if (!current_inbox.get_auto_save_full_msgs()) data.msg_current.set_full_msg(null);

                // Add the message to database
                data.msg_current.set_seen(false);
                db.add_message(data.msg_current);

                // Add the message's attachments to database
                if (data.msg_structure != null) {
                    for (int ii = 0;ii < data.msg_structure.size();++ii) {
                        Attachment att = new Attachment();
                        att.set_account(current_inbox.get_id());
                        att.set_message(data.msg_current.get_id());

                        // Mime part, i.e. - BODY[1], BODY[1.1], for attachment
                        att.set_pop_indx(data.msg_structure.get(ii)[0]);
                        att.set_mime_type(data.msg_structure.get(ii)[1]);
                        att.set_boundary(data.msg_structure.get(ii)[2]);
                        att.set_name(data.msg_structure.get(ii)[3]);
                        att.set_transfer_encoding(data.msg_structure.get(ii)[4]);
                        if (!data.msg_structure.get(ii)[6].isEmpty()) {
                            att.set_size(Integer.parseInt(data.msg_structure.get(ii)[6]));
                        } else {
                            att.set_size(-1);
                        }
                        db.add_attachment(att);
                    }
                }

                cmd_start = false;
                break;
        }
        if (!cmd_start) {
            ++data.cmd_index;
            if (data.cmd_index >= data.delegation.size()) data.cmd_index = 0;
            pop_delegation(true);
        }
    }

    private void pop_capability(boolean go) {
        if (go) {
            tag = "CAPA";
            write(tag);
        } else {
            if (current_inbox.get_imap_or_pop_extensions().length() < 4) {
                String[] ext = data.sbuffer.toString().split("\n");
                for (String s : ext) {
                    if (s.toUpperCase().startsWith("EXPIRE")) {
                        String st = s.substring(7).trim().replace("\r", "");
                        if (!st.equals("NEVER")) {
                            if (st.equals("0")) {
                                Dialogs.dialog_simple(
                                    null,
                                    act.get().getString(R.string.err_pop_immediate),
                                    act.get()
                                );
                            } else {
                                Dialogs.dialog_simple(
                                    null,
                                    String.format(act.get().getString(R.string.err_pop_expiration), st),
                                    act.get()
                                );
                            }
                        }
                    }
                }
            }
            current_inbox.set_imap_or_pop_extensions(
                data.sbuffer.deleteCharAt(data.sbuffer.length() - 1).toString().toUpperCase()
            );
            db.update_account(current_inbox);
        }
    }

    private void pop_login(boolean go) {
        if (go) {
            load_extensions();
            if (data.auths.contains("LOGIN")) {
                tag = "AUTH LOGIN";
                data.auth = "AUTH LOGIN";
                write(tag);
            } else if (data.auths.contains("PLAIN")) {
                tag = "AUTH PLAIN";
                data.auth = "AUTH PLAIN";
                write(tag);
            } else {
                error_dialog(act.get().getString(R.string.err_no_authentication));
                data.sequence.clear();
                pop_logout(true);
            }
        } else {
            if (tag.startsWith("AUTH LOGIN")) {
                // LOGIN type of authentication
                String str = new String(
                    Base64.decode(
                        data.cmd_return.substring(2).trim().getBytes(), Base64.DEFAULT
                    )
                ).toUpperCase();
                if (str.startsWith("USERNAME")) {
                    if (str.length() > 500) {
                        write_limited(
                            Base64.encodeToString(str.getBytes(), Base64.DEFAULT).trim().toCharArray()
                        );
                    } else {
                        write(Base64.encodeToString(
                            current_inbox.get_username().getBytes(), Base64.DEFAULT).trim()
                        );
                    }
                    tag = "AUTH LOGIN password";
                } else if (str.startsWith("PASSWORD")) {
                    tag = "";
                    str = current_inbox.get_pass();
                    if (str.isEmpty()) {
                        write("=");
                    } else {
                        str = Base64.encodeToString(str.getBytes(), Base64.DEFAULT).trim();
                        if (str.length() > 500) {
                            write_limited(str.toCharArray());
                        } else {
                            write(str);
                        }
                    }
                    clear_buff();
                }
            } else if (data.auth.equals("AUTH PLAIN")) {
                // PLAIN type of authentication
                String str = Base64.encodeToString(
                    ("\0"+ current_inbox.get_username() + "\0" + current_inbox.get_pass()).getBytes(),
                        Base64.DEFAULT
                ).trim();
                if (str.length() > 500) {
                    write_limited(str.toCharArray());
                } else {
                    write(str);
                }
                clear_buff();
            }
        }
    }

    private void pop_logout(boolean go) {
        if (go) {
            tag = "QUIT";
            write(tag);
        } else {
            clear_buff();
            over = true;
        }
    }

    /**
     * Obtain count and total size of messages on server.
     **/
    private void pop_stat(boolean go) {
        if (go) {
            tag = "STAT";
            write(tag);
        } else {
            // Update account message statistics
            int messages = -1;
            int total_size = -1;

            pat = Pattern.compile("\\+OK (\\d+) (\\d+).*", Pattern.CASE_INSENSITIVE);
            mat = pat.matcher(data.cmd_return);
            if (mat.matches()) {
                messages = Integer.parseInt(mat.group(1));
                total_size = Integer.parseInt(mat.group(2));
            }

            // Is the current inbox to be refreshed?
            if (messages == db.get_messages_count(current_inbox.get_id())
                && total_size == current_inbox.get_total_size()) {
                current_inbox.set_to_be_refreshed(false);
            } else {
                current_inbox.set_to_be_refreshed(true);
                current_inbox.set_messages(messages);
                current_inbox.set_total_size(total_size);
                db.update_account(current_inbox);
            }

            clear_buff();
        }
    }

    /**
     * Lists the messages by sequence number and size.
     **/
    private void pop_list(boolean go) {
        if (go) {
            tag = "LIST";
            write(tag);
        } else {
            if (data.sbuffer.length() > 0) {
                String[] s_tmp;
                for (String str : data.sbuffer.toString().split("\n")) {
                    s_tmp = str.split(" ");
                    if (s_tmp.length >= 2) data.message_size.add(Integer.parseInt(s_tmp[1]));
                }
            }
            clear_buff();
        }
    }

    /**
     * Lists the messages by sequence number and UID.
     **/
    private void pop_uidl(boolean go) {
        if (go) {
            tag = "UIDL";
            write(tag);
        } else {
            if (data.sbuffer.length() > 0) {
                String[] s_tmp;
                for (String str : data.sbuffer.toString().split("\n")) {
                    s_tmp = str.split(" ");
                    if (s_tmp.length >= 2) {
                        data.message_nums.add(s_tmp[0]);
                        data.message_uids.add(s_tmp[1]);
                    }
                }
            }
            clear_buff();
        }
    }

    /**
     * Compare server and local messages.
     **/
    private void pop_check_refresh() {
        // Messages in DB
        HashMap<Integer, String> local_msgs = db.get_all_message_uids(current_inbox.get_id());
        Iterator<HashMap.Entry<Integer, String>> local_msgs_iterator;
        int local_msgs_num = local_msgs.size();

        if (current_inbox.get_messages() == 0 && local_msgs_num == 0) {
            // Update spinning dialog message
            on_ui_thread("-1", act.get().getString(R.string.progress_nothing));
            if (!multiple && sp != null) sp.do_after();
        } else if (current_inbox.get_messages() == 0 && local_msgs_num > 0) {
            // Update spinning dialog message
            if (sp != null) {
                on_ui_thread(
                    "-1",
                    local_msgs_num + " " + act.get().getString(R.string.progress_deleted_msgs)
                );
            }
            db.delete_all_messages(local_msgs);
            if (!multiple && sp != null) sp.do_after();
        } else if (current_inbox.get_messages() > 0 && local_msgs_num == 0) {
            // Notify the user of the new message(s)
            Common.notify_update(act.get().getString(R.string.notification_new_message), act.get());

            // Adding all new messages
            data.delegate = true;
            data.delegation.clear();
            data.delegation.add("ALL_MSGS");
            data.delegation.add("TOP_HDR");
            data.delegation.add("RETR_MSG");
            data.delegation.add("ADD_MSG");
            data.msg_index = 0;
            pop_delegation(true);
        } else {
            // Notify - new message(s)
            Common.notify_update(act.get().getString(R.string.notification_new_message), act.get());

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
                on_ui_thread(
                    "-1",
                    deleted_msgs + " " + act.get().getString(R.string.progress_deleted_msgs)
                );
            }

            // Looking for new messages, and adding
            for (int i = data.message_uids.size() - 1;i >= 0;--i) {
                if (local_msgs.containsValue(data.message_uids.get(i))) {
                    data.message_nums.remove(data.message_nums.get(i));
                    data.message_size.remove(data.message_size.get(i));
                    data.message_uids.remove(data.message_uids.get(i));
                }
            }

            if (!data.message_uids.isEmpty()) {
                // Adding all new messages
                data.delegate = true;
                data.delegation.clear();
                data.delegation.add("ALL_MSGS");
                data.delegation.add("TOP_HDR");
                data.delegation.add("RETR_MSG");
                data.delegation.add("ADD_MSG");
                data.msg_index = 0;
                pop_delegation(true);
            }
        }

        // Start the imap_delegation
        if (!data.delegate) pop_conductor(false);
    }

    /**
     * TOP command obtains all message headers.
     **/
    private void pop_msg_top(boolean go) {
        if (go) {
            tag = "TOP";
            write("TOP " + data.message_nums.get(data.msg_index - 1) + " 0");
        } else {
            data.hdr = data.sbuffer.length() - 1;

            // Processing minimum headers required are From: and Date:
            HashMap<String, String> heads = Utils.parse_headers(data.sbuffer.toString());

            if (heads.get("received:") != null) data.msg_current.set_received(heads.get("received:"));

            if (heads.get("to:") != null) {
                String str_tmp = heads.get("to:");
                if (Utils.is_encoded_word(str_tmp)) str_tmp = Utils.parse_encoded_word(str_tmp);
                data.msg_current.set_to(str_tmp);
            }

            if (heads.get("subject:") != null) {
                String str_tmp = heads.get("subject:");
                if (Utils.is_encoded_word(str_tmp)) str_tmp = Utils.parse_encoded_word(str_tmp);
                data.msg_current.set_subject(str_tmp);
            }

            if (heads.get("message-id:") != null) {
                data.msg_current.set_message_id(heads.get("message-id:"));
            }

            if (heads.get("from:") != null) {
                String str_tmp = heads.get("from:");
                if (Utils.is_encoded_word(str_tmp)) str_tmp = Utils.parse_encoded_word(str_tmp);
                data.msg_current.set_from(str_tmp);
            }

            if (heads.get("cc:") != null) {
                String str_tmp = heads.get("cc:");
                if (Utils.is_encoded_word(str_tmp)) str_tmp = Utils.parse_encoded_word(str_tmp);
                data.msg_current.set_cc(str_tmp);
            }

            if (heads.get("bcc:") != null) {
                String str_tmp = heads.get("bcc:");
                if (Utils.is_encoded_word(str_tmp)) str_tmp = Utils.parse_encoded_word(str_tmp);
                data.msg_current.set_bcc(str_tmp);
            }

            if (heads.get("date:") != null) {
                data.msg_current.set_date(heads.get("date:"));
            }

            if (heads.get("content-type:") != null) {
                String str_tmp = "Content-Type: " + heads.get("content-type:");
                data.msg_current.set_content_type(str_tmp);
                data.crypto_contents = str_tmp.matches("(?i).*multipart/encrypted.*")
                    || str_tmp.matches("(?i).*multipart/signed.*");
            } else data.msg_current.set_content_type("Content-Type: text/other; charset=utf-8");

            if (heads.get("content-transfer-encoding:") != null) {
                data.msg_current.set_content_transfer_encoding(heads.get("content-transfer-encoding:"));
            }

            clear_buff();
        }
    }

    /**
     * Downloads a message from server, and parse.
     **/
    private void pop_retr_msg(boolean go) {
        if (go) {
            tag = "RETR";
            data.line_count = 0;
            data.msg_current.set_size(data.message_size.get(data.msg_index - 1));
            write(tag + " " + data.message_nums.get(data.msg_index - 1));
        } else {
            data.msg_current.set_full_msg(data.sbuffer.toString());

            // 0=content_type, 1=boundary, 2=charset
            String[] content_type = Utils.content_type_boundary(data.msg_current.get_content_type());

            if (content_type[1] == null) {
                // No MIME, text only email
                if (content_type[0].matches("(?i)^TEXT/PLAIN.*")) {
                    // Converting mime body to non-transfer-encoding readable body
                    if (data.msg_current.get_content_transfer_encoding() != null) {
                        data.msg_current.set_contents_plain(
                            Utils.parse_transfer_encoding(
                                data.msg_current.get_content_transfer_encoding(),
                                data.sbuffer.substring(data.hdr),
                                content_type[2]
                            )
                        );
                    } else {
                        data.msg_current.set_contents_plain(data.sbuffer.toString());
                    }
                    data.msg_current.set_charset_plain(content_type[2]);
                } else if (content_type[0].matches("(?i)^TEXT/HTML.*")) {
                    // Converting mime body to non-transfer-encoding readable body
                    if (data.msg_current.get_content_transfer_encoding() != null) {
                        data.msg_current.set_contents_html(
                            Utils.parse_transfer_encoding(
                                data.msg_current.get_content_transfer_encoding(),
                                data.sbuffer.substring(data.hdr),
                                content_type[2]
                            )
                        );
                    } else {
                        data.msg_current.set_contents_html(data.sbuffer.toString());
                    }
                    data.msg_current.set_charset_html(content_type[2]);
                } else {
                    // Not PLAIN or HTML
                    data.msg_current.set_contents_other(data.sbuffer.substring(data.hdr));
                }
            } else {
                // MIME type, multipart email
                pop_parse_mime_msg(content_type[1]);
            }

            clear_buff();
        }
    }

    /**
     * Downloads a message from server.
     **/
    private void pop_retr_full_msg(boolean go) {
        if (go) {
            tag = "RETR";
            write(tag + " " + data.message_nums.get(0));
        } else {
            // Adding to current message
            data.msg_current.set_full_msg(data.sbuffer.toString());
            clear_buff();
        }
    }

    /**
     * Converts string into bodystructure, and contents.
     * Boundary i.e. --my_boundary
     **/
    private void pop_parse_mime_msg(String boundary) {
        // Message reduced to only MIME
        String buff = data.msg_current.get_full_msg();
        int index_boundary = buff.indexOf(boundary);
        int index_boundary_two = buff.indexOf(boundary + "--", index_boundary);
        buff = buff.substring(index_boundary, index_boundary_two + boundary.length() + 2);

        // Converting mime body to non-transfer-encoding readable body
        if (data.msg_current.get_content_transfer_encoding() != null) {
            if (data.msg_current.get_content_transfer_encoding().matches("(?i).*BASE64.*")) {
                buff = Utils.parse_BASE64(buff);
            } else if (data.msg_current.get_content_transfer_encoding()
                .matches("(?i).*QUOTED-PRINTABLE.*")
            ) {
                pat = Pattern.compile(
                    ".*(charset|charset\\*)=(.*)", Pattern.CASE_INSENSITIVE|Pattern.DOTALL
                );
                mat = pat.matcher(data.msg_current.get_content_type());
                String c_set = "utf-8";
                if (mat.matches()) c_set = mat.group(1);
                buff = Utils.parse_quoted_printable(buff, c_set);
            }
        }

        if (data.crypto_contents) data.msg_current.set_contents_crypto(buff);

        // Structural parsing
        data.msg_structure = Utils.mime_bodystructure(
            buff, boundary, data.msg_current.get_content_type()
        );

        Utils.mime_parse_full_msg_into_texts(
            buff, data.msg_structure, data.msg_texts, data.msg_current
        );

        // Declare the number of attachments for later
        if (data.msg_structure != null) data.msg_current.set_attachments(data.msg_structure.size());
    }

    private void pop_save_attachment() {
        tag = "SAVE_ATTACHMENT";

        // Saving message attachment
        String att = Utils.mime_part_section(
            data.msg_current.get_full_msg(),
            data.att_item.get_pop_indx(),
            data.att_item.get_boundary()
        );
        try {
            // Converting transfer encoding
            if (data.att_item != null) {
                data.os = act.get().getContentResolver().openOutputStream(data.a_file.getUri(),"rw");

                // Parsing file download
                if (data.att_item.get_transfer_encoding().equalsIgnoreCase("BASE64")) {
                    boolean CR = false;
                    StringBuilder sb_tmp = new StringBuilder(0);
                    for (int i = 0; i < att.length(); ++i) {
                        if (att.charAt(i) == '\n') {
                            if (CR) {
                                data.os.write(
                                    Base64.decode(sb_tmp.toString().getBytes(), Base64.DEFAULT));
                                sb_tmp.setLength(0);
                                CR = false;
                            }
                        } else if (att.charAt(i) == '\r') {
                            CR = true;
                        } else if (att.charAt(i) != '=') {
                            sb_tmp.append(att.charAt(i));
                            CR = false;
                        }
                    }
                    if (sb_tmp.length() > 0) {
                        data.os.write(Base64.decode(sb_tmp.toString().getBytes(), Base64.DEFAULT));
                    }
                } else if (data.att_item.get_transfer_encoding()
                    .equalsIgnoreCase("QUOTED-PRINTABLE")
                ) {
                    // QUOTED-PRINTABLE, data problems: removes \n line endings
                    data.os.write(Utils.parse_quoted_printable(att, "utf-8").getBytes());
                } else {
                    // 7BIT, 8BIT, BINARY
                    data.os.write(att.getBytes());
                }
                data.os.flush();
                data.os.close();
            }
            if (sp != null) {
                on_ui_thread("-1", act.get().getString(R.string.progress_download_complete));
                sp.do_after();
                Dialogs.toaster(
                    true,
                    act.get().getString(R.string.message_action_done),
                    act.get()
                );
            }
        } catch (IOException e) {
            error_dialog(e);
            if (sp != null) {
                on_ui_thread("-1", act.get().getString(R.string.err_not_saved));
                sp.do_after();
            }
        }
    }

    private void pop_save_full_msg() {
        tag = "SAVE_MSG";

        if (data.a_file != null) {
            // Prepare for direct file write
            try {
                data.os = act.get().getContentResolver().openOutputStream(data.a_file.getUri(),"rw");
                if (data.os != null) {
                    data.os.write(data.msg_current.get_full_msg().getBytes());
                    data.os.flush();
                    data.os.close();
                }
                if (sp != null) {
                    on_ui_thread("-1", act.get().getString(R.string.progress_download_complete));
                    sp.do_after();
                    Dialogs.toaster(
                        true,
                        act.get().getString(R.string.message_action_done),
                        act.get()
                    );
                }
            } catch (IOException ioe) {
                InboxPager.log = InboxPager.log.concat(ioe.getMessage() + "\n\n");
                error_dialog(ioe);
                if (sp != null) {
                    on_ui_thread("-1", act.get().getString(R.string.err_not_saved));
                    sp.do_after();
                }
            }
        }

        // Saving full message to DB
        if (data.a_file == null || data.save_in_db) db.update_message(data.msg_current);
    }

    /**
     * Deletes a message from server.
     **/
    private void pop_delete_msg(boolean go) {
        if (go) {
            tag = "DELE";
            write(tag + " " + data.message_nums.get(0));
        } else {
            db.delete_message(data.msg_current.get_id());
            on_ui_thread("-1", act.get().getString(R.string.progress_deleted_msg));
            ((InboxMessage) act.get()).delete_message_ui();
            clear_buff();
        }
    }
}
