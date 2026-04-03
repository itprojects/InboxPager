/*
 * InboxPager, an Android e-mail client.
 * Copyright (C) 2026  ITPROJECTS
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

import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import net.inbox.Common;
import net.inbox.InboxPager;
import net.inbox.pager.R;
import net.inbox.visuals.Dialogs;
import net.inbox.visuals.OAuth2Preferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class OAuth2AuthServer extends Thread {

    private static final String response_body_template =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>%s</title></head><body><h2>%s</h2><p>%s</p></body></html>";

    public static final String server_ip = "127.0.0.1";
    public static int port = 1234;

    public volatile boolean over = false;

    protected WeakReference<AppCompatActivity> act;

    private ServerSocket server_socket;

    private int current_inbox_id;
    private String https_token_endpoint;
    private String https_request_refresh_token;

    public OAuth2AuthServer(
        AppCompatActivity at,
        int current_inbox_id_,
        int available_port,
        String token_endpoint,
        String request_refresh_token
    ) {
        act = new WeakReference<>(at);
        current_inbox_id = current_inbox_id_;
        port = available_port;
        https_token_endpoint = token_endpoint;
        https_request_refresh_token = request_refresh_token;
    }

    @Override
    public void run() {
        // Part 1, listening for authentication code, by HTTP GET request from email server
        String str_auth_code = null;
        try {
            server_socket = new ServerSocket(port, 0, InetAddress.getByName(server_ip));
            try (Socket client_socket = server_socket.accept()) {
                // Client has connected to the server on this device
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client_socket.getInputStream())
                );
                StringBuilder sb_meta = new StringBuilder();
                String str_http_get_request = null;
                String line;
                while ((line = reader.readLine()) != null) { // read client POST request headers
                    if (sb_meta.length() == 0) {
                        str_http_get_request = line;
                    } else if (line.isEmpty()) {
                        break;
                    }
                    sb_meta.append(line);
                }

                boolean http_get_request = sb_meta.toString().startsWith("GET");

                // Discover auth code, by parsing HTTP GET request
                String response_header_to_auth_code;
                if (http_get_request) {
                    response_header_to_auth_code = "HTTP/1.1 200 OK\r\nContent-Type: text/html;charset=UTF-8\r\n\r\n";
                    int i_start_auth_code = str_http_get_request.indexOf("code=");
                    int i_end_auth_code;
                    if (i_start_auth_code > 0) {
                        i_end_auth_code = str_http_get_request.indexOf(
                            "&", i_start_auth_code + 5
                        );
                        if (i_end_auth_code > 0) {
                            str_auth_code = str_http_get_request.substring(
                                i_start_auth_code + 5, i_end_auth_code
                            );
                        } else {
                            str_auth_code = str_http_get_request.substring(
                                i_start_auth_code + 5
                            );
                        }
                    } else {
                        throw new IOException(
                            act.get().getString(R.string.oauth2_server_failure_auth_code_extraction)
                        );
                    }

                    try {
                        str_auth_code = URLDecoder.decode(
                            str_auth_code, StandardCharsets.UTF_8.name()
                        );
                    } catch (UnsupportedEncodingException e) {
                        response_header_to_auth_code = "HTTP/1.1 400 Bad Request\r\nContent-type: text/html;charset=UTF-8\r\n\r\n;";
                        str_auth_code = null; // failed url decoding
                        String error = e.getMessage();
                        InboxPager.log = InboxPager.log.concat(error + "\n\n");
                        Dialogs.toaster(true, error, act.get());
                    }
                } else {
                    response_header_to_auth_code = "HTTP/1.1 400 Bad Request\r\nContent-type: text/html;charset=UTF-8\r\n\r\n;";
                    Dialogs.dialog_simple(
                        act.get().getString(R.string.err_error),
                        act.get().getString(R.string.oauth2_server_failure_bad_method),
                        act.get()
                    );
                    InboxPager.log = InboxPager.log.concat(
                        act.get().getString(R.string.oauth2_server_failure_bad_method)
                    );
                }

                // Send server (device) response back to client (your email server)
                PrintWriter writer = new PrintWriter(client_socket.getOutputStream());
                writer.print(response_header_to_auth_code);
                writer.flush();
                writer.close();
            }
        } catch (SocketException e) {
            if (!over) // Show real exception
                Dialogs.dialog_exception(e, act.get());
            InboxPager.log = InboxPager.log.concat(e.getMessage() == null ? "" : e.getMessage());
        } catch (IOException e) {
            Dialogs.dialog_exception(e, act.get());
            InboxPager.log = InboxPager.log.concat(e.getMessage() == null ? "" : e.getMessage());
        }

        // Part 2, exchange the email server's authentication code for a refresh token
        if (str_auth_code != null && !str_auth_code.isEmpty() && !over) { // After errors are thrown
            String s_error;
            HttpsURLConnection connection = null;
            try {
                URL url = new URL(https_token_endpoint);

                connection = (HttpsURLConnection) url.openConnection();

                connection.setRequestMethod("POST"); // Set request method
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setConnectTimeout(15000); // 15 seconds
                connection.setReadTimeout(15000); // 15 seconds

                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("Accept", "application/json");

                String s_params = https_request_refresh_token + "&code=" +
                    URLEncoder.encode(str_auth_code, "UTF-8");

                OutputStream output_stream = connection.getOutputStream();
                BufferedWriter buffered_writer = new BufferedWriter(
                    new OutputStreamWriter(output_stream, StandardCharsets.UTF_8)
                );

                buffered_writer.write(s_params);
                buffered_writer.flush();
                buffered_writer.close();
                output_stream.close();

                int response_code = connection.getResponseCode();

                BufferedReader buffered_reader;
                StringBuilder s_response = new StringBuilder();
                String line;
                if (response_code == HttpsURLConnection.HTTP_OK) {
                    buffered_reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                    );

                    while ((line = buffered_reader.readLine()) != null) {
                        s_response.append(line);
                    }

                    JSONObject json_response = OAuth2.parse_raw_response_to_json(s_response.toString());
                    if (json_response == null) { // BAD JSON
                        Dialogs.dialog_simple(
                            act.get().getString(R.string.oauth2_server_failure_title),
                            act.get().getString(R.string.oauth2_server_failure_bad_json),
                            act.get()
                        );
                    } else if (json_response.has("error")) { // ERROR
                        Dialogs.dialog_simple(
                            act.get().getString(R.string.oauth2_server_failure_title),
                            act.get().getString(R.string.oauth2_server_failure_client_sent_errors),
                            act.get()
                        );
                    } else { // OK
                        // Parse response, write to database
                        if (json_response.has("refresh_token")) {
                            String access_token = null;
                            String expires_in = null; // for access_token
                            final String refresh_token = String.valueOf(
                                json_response.get("refresh_token")
                            );
                            String refresh_token_expires_in = null;
                            if (json_response.has("refresh_token_expires_in")) {
                                refresh_token_expires_in = String.valueOf(
                                    OAuth2.duration_to_long(
                                        String.valueOf(
                                            json_response.get("refresh_token_expires_in")
                                        )
                                    )
                                );
                            }
                            if (json_response.has("access_token")) {
                                access_token = String.valueOf(json_response.get("access_token"));
                            }
                            if (json_response.has("expires_in")) {
                                expires_in = String.valueOf(
                                    OAuth2.duration_to_long(
                                        String.valueOf(
                                            json_response.get("expires_in")
                                        )
                                    )
                                );
                            }

                            // Write in database
                            InboxPager.get_db().set_oauth2_auth_jwt(
                                current_inbox_id,
                                access_token,
                                expires_in,
                                refresh_token,
                                refresh_token_expires_in
                            );

                            // Undate refresh token in UI
                            act.get().runOnUiThread(
                                () -> {
                                    OAuth2Preferences at = (OAuth2Preferences) act.get();
                                    at.account_is_modified = true;
                                    ((EditText) at.findViewById(R.id.et_oauth2_refresh_token))
                                        .setText(refresh_token);
                                }
                            );

                            Dialogs.dialog_simple(
                                act.get().getString(R.string.oauth2_server_success_title),
                                act.get().getString(R.string.oauth2_server_success_body),
                                act.get()
                            );
                        } else { // unlikely, but failure
                            throw new Exception(
                                act.get().getString(
                                    R.string.oauth2_server_failure_missing_refresh_token
                                )
                            );
                        }
                    }
                } else {
                    buffered_reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream())
                    );

                    while ((line = buffered_reader.readLine()) != null) {
                        s_response.append(line);
                    }

                    throw new Exception(
                        act.get().getString(R.string.oauth2_error_in_https) + " " +
                            response_code + " " + s_response
                    );
                }
                buffered_reader.close();
            } catch (Exception e) {
                s_error = act.get().getString(R.string.oauth2_error_auth_refresh);
                InboxPager.log = InboxPager.log.concat(s_error + ", " + e.getMessage() + "\n\n");
                Dialogs.dialog_simple(act.get().getString(R.string.err_error), s_error, act.get());
            } finally {
                if (connection != null)
                    connection.disconnect(); // ensure disconnection
            }
        }

        // Restore UI at end of Thread
        act.get().runOnUiThread(
            () -> {
                // Prevents screen rotation crash
                Common.fixed_or_rotating_orientation(false, act.get()); // ROTATING

                // Hide notice
                ((OAuth2Preferences) act.get()).tv_oauth2_request_auth_server.setVisibility(View.GONE);
                ((OAuth2Preferences) act.get()).tv_oauth2_request_auth_note.setVisibility(View.GONE);
                ((OAuth2Preferences) act.get()).tv_oauth2_request_auth_copy_url.setVisibility(View.GONE);
            }
        );
    }

    public static int get_free_port() {
        int free_port;
        try (ServerSocket s = new ServerSocket(0)) {
            free_port = s.getLocalPort();
        } catch (IOException e) {
            String error = e.getMessage();
            InboxPager.log = InboxPager.log.concat(error == null ? "IOException" : error);
            free_port = -1;
        }
        return free_port;
    }

    // Cancel operations
    public void cancel_action() {
        over = true;
        if (server_socket != null && !server_socket.isClosed()) {
            try {
                server_socket.close();
            } catch (IOException e) {
                String error = e.getMessage();
                InboxPager.log = InboxPager.log.concat(error == null ? "" : error);
            }
        }
    }
}
