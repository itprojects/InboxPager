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
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;

import net.inbox.InboxMessage;
import net.inbox.InboxSend;
import net.inbox.InboxUI;
import net.inbox.Pager;
import net.inbox.R;
import net.inbox.server.Handler;

public class SpinningStatus extends AsyncTask<Void, String, Void> {

    public boolean unblock;

    private boolean call_back;
    private boolean call_cancel;

    private AppCompatActivity act;
    private ProgressDialog pd;
    private Handler handler;

    public SpinningStatus(boolean cb, AppCompatActivity at, Handler hand) {
        call_back = cb;
        act = at;
        handler = hand;
    }

    /**
     * Runs on UI Thread.
     **/
    @Override
    protected void onPreExecute() {
        pd = new ProgressDialog(act);
        pd.setTitle("?");
        pd.setMessage(act.getString(R.string.progress_refreshing));
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.setIndeterminate(true);
        pd.setProgress(0);
        pd.setMax(100);
        pd.setCancelable(false);
        String cnc = act.getResources().getString(android.R.string.cancel);
        pd.setButton(ProgressDialog.BUTTON_NEGATIVE, cnc, (dialog, which) -> {
            unblock = true;
            call_cancel = true;
        });
        pd.show();
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
        pd.dismiss();
        if (call_back) {
            if (act.getClass().toString().endsWith(".Pager")) {
                // Refresh the account list
                ((Pager) act).mass_refresh();
            } else if (act.getClass().toString().endsWith(".InboxMessage")) {
                // Set server certificate details
                ((InboxMessage) act).connection_security();
            } else if (act.getClass().toString().endsWith(".InboxSend")) {
                // Set server certificate details
                ((InboxSend) act).connection_security();
            } else if (act.getClass().toString().endsWith(".InboxUI")) {
                // Refresh the message list
                ((InboxUI) act).populate_list_view();
            }
        }
    }
}
