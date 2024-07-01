/*
 * InboxPager, an android email client.
 * Copyright (C) 2016-2024  ITPROJECTS
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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import androidx.appcompat.app.AlertDialog;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.inbox.InboxPager;
import net.inbox.pager.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class Test extends AsyncTask<Void, Integer, Void> {

    private String[] test_results = new String[] { ".", ".", ".", ".", ".", ".", "."};
    private String server_name;
    private int[] ports = new int[] { 25, 110, 143, 465, 587, 993, 995 };
    private BufferedReader r = null;
    private boolean over = false;

    private ProgressDialog pd;

    // Prevent context leaks
    private WeakReference<Context> ctx;

    public Test(String s, Context ct) {
        server_name = s;
        ctx = new WeakReference<>(ct);
    }

    /**
     * Runs on UI Thread
     **/
    @Override
    protected void onPreExecute() {
        // Spinning wheel server checks
        pd = new ProgressDialog(ctx.get());
        pd.setTitle(server_name);
        pd.setMessage(ctx.get().getString(R.string.progress_refreshing));
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.setIndeterminate(true);
        pd.setProgress(0);
        pd.setMax(100);
        pd.setCancelable(false);
        String cnc = ctx.get().getString(android.R.string.cancel);
        pd.setButton(ProgressDialog.BUTTON_NEGATIVE, cnc, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                publishProgress(100);
                over = true;
            }
        });
        pd.show();
    }

    /**
     * Runs on NON-UI Thread
     **/
    @Override
    protected Void doInBackground(Void... params) {
        if (!over) {
            for(int i = 0; i < 7;++i) {
                switch (i) {
                    case 0:
                        publishProgress(14*i, i);
                        test(false, ports[0], i);
                        break;
                    case 1:
                        publishProgress(14*i, i);
                        test(false, ports[1], i);
                        break;
                    case 2:
                        publishProgress(14*i, i);
                        test(false, ports[2], i);
                        break;
                    case 3:
                        publishProgress(14*i, i);
                        test(true, ports[3], i);
                        break;
                    case 4:
                        publishProgress(14*i, i);
                        test(false, ports[4], i);
                        break;
                    case 5:
                        publishProgress(14*i, i);
                        test(true, ports[5], i);
                        break;
                    case 6:
                        publishProgress(100, i);
                        test(true, ports[6], i);
                        break;
                }
            }
        }
        return null;
    }

    private void test(final Boolean ssl, final int port, final int indx) {
        Runnable rn = new Runnable() {

            @Override
            public void run() {
                try {
                    // SSL socket
                    SSLSocket s = null;
                    // Ordinary socket
                    Socket sn = new Socket();
                    if (ssl) {
                        SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
                        s = (SSLSocket) sf.createSocket(server_name, port);
                    } else {
                        sn = SocketFactory.getDefault().createSocket(server_name, port);
                    }
                    try {
                        if (ssl) {
                            r = new BufferedReader(new InputStreamReader(s.getInputStream()));
                        } else {
                            r = new BufferedReader(new InputStreamReader(sn.getInputStream()));
                        }
                        test_results[indx] = r.readLine();
                    } catch (IOException ioe) {
                        InboxPager.log = InboxPager.log.concat(ioe.getMessage() + "\n\n");
                    }
                    if (r != null) r.close();
                    if (s != null && !s.isClosed()) s.close();
                    if (!sn.isClosed()) sn.close();
                } catch (Exception e) {
                    InboxPager.log = InboxPager.log.concat(ctx.get().getString(R.string.ex_field)
                            + e.getMessage() + "\n\n");
                }
            }
        };
        Thread th = new Thread(rn);
        th.start();
        try {
            // 60 second timeout for response
            th.join(60100);
        } catch (InterruptedException e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
        }
    }

    /**
     * Runs on UI Thread.
     **/
    @Override
    protected void onProgressUpdate(Integer... values) {
        if (pd != null) {
            pd.setProgress(values[0]);
            if (values.length >= 2) {
                switch (values[1]) {
                    case 0:
                        pd.setMessage("1/7\t" + ctx.get().getString(R.string.edit_account_checking)
                                + ".." + ctx.get().getString(R.string.edit_account_smtp1));
                        break;
                    case 1:
                        pd.setMessage("2/7\t" + ctx.get().getString(R.string.edit_account_checking)
                                + ".." + ctx.get().getString(R.string.edit_account_pop1));
                        break;
                    case 2:
                        pd.setMessage("3/7\t" + ctx.get().getString(R.string.edit_account_checking)
                                + ".." + ctx.get().getString(R.string.edit_account_imap1));
                        break;
                    case 3:
                        pd.setMessage("4/7\t" + ctx.get().getString(R.string.edit_account_checking)
                                + ".." + ctx.get().getString(R.string.edit_account_smtp2));
                        break;
                    case 4:
                        pd.setMessage("5/7\t" + ctx.get().getString(R.string.edit_account_checking)
                                + ".." + ctx.get().getString(R.string.edit_account_smtp3));
                        break;
                    case 5:
                        pd.setMessage("6/7\t" + ctx.get().getString(R.string.edit_account_checking)
                                + ".." + ctx.get().getString(R.string.edit_account_imap2));
                        break;
                    case 6:
                        pd.setMessage("7/7\t" + ctx.get().getString(R.string.edit_account_checking)
                                + ".." + ctx.get().getString(R.string.edit_account_pop2));
                        break;
                }
            }
        }
    }

    /**
     * Runs on UI Thread.
     **/
    @Override
    protected void onPostExecute(Void result) {
        if (pd != null) pd.dismiss();
        // Show the complete results
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx.get());
        builder.setTitle(ctx.get().getString(R.string.edit_account_test_results));
        TextView tv_results  = new TextView(ctx.get());
        tv_results.setTextIsSelectable(true);
        String str = (test_results[0].startsWith("220")) ? "\u2713" : "\u274C";
        str += ctx.get().getString(R.string.edit_account_smtp1) + "\n";
        str += (test_results[1].toLowerCase().contains("ready")) ? "\u2713" : "\u274C";
        str += ctx.get().getString(R.string.edit_account_pop1) + "\n";
        str += (test_results[2].toLowerCase().contains("ready")) ? "\u2713" : "\u274C";
        str += ctx.get().getString(R.string.edit_account_imap1) + "\n";
        str += (test_results[3].startsWith("220")) ? "\u2713" : "\u274C";
        str += ctx.get().getString(R.string.edit_account_smtp2) + "\n";
        str += (test_results[4].startsWith("220")) ? "\u2713" : "\u274C";
        str += ctx.get().getString(R.string.edit_account_smtp3) + "\n";
        str += (test_results[5].toLowerCase().contains("ready")) ? "\u2713" : "\u274C";
        str += ctx.get().getString(R.string.edit_account_imap2) + "\n";
        str += (test_results[6].toLowerCase().contains("ready")) ? "\u2713" : "\u274C";
        str += ctx.get().getString(R.string.edit_account_pop2) + "\n";
        tv_results.setText(str);
        tv_results.setPadding(20, 20, 20, 20);
        tv_results.setTextSize(18);
        RelativeLayout rel = new RelativeLayout(ctx.get());
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        rel.addView(tv_results, lp);
        builder.setView(rel);
        builder.setCancelable(true);
        builder.setPositiveButton(ctx.get().getString(android.R.string.ok), null);
        builder.show();
    }
}
