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

import net.inbox.InboxMessage;
import net.inbox.InboxPager;
import net.inbox.InboxSend;
import net.inbox.db.Attachment;
import net.inbox.db.DBAccess;
import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.pager.R;
import net.inbox.visuals.Dialogs;
import net.inbox.visuals.SpinningStatus;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class NetworkThread extends Thread {

    public SpinningStatus sp;

    // Exception has happened
    boolean excepted;

    // Multiple refresh
    boolean multiple;

    // True, when waiting for login
    boolean ready = false;

    // True, when exchange ends
    boolean over = false;

    // Command ID tag
    String tag = "";

    protected DBAccess db;
    protected WeakReference<AppCompatActivity> act;

    /**
     * Command return status codes.
     * IMAP OK = 1, NO = 2, BAD = 3
     **/
    int stat = 0;

    // Regular Expressions
    Matcher mat;
    Pattern pat;

    SocketIO io_sock;

    Thread io_sock_thread;

    Data data;

    abstract static class Data {
        // Command response returned by server
        String cmd_return = "";

        // Command response parameters returned by server
        String cmd_return_pars = "";

        // Data buffer, i.e message body
        StringBuilder sbuffer = new StringBuilder();

        // Testing?
        boolean test_mode;

        // Command que
        ArrayList<String> sequence = new ArrayList<>();

        // Command delegate que, mass email download
        ArrayList<String> delegation = new ArrayList<>();

        // Commands are delegated
        boolean delegate = false;

        // Current message is encrypted
        boolean crypto_contents = false;

        // Current message
        Message msg_current = new Message();

        // Save full message to DB
        boolean save_in_db;

        // Saving or uploading a file
        DocumentFile a_file;

        // Type of authentication to use
        String auth = "";

        // Server abilities
        ArrayList<String> auths = new ArrayList<>();
        ArrayList<String> general = new ArrayList<>();
    }

    Inbox current_inbox;

    // Last session state variables
    public int last_connection_data_id = -1;
    public String last_connection_data = null;

    public NetworkThread(AppCompatActivity at) {
        db = InboxPager.get_db(); // Get the database
        act = new WeakReference<>(at);
    }

    public abstract void reset();

    /**
     * Reading the reply of the network socket.
     **/
    public abstract void reply(String l);

    public abstract void test_server(Inbox inn, AppCompatActivity at);

    /**
     * Communicating individually with remote server.
     * Refreshes the inbox (IMAP,POP) with the changes in the messages.
     **/
    public abstract void default_action(boolean multi, Inbox inn, AppCompatActivity at);

    /**
     * Communicating individually with remote server.
     * Downloads an attachment, of a particular message.
     * In the case of SMTP it prepares attachments for transmission.
     **/
    public abstract void attachment_action(int aid, Attachment att, Object doc_file, AppCompatActivity at);

    /**
     * Communicating individually with remote server.
     * Downloads a particular message.
     * In the case of SMTP it tries to send a message.
     **/
    public abstract void msg_action(int aid, Message msg, Object doc_file, boolean sv, AppCompatActivity at);

    /**
     * Communicating individually with remote server.
     * Moves a particular message to a different folder.
     **/
    public abstract void move_action(int aid, Message msg, AppCompatActivity at);

    /**
     * Loads the capability extensions.
     **/
    public abstract void load_extensions();

    /**
     * Cancel the current activities, for an unknown reason.
     **/
    public abstract void cancel_action();

    /**
     * Clears session buffers.
     **/
    public abstract void clear_buff();

    /**
     * Communication through network socket.
     **/
    public void write(String s) {
        if (!io_sock.write(s))
            throw new java.lang.NullPointerException(act.get().getString(R.string.ex_do_io));
    }

    /**
     * Writes to socket, bypassing limits.
     * IMAP:
     * ???
     * POP:
     * Command Line = 255 or Reply Line = 512 (with \r\n).
     * SMTP:
     * Command Line or Reply Line = 512 (with \r\n).
     * Text Line = 1000 (with \r\n).
     **/
    void write_limited(char[] arr) {
        boolean cr = false;
        StringBuilder sb_write_out = new StringBuilder();
        for (char b : arr) {
            if (sb_write_out.length() > 500) {
                write(sb_write_out.toString());
                sb_write_out.setLength(0);
            }
            // CR= 13, LF=10
            if (b == 10 && cr)  {
                write(sb_write_out.toString());
                sb_write_out.setLength(0);
            }
            cr = b == 13;
            sb_write_out.append(b);
        }
        if (sb_write_out.length() > 0 ) {
            write(sb_write_out.toString());
            sb_write_out.setLength(0);
        }
    }

    void socket_start_imap(IMAP hand) {
        // Starting communication on socket
        io_sock = new SocketIO(
            current_inbox.get_imap_or_pop_server(),
            current_inbox.get_imap_or_pop_port(),
            hand,
            act.get().getBaseContext()
        );
        io_sock_thread = new Thread(io_sock);
        io_sock_thread.start();
    }

    void socket_start_pop(POP hand) {
        // Starting communication on socket
        io_sock = new SocketIO(
            current_inbox.get_imap_or_pop_server(),
            current_inbox.get_imap_or_pop_port(),
            hand,
            act.get().getBaseContext()
        );
        io_sock_thread = new Thread(io_sock);
        io_sock_thread.start();
    }

    void socket_start_smtp(SMTP hand) {
        // Starting communication on socket
        io_sock = new SocketIO(
            current_inbox.get_smtp_server(),
            current_inbox.get_smtp_port(),
            hand,
            act.get().getBaseContext()
        );
        io_sock_thread = new Thread(io_sock);
        io_sock_thread.start();
    }

    void error_dialog(Exception e) {
        // Stop the current actions
        cancel_action();

        if (multiple) {
            // Only flag errors
            Dialogs.toaster(
                true,
                act.get().getString(R.string.err_refresh) + " " + current_inbox.get_email(),
                act.get()
            );
        } else {
            if (sp != null) sp.do_after();

            // Dialog for common cases
            if (e.getMessage() != null && e.getMessage().matches("(?i).*ENETUNREACH.*")) {
                Dialogs.dialog_simple(
                    act.get().getString(R.string.ex_title),
                    act.get().getString(R.string.ex_no_internet),
                    act.get()
                );
            } else if (e.getMessage() != null
                && e.getMessage().matches("(?i).*Unable to resolve host.*")
            ) {
                Dialogs.dialog_simple(
                    act.get().getString(R.string.ex_title),
                    act.get().getString(R.string.ex_no_remote_address) + "\n\n" + e.getMessage(),
                    act.get()
                );
            } else Dialogs.dialog_exception(e, act.get());

            // Connection icon update
            act.get().runOnUiThread(
                    () -> {
                    if (act.get().getClass().toString().endsWith(".InboxMessage")) {
                        // Set server certificate details
                        ((InboxMessage) act.get()).connection_security();
                    } else if (act.get().getClass().toString().endsWith(".InboxSend")) {
                        // Set server certificate details
                        ((InboxSend) act.get()).connection_security();
                    }
                }
            );
        }
    }

    void error_dialog(String s_error) {
        // Stop the current actions
        cancel_action();

        // Dismiss the spinning dialog
        if (multiple) {
            String s = act.get().getString(R.string.ex_field) + s_error;
            InboxPager.log = InboxPager.log.concat(s + "\n\n");
            on_ui_thread_toast(s);
        } else {
            sp.do_after();
            Dialogs.dialog_simple(null, s_error, act.get());
        }
    }

    /**
     * Android requires only UI Thread change the UI.
     * Updates the spinning status dialog.
     **/
    void on_ui_thread(final String title, final String msg) {
        sp.set_progress(title, msg);
    }

    /**
     * Android requires only UI Thread change the UI.
     * Continue mass-refresh.
     **/
    void on_ui_thread_continue_refresh() {
        act.get().runOnUiThread(((InboxPager) act.get())::mass_refresh);
    }

    void on_ui_thread_toast(String s) {
        act.get().runOnUiThread(() -> Dialogs.toaster(false, s, act.get()));
    }
}
