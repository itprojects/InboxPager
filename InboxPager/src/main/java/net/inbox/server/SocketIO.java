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

import android.content.Context;

import net.inbox.InboxPager;
import net.inbox.pager.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.cert.CertificateEncodingException;
import javax.security.cert.X509Certificate;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

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
            if (!hv.verify(server, s.getSession())) {
                InboxPager.log = InboxPager.log.concat(ctx.getString(R.string.ex_field)
                        + "Possibly Unverified Host: '" + server + "' != '"
                        + s.getSession().getPeerHost() + "'" + "\n\n");
            }

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
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(ctx.getString(R.string.ex_field) + e.getMessage() + "\n\n");
            handler.excepted = true;
            handler.error_dialog(e);
        }
    }

    void closing() {
        try {
            if (s != null && !s.isClosed()) s.close();
        } catch (IOException e) {
            InboxPager.log = InboxPager.log.concat(ctx.getString(R.string.ex_field) + e.getMessage() + "\n\n");
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

    String print() {
        String lb = "";
        SSLSession session_0 = s.getSession();

        try {
            lb = session_0.getPeerHost() + ":" + session_0.getPeerPort() + "\n\n";
            for (X509Certificate cert : session_0.getPeerCertificateChain()) {
                lb = lb.concat("\n\uD83D\uDCDC"
                        + cert.getIssuerDN().getName() + "\n\n"
                        + getKeyLength(cert.getPublicKey())
                        + cert.getSigAlgName() + "\n"
                        + "SHA-256:\n\n" + sha256Hex(cert.getEncoded()).toUpperCase() + "\n\n");
            }

            lb = lb.replaceAll("(?i)(CN=|O=|OU=|L=|ST=|C=)", "\n");
        } catch (SSLPeerUnverifiedException | CertificateEncodingException ee) {
            InboxPager.log = InboxPager.log.concat(ctx.getString(R.string.ex_field) + ee.getMessage() + "\n\n");
        }

        return lb;
    }

    // See About Activity for licenses and links
    // (how-to-determine-length-of-x509-public-key)
    private String getKeyLength(PublicKey pk) {
        if (pk instanceof RSAPublicKey) {
            return ((RSAPublicKey) pk).getModulus().bitLength() + " bit RSA Public Key\n";
        } else if (pk instanceof ECPublicKey) {
            java.security.spec.ECParameterSpec pk_spec = ((ECPublicKey) pk).getParams();

            return pk_spec == null ? "? Public Key\n" : pk_spec.getOrder().bitLength()
                    + " bit Elliptic Curve Public Key\n";
        } else if (pk instanceof DSAPublicKey) {
            DSAPublicKey pk_dsa = (DSAPublicKey) pk;

            return pk_dsa.getParams() == null ? pk_dsa.getY().bitLength() + " bit DSA Public Key\n"
                    : pk_dsa.getParams().getP().bitLength() + " bit DSA Public Key\n";
        } else return "? Public key\n";
    }
}
