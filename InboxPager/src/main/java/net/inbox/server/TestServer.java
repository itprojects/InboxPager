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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.inbox.Common;
import net.inbox.InboxPager;
import net.inbox.pager.R;
import net.inbox.visuals.Dialogs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class TestServer {

    private String progress_msg;
    private String test_results_text;
    private String[] test_results;
    private String server_name;

    private final int[] ports = new int[]{ 25, 110, 143, 465, 587, 993, 995 };

    private int operation_index = -1;
    private boolean over = true;

    private ProgressBar pb;
    private TextView tv_results;
    private TextView tv_message;

    private AlertDialog test_server_dialog;
    private WeakReference<AppCompatActivity> act;
    private Handler handler_ui;
    private Thread th;

    public TestServer(String s, AppCompatActivity at) {
        act = new WeakReference<>(at);
        server_name = s;

        handler_ui = new Handler(Looper.getMainLooper());

        // Prevents screen rotation crash
        Common.fixed_or_rotating_orientation(true, at);

        View layout = at.getLayoutInflater().inflate(R.layout.test_server_dialog, null);
        pb = layout.findViewById(R.id.pb);
        tv_results = layout.findViewById(R.id.tv_results);

        // Create dialog
        test_server_dialog = new AlertDialog.Builder(at)
            .setTitle(server_name)
            .setMessage(at.getString(R.string.progress_refreshing))
            .setPositiveButton(at.getString(R.string.progress_test), null)
            .setNegativeButton(
                at.getString(android.R.string.cancel),
                (dialog, id) -> {
                    over = true;
                    operation_index = -1;
                    dialog.dismiss();
                    Common.fixed_or_rotating_orientation(false, at);
                }
            ).setCancelable(false).setView(layout).create();
            //.setOnDismissListener(dialog_interface -> {})

        test_server_dialog.show();
        test_server_dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
            v -> { // performed here, after show(), to prevent dismiss action duplication
                if (over) {
                    over = false;
                    operation_index = -1;
                    test_results = new String[]{ ".", ".", ".", ".", ".", ".", "." };
                    pb.setProgress(0);
                    test_results_text = "";
                    handler_ui.post(this::set_text_results); // Show results in UI
                    next_test();
                }
            }
        );
    }

    private void next_test() {
        ++operation_index;
        if (!over && operation_index < 7) {
            if (th != null && th.isAlive()) {
                th.interrupt();
                th = null;
            }
            switch (operation_index) {
                case 0:
                    set_progress(0, 0);
                    test_results_text = act.get().getString(R.string.edit_account_test_results) + "\n\n";
                    handler_ui.post(this::set_text_results); // Show results in UI
                    th = new Thread(test(false, ports[0], 0));
                    break;
                case 1:
                    set_progress(14, 1);
                    th = new Thread(test(false, ports[1], 1));
                    break;
                case 2:
                    set_progress(14 * 2, 2);
                    th = new Thread(test(false, ports[2], 2));
                    break;
                case 3:
                    set_progress(14 * 3, 3);
                    th = new Thread(test(true, ports[3], 3));
                    break;
                case 4:
                    set_progress(14 * 4, 4);
                    th = new Thread(test(false, ports[4], 4));
                    break;
                case 5:
                    set_progress(14 * 5, 5);
                    th = new Thread(test(true, ports[5], 5));
                    break;
                case 6:
                    set_progress(100, 6);
                    th = new Thread(test(true, ports[6], 6));
                    break;
                default:
                    return;
            }
            th.start();
            try {
                th.join(10000); // 10 second timeout for response
            } catch (InterruptedException e) {
                InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            }
        } else {
            over = true;
        }
    }

    private Runnable test(final Boolean ssl, final int port, final int indx) {
        return () -> {
            SSLSocket s = null;// SSL socket
            Socket sn = new Socket(); // Ordinary socket

            try {
                if (ssl) {
                    SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
                    s = (SSLSocket) sf.createSocket(server_name, port);
                    s.setSoTimeout(10000); // 10 seconds timeout for reading
                } else {
                    //sn = SocketFactory.getDefault().createSocket(server_name, port);
                    sn.connect(new InetSocketAddress(server_name, port), 3000);
                }

                BufferedReader r;
                if (ssl) {
                    r = new BufferedReader(new InputStreamReader(s.getInputStream()));
                } else {
                    r = new BufferedReader(new InputStreamReader(sn.getInputStream()));
                }

                test_results[indx] = r.readLine();

                if (s != null && !s.isClosed()) s.close();
                if (!sn.isClosed()) sn.close();
            } catch (IOException e) {
                InboxPager.log = InboxPager.log.concat(
                    act.get().getString(R.string.ex_field) + e.getMessage() + "\n\n"
                );
                handler_ui.post(() -> Dialogs.toaster(false, e.getMessage(), act.get()));
            }

            s = null;
            sn = null;

            switch(indx) {
                case 0: {
                    test_results_text += (test_results[0].startsWith("220")) ? "✓" : "⨯";
                    test_results_text += act.get().getString(R.string.edit_account_smtp1) + ", ";
                    break;
                }
                case 1: {
                    test_results_text += (test_results[1].toLowerCase().contains("ready")) ? "✓" : "⨯";
                    test_results_text += act.get().getString(R.string.edit_account_pop1) + ", ";
                    break;
                }
                case 2: {
                    test_results_text += (test_results[2].toLowerCase().contains("ready")) ? "✓" : "⨯";
                    test_results_text += act.get().getString(R.string.edit_account_imap1) + ", ";
                    break;
                }
                case 3: {
                    test_results_text += (test_results[3].startsWith("220")) ? "✓" : "⨯";
                    test_results_text += act.get().getString(R.string.edit_account_smtp2) + ", ";
                    break;
                }
                case 4: {
                    test_results_text += (test_results[4].startsWith("220")) ? "✓" : "⨯";
                    test_results_text += act.get().getString(R.string.edit_account_smtp3) + ", ";
                    break;
                }
                case 5: {
                    test_results_text += (test_results[5].toLowerCase().contains("ready")) ? "✓" : "⨯";
                    test_results_text += act.get().getString(R.string.edit_account_imap2) + ", ";
                    break;
                }
                case 6: {
                    test_results_text += (test_results[6].toLowerCase().contains("ready")) ? "✓" : "⨯";
                    test_results_text += act.get().getString(R.string.edit_account_pop2);
                    break;
                }
            }
            handler_ui.post(this::set_text_results); // Show results in UI

            if (!over) next_test();
        };
    }

    private void set_progress(int a, int b) {
        progress_msg = "";
        switch (b) {
            case 0:
                progress_msg = "1/7\t" + act.get().getString(R.string.edit_account_checking)
                                + ".." + act.get().getString(R.string.edit_account_smtp1);
                test_results_text = "";
                handler_ui.post(this::set_text_results);
                break;
            case 1:
                progress_msg = "2/7\t" + act.get().getString(R.string.edit_account_checking)
                                + ".." + act.get().getString(R.string.edit_account_pop1);
                break;
            case 2:
                progress_msg = "3/7\t" + act.get().getString(R.string.edit_account_checking)
                                + ".." + act.get().getString(R.string.edit_account_imap1);
                break;
            case 3:
                progress_msg = "4/7\t" + act.get().getString(R.string.edit_account_checking)
                                + ".." + act.get().getString(R.string.edit_account_smtp2);
                break;
            case 4:
                progress_msg = "5/7\t" + act.get().getString(R.string.edit_account_checking)
                                + ".." + act.get().getString(R.string.edit_account_smtp3);
                break;
            case 5:
                progress_msg = "6/7\t" + act.get().getString(R.string.edit_account_checking)
                                + ".." + act.get().getString(R.string.edit_account_imap2);
                break;
            case 6:
                progress_msg = "7/7\t" + act.get().getString(R.string.edit_account_checking)
                                + ".." + act.get().getString(R.string.edit_account_pop2);
                break;
        }

        if (tv_message == null) // get here, because null in constructor
            tv_message = test_server_dialog.findViewById(android.R.id.message);

        tv_message.post(() -> test_server_dialog.setMessage(progress_msg));

        pb.post(() -> pb.setProgress(a));
    }

    public void set_text_results() {
        tv_results.post(() -> tv_results.setText(test_results_text));
    }
}