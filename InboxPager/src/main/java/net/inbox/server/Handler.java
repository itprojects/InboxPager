/*
 * InboxPager, an android email client.
 * Copyright (C) 2016  I.T.
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

import net.inbox.InboxMessage;
import net.inbox.InboxSend;
import net.inbox.Pager;
import net.inbox.R;
import net.inbox.db.Attachment;
import net.inbox.db.DBAccess;
import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.dialogs.Dialogs;
import net.inbox.dialogs.SpinningStatus;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.appcompat.app.AppCompatActivity;

public abstract class Handler extends Thread {

    protected Context ctx;

    protected DBAccess db;

    public SpinningStatus sp;

    // Exception has happened
    boolean excepted;

    // Multiple refresh
    boolean multiple;

    // True, when waiting for login
    boolean ready = false;

    // True, when exchange ends
    public boolean over = false;

    // Command ID tag
    String tag = "";

    /**
     * Command return status codes.
     * <p>
     * IMAP OK = 1, NO = 2, BAD = 3
     **/
    int stat = 0;

    // Regular Expressions
    Matcher mat;
    Pattern pat;

    SocketIO io_sock;

    Thread io_sock_thread;

    public Data data;

    abstract class Data {
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
        File a_file;

        // Type of authentication to use
        String auth = "";

        // Server abilities
        ArrayList<String> auths = new ArrayList<>();
        ArrayList<String> general = new ArrayList<>();
    }

    Inbox current_inbox;

    // Last session state variables
    boolean last_connection_hostname = true;

    int last_connection_data_id = -1;

    ArrayList<String[]> last_connection_data;

    public Handler(Context ct) {
        // Get the database
        db = Pager.get_db();
        ctx = ct;
    }

    public abstract void reset();

    /**
     * Reading the reply of the network socket.
     **/
    public abstract void reply(String l);

    public abstract void test_server(Inbox inn, Context ct);

    /**
     * Communicating individually with remote server.
     * Refreshes the inbox (IMAP,POP) with the changes in the messages.
     **/
    public abstract void default_action(boolean multi, Inbox inn, Context ct);

    /**
     * Communicating individually with remote server.
     * Downloads an attachment, of a particular message.
     * In the case of SMTP it prepares attachments for transmission.
     **/
    public abstract void attachment_action(int aid, Attachment att, String save_path, Context ct);

    /**
     * Communicating individually with remote server.
     * Downloads a particular message.
     * In the case of SMTP it tries to send a message.
     **/
    public abstract void msg_action(int aid, Message msg, String save_path, boolean sv, Context ct);

    /**
     * Communicating individually with remote server.
     * Moves a particular message to a different folder.
     **/
    public abstract void move_action(int aid, Message msg, Context ct);

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
        if (!io_sock.write(s)) throw new
                java.lang.NullPointerException(ctx.getString(R.string.ex_do_io));
    }

    /**
     * Writes to socket, bypassing limits.
     * IMAP:
     * ???
     * <p>
     * POP:
     * Command Line = 255 or Reply Line = 512 (with \r\n).
     * <p>
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
            if (b == 10 && cr) {
                write(sb_write_out.toString());
                sb_write_out.setLength(0);
            }
            cr = b == 13;
            sb_write_out.append(b);
        }
        if (sb_write_out.length() > 0) {
            write(sb_write_out.toString());
            sb_write_out.setLength(0);
        }
    }

    void socket_start_imap(IMAP hand) {
        // Starting communication on socket
        io_sock = new SocketIO(current_inbox.get_imap_or_pop_server(),
                current_inbox.get_imap_or_pop_port(), hand, ctx);
        io_sock_thread = new Thread(io_sock);
        io_sock_thread.start();
    }

    void socket_start_pop(POP hand) {
        // Starting communication on socket
        io_sock = new SocketIO(current_inbox.get_imap_or_pop_server(),
                current_inbox.get_imap_or_pop_port(), hand, ctx);
        io_sock_thread = new Thread(io_sock);
        io_sock_thread.start();
    }

    void socket_start_smtp(SMTP hand) {
        // Starting communication on socket
        io_sock = new SocketIO(current_inbox.get_smtp_server(),
                current_inbox.get_smtp_port(), hand, ctx);
        io_sock_thread = new Thread(io_sock);
        io_sock_thread.start();
    }

    void error_dialog(Exception e) {
        // Stop the current actions
        cancel_action();

        // Dismiss the spinning dialog
        Pager.log += ctx.getString(R.string.ex_field) + e.getMessage() + "\n\n";
        if (!multiple) {
            if (sp != null) sp.unblock = true;
            Dialogs.dialog_exception(e, (AppCompatActivity) ctx);

            // Connection icon update
            ((AppCompatActivity) ctx).runOnUiThread(() -> {
                if (ctx.getClass().toString().endsWith(".InboxMessage")) {
                    // Set server certificate details
                    ((InboxMessage) ctx).connection_security();
                } else if (ctx.getClass().toString().endsWith(".InboxSend")) {
                    // Set server certificate details
                    ((InboxSend) ctx).connection_security();
                }
            });
        } else {
            // Flag errors
            Dialogs.toaster(true, ctx.getString(R.string.err_refresh) + " " +
                    current_inbox.get_email(), (AppCompatActivity) ctx);
        }
    }

    void error_dialog(String s) {
        // Stop the current actions
        cancel_action();

        // Dismiss the spinning dialog
        if (multiple) {
            Pager.log += ctx.getString(R.string.ex_field) + s + "\n\n";
        } else {
            sp.unblock = true;
            Dialogs.dialog_error_line(s, (AppCompatActivity) ctx);
        }
    }

    /**
     * Android requires only UI Thread change the UI.
     * Updates the spinning status dialog.
     **/
    void on_ui_thread(final String title, final String msg) {
        ((AppCompatActivity) ctx).runOnUiThread(() -> sp.onProgressUpdate(title, msg));
    }

    /**
     * Android requires only UI Thread change the UI.
     * Continue mass-refresh.
     **/
    void on_ui_thread_continue_refresh() {
        final Pager page = (Pager) ctx;
        page.runOnUiThread(page::mass_refresh);
    }

    public boolean get_hostname_verify() {
        return last_connection_hostname;
    }

    public int get_last_connection_data_id() {
        return last_connection_data_id;
    }

    public ArrayList<String[]> get_last_connection_data() {
        return last_connection_data;
    }
}
