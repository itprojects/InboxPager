/*
 * InboxGPG interacts with OpenKeychain encryption package.
 * Copyright (C) 2016-2026  ITPROJECTS
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

import static net.inbox.Common.set_activity_insets_listener;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;

import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import net.inbox.pager.R;
import net.inbox.visuals.Dialogs;
import net.inbox.server.Utils;

import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class InboxGPG extends AppCompatActivity {

    private static final String open_pgp_provider = "org.sufficientlysecure.keychain";
    public static final int NO_KEY = 0;
    public static final int REQUEST_CODE_CLEARTEXT_SIGN = 9910;
    public static final int REQUEST_CODE_ENCRYPT = 9911;
    public static final int REQUEST_CODE_SIGN_AND_ENCRYPT = 9912;
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY = 9913;
    public static final int REQUEST_CODE_GET_KEY_IDS = 9915;
    public static final int REQUEST_CODE_DETACHED_SIGN = 9916;
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY_DETACHED = 9917;
    public static final int REQUEST_CODE_KEY_PREFERENCE = 9999;

    private static long l_sign_key_id = 0;
    private static long[] rcpt_keys = null;

    private int intent_request_code = 0;

    private boolean msg_ready = false;
    private boolean msg_encrypted = false;
    private boolean msg_signed = false;

    private String msg_contents; // given message
    private String msg_crypto; // encrypted/decrypted message
    private String msg_signature; // signature for verification or return
    private String msg_integrity; // message integrity check

    private String[] gpg_actions = null;
    private String[] rcpt_mailboxes = null;
    private ArrayList<String> attachment_paths = new ArrayList<>();

    private CheckBox cb_sign_to_self;
    private TextView tv_signing_key;
    private TextView tv_recipients_pick;
    private TextView tv_recipients_count;
    private TextView tv_recipients_list_display;
    private TextView tv_message;
    private TextView tv_cipher_text;
    private TextView tv_signature;
    private Spinner spinner_gpg_action;

    private OpenPgpServiceConnection open_pgp_service_connection;

    @Override
    public void onCreate(Bundle saved_instance_state) {
        super.onCreate(saved_instance_state);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );

        // For camera cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) // Android API >= 35
            EdgeToEdge.enable(this); // run before setContentView()

        setContentView(R.layout.openpgp);
        LinearLayout main_root = findViewById(R.id.root_view_openpgp);

        try {
            Toolbar tb = findViewById(R.id.send_toolbar);
            setSupportActionBar(tb);

            // Find the title
            TextView openpgp_title = tb.findViewById(R.id.openpgp_title);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(false);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                String s_title = getString(R.string.open_pgp_dialog_title);
                openpgp_title.setText(s_title.toUpperCase());
            }

            TextView tv_reset = findViewById(R.id.tv_reset);
            tv_reset.setOnClickListener(
                v -> {
                    l_sign_key_id = 0;
                    tv_signing_key.setText(getString(R.string.open_pgp_signing_key_cross));
                    rcpt_keys = null;
                    tv_recipients_pick.setText(getString(R.string.open_pgp_rcpt_key_cross));
                    reset_activity();
                }
            );

            TextView tv_ready = findViewById(R.id.tv_ready);
            tv_ready.setOnClickListener(
                v -> {
                    if (msg_ready) {
                        Intent intent_a = getIntent();
                        Bundle b = new Bundle();
                        b.putInt("ret-code", intent_request_code);
                        switch (intent_request_code) {
                            case 91: // encrypt and/or sign
                                InboxSend.msg_crypto = pgp_mime_serialization();
                                msg_crypto = "";
                                break;
                            case 92: // decrypted and verified
                                if (msg_crypto != null) {
                                    InboxMessage.msg_clear_text = msg_crypto;
                                    msg_crypto = "";
                                }
                                b.putString("msg-signature", msg_signature);
                                break;
                            case 93: // verified clear text signature
                                b.putString("msg-signature", msg_signature);
                                break;
                        }
                        intent_a.putExtras(b);
                        setResult(19091, intent_a);
                        finish();
                    } else {
                        toaster(false, getString(R.string.open_pgp_press_start));
                    }
                }
            );

            spinner_gpg_action = findViewById(R.id.spinner_gpg_action);
            tv_signing_key = findViewById(R.id.tv_pick_sign_key);
            cb_sign_to_self = findViewById(R.id.cb_sign_to_self);
            tv_recipients_pick = findViewById(R.id.tv_recipients_pick);
            tv_recipients_count = findViewById(R.id.tv_recipients_count);
            tv_recipients_list_display = findViewById(R.id.tv_recipients_list);
            tv_message = findViewById(R.id.tv_message);
            tv_cipher_text = findViewById(R.id.tv_encrypted);
            tv_signature = findViewById(R.id.tv_signature);
            tv_signing_key.setOnClickListener(v -> select_signing_key());
            tv_recipients_pick.setOnClickListener(v -> select_rcpt_keys());

            TextView tv_start = findViewById(R.id.tv_start);
            tv_start.setOnClickListener(
                v -> {
                    reset_activity(); // Reset first
                    if (msg_signed || msg_encrypted) {
                        open_connection();
                    } else {
                        switch (spinner_gpg_action.getSelectedItemPosition()) {
                            case 0: // Encrypt
                                if (l_sign_key_id == NO_KEY) {
                                    toaster(true, getString(R.string.err_pick_signing_key));
                                } else if (rcpt_keys == null) {
                                    toaster(true, getString(R.string.err_pick_rcpt_keys));
                                } else {
                                    encrypt(new Intent());
                                }
                                break;
                            case 1: // Sign and encrypt
                                if (l_sign_key_id == NO_KEY) {
                                    toaster(true, getString(R.string.err_pick_signing_key));
                                } else if (rcpt_keys == null) {
                                    toaster(true, getString(R.string.err_pick_rcpt_keys));
                                } else {
                                    sign_and_encrypt(new Intent());
                                }
                                break;
                            case 2: // Detached sign (of clear text)
                                if (l_sign_key_id == NO_KEY) {
                                    toaster(true, getString(R.string.err_pick_signing_key));
                                } else {
                                    detached_sign(new Intent());
                                }
                                break;
                        }
                    }
                }
            );

            // Obtain request code
            intent_request_code = getIntent().getIntExtra("request-code", 0);
            switch (intent_request_code) {
                case 91:{
                    // Encryption options
                    gpg_actions = new String[] {
                        getString(R.string.open_pgp_list_items_encrypt),
                        getString(R.string.open_pgp_list_items_sign_encrypt),
                        getString(R.string.open_pgp_list_items_detach_sign)
                    };

                    // Obtaining recipients
                    if (getIntent().getExtras() != null) {
                        String rcpt_s = getIntent().getExtras().getString("recipients");
                        if (rcpt_s != null && !rcpt_s.isEmpty()) {
                            rcpt_mailboxes = rcpt_s.split(",");
                            for (int i = 0;i < rcpt_mailboxes.length;++i) {
                                rcpt_mailboxes[i] = rcpt_mailboxes[i].trim();
                            }
                            tv_recipients_list_display.setText(rcpt_s);
                        }
                    }

                    // Message data
                    msg_contents = getIntent().getStringExtra("message-data");
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
                case 92:{ // decrypt and verify signature
                    gpg_actions = new String[] {
                        getString(R.string.open_pgp_list_items_decrypt_and_verify)
                    };
                    msg_encrypted = true;
                    msg_contents = InboxMessage.msg_clear_text;
                    InboxMessage.msg_clear_text = "";
                    if (msg_contents.length() > 500) {
                        tv_cipher_text.setText(msg_contents.substring(0, 500));
                    } else tv_cipher_text.setText(msg_contents);
                    break;
                }
                case 93:{ // clear text verify signature
                    gpg_actions = new String[] {
                        getString(R.string.open_pgp_list_items_decrypt_and_verify)
                    };
                    msg_signed = true;
                    msg_signature = getIntent().getStringExtra("signature");
                    msg_contents = InboxMessage.msg_clear_text;
                    InboxMessage.msg_clear_text = "";
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

            // Restore existing state
            if (saved_instance_state != null) {
                l_sign_key_id = saved_instance_state.getLong("sv_l_sign_key_id");
                rcpt_keys = saved_instance_state.getLongArray("sv_rcpt_keys");
                msg_ready = saved_instance_state.getBoolean("sv_msg_ready");
                msg_encrypted = saved_instance_state.getBoolean("sv_msg_encrypted");
                msg_signed = saved_instance_state.getBoolean("sv_msg_signed");
                msg_contents = saved_instance_state.getString("sv_msg_contents");
                msg_crypto = saved_instance_state.getString("sv_msg_crypto");
                msg_signature = saved_instance_state.getString("sv_msg_signature");
                msg_integrity = saved_instance_state.getString("sv_msg_integrity");
                gpg_actions = saved_instance_state.getStringArray("sv_gpg_actions");
                rcpt_mailboxes = saved_instance_state.getStringArray("sv_rcpt_mailboxes");
                attachment_paths = saved_instance_state.getStringArrayList("sv_attachment_paths");

                cb_sign_to_self.setChecked(saved_instance_state.getBoolean("sv_cb_sign_to_self"));
                tv_signing_key.setText(saved_instance_state.getString("sv_tv_signing_key"));
                tv_recipients_pick.setText(saved_instance_state.getString("sv_tv_recipients_pick"));
                tv_recipients_count.setText(saved_instance_state.getString("sv_tv_recipients_count"));
                tv_recipients_list_display.setText(saved_instance_state.getString("sv_tv_recipients_list_display"));
                tv_message.setText(saved_instance_state.getString("sv_tv_message"));
                tv_cipher_text.setText(saved_instance_state.getString("sv_tv_cipher_text"));
                tv_signature.setText(saved_instance_state.getString("sv_tv_signature"));
                spinner_gpg_action.setSelection(
                    saved_instance_state.getInt("sv_spinner_gpg_action")
                );
            }

            ArrayAdapter<String> adapt = new ArrayAdapter<>(this, R.layout.spinner_item, gpg_actions);
            adapt.setDropDownViewResource(android.R.layout.simple_list_item_checked);
            spinner_gpg_action.setAdapter(adapt);

            if (msg_encrypted || msg_signed) { // Free visual space
                cb_sign_to_self.setVisibility(View.GONE);
                tv_recipients_pick.setVisibility(View.GONE);
                findViewById(R.id.tv_pick_sign_key).setVisibility(View.GONE);
                findViewById(R.id.tv_recipients).setVisibility(View.GONE);
                findViewById(R.id.tv_recipients_list).setVisibility(View.GONE);
            }
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            finish();
        }

        // Handle insets for cutout and system bars
        set_activity_insets_listener(main_root);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle saved_instance_state) {
        super.onSaveInstanceState(saved_instance_state);
        saved_instance_state.putLong("sv_l_sign_key_id", l_sign_key_id);
        saved_instance_state.putLongArray("sv_rcpt_keys", rcpt_keys);
        saved_instance_state.putBoolean("sv_msg_ready", msg_ready);
        saved_instance_state.putBoolean("sv_msg_encrypted", msg_encrypted);
        saved_instance_state.putBoolean("sv_msg_signed", msg_signed);
        saved_instance_state.putString("sv_msg_contents", msg_contents);
        saved_instance_state.putString("sv_msg_crypto", msg_crypto);
        saved_instance_state.putString("sv_msg_signature", msg_signature);
        saved_instance_state.putString("sv_msg_integrity", msg_integrity);
        saved_instance_state.putStringArray("sv_gpg_actions", gpg_actions);
        saved_instance_state.putStringArray("sv_rcpt_mailboxes", rcpt_mailboxes);
        saved_instance_state.putStringArrayList("sv_attachment_paths", attachment_paths);

        saved_instance_state.putBoolean("sv_cb_sign_to_self", cb_sign_to_self.isChecked());
        saved_instance_state.putString("sv_tv_signing_key", tv_signing_key.getText().toString());
        saved_instance_state.putString("sv_tv_recipients_pick", tv_recipients_pick.getText().toString());
        saved_instance_state.putString("sv_tv_recipients_count", tv_recipients_count.getText().toString());
        saved_instance_state.putString("sv_tv_recipients_list_display", tv_recipients_list_display.getText().toString());
        saved_instance_state.putString("sv_tv_message", tv_message.getText().toString());
        saved_instance_state.putString("sv_tv_cipher_text", tv_cipher_text.getText().toString());
        saved_instance_state.putString("sv_tv_signature", tv_signature.getText().toString());
        saved_instance_state.putInt("sv_spinner_gpg_action", spinner_gpg_action.getSelectedItemPosition());
    }

    @Override
    public void finish() {
        super.finish();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.right_in, R.anim.right_out);
        } else { // Android API <= 33
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
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
                public void onBound(IOpenPgpService2 service) {
                    get_sign_key_id(new Intent());
                }

                @Override
                public void onError(Exception e) {
                    InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                    Dialogs.dialog_exception(e, InboxGPG.this);
                }
            }
        );
        open_pgp_service_connection.bindToService();
    }

    private void get_sign_key_id(Intent data) {
        data.setAction(OpenPgpApi.ACTION_GET_SIGN_KEY_ID);
        OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
        api.executeApiAsync(
            data,
            null,
            null,
            new SignKeyCallback(REQUEST_CODE_KEY_PREFERENCE, this)
        );
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
                    InboxPager.log += e.getMessage() + "\n\n";
                }
            }
        );
        open_pgp_service_connection.bindToService();
    }

    private void get_rcpt_keys(Intent data) {
        data.setAction(OpenPgpApi.ACTION_GET_KEY_IDS);
        data.putExtra(OpenPgpApi.EXTRA_USER_IDS, rcpt_mailboxes);
        OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
        api.executeApiAsync(
            data,
            null,
            null,
            new ResultsCallback(null, REQUEST_CODE_GET_KEY_IDS)
        );
    }

    /**
     * Inline non-mime pgp messages. Not implemented.
     **/
    public void cleartext_sign(Intent data) {
        data.setAction(OpenPgpApi.ACTION_CLEARTEXT_SIGN);
        data.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, l_sign_key_id);
        InputStream is;
        try {
            is = get_input_stream(true, true);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
            api.executeApiAsync(data, is, os, new ResultsCallback(os, REQUEST_CODE_CLEARTEXT_SIGN));
        } catch (OutOfMemoryError e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            toaster(false, getString(R.string.ex_ran_out_of_ram));
        }
    }

    /**
     * Clear text signature PGP/MIME message.
     **/
    public void detached_sign(Intent data) {
        data.setAction(OpenPgpApi.ACTION_DETACHED_SIGN);
        data.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, l_sign_key_id);
        InputStream is;
        try {
            is = get_input_stream(true, true);
            OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
            api.executeApiAsync(
                data,
                is,
                null,
                new ResultsCallback(null, REQUEST_CODE_DETACHED_SIGN)
            );
        } catch (OutOfMemoryError e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            toaster(false, getString(R.string.ex_ran_out_of_ram));
        }
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

        InputStream is;
        try {
            is = get_input_stream(true, false);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
            api.executeApiAsync(data, is, os, new ResultsCallback(os, REQUEST_CODE_ENCRYPT));
        } catch (OutOfMemoryError e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            toaster(false, getString(R.string.ex_ran_out_of_ram));
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            toaster(false, getString(R.string.err_unknown_error));
        }
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
        InputStream is;
        try {
            is = get_input_stream(true, false);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
            api.executeApiAsync(
                data,
                is,
                os,
                new ResultsCallback(os, REQUEST_CODE_SIGN_AND_ENCRYPT)
            );
        } catch (OutOfMemoryError e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            toaster(false, getString(R.string.ex_ran_out_of_ram));
        }
    }

    private void decrypt_and_verify(Intent data) {
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);

        InputStream is;
        try {
            is = get_input_stream(false, false);
            ByteArrayOutputStream os = new ByteArrayOutputStream();

            OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
            api.executeApiAsync(
                data,
                is,
                os,
                new ResultsCallback(os, REQUEST_CODE_DECRYPT_AND_VERIFY)
            );
        } catch (OutOfMemoryError e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            toaster(false, getString(R.string.ex_ran_out_of_ram));
        }
    }

    public void decrypt_and_verify_detached(Intent data) {
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        if (msg_signature == null) {
            toaster(true, getString(R.string.crypto_failure));
        } else {
            data.putExtra(OpenPgpApi.EXTRA_DETACHED_SIGNATURE, msg_signature.getBytes());

            InputStream is;
            try {
                is = get_input_stream(false, false);

                OpenPgpApi api = new OpenPgpApi(this, open_pgp_service_connection.getService());
                api.executeApiAsync(
                    data,
                    is,
                    null,
                    new ResultsCallback(null, REQUEST_CODE_DECRYPT_AND_VERIFY_DETACHED)
                );
            } catch (OutOfMemoryError e) {
                InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                toaster(false, getString(R.string.ex_ran_out_of_ram));
            }
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
                    InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                    toaster(true, e.getMessage());
                }
            }
        );
        open_pgp_service_connection.bindToService();
    }

    /**
     * Converts String to ByteArrayInputStream.
     **/
    private InputStream get_input_stream(boolean mime, boolean clear) throws OutOfMemoryError {
        InputStream is;
        if (mime) {
            if (clear) {
                msg_crypto = mime_serialization().replaceAll("\n", "\r\n");
                is = new ByteArrayInputStream(msg_crypto.getBytes(StandardCharsets.UTF_8));
            } else {
                is = new ByteArrayInputStream(mime_serialization().getBytes(StandardCharsets.UTF_8));
            }
        } else {
            if (msg_contents == null) return null;
            is = new ByteArrayInputStream(msg_contents.getBytes(StandardCharsets.UTF_8));
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
        if (attachment_paths == null || attachment_paths.isEmpty()) {
            msg_mime += "--\n";
        } else {
            // Message attachments
            for (int i = 0;i < attachment_paths.size();++i) {
                Uri uri = Uri.parse(attachment_paths.get(i));

                Common.check_read_give(this, uri);

                DocumentFile ff = DocumentFile.fromSingleUri(this, uri);

                if (i != 0) {
                    msg_mime = msg_mime.concat("\n--" + bounds + "\n");
                } else msg_mime = msg_mime.concat("\n");

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
                    try (InputStream in_stream = getContentResolver().openInputStream(uri)) {
                        byte[] bfr = new byte[(int) ff.length()];
                        if ((int) ff.length() > 0) {
                            int t;
                            while ((t = in_stream.read(bfr)) != -1) {
                                b_stream.write(bfr, 0, t);
                            }
                        }
                    }
                } catch (IOException e) {
                    InboxPager.log = InboxPager.log.concat(getString
                            (R.string.ex_field) + e.getMessage() + "\n\n");
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
            pgp_mime += "\n--" + bounds + "\n";
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

    private void toaster(final boolean time, final String msg) {
        runOnUiThread(() -> {
            if (time) {
                Toast.makeText(InboxGPG.this, msg, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(InboxGPG.this, msg, Toast.LENGTH_LONG).show();
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
                                        if (msg_crypto.length() > 700) {
                                            tv_cipher_text.setText(msg_crypto.substring(0, 700));
                                        } else tv_cipher_text.setText(msg_crypto);
                                    }
                                    msg_ready = true;
                                    break;
                                case 92:
                                    // Encrypted and/or signed, decryption
                                    if (msg_encrypted) {
                                        msg_crypto = os.toString("UTF-8");
                                        if (msg_crypto.length() > 700) {
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
                            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                        }
                    }

                    switch (request_code) {
                        case REQUEST_CODE_DECRYPT_AND_VERIFY:
                        case REQUEST_CODE_DECRYPT_AND_VERIFY_DETACHED: {
                            //OpenPgpDecryptionResult decryption_res = result.getParcelableExtra(
                            //    OpenPgpApi.RESULT_DECRYPTION
                            //);
                            OpenPgpSignatureResult signature_res = result.getParcelableExtra(
                                OpenPgpApi.RESULT_SIGNATURE
                            );
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
                            byte[] detached_sig = result.getByteArrayExtra(
                                OpenPgpApi.RESULT_DETACHED_SIGNATURE
                            );
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
                        InboxGPG.this.startIntentSenderFromChild(
                            InboxGPG.this,
                            pi.getIntentSender(),
                            request_code,
                            null,
                            0,
                            0,
                            0
                        );
                    } catch (IntentSender.SendIntentException e) {
                        InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                    }
                    break;
                }
                case OpenPgpApi.RESULT_CODE_ERROR: {
                    toaster(true, getString(R.string.crypto_failure));
                    InboxPager.log = InboxPager.log.concat(
                        ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR))
                            .getMessage() + "\n\n"
                    );
                    msg_ready = false;
                    break;
                }
            }
        }
    }

    private static class SignKeyCallback implements OpenPgpApi.IOpenPgpCallback {

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
                            act, pi.getIntentSender(), requestCode, null, 0, 0, 0
                        );
                    } catch (IntentSender.SendIntentException e) {
                        InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                    }
                    break;
                }
                case OpenPgpApi.RESULT_CODE_ERROR: {
                    InboxPager.log = InboxPager.log.concat(
                        ((OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR))
                            .getMessage() + "\n\n"
                    );
                    break;
                }
            }
        }
    }
}
