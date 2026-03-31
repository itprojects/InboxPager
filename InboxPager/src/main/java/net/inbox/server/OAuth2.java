/*
 * InboxPager, an Android email client.
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

import android.content.Context;
import android.util.Base64;

import net.inbox.InboxPager;
import net.inbox.db.Inbox;
import net.inbox.pager.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class OAuth2 {

    private static final String oauth2_code_verifier_character_space =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"; // 66

    // Obtains access token from server, using refresh token,
    // saves it into the account, to be later written to database
    public static String obtain_access_token(Context ctx, Inbox current) {
        String client_id = current.get_oauth2_client_id();
        String client_secret = current.get_oauth2_client_secret();
        String refresh_token = current.get_oauth2_refresh_token();
        String token_endpoint = current.get_oauth2_token_endpoint();

        // Check for sane configuration
        String s_error = null;
        if (client_id == null || client_id.isEmpty()) {
            s_error = ctx.getString(R.string.oauth2_bad_parameter) +
                ctx.getString(R.string.oauth2_client_id);
            return s_error;
        }

        //if (client_secret == null || client_secret.isEmpty()) { // often required
        //    s_error = ctx.getString(R.string.oauth2_bad_parameter) +
        //        ctx.getString(R.string.oauth2_client_secret);
        //    return s_error;
        //}

        if (refresh_token == null || refresh_token.isEmpty()) {
            s_error = ctx.getString(R.string.oauth2_bad_parameter) +
                ctx.getString(R.string.oauth2_refresh_token);
            return s_error;
        }

        if (token_endpoint == null || token_endpoint.isEmpty()) {
            s_error = ctx.getString(R.string.oauth2_bad_parameter) +
                ctx.getString(R.string.oauth2_token_endpoint);
            return s_error;
        }

        HttpsURLConnection connection = null;
        try {
            URL url = new URI(current.get_oauth2_token_endpoint()).toURL();

            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST"); // Set request method
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setConnectTimeout(15000); // 15 seconds
            connection.setReadTimeout(15000); // 15 seconds
            connection.setDoInput(true);
            connection.setDoOutput(true);

            Map<String,String> map_query_params = new LinkedHashMap<>();
            map_query_params.put("client_id", client_id);
            if (client_secret !=null && !client_secret.isEmpty())
                map_query_params.put("client_secret", client_secret);
            map_query_params.put("refresh_token", refresh_token);
            map_query_params.put("grant_type", "refresh_token");
            String s_params = map_to_urlencoded(map_query_params);

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

                String[] result = response_to_refresh_token(s_response.toString());
                if (result.length == 2) {
                    current.set_oauth2_access_token(result[0]); // access token
                    current.set_oauth2_access_token_expires_in(result[1]); // timestamp
                } else s_error = ctx.getString(R.string.oauth2_cannot_parse) + "\n\n" + result[0];
            } else {
                buffered_reader = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream())
                );

                while ((line = buffered_reader.readLine()) != null) {
                    s_response.append(line);
                }

                throw new Exception(
                    ctx.getString(R.string.oauth2_error_in_https) + " " + response_code + " " + s_response
                );
            }

            buffered_reader.close();
        } catch (Exception e) {
            s_error = ctx.getString(R.string.oauth2_error_access_token);
            InboxPager.log = InboxPager.log.concat(
                current.get_email() + ":\n\n" + s_error + " " + e.getMessage() + "\n\n"
            );
        } finally {
            if (connection != null)
                connection.disconnect(); // ensure disconnection
        }

        return s_error;
    }

    // Extracts access token from OAuth2 refresh token response
    public static String[] response_to_refresh_token(String raw_response) {
        JSONObject json_response = parse_raw_response_to_json(raw_response);
        if (json_response == null) return new String[]{ "no json" };
        if (json_response.has("access_token")) {
            try {
            String access_token = json_response.getString("access_token");
                if (json_response.has("expires_in")) {
                    return new String[]{
                        access_token,
                        String.valueOf(
                            duration_to_long(json_response.getString("expires_in"))
                        )
                    };
                } else {
                    return new String[]{ "no expires_in" };
                }
            } catch (JSONException je) {
                return new String[]{ je.getMessage() };
            }
        } else {
            return new String[]{ "no access_token" };
        }
    }

    public static JSONObject parse_raw_response_to_json(String raw_response) {
        if (raw_response == null || raw_response.isEmpty())
            return null;

        int i_json_start = raw_response.lastIndexOf("{");
        int i_json_end = raw_response.lastIndexOf("}");

        if (i_json_start != -1 && i_json_end != -1) {
            try {
                return new JSONObject(raw_response.substring(i_json_start, i_json_end + 1));
            } catch (JSONException je) {
                InboxPager.log = InboxPager.log.concat(je.getMessage() + "\n\n");
                return null;
            }
        } else {
            InboxPager.log = InboxPager.log.concat("no response" + "\n\n");
            return null;
        }
    }

    // String duration to numerical (long) duration
    // System.currentTimeMillis() is expected in UTC milliseconds
    public static long duration_to_long(String duration) {
        if (duration == null || duration.isEmpty())
            return 0;
        return System.currentTimeMillis() + 1000 * Long.parseLong(duration);
    }

    // Returns true if the token is still valid
    public static boolean is_token_valid(String timestamp) {
        if (timestamp == null || timestamp.isEmpty())
            return false;
        return Long.parseLong(timestamp) > System.currentTimeMillis();
    }

    public static String xoauth2_string(String account_email, String access_token) {
        // user={user}\1auth=Bearer {token}\1\1
        // \1 is hex code for Control + A (ASCII 1)
        String raw = "user=" + account_email + "\001auth=Bearer " + access_token + "\001\001";

        // Encode the string to Base64 using UTF-8 charset, Base64.NO_WRAP makes it single line.
        return Base64.encodeToString(raw.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    public static String map_to_urlencoded(Map<String, String> map) {
        StringBuilder sb_url_encoded = new StringBuilder();
        try{
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String v = entry.getValue();
                if (v != null && !v.isEmpty()) {
                    if (sb_url_encoded.length() > 0)
                        sb_url_encoded.append("&");
                    sb_url_encoded.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                    sb_url_encoded.append("=");
                    sb_url_encoded.append(URLEncoder.encode(v, "UTF-8"));
                }
            }
        } catch (UnsupportedEncodingException e) {
            String s_error = e.getMessage();
            InboxPager.log = InboxPager.log.concat(s_error + "\n\n");
            return null;
        }
        return sb_url_encoded.toString();
    }

    // Code verifier string from 48 to 128 characters long is used
    // to create an authenticated server and client connection
    public static String oauth2_code_verifier_string() {
        int n_min = 43;
        int n_max = 128;

        // Code verifier length
        int n_chars = i_math_random_length(n_min, n_max);
        SecureRandom secure_random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0;i < n_chars;++i) {
            sb.append(
                oauth2_code_verifier_character_space.charAt(
                    i_math_random_oauth2(secure_random)
                )
            );
        }

        return sb.toString();
    }

    // Generate random ints, between lower and upper bounds
    private static int i_math_random_length(int n_min, int n_max) {
        return (int)((Math.random() * (n_max - n_min)) + n_min);
    }

    private static int i_math_random_oauth2(SecureRandom secure_random) {
        return (int)(secure_random.nextFloat() * 65); // 65 index is char 66
    }

    // Create a PKCE S256 code challenge, given a verifier string
    public static String oauth2_code_challenge_string(String verifier) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            return null;
        }
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(hash, Base64.NO_WRAP|Base64.NO_PADDING|Base64.URL_SAFE);
    }

    public static String create_auth_url(
        int available_port,
        String username,
        String verifier,
        String client_id,
        String scopes,
        String auth_endpoint,
        String extras
    ) {
        String code_challenge = OAuth2.oauth2_code_challenge_string(verifier);

        Map<String,String> map_query_params = new LinkedHashMap<>();
        map_query_params.put("client_id", client_id);
        if (username != null && !username.isEmpty())
            map_query_params.put("login_hint", username);
        map_query_params.put("response_type", "code");
        map_query_params.put("redirect_uri", "http://localhost:" + available_port + "/");
        map_query_params.put("code_challenge", code_challenge);
        map_query_params.put("code_challenge_method", "S256");
        map_query_params.put("scope", scopes);

        // URL encode extra parameters
        if (extras != null && !extras.isEmpty()) {
            String[] parts = extras.split("&");
            for (String s : parts) {
                String[] kv = s.split("=", 2);
                if (kv.length > 1)
                    map_query_params.put(kv[0], kv[1]);
            }
        }

        // Create copyable URL text
        return auth_endpoint + "?" + OAuth2.map_to_urlencoded(map_query_params);
    }

    public static String auth_code_for_refresh_token(
        int available_port,
        String client_id,
        String client_secret,
        String scopes,
        String verifier
    ) {
        Map<String,String> map_query_params = new LinkedHashMap<>();
        map_query_params.put("client_id", client_id);
        if (!client_secret.isEmpty())
            map_query_params.put("client_secret", client_secret);
        map_query_params.put("scope", scopes);
        map_query_params.put("redirect_uri", "http://localhost:" + available_port + "/");
        map_query_params.put("grant_type", "authorization_code");
        map_query_params.put("code_verifier", verifier);

        // Create refresh token request
        return map_to_urlencoded(map_query_params);
    }
}
