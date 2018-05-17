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

import net.inbox.Pager;
import net.inbox.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.cert.X509Certificate;

class SocketIO implements Runnable {

    private Context ctx;

    private BufferedReader r;
    private PrintWriter w;
    private SSLSocket s;

    private int port;
    private String server;
    private Handler handler;

    SocketIO(String srv, int prt, IMAP hand, Context ct) {
        server = srv;
        port = prt;
        handler = hand;
        ctx = ct;
    }

    SocketIO(String srv, int prt, POP hand, Context ct) {
        server = srv;
        port = prt;
        handler = hand;
        ctx = ct;
    }

    SocketIO(String srv, int prt, SMTP hand, Context ct) {
        server = srv;
        port = prt;
        handler = hand;
        ctx = ct;
    }

    public void run() {
        try {
            SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            s = (SSLSocket) sf.createSocket(server, port);
            HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
            if (hv.verify(server, s.getSession())) {
                try {
                    StringBuilder sb = new StringBuilder();
                    w = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
                    r = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    int i;
                    boolean cr = false;
                    while ((i = r.read()) != -1) {
                        if (i == 10 && cr) {
                            sb.append((char) i);
                            sb.deleteCharAt(sb.length() - 1);
                            sb.deleteCharAt(sb.length() - 1);
                            handler.reply(sb.toString());
                            sb.setLength(0);
                        } else if (i == 13) {
                            cr = true;
                            sb.append((char) i);
                        } else {
                            cr = false;
                            sb.append((char) i);
                        }
                    }
                } catch (IOException ee) {
                    if (r != null) r.close();
                }
                if (w != null) w.close();
                if (r != null) r.close();
                if (s != null && !s.isClosed()) s.close();
            } else {
                closing();
                handler.last_connection_hostname = false;
                throw new javax.net.ssl.SSLException("'"+ server + "' != '"
                        + s.getSession().getPeerHost() + "'");
            }
        } catch (Exception e) {
            Pager.log += ctx.getString(R.string.ex_field) + e.getMessage() + "\n\n";
            handler.excepted = true;
            handler.error_dialog(e);
        }
    }

    void closing() {
        try {
            if (s != null && !s.isClosed()) s.close();
        } catch (IOException e) {
            Pager.log += ctx.getString(R.string.ex_field) + e.getMessage() + "\n\n";
        }
    }

    boolean closed_already() {
        return (s == null || s.isClosed());
    }

    public boolean write(String l) {
        if (w == null || r == null || s == null) return false;
        w.print(l + "\r\n");
        w.flush();
        return true;
    }

    ArrayList<String[]> print() {
        SSLSession session_0 = s.getSession();
        X509Certificate[] certs = new X509Certificate[1];

        try {
            certs = session_0.getPeerCertificateChain();
        } catch (SSLPeerUnverifiedException ee) {
            Pager.log += ctx.getString(R.string.ex_field) + ee.getMessage() + "\n\n";
        }

        ArrayList<String[]> list = new ArrayList<>();
        list.add(new String[]{ "-" });
        for (X509Certificate cert : certs) {
            String[] aa = cert.getIssuerDN().getName().split(",");
            String[] lst = new String[] { "", "", "", "", "", "", "", ""};
            for (String aaa : aa) {
                if (aaa != null) {
                    String[] bb = aaa.split("=");
                    String cc = bb[0].trim();
                    switch (cc) {
                        case "CN":
                            lst[0] = bb[1].trim();
                            break;
                        case "O":
                            lst[1] = bb[1].trim();
                            break;
                        case "OU":
                            lst[2] = bb[1].trim();
                            break;
                        case "L":
                            lst[3] = bb[1].trim();
                            break;
                        case "ST":
                            lst[4] = bb[1].trim();
                            break;
                        case "C":
                            lst[5] = bb[1].trim();
                            break;
                    }
                }
            }

            lst[6] = cert.getSigAlgName();
            lst[7] = String.valueOf(((RSAPublicKey)cert.getPublicKey()).getModulus().bitLength());
            list.add(lst);
        }
        return list;
    }
}
