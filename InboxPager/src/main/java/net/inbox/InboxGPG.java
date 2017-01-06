/**
 * InboxGPG interacts with OpenKeychain encryption package.
 * Copyright (C) 2017  ITPROJECTS
 * Copyright (C) 2013-2015 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package net.inbox;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import net.inbox.dialogs.Dialogs;
import net.inbox.server.Utils;

import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class InboxGPG extends AppCompatActivity {

    public static final int NO_KEY = 0;
    public static final int REQUEST_CODE_CLEARTEXT_SIGN = 9910;
    public static final int REQUEST_CODE_ENCRYPT = 9911;
    public static final int REQUEST_CODE_SIGN_AND_ENCRYPT = 9912;
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY = 9913;
    public static final int REQUEST_CODE_GET_KEY_IDS = 9915;
    public static final int REQUEST_CODE_DETACHED_SIGN = 9916;
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY_DETACHED = 9917;
    public static final int REQUEST_CODE_KEY_PREFERENCE = 9999;

    private static long l_sign_key_id;
    private static long[] rcpt_keys = null;

    private int intent_request_code = 0;

    private boolean msg_ready;
    private boolean msg_encrypted;
    private boolean msg_signed;
    private String msg_contents;// given message
    private String msg_crypto;// encrypted/decrypted message
    private String msg_signature;// signature for verification or return
    private String msg_integrity;// message integrity check

    private String open_pgp_provider = "org.sufficientlysecure.keychain";
    private String[] gpg_actions = null;
    private String[] rcpt_mailboxes = null;
    private ArrayList<String> attachment_paths = null;

    private CheckBox cb_sign_to_self;
    private TextView tv_signing_key;
    private TextView tv_recipients_pick;
    private TextView tv_recipients_count;
    private TextView tv_recipients_list;
    private TextView tv_message;
    private TextView tv_cipher_text;
    private TextView tv_signature;
    private Spinner spinner_gpg_action;

    private OpenPgpServiceConnection open_pgp_service_connection;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.openpgp);

        Toolbar tb = (Toolbar) findViewById(R.id.send_toolbar);
        setSupportActionBar(tb);

        // Find the title
        TextView tv_t;
        for (int i = 0; i < tb.getChildCount(); ++i) {
            int idd = tb.getChildAt(i).getId();
            if (idd == -1) {
                tv_t = (TextView) tb.getChildAt(i);
                tv_t.setTextColor(ContextCompat.getColor(this, R.color.color_title));
                tv_t.setTypeface(Pager.tf);
                break;
            }
        }

        if (getSupportActionBar() != null) {
            String s_title = getString(R.string.open_pgp_dialog_title);
            getSupportActionBar().setTitle(s_title.toUpperCase());
        }

        TextView tv_reset = (TextView) findViewById(R.id.tv_reset);
        tv_reset.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                l_sign_key_id = 0;
                tv_signing_key.setText(getString(R.string.open_pgp_signing_key_cross));
                rcpt_keys = null;
                tv_recipients_pick.setText(getString(R.string.open_pgp_rcpt_key_cross));
                reset_activity();
            }
        });

        TextView tv_ready = (TextView) findViewById(R.id.tv_ready);
        tv_ready.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (msg_ready) {
                    Intent intent_a = getIntent();
                    Bundle b = new Bundle();
                    b.putInt("ret-code", intent_request_code);
                    switch (intent_request_code) {
                        case 91:// encrypt and/or sign
                            msg_crypto = pgp_mime_serialization();
                            b.putString("message-crypto", msg_crypto);
                            break;
                        case 92:// decrypted and verified
                            if (msg_crypto != null) { b.putString("message-crypto", msg_crypto); }
                            b.putString("msg-signature", msg_signature);
                            break;
                        case 93:// verified clear text signature
                            b.putString("msg-signature", msg_signature);
                            break;
                    }
                    intent_a.putExtras(b);
                    setResult(19091, intent_a);
                    finish();
                }
            }
        });

        tv_signing_key = (TextView) findViewById(R.id.tv_pick_sign_key);
        tv_recipients_pick = (TextView) findViewById(R.id.tv_recipients_pick);
        tv_recipients_count = (TextView) findViewById(R.id.tv_recipients_count);
        tv_recipients_list = (TextView) findViewById(R.id.tv_recipients_list);
        tv_message = (TextView) findViewById(R.id.tv_message);
        tv_cipher_text = (TextView) findViewById(R.id.tv_encrypted);
        tv_signature = (TextView) findViewById(R.id.tv_signature);

        // Obtain request code
        intent_request_code = getIntent().getIntExtra("request-code", 0);

        init_ui();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.right_in, R.anim.right_out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Try again after user interaction
        if (resultCode == RESULT_OK) {
            /*
             * The data originally given to one of the methods above, is again
             * returned here to be used when calling the method again after user
             * interaction. The Intent now also contains results from the user
             * interaction, for example selected key ids.
             */
            switch (requestCode) {
                case REQUEST_CODE_CLEARTEXT_SIGN: {
                    cleartext_sign(data);
                    break;
                }
                case REQUEST_CODE_DETACHED_SIGN: {
                    detached_sign(data);
                    break;
                }
                case REQUEST_CODE_ENCRYPT: {
                    encrypt(data);
                    break;
                }
                case REQUEST_CODE_SIGN_AND_ENCRYPT: {
                    sign_and_encrypt(data);
                    break;
                }
                case REQUEST_CODE_DECRYPT_AND_VERIFY: {
                    decrypt_and_verify(data);
                    break;
                }
                case REQUEST_CODE_GET_KEY_IDS: {
                    get_rcpt_keys(data);
                    break;
                }
                case REQUEST_CODE_DECRYPT_AND_VERIFY_DETACHED: {
                    decrypt_and_verify_detached(data);
                    break;
                }
                case REQUEST_CODE_KEY_PREFERENCE: {
                    if (data == null) {
                        l_sign_key_id = NO_KEY;
                        tv_signing_key.setText(R.string.open_pgp_signing_key_cross);
                    } else {
                        l_sign_key_id = data.getLongExtra("sign_key_id", -1);
                        tv_signing_key.setText(R.string.open_pgp_signing_key_check);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (open_pgp_service_connection != null) open_pgp_service_connection.unbindFromService();
    }

    private void init_ui() {
        switch (intent_request_code) {
            case 91:{
                // Encryption options
                gpg_actions = new String[] {
                        getString(R.string.open_pgp_list_items_encrypt),
                        getString(R.string.open_pgp_list_items_sign_encrypt),
                        getString(R.string.open_pgp_list_items_detach_sign)
                };

                // Obtaining recipients
                String rcpt_s = getIntent().getExtras().getString("recipients");
                if (rcpt_s != null && !rcpt_s.isEmpty()) {
                    rcpt_mailboxes = rcpt_s.split(",");
                    for (int i = 0;i < rcpt_mailboxes.length;++i) {
                        rcpt_mailboxes[i] = rcpt_mailboxes[i].trim();
                    }
                    tv_recipients_list.setText(rcpt_s);
                }

                // Message data
                msg_contents = getIntent().getStringExtra("message-data");
                tv_message.setText(msg_contents);
                if (msg_contents != null) {
                    if (msg_contents.length() > 500) {
                        tv_message.setText(msg_contents.substring(0, 500));
                    } else {
                        tv_message.setText(msg_contents);
                    }
                }

                // Obtaining attachments
                attachment_paths = getIntent().getExtras().getStringArrayList("attachments");
                break;
            }
            case 92:{// decrypt and verify signature
                gpg_actions = new String[] {
                        getString(R.string.open_pgp_list_items_decrypt_and_verify)
                };
                msg_encrypted = true;
                msg_contents = getIntent().getStringExtra("message-data");
                if (msg_contents.length() > 500) {
                    tv_cipher_text.setText(msg_contents.substring(0, 500));
                } else tv_cipher_text.setText(msg_contents);
                break;
            }
            case 93:{// clear text verify signature
                gpg_actions = new String[] {
                        getString(R.string.open_pgp_list_items_decrypt_and_verify)
                };
                msg_signed = true;
                msg_signature = getIntent().getStringExtra("signature");
                msg_contents = getIntent().getStringExtra("message-data");
                tv_message.setText(msg_contents);
                if (msg_contents != null) {
                    if (msg_contents.length() > 500) {
                        tv_message.setText(msg_contents.substring(0, 500));
                    } else {
                        tv_message.setText(msg_contents);
                    }
                }
                tv_signature.setText(msg_signature);
                break;
            }
        }

        cb_sign_to_self = (CheckBox) findViewById(R.id.cb_sign_to_self);
        if (msg_encrypted || msg_signed) {
            // Free visual space
            cb_sign_to_self.setVisibility(View.GONE);
            tv_recipients_pick.setVisibility(View.GONE);
            TextView tv_1 = (TextView) findViewById(R.id.tv_pick_sign_key);
            TextView tv_2 = (TextView) findViewById(R.id.tv_recipients);
            TextView tv_3 = (TextView) findViewById(R.id.tv_recipients_list);
            tv_1.setVisibility(View.GONE);
            tv_2.setVisibility(View.GONE);
            tv_3.setVisibility(View.GONE);
        }

        spinner_gpg_action = (Spinner) findViewById(R.id.spinner_gpg_action);
        ArrayAdapter<String> adapt = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, gpg_actions);
        adapt.setDropDownViewResource(android.R.layout.simple_list_item_checked);
        spinner_gpg_action.setAdapter(adapt);

        TextView tv_start = (TextView) findViewById(R.id.tv_start);
        tv_start.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // Reset first
                reset_activity();
                if (msg_signed || msg_encrypted) {
                    open_connection();
                } else {
                    switch (spinner_gpg_action.getSelectedItemPosition()) {
                        case 0:// Encrypt
                            if (l_sign_key_id == NO_KEY) {
                                toaster(getString(R.string.err_pick_signing_key));
                            } else if (rcpt_keys == null) {
                                toaster(getString(R.string.err_pick_rcpt_keys));
                            } else {
                                encrypt(new Intent());
                            }
                            break;
                        case 1:// Sign and encrypt
                            if (l_sign_key_id == NO_KEY) {
                                toaster(getString(R.string.err_pick_signing_key));
                            } else if (rcpt_keys == null) {
                                toaster(getString(R.string.err_pick_rcpt_keys));
                            } else {
                                sign_and_encrypt(new Intent());
                            }
                            break;
                        case 2:// Detached sign (of clear text)
                            if (l_sign_key_id == NO_KEY) {
                                toaster(getString(R.string.err_pick_signing_key));
                            } else {
                                detached_sign(new Intent());
                            }
                            break;
                    }
                }
            }
        });

        tv_signing_key.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                select_signing_key();
            }
        });

        tv_recipients_pick.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                select_rcpt_keys();
            }
        });
    }

    private void reset_activity() {
        if (intent_request_code == 91) {
            tv_cipher_text.setText("");
            tv_signature.setText("");
            msg_crypto = "";
            msg_signature = "";
        }
        if (intent_request_code == 92) {
            tv_message.setText("");
        }
    }

    private void select_signing_key() {
        open_pgp_service_connection = new OpenPgpServiceConnection(
                getApplicationContext(),
                open_pgp_provider,
                new OpenPgpServiceConnection.OnBound() {

                    @Override
                    public void onBound(IOpenPgpService2 service) { get_sign_key_id(new Intent()); }

                    @Override
                    public void onError(Exception e) {
                        Pager.log += e.getMessage() + "\n\n";
                    }
                }
        );
        open_pgp_service_connection.bindToService();
    }

    private void get_sign_key_id(Intent data) {
        data.setAction(OpenPgpApi.ACTION_GET_SIGN_KEY_ID);

        OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
        api.executeApiAsync(data, null, null, new SignKeyCallback(REQUEST_CODE_KEY_PREFERENCE, this));
    }

    private void select_rcpt_keys() {
        open_pgp_service_connection = new OpenPgpServiceConnection(
                getApplicationContext(),
                open_pgp_provider,
                new OpenPgpServiceConnection.OnBound() {

                    @Override
                    public void onBound(IOpenPgpService2 service) { get_rcpt_keys(new Intent()); }

                    @Override
                    public void onError(Exception e) {
                        Pager.log += e.getMessage() + "\n\n";
                    }
                }
        );
        open_pgp_service_connection.bindToService();
    }

    private void get_rcpt_keys(Intent data) {
        data.setAction(OpenPgpApi.ACTION_GET_KEY_IDS);
        data.putExtra(OpenPgpApi.EXTRA_USER_IDS, rcpt_mailboxes);

        OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
        api.executeApiAsync(data, null, null, new ResultsCallback(null, REQUEST_CODE_GET_KEY_IDS));
    }

    /**
     * Inline non-mime pgp messages. Not implemented.
     **/
    public void cleartext_sign(Intent data) {
        data.setAction(OpenPgpApi.ACTION_CLEARTEXT_SIGN);
        data.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, l_sign_key_id);

        InputStream is = get_input_stream(true, true);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
        api.executeApiAsync(data, is, os, new ResultsCallback(os, REQUEST_CODE_CLEARTEXT_SIGN));
    }

    /**
     * Clear text signature PGP/MIME message.
     **/
    public void detached_sign(Intent data) {
        data.setAction(OpenPgpApi.ACTION_DETACHED_SIGN);
        data.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, l_sign_key_id);

        InputStream is = get_input_stream(true, true);

        OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
        api.executeApiAsync(data, is, null, new ResultsCallback(null, REQUEST_CODE_DETACHED_SIGN));
    }

    private void encrypt(Intent data) {
        data.setAction(OpenPgpApi.ACTION_ENCRYPT);
        if (cb_sign_to_self.isChecked()) {
            long[] ll = new long[rcpt_keys.length + 1];
            for (int ii = 0;ii < ll.length;++ii) {
                if (ii == rcpt_keys.length) {
                    ll[ii] = l_sign_key_id;
                } else {
                    ll[ii] = rcpt_keys[ii];
                }
            }
            data.putExtra(OpenPgpApi.EXTRA_KEY_IDS, ll);
        } else {
            data.putExtra(OpenPgpApi.EXTRA_KEY_IDS, rcpt_keys);
        }
        //data.putExtra(OpenPgpApi.EXTRA_USER_IDS, rcpt_mailboxes);
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);

        InputStream is = get_input_stream(true, false);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
        api.executeApiAsync(data, is, os, new ResultsCallback(os, REQUEST_CODE_ENCRYPT));
    }

    public void sign_and_encrypt(Intent data) {
        data.setAction(OpenPgpApi.ACTION_SIGN_AND_ENCRYPT);
        data.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, l_sign_key_id);
        if (cb_sign_to_self.isChecked()) {
            long[] ll = new long[rcpt_keys.length + 1];
            for (int ii = 0;ii < ll.length;++ii) {
                if (ii == rcpt_keys.length) {
                    ll[ii] = l_sign_key_id;
                } else {
                    ll[ii] = rcpt_keys[ii];
                }
            }
            data.putExtra(OpenPgpApi.EXTRA_KEY_IDS, ll);
        } else {
            data.putExtra(OpenPgpApi.EXTRA_KEY_IDS, rcpt_keys);
        }
        //data.putExtra(OpenPgpApi.EXTRA_USER_IDS, rcpt_mailboxes);
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);

        InputStream is = get_input_stream(true, false);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
        api.executeApiAsync(data, is, os, new ResultsCallback(os, REQUEST_CODE_SIGN_AND_ENCRYPT));
    }

    private void decrypt_and_verify(Intent data) {
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);

        InputStream is = get_input_stream(false, false);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
        api.executeApiAsync(data, is, os, new ResultsCallback(os, REQUEST_CODE_DECRYPT_AND_VERIFY));
    }

    public void decrypt_and_verify_detached(Intent data) {
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        if (msg_signature == null) {
            toaster(getString(R.string.open_pgp_failure));
        } else {
            data.putExtra(OpenPgpApi.EXTRA_DETACHED_SIGNATURE, msg_signature.getBytes());
            InputStream is = get_input_stream(false, false);

            OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
            api.executeApiAsync(data, is, null, new ResultsCallback
                    (null, REQUEST_CODE_DECRYPT_AND_VERIFY_DETACHED));
        }
    }

    private void open_connection() {
        open_pgp_service_connection = new OpenPgpServiceConnection(
                getApplicationContext(),
                open_pgp_provider,
                new OpenPgpServiceConnection.OnBound() {

                    @Override
                    public void onBound(IOpenPgpService2 service) {
                        if (intent_request_code == 92) {
                            decrypt_and_verify(new Intent());
                        } else if (intent_request_code == 93) {
                            decrypt_and_verify_detached(new Intent());
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Pager.log += e.getMessage() + "\n\n";
                        toaster(e.getMessage());
                    }
                }
        );
        open_pgp_service_connection.bindToService();
    }

    /**
     * Converts String to ByteArrayInputStream.
     **/
    private InputStream get_input_stream(boolean mime, boolean clear) {
        InputStream is = null;
        try {
            if (mime) {
                if (clear) {
                    msg_crypto = mime_serialization();
                    is = new ByteArrayInputStream(msg_crypto.getBytes("UTF-8"));
                } else {
                    is = new ByteArrayInputStream(mime_serialization().getBytes("UTF-8"));
                }
            } else {
                if (msg_contents == null) return null;
                is = new ByteArrayInputStream(msg_contents.getBytes("UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            Pager.log += e.getMessage() + "\n\n";
            Dialogs.toaster(false, e.getMessage(), this);
        }
        return is;
    }

    /**
     * PGP/MIME serialization.
     **/
    private String mime_serialization() {
        String bounds = Utils.boundary();
        String msg_mime = "Content-type: multipart/mixed;\n boundary=" + "\"" + bounds + "\"\n";

        // Message textual contents
        msg_mime += "\n--" + bounds + "\n";
        msg_mime += "Content-Type: text/plain; charset=\"utf-8\"\n";
        msg_mime += "Content-Transfer-Encoding: 8bit\n\n";
        if (msg_contents != null) msg_mime += msg_contents + "\n--" + bounds;
        if (attachment_paths == null) {
            msg_mime += "--\n";
        } else {
            // Message attachments
            for (int i = 0;i < attachment_paths.size();++i) {
                File ff = new File(attachment_paths.get(i));
                msg_mime += "\n--" + bounds + "\n";
                if (Utils.all_ascii(ff.getName())) {
                    msg_mime += "Content-Type: application/octet-stream; name=\"" + ff.getName() + "\"\n";
                    msg_mime += "Content-Transfer-Encoding: base64\n";
                    msg_mime += "Content-Disposition: attachment; filename=\"" + ff.getName() + "\"\n";
                } else {
                    msg_mime += "Content-Type: application/octet-stream; name*=\""
                            + Utils.to_base64_utf8(ff.getName()) + "\"\n";
                    msg_mime += "Content-Transfer-Encoding: base64\n";
                    String new_name = Utils.content_disposition_name(true, ff.getName());
                    msg_mime += "Content-Disposition: attachment; filename*=" + new_name + "\n";
                }
                msg_mime += "\n";
                ByteArrayOutputStream b_stream = new ByteArrayOutputStream();
                try {
                    InputStream in_stream = new FileInputStream(ff);
                    byte[] bfr = new byte[(int)ff.length()];
                    if ((int)ff.length() > 0) {
                        int t;
                        while ((t = in_stream.read(bfr)) != -1) { b_stream.write(bfr, 0, t); }
                    }
                } catch (IOException e) {
                    Pager.log += getString(R.string.ex_field) + e.getMessage() + "\n\n";
                    Dialogs.toaster(true, e.getMessage(), this);
                }
                msg_mime += new String(Base64.encode(b_stream.toByteArray(), Base64.DEFAULT));
                if (msg_mime.charAt(msg_mime.length() - 1) == '\n') {
                    msg_mime = msg_mime.substring(0, msg_mime.length() - 1);
                }
            }
            msg_mime += "\n--" + bounds + "--\n";
        }

        // Outer boundary shell
        bounds = Utils.boundary();

        msg_mime = "Content-type: multipart/mixed;\n boundary=" + "\"" + bounds + "\"\n"
                + "\n--" + bounds + "\n" + msg_mime;
        msg_mime += "\n--" + bounds + "--\n";

        return msg_mime;
    }

    private String pgp_mime_serialization() {
        String bounds = Utils.boundary();
        String pgp_mime;
        if (spinner_gpg_action.getSelectedItemPosition() == 2) {
            // Creating signed clear text (+/- attachments) pgp/mime
            pgp_mime = "Content-type: multipart/signed; micalg=" + msg_integrity + ";\n"
                    + " protocol=\"application/pgp-signature\";\n"
                    + " boundary=" + "\"" + bounds + "\"\n";
            pgp_mime += "\n--" + bounds + "\n";
            pgp_mime += msg_crypto;
            pgp_mime += "--" + bounds + "\n";
            pgp_mime += "Content-Type: application/pgp-signature\n";
            pgp_mime += "Content-Description: OpenPGP digital signature\n";
            pgp_mime += "Content-Disposition: attachment\n\n";
            pgp_mime += msg_signature;
            pgp_mime += "\n--" + bounds + "--\n";
        } else {
            // Creating signed and/or encrypted pgp/mime
            pgp_mime = "Content-type: multipart/encrypted; protocol=\"application/pgp-encrypted\";"
                    + "\n boundary=" + "\"" + bounds + "\"\n";
            pgp_mime += "\n--" + bounds + "\n";
            pgp_mime += "Content-Type: application/pgp-encrypted\n";
            pgp_mime += "Content-Description: PGP/MIME version identification\n\n";
            pgp_mime += "Version: 1\n";
            pgp_mime += "\n--" + bounds + "\n";
            pgp_mime += "Content-Type: application/octet-stream\n";
            pgp_mime += "Content-Description: OpenPGP encrypted message\n";
            pgp_mime += "Content-Disposition: inline\n\n";
            pgp_mime += msg_crypto;
            pgp_mime += "\n--" + bounds + "--\n";
        }

        return pgp_mime;
    }

    private void toaster(final String msg) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(InboxGPG.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class ResultsCallback implements OpenPgpApi.IOpenPgpCallback {

        int request_code;
        ByteArrayOutputStream os;

        private ResultsCallback(ByteArrayOutputStream bos, int rc) {
            os = bos;
            request_code = rc;
        }

        @Override
        public void onReturn(Intent result) {
            switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                case OpenPgpApi.RESULT_CODE_SUCCESS: {
                    // ENCRYPT|DECRYPT|SIGN|VERIFY
                    if (os != null) {
                        try {
                            switch (intent_request_code) {
                                case 91:
                                    // If encrypting, sign-encrypting
                                    if (spinner_gpg_action.getSelectedItemPosition() != 2) {
                                        // Signing and/or encryption
                                        msg_crypto = os.toString("UTF-8");
                                        if (msg_crypto != null && msg_crypto.length() > 700) {
                                            tv_cipher_text.setText(msg_crypto.substring(0, 700));
                                        } else tv_cipher_text.setText(msg_crypto);
                                    }
                                    msg_ready = true;
                                    break;
                                case 92:
                                    // Encrypted and/or signed, decryption
                                    if (msg_encrypted) {
                                        msg_crypto = os.toString("UTF-8");
                                        if (msg_crypto != null && msg_crypto.length() > 700) {
                                            tv_message.setText(msg_crypto.substring(0, 700));
                                        } else tv_message.setText(msg_crypto);
                                    }
                                    msg_ready = true;
                                case 93:
                                    // Signed clear text verification
                                    msg_ready = true;
                                    break;
                            }
                        } catch (UnsupportedEncodingException e) {
                            Pager.log += e.getMessage() + "\n\n";
                        }
                    }

                    switch (request_code) {
                        case REQUEST_CODE_DECRYPT_AND_VERIFY:
                        case REQUEST_CODE_DECRYPT_AND_VERIFY_DETACHED: {
                            //OpenPgpDecryptionResult decryption_res =
                                    //result.getParcelableExtra(OpenPgpApi.RESULT_DECRYPTION);
                            OpenPgpSignatureResult signature_res =
                                    result.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
                            if (signature_res.getKeyId() != 0) {
                                msg_signature = signature_res.getPrimaryUserId() + "\n\n"
                                        + signature_res.getConfirmedUserIds().toString();
                                tv_signature.setText(msg_signature);
                            } else {
                                tv_signature.setText(getString(R.string.open_pgp_bad_signature));
                            }
                            msg_ready = true;
                            break;
                        }
                        case REQUEST_CODE_DETACHED_SIGN: {
                            byte[] detached_sig
                                    = result.getByteArrayExtra(OpenPgpApi.RESULT_DETACHED_SIGNATURE);
                            msg_signature = new String(detached_sig);
                            tv_signature.setText(msg_signature);
                            msg_integrity = result.getStringExtra("signature_micalg");
                            msg_ready = true;
                            break;
                        }
                        case REQUEST_CODE_GET_KEY_IDS: {
                            rcpt_keys = result.getLongArrayExtra(OpenPgpApi.RESULT_KEY_IDS);
                            if (rcpt_keys != null) {
                                tv_recipients_pick.setText(getString(R.string.open_pgp_rcpt_key_check));
                                tv_recipients_count.setText(String.valueOf(rcpt_keys.length));
                            }
                            break;
                        }
                    }

                    break;
                }
                case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                    PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                    try {
                        InboxGPG.this.startIntentSenderFromChild
                                (InboxGPG.this, pi.getIntentSender(), request_code, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Pager.log += e.getMessage() + "\n\n";
                    }
                    break;
                }
                case OpenPgpApi.RESULT_CODE_ERROR: {
                    toaster(getString(R.string.open_pgp_failure));
                    Pager.log += ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR))
                            .getMessage() + "\n\n";
                    msg_ready = false;
                    break;
                }
            }
        }
    }

    private class SignKeyCallback implements OpenPgpApi.IOpenPgpCallback {

        int requestCode;
        AppCompatActivity act;

        private SignKeyCallback(int rc, AppCompatActivity ac) {
            requestCode = rc;
            act = ac;
        }

        @Override
        public void onReturn(Intent result) {
            switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                case OpenPgpApi.RESULT_CODE_SUCCESS: {
                    //long keyId = result.getLongExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, NO_KEY);
                    break;
                }
                case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                    PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                    try {
                        act.startIntentSenderFromChild(
                                act, pi.getIntentSender(), requestCode, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Pager.log += e.getMessage() + "\n\n";
                    }
                    break;
                }
                case OpenPgpApi.RESULT_CODE_ERROR: {
                    Pager.log += ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR))
                            .getMessage() + "\n\n";
                    break;
                }
            }
        }
    }
}