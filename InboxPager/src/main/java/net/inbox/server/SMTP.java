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

import net.inbox.InboxPager;
import net.inbox.InboxSend;
import net.inbox.R;
import net.inbox.db.Attachment;
import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.dialogs.Dialogs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;

public class SMTP extends Handler {

    private class DataSMTP extends Data {
        // SMTP host
        String smtp_host;

        //SMTP in UTF-8 encoding
        boolean smtp_utf_8;

        // Holds To:, Cc:, Bcc: recipients
        ArrayList<String> total_list = new ArrayList<>();

        // Command sequence
        ArrayList<String> sequence = new ArrayList<>();

        // Message attachments
        ArrayList<String> msg_current_attachments = new ArrayList<>();
    }

    private DataSMTP data;

    public SMTP(Context ct) {
        super(ct);
    }

    @Override
    public void reset() {
        over = false;
        ready = false;
        excepted = false;
        tag = "";
        stat = 0;
        io_sock = null;
        data = null;
        current_inbox = null;
    }

    @Override
    public void reply(String l) {
        stat = smtp_cmd(l);
        if (stat > 0) {
            smtp_conductor();
        } else {
            // Message data
            data.sbuffer.append(l);
        }
    }

    @Override
    public void test_server(Inbox inn, Context ct) {
        current_inbox = inn;
        ctx = ct;

        // A partial reset of variables
        over = false;
        ready = false;

        // Data storage
        data = new DataSMTP();

        // Testing
        data.test_mode = true;

        // Testing using EHLO command
        current_inbox.set_ehlo(true);

        socket_start_smtp(this);

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

                    if (current_inbox.get_smtp_extensions() != null
                            && !current_inbox.get_smtp_extensions().isEmpty()) {
                        String tested;
                        if (current_inbox.get_smtp_extensions()
                                .equals(ctx.getString(R.string.err_no_ehlo))) {
                            tested = ctx.getString(R.string.err_no_ehlo);
                        } else {
                            // Preparing the dialog message
                            data.auths = new ArrayList<>();
                            data.general = new ArrayList<>();
                            String[] parts = current_inbox.get_smtp_extensions().split("\n");
                            for (int g = 0;g < parts.length;++g) {
                                if (parts[g] != null) {
                                    parts[g] = parts[g].trim();
                                    parts[g] = parts[g].toLowerCase();
                                    if (parts[g].startsWith("auth")) {
                                        String[] au = parts[g].substring(5).split(" ");
                                        Collections.addAll(data.auths, au);
                                        //for (String tt : au) data.auths.add(tt);
                                        continue;
                                    }
                                    data.general.add(parts[g]);
                                }
                            }
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
                        Dialogs.dialog_server_ext(ctx.getString(R.string.edit_account_check_smtp),
                                tested, (AppCompatActivity) ctx);
                    } else {
                        Dialogs.dialog_server_ext(ctx.getString(R.string.edit_account_check_smtp),
                                ctx.getString(R.string.edit_account_check_fail),
                                (AppCompatActivity) ctx);
                    }
                    reset();
                    over = true;
                }
            }).start();
        }
        over = true;
    }

    @Override
    public void default_action(boolean multi, Inbox inn, Context ct) {}

    @Override
    public void attachment_action(int aid, Attachment att, String save_path, Context ct) {}

    @Override
    public void msg_action(int aid, Message msg, String save_path, boolean sv, Context ct) {
        current_inbox = db.get_account(aid);
        ctx = ct;
        if (sp != null) {
            on_ui_thread("-1", ctx.getString(R.string.progress_connecting) + " "
                    + current_inbox.get_smtp_server());
        }

        // A partial reset of variables
        over = false;
        ready = false;
        excepted = false;

        // Data storage
        data = new DataSMTP();

        // Check server capabilities
        // SMTP Server extensions check
        current_inbox.set_ehlo(current_inbox.get_smtp_extensions().equals("-1"));

        // Main mail sequence
        data.sequence.add("RCPT");
        data.sequence.add("DATA");

        data.msg_current = msg;

        // Check for attachments
        if (save_path != null) {
            String[] str = save_path.trim().split("\uD83D\uDCCE");
            for (int i = 0;i < str.length;++i) {
                data.msg_current_attachments.add(i, str[i]);
            }
        }

        socket_start_smtp(this);
    }

    @Override
    public void move_action(int aid, Message msg, Context ct) {}

    @Override
    public void load_extensions() {
        String str = current_inbox.get_smtp_extensions();
        if (str != null && !str.isEmpty()) {
            String[] arr = str.toUpperCase().split("\n");
            for (String s : arr) {
                if (s.startsWith("AUTH ")) {
                    Collections.addAll(data.auths, s.substring(5).split(" "));
                } else {
                    data.general.add(s);
                }
            }
        }
    }

    @Override
    public void cancel_action() {
        if (!io_sock.closed_already()) {
            write("RSET");
            write("QUIT");
        }
        over = true;
    }

    @Override
    public void clear_buff() {}

    /**
     * A que of commands towards a goal, ex. send message.
     **/
    private void smtp_conductor() {
        if (over) return;
        try {
            switch (stat) {
                case 220:
                    smtp_220();
                    break;
                case 221:
                    smtp_221();
                    break;
                case 235:
                    smtp_235();
                    break;
                case 250:
                    smtp_250();
                    break;
                case 334:
                    smtp_334();
                    break;
                case 354:
                    smtp_354();
                    break;
                case 421:
                    smtp_421();
                    break;
                case 450:
                    smtp_450();
                    break;
                case 451:
                    smtp_451();
                    break;
                case 452:
                    smtp_452();
                    break;
                case 455:
                    smtp_455();
                    break;
                case 500:
                    smtp_500();
                    break;
                case 501:
                    smtp_501();
                    break;
                case 502:
                    smtp_502();
                    break;
                case 503:
                    smtp_503();
                    break;
                case 504:
                    smtp_504();
                    break;
                case 535:
                    smtp_535();
                    break;
                case 550:
                    smtp_550();
                    break;
                case 551:
                    smtp_551();
                    break;
                case 552:
                    smtp_552();
                    break;
                case 553:
                    smtp_553();
                    break;
                case 554:
                    smtp_554();
                    break;
                case 555:
                    smtp_555();
                    break;
            }
        } catch (Exception e) {
            // Stop the current actions
            cancel_action();

            // Dismiss the spinning dialog
            if (sp != null) sp.unblock = true;

            // Start a exception details new dialog
            Dialogs.dialog_exception(e, (AppCompatActivity) ctx);
        }
    }

    /**
     * Looks for response code.
     **/
    private int smtp_cmd(String l) {
        if (l.length() < 3) {
            return 0;
        }

        switch(l.charAt(0)) {
            case '2':// OK
            case '3':// WAIT
            case '4':// PARAMS!
            case '5':// ERROR
                break;
            default:
                return 0;
        }

        switch(l.charAt(1)) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                break;
            default:
                return 0;
        }

        switch(l.charAt(2)) {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                break;
            default:
                return 0;
        }

        if (l.charAt(3) == ' ') {
            data.cmd_return = l.substring(4).trim();
            return Integer.parseInt(l.substring(0, 3));
        } else if (l.charAt(3) == '-') {
            data.cmd_return_pars += l.substring(4) + "\n";
        }
        return 0;
    }

    /**
     * <domain> Service ready.
     **/
    private void smtp_220() {
        tag = current_inbox.get_ehlo() ? "EHLO " : "HELO ";
        data.smtp_host = data.cmd_return.split(" ")[0];
        write(tag + data.smtp_host);
    }

    /**
     * <domain> Service closing transmission channel.
     **/
    private void smtp_221() {
        if (sp != null) sp.unblock = true;
    }

    /**
     * Authentication successful.
     **/
    private void smtp_235() {
        if (sp != null) on_ui_thread("-1", ctx.getString(R.string.progress_authenticated));

        // Prepare certificate information
        last_connection_data_id = current_inbox.get_id();
        last_connection_data = io_sock.print();

        // Make a full RCPT list
        if (data.msg_current.get_to() != null && !data.msg_current.get_to().isEmpty()) {
            Collections.addAll(data.total_list, data.msg_current.get_to().split(","));
        }
        if (data.msg_current.get_cc() != null && !data.msg_current.get_cc().isEmpty()) {
            Collections.addAll(data.total_list, data.msg_current.get_cc().split(","));
        }
        if (data.msg_current.get_bcc() != null && !data.msg_current.get_bcc().isEmpty()) {
            Collections.addAll(data.total_list, data.msg_current.get_bcc().split(","));
        }

        // Starting message sending
        String user_name = current_inbox.get_username();
        if (!user_name.contains("@")) user_name += current_inbox.get_smtp_server();
        tag = "MAIL FROM";
        String s = "MAIL FROM: <" + user_name + ">";
        if (!data.smtp_utf_8) Utils.to_ascii(s);
        write(s);
    }

    /**
     * Requested mail action completed okay.
     **/
    private void smtp_250() {
        if (ready) {
            if (over || data.sequence.size() < 1) {
                over = true;
                write("QUIT");
                return;
            }

            // Sending message details, when logged in.
            switch(data.sequence.get(0)) {
                case "RCPT":
                    tag = "RCPT TO";
                    String s = "RCPT to: <";
                    if (data.total_list.size() > 0) {
                        s += data.total_list.get(0);
                        data.total_list.remove(0);
                    }
                    s += ">";
                    if (!data.smtp_utf_8) Utils.to_ascii(s);
                    write(s);
                    if (data.total_list.size() < 1) data.sequence.remove(0);
                    break;
                case "DATA":
                    tag = "DATA";
                    write(tag);
                    data.sequence.remove(0);
                    break;
            }
        } else {
            String temp;
            temp = data.cmd_return_pars;
            temp += data.cmd_return;
            ready = true;
            if (sp != null) on_ui_thread("-1", ctx.getString(R.string.progress_connected));
            if (current_inbox.get_smtp_extensions().equals("-1")) {
                if (temp.contains(data.smtp_host)) {
                    String[] arr_ext = temp.split("\n");
                    temp = "";
                    for (String t : arr_ext) {
                        if (!t.contains(data.smtp_host)) temp = temp.concat(t + "\n");
                    }
                    temp = temp.trim();
                }
                current_inbox.set_smtp_extensions(temp);
                db.update_account(current_inbox);
            }
            if (!data.test_mode) {
                data.smtp_utf_8 = data.general.contains("SMTPUTF8");
                load_extensions();
                if (data.auths.contains("LOGIN")) {
                    data.auth = "LOGIN";
                    tag = "AUTH LOGIN";
                    write(tag);
                } else if (data.auths.contains("PLAIN")) {
                    data.auth = "PLAIN";
                    tag = "AUTH PLAIN";
                    write(tag);
                } else {
                    error_dialog(ctx.getString(R.string.err_no_authentication));
                    data.sequence.clear();
                    cancel_action();
                }
            } else {
                current_inbox.set_smtp_extensions(temp);
                write("QUIT");
            }
        }
    }

    /**
     * Authentication needed. User name and password.
     **/
    private void smtp_334() {
        if (data.auth.equalsIgnoreCase("LOGIN")) {
            // LOGIN type of authentication
            String str = new String(Base64.decode(data.cmd_return.trim().getBytes(),
                    Base64.DEFAULT)).toUpperCase();
            if (str.startsWith("USERNAME")) {
                write(new String(Base64.encode(current_inbox.get_username().getBytes(),
                        Base64.DEFAULT)).trim());
            } else if (str.startsWith("PASSWORD")) {
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
            }
        } else if (data.auth.equalsIgnoreCase("PLAIN")) {
            // PLAIN type of authentication
            String str = Base64.encodeToString(("\0"+ current_inbox.get_username() + "\0"
                    + current_inbox.get_pass()).getBytes(), Base64.DEFAULT);
            if (str.length() > 500) {
                write_limited(str.toCharArray());
            } else {
                write(str);
            }
        }
    }

    /**
     * Start mail (user client) input. After DATA.
     **/
    private void smtp_354() {
        try {
            String user_name = current_inbox.get_username();
            if (!user_name.contains("@")) user_name += current_inbox.get_smtp_server();
            String st = "From: " + user_name;
            if (!data.smtp_utf_8) st = Utils.to_ascii(st);
            write(st);
            st = "To: " + data.msg_current.get_to();
            if (!data.smtp_utf_8) st = Utils.to_ascii(st);
            write_limited(st.toCharArray());
            if (data.msg_current.get_cc() != null && !data.msg_current.get_cc().isEmpty()) {
                st = "Cc: " + data.msg_current.get_cc();
                if (!data.smtp_utf_8) st = Utils.to_ascii(st);
                write_limited(st.toCharArray());
            }
            if (data.msg_current.get_bcc() != null && !data.msg_current.get_bcc().isEmpty()) {
                st = "Bcc: " + data.msg_current.get_bcc();
                if (!data.smtp_utf_8) st = Utils.to_ascii(st);
                write_limited(st.toCharArray());
            }
            // Message-ID
            st = "Message-ID: <" + String.valueOf(Math.random() * 1000);
            st += String.valueOf(System.currentTimeMillis()) + ">";
            write(st);
            if (data.msg_current.get_subject() != null
                    && !data.msg_current.get_subject().isEmpty()) {
                st = "Subject: ";
                if (!Utils.all_ascii(data.msg_current.get_subject())) {
                    st += Utils.to_base64_utf8(data.msg_current.get_subject()).trim();
                } else {
                    st += data.msg_current.get_subject().trim();
                }
            }
            write_limited(st.toCharArray());
            if (sp != null) on_ui_thread("-1", ctx.getString(R.string.send_headers_sent));
            sleep(100);
            if (data.msg_current.get_contents_crypto() != null) {
                // PGP/MIME
                System.gc();
                write("MIME-Version: 1.0");
                String dat = data.msg_current.get_contents_crypto();
                StringBuilder sb_write_out = new StringBuilder();
                for (int ii = 0;ii < dat.length();++ii) {
                    if (dat.charAt(ii) == '\n') {
                        write(sb_write_out.toString());
                        sb_write_out.setLength(0);
                    } else {
                        sb_write_out.append(dat.charAt(ii));
                        if (sb_write_out.length() > 997) {
                            write(sb_write_out.toString());
                            sb_write_out.setLength(0);
                        }
                    }
                }
                if (sb_write_out.length() > 0) {
                    write(sb_write_out.toString());
                    sb_write_out.setLength(0);
                }
            } else if (data.msg_current_attachments.size() > 0) {
                write("MIME-Version: 1.0");
                String bounds = Utils.boundary();
                write("Content-type: multipart/mixed; boundary=" + "\"" + bounds + "\"");

                // Message textual contents
                if (!data.msg_current.get_contents_plain().isEmpty()) {
                    sleep(100);
                    write("--" + bounds);
                    write("Content-Type: text/plain; charset=\"utf-8\"");
                    write("Content-Transfer-Encoding: 8bit");
                    write("\n");
                    write_limited(data.msg_current.get_contents_plain().toCharArray());
                }

                // Message attachments
                ArrayList<Byte> line_buf_bytes = new ArrayList<>();
                for (int i = 0;i < data.msg_current_attachments.size();++i) {
                    File ff = new File(data.msg_current_attachments.get(i));
                    if (sp != null) {
                        on_ui_thread("-1", ctx.getString(R.string.send_upload_attachment)
                                + " " + ff.getName());
                    }

                    String mime_type_guess = URLConnection.guessContentTypeFromName(ff.getName());
                    if (mime_type_guess == null || mime_type_guess.isEmpty()) {
                        mime_type_guess = "application/octet-stream";
                    }

                    write("--" + bounds);
                    if (Utils.all_ascii(ff.getName())) {
                        write("Content-Type: " + mime_type_guess + "; name=\"" + ff.getName() + "\"");
                        write("Content-Transfer-Encoding: base64");
                        write("Content-Disposition: attachment; filename=\"" + ff.getName() + "\"");
                    } else {
                        write("Content-Type: " + mime_type_guess + "; name*=\"" + Utils.to_base64_utf8(ff.getName()) + "\"");
                        write("Content-Transfer-Encoding: base64");
                        String new_name = Utils.content_disposition_name(true, ff.getName());
                        write("Content-Disposition: attachment; filename*=" + new_name);
                    }
                    write("\n");
                    ByteArrayOutputStream b_stream = new ByteArrayOutputStream();
                    try {
                        InputStream in_stream = new FileInputStream(ff);
                        byte[] bfr = new byte[(int)ff.length()];
                        if ((int)ff.length() > 0) {
                            int t;
                            while ((t = in_stream.read(bfr)) != -1) { b_stream.write(bfr, 0, t); }
                        }
                    } catch (IOException e) {
                        InboxPager.log = InboxPager.log.concat(ctx.getString
                                (R.string.ex_field) + e.getMessage() + "\n\n");
                    }
                    byte[] a_bytes = Base64.encode(b_stream.toByteArray(), Base64.DEFAULT);
                    for (byte b : a_bytes) {
                        if (b == 10) {
                            byte[] b64s = new byte[line_buf_bytes.size()];
                            for (int d = 0;d < line_buf_bytes.size();d++) {
                                b64s[d] = line_buf_bytes.get(d);
                            }
                            write(new String(b64s));
                            line_buf_bytes = new ArrayList<>();
                        } else line_buf_bytes.add(b);
                    }
                    if (line_buf_bytes.size() > 0) {
                        byte[] b64s = new byte[line_buf_bytes.size()];
                        for (int d = 0;d < line_buf_bytes.size();d++) {
                            b64s[d] = line_buf_bytes.get(d);
                        }
                        write(new String(b64s));
                        line_buf_bytes = new ArrayList<>();
                    }
                }
                write("--" + bounds + "--");
            } else {
                // Simple Message textual contents
                if (!data.msg_current.get_contents_plain().isEmpty()) {
                    write("Content-Type: text/plain; charset=utf-8");
                    write("Content-Transfer-Encoding: 8bit");
                    write("\n");
                    write_limited(data.msg_current.get_contents_plain().toCharArray());
                }
            }
            sleep(100);
            write("\r\n.");
            write("QUIT");
            if (sp != null) {
                on_ui_thread("-1", ctx.getString(R.string.send_sent));
                sp.unblock = true;
                Dialogs.toaster(false, ctx.getString(R.string.send_sent), (AppCompatActivity) ctx);
            }
            InboxPager.notify_update();
            final InboxSend inb = (InboxSend) ctx;
            ((InboxSend) ctx).runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    inb.connection_security();
                }
            });
        } catch (InterruptedException e) {
            InboxPager.log += ctx.getString(R.string.ex_field) + e.getMessage() + "\n\n";
        }
    }

    /**
     * <domain> Service not available. Closing transmission channel.
     **/
    private void smtp_421() {
        if (!data.test_mode) error_dialog(data.cmd_return);
    }

    /**
     * Requested mail action not taken.
     * Mailbox unavailable - busy, blocked, or policy.
     **/
    private void smtp_450() {
        error_dialog(data.cmd_return);
    }

    /**
     * Requested action aborted. Local error in processing.
     **/
    private void smtp_451() {
        error_dialog(data.cmd_return);
    }

    /**
     * Requested action not taken. Insufficient system storage.
     **/
    private void smtp_452() {
        error_dialog(data.cmd_return);
    }

    /**
     * Server unable to accommodate parameters.
     **/
    private void smtp_455() {
        error_dialog(data.cmd_return);
    }

    /**
     * Syntax error, command unrecognized (or too long).
     **/
    private void smtp_500() {
        error_dialog(data.cmd_return);
    }

    /**
     * Syntax error in parameters or arguments.
     **/
    private void smtp_501() {
        error_dialog(data.cmd_return);
    }

    /**
     * Command not implemented.
     **/
    private void smtp_502() {
        if (data.test_mode) {
            current_inbox.set_smtp_extensions(ctx.getString(R.string.err_no_ehlo));
            db.update_account(current_inbox);
        } else {
            error_dialog(data.cmd_return);
        }
    }

    /**
     * Bad sequence of commands.
     **/
    private void smtp_503() {
        error_dialog(data.cmd_return);
    }

    /**
     * Command parameter not implemented.
     **/
    private void smtp_504() {
        error_dialog(data.cmd_return);
    }

    /**
     * No such authentication.
     **/
    private void smtp_535() {
        if (data.cmd_return.startsWith("5.7.8") && data.cmd_return.toLowerCase()
                .contains("authentication failure")) {
            // Bad User Name or Password
            error_dialog(ctx.getString(R.string.err_wrong_user_or_pass));
        } else {
            error_dialog(data.cmd_return);
        }
    }

    /**
     * Requested action not taken.
     * Mailbox unavailable - not found, no access, or policy.
     **/
    private void smtp_550() {
        error_dialog(data.cmd_return);
    }

    /**
     * User not local. Please try <forward-path>.
     **/
    private void smtp_551() {
        error_dialog(data.cmd_return);
    }

    /**
     * Requested mail action aborted. Exceeds storage allocation.
     **/
    private void smtp_552() {
        error_dialog(data.cmd_return);
    }

    /**
     * Requested action not taken.
     * Mailbox name not allowed - incorrect mailbox syntax.
     **/
    private void smtp_553() {
        error_dialog(data.cmd_return);
    }

    /**
     * Transaction failed (or "No SMTP service here"?).
     **/
    private void smtp_554() {
        error_dialog(data.cmd_return);
    }

    /**
     * MAIL FROM/RCPT TO parameters not recognized or not implemented.
     **/
    private void smtp_555() {
        error_dialog(data.cmd_return);
    }
}
