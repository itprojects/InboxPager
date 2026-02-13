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
package net.inbox.visuals;

import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import net.inbox.Common;
import net.inbox.InboxMessage;
import net.inbox.InboxPager;
import net.inbox.InboxSend;
import net.inbox.server.NetworkThread;

import java.lang.ref.WeakReference;

public class SpinningStatus {

    private boolean call_back;
    private boolean mass_refresh;

    private NetworkThread newtowrk_thread;
    private AlertDialog pd;
    private Handler handler_ui;
    private WeakReference<AppCompatActivity> act;

    public SpinningStatus(boolean cb, boolean mr, AppCompatActivity at, NetworkThread net_thread) {
        call_back = cb;
        mass_refresh = mr;
        act = new WeakReference<>(at);
        newtowrk_thread = net_thread;

        handler_ui = new Handler(Looper.getMainLooper());

        ProgressBar pb = new ProgressBar(at);
        pb.setProgress(0);
        pb.setMax(100);

        // Create dialog
        pd = new AlertDialog.Builder(at)
            .setTitle("?")
            .setMessage("?")
            .setNegativeButton(
                at.getString(android.R.string.cancel),
                (dialog, id) -> {
                    dialog.dismiss();
                    if (newtowrk_thread != null) newtowrk_thread.cancel_action();
                }
            )
            .setCancelable(false).setView(pb).create();

        pd.show();
    }

    public void set_progress(String... msg) {
        if (!msg[0].equals("-1")) pd.setTitle(msg[0]);
        pd.setMessage(msg[1]);
    }

    public void do_after() {
        try {
            if (pd != null && pd.isShowing()) pd.dismiss();
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(
                this.getClass().getName() + e.getMessage() + "\n\n"
            );
        } finally {
            pd = null;
        }

        if (act.get() != null) {
            final String class_name = act.get().getClass().toString();

            // Restore device screen orientation after operation
            if (class_name.endsWith(".InboxPager") || class_name.endsWith(".InboxMessage") ||
                class_name.endsWith(".InboxSend")
            ) {
                handler_ui.post(
                    () -> Common.fixed_or_rotating_orientation(false, act.get())
                );
            }

            // Continue process after async task
            if (call_back) {
                if (class_name.endsWith(".InboxPager")) {
                    if (mass_refresh) {
                        // Refresh the account list
                        handler_ui.post(() -> ((InboxPager) act.get()).mass_refresh());
                    } else {
                        handler_ui.post(
                            () -> {
                                // Set server certificate details
                                ((InboxPager) act.get()).connection_security();
                                // Refresh the message list
                                ((InboxPager) act.get()).populate_messages_list_view();
                            }
                        );
                    }
                } else if (class_name.endsWith(".InboxMessage")) {
                    // Set server certificate details
                    handler_ui.post(() -> ((InboxMessage) act.get()).connection_security());
                } else if (class_name.endsWith(".InboxSend")) {
                    // Set server certificate details
                    handler_ui.post(() -> ((InboxSend) act.get()).connection_security());
                }
            }
        }
    }
}
