/**
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

import net.inbox.Pager;
import net.inbox.R;
import net.inbox.dialogs.Dialogs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.security.interfaces.RSAPublicKey;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.cert.X509Certificate;

public class SocketIO implements Runnable {

    private Context ctx;

    private BufferedReader r;
    private PrintWriter w;
    private SSLSocket s;

    private int port;
    private String server;
    private Handler handler;

    public SocketIO(String srv, int prt, IMAP hand, Context ct) {
        server = srv;
        port = prt;
        handler = hand;
        ctx = ct;
    }

    public SocketIO(String srv, int prt, POP hand, Context ct) {
        server = srv;
        port = prt;
        handler = hand;
        ctx = ct;
    }

    public SocketIO(String srv, int prt, SMTP hand, Context ct) {
        server = srv;
        port = prt;
        handler = hand;
        ctx = ct;
    }

    public void run() {
        try {
            SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            s = (SSLSocket) sf.createSocket(server, port);
            try {
                StringBuilder sb = new StringBuilder();
                w = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
                r = new BufferedReader(new InputStreamReader(s.getInputStream()));
                int i;
                boolean cr = false;
                while ((i = r.read()) != -1) {
                    if (i == 10 && cr) {
                        sb.append((char)i);
                        sb.deleteCharAt(sb.length() - 1);
                        sb.deleteCharAt(sb.length() - 1);
                        handler.reply(sb.toString());
                        sb.setLength(0);
                    } else if (i == 13) {
                        cr = true;
                        sb.append((char)i);
                    } else {
                        cr = false;
                        sb.append((char)i);
                    }
                }
            } catch (IOException ee) {
                //System.out.println("Socket closed already.");
            } finally {
                if (r != null) r.close();
            }
            if (w != null) w.close();
            if (r != null) r.close();
            if (s != null && !s.isClosed()) s.close();
        } catch (Exception e) {
            handler.excepted = true;
            Pager.log += ctx.getString(R.string.ex_field) + e.getMessage() + "\n";
            Dialogs.dialog_exception(e, (AppCompatActivity) ctx);
        }
    }

    public void closing() {
        try {
            if (s != null && !s.isClosed()) s.close();
        } catch (IOException e) {
            System.out.println("Socket closed already. No closing().");
            Pager.log += ctx.getString(R.string.ex_field) + e.getMessage() + "\n";
        }
    }

    public boolean write(String l) {
        if (w == null || r == null || s == null) return false;
        w.print(l + "\r\n");
        w.flush();
        return true;
    }

    public String printSocket() {
        SSLSession session_0 = s.getSession();
        X509Certificate[] certs = new X509Certificate[1];

        try {
            certs = session_0.getPeerCertificateChain();
        } catch (SSLPeerUnverifiedException ee) {
            System.out.println("Peer Unverified Exception: " + ee);
            Pager.log += ctx.getString(R.string.ex_field) + ee.getMessage() + "\n";
        }

        String list = "";
        for (X509Certificate cert : certs) {
            String[] aa = cert.getIssuerDN().getName().split(",");

            String name = "\n\n";
            String ssl;
            String str = "";

            for (String aaa : aa) {
                if (aaa != null) {
                    String[] bb = aaa.split("=");
                    String cc = bb[0].trim();
                    switch (cc) {
                        case "CN":
                            name += ctx.getString(R.string.ssl_auth_popup_cn) + "\n" + bb[1].trim();
                            break;
                        case "O":
                            str += ctx.getString(R.string.ssl_auth_popup_o) + "    " + bb[1].trim();
                            break;
                        case "OU":
                            str += ctx.getString(R.string.ssl_auth_popup_ou) + "    " + bb[1].trim();
                            break;
                        case "L":
                            str += ctx.getString(R.string.ssl_auth_popup_l) + "    " + bb[1].trim();
                            break;
                        case "ST":
                            str += ctx.getString(R.string.ssl_auth_popup_st) + "    " + bb[1].trim();
                            break;
                        case "C":
                            str += ctx.getString(R.string.ssl_auth_popup_c) + "    " + bb[1].trim();
                            break;
                    }
                    str += "\n";
                }
            }

            ssl = "\n" + ctx.getString(R.string.ssl_auth_popup_transport) + "    "
                    + cert.getSigAlgName() + "\n" + ctx.getString(R.string.ssl_auth_popup_key_size)
                    + "    " + ((RSAPublicKey)cert.getPublicKey()).getModulus().bitLength();

            list += (name + ssl + str);
        }
        return list;
    }
}
