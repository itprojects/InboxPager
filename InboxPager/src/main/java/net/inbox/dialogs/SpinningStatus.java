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
package net.inbox.dialogs;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;

import net.inbox.InboxMessage;
import net.inbox.InboxSend;
import net.inbox.InboxPager;
import net.inbox.server.Handler;

import java.lang.ref.WeakReference;

public class SpinningStatus extends AsyncTask<Void, String, Void> {

    public boolean unblock;

    private boolean call_back;
    private boolean call_cancel;
    private boolean mass_refresh;

    private WeakReference<AppCompatActivity> act;
    private ProgressDialog pd;
    private Handler handler;

    public SpinningStatus(boolean cb, boolean mr, AppCompatActivity at, Handler hand) {
        call_back = cb;
        mass_refresh = mr;
        act = new WeakReference<>(at);
        handler = hand;
    }

    /**
     * Runs on UI Thread.
     **/
    @Override
    protected void onPreExecute() {
        AppCompatActivity appact = act.get();
        if (appact != null) {
            pd = new ProgressDialog(appact);
            pd.setTitle("?");
            pd.setMessage("?");
            pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            pd.setIndeterminate(true);
            pd.setProgress(0);
            pd.setMax(100);
            pd.setCancelable(false);
            String cnc = appact.getResources().getString(android.R.string.cancel);
            pd.setButton(ProgressDialog.BUTTON_NEGATIVE, cnc, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    unblock = true;
                    call_cancel = true;
                }
            });
            pd.show();
        }
    }

    /**
     * Runs on NON-UI Thread.
     **/
    @Override
    protected Void doInBackground(Void... voids) {
        while (!unblock) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (call_cancel) {
            if (handler != null) handler.cancel_action();
        }

        return null;
    }

    /**
     * Runs on UI Thread.
     **/
    @Override
    public void onProgressUpdate(String... msg) {
        if (!msg[0].equals("-1")) pd.setTitle(msg[0]);
        pd.setMessage(msg[1]);
    }

    /**
     * Runs on UI Thread.
     **/
    @Override
    protected void onPostExecute(Void voids) {
        try {
            if (pd != null && pd.isShowing()) pd.dismiss();
        } catch (Exception e) {
            InboxPager.log += this.getClass().getName() + e.getMessage() + "\n\n";
        } finally {
            pd = null;
        }

        // Restore device screen orientation after operation
        AppCompatActivity appact = act.get();
        if (appact != null) {
            if (act.getClass().toString().endsWith(".InboxPager")) {
                ((InboxPager) appact).handle_orientation(false);
            } else if (act.getClass().toString().endsWith(".InboxMessage")) {
                ((InboxMessage) appact).handle_orientation(false);
            } else if (act.getClass().toString().endsWith(".InboxSend")) {
                ((InboxSend) appact).handle_orientation(false);
            }
        }

        // Continue process after async task
        if (call_back) {
            if (appact != null) {
                if (act.getClass().toString().endsWith(".InboxPager")) {
                    if (mass_refresh) {
                        // Refresh the account list
                        ((InboxPager) appact).mass_refresh();
                    } else {
                        // Set server certificate details
                        ((InboxPager) appact).connection_security();
                        // Refresh the message list
                        ((InboxPager) appact).populate_messages_list_view();
                    }
                } else if (act.getClass().toString().endsWith(".InboxMessage")) {
                    // Set server certificate details
                    ((InboxMessage) appact).connection_security();
                } else if (act.getClass().toString().endsWith(".InboxSend")) {
                    // Set server certificate details
                    ((InboxSend) appact).connection_security();
                }
            }
        }
    }
}
