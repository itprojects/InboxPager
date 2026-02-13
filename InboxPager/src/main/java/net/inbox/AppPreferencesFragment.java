/*
 * InboxPager, an android email client.
 * Copyright (C) 2016-2026  ITPROJECTS
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
package net.inbox;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.inbox.pager.R;

public class AppPreferencesFragment extends PreferenceFragmentCompat {

    private AlertDialog dialog_pw;
    private EditText et_pw;
    private EditText et_pw_retype;
    private TextView tv_description;
    private Preference pw_protection;
    private Preference pw_emergency_protection;
    private SharedPreferences prefs;

    @Override
    public void onCreatePreferences(Bundle saved_instance_state, String root_key) {
        setPreferencesFromResource(R.xml.settings, root_key);

        // Application preferences
        prefs = getPreferenceManager().getSharedPreferences();

        // Enable/Disable application password protection
        pw_protection = getPreferenceManager().findPreference("pw_protection");

        if (pw_protection != null) {
            if (prefs.getBoolean("pw_protection", false)) {
                pw_protection.setSummary(getString(R.string.sett_enable_pw_summary_on));
            } else {
                pw_protection.setSummary(getString(R.string.sett_enable_pw_summary_off));
            }

            pw_protection.setOnPreferenceClickListener(
                p -> {
                    if (prefs.getBoolean("pw_protection", false)) {
                        // Password is set. Offer to change or remove.
                        AlertDialog.Builder builder = new AlertDialog.Builder(p.getContext());
                        builder.setTitle(getString(R.string.sett_change_pw_title));
                        builder.setMessage(getString(R.string.sett_change_pw_q));
                        builder.setNegativeButton(getString(android.R.string.no), null);
                        builder.setNeutralButton(
                            getString(R.string.sett_change_pw_remove),
                            (dialog, id) -> {
                                try {
                                    pw_protection.setSummary(
                                        getString(R.string.sett_enable_pw_summary_off)
                                    );
                                    prefs.edit().putBoolean("pw_protection", false).apply();
                                    InboxPager.get_db().rekey_db("cleartext");
                                    et_pw.setText("");
                                    et_pw_retype.setText("");
                                } catch (Exception e) {
                                    InboxPager.log = InboxPager.log.concat(
                                        e.getMessage() == null ? "!REKEY!ERROR!" : e.getMessage()
                                    );
                                }
                            }
                        );
                        builder.setPositiveButton(
                            getString(R.string.sett_change_pw_change),
                            (dialog, id) -> dialog_pw(true)
                        );
                        builder.show();
                    } else {
                        dialog_pw(true); // No password is set. Offer to set password.
                    }

                    return true;
                }
            );
        }

        // Enable/Disable emergency password protection
        pw_emergency_protection = getPreferenceManager().findPreference("pw_emergency_protection");

        if (pw_emergency_protection != null) {
            if (prefs.getBoolean("pw_emergency_protection", false)) {
                pw_emergency_protection.setSummary(
                    getString(R.string.sett_enable_pw_emergency_summary_on)
                );
            } else {
                pw_emergency_protection.setSummary(
                    getString(R.string.sett_enable_pw_emergency_summary_off)
                );
            }

            pw_emergency_protection.setOnPreferenceClickListener(
                p -> {
                    if (prefs.getBoolean("pw_emergency_protection", false)) {
                        // Password is set. Offer to change or remove.
                        AlertDialog.Builder builder = new AlertDialog.Builder(p.getContext());
                        builder.setTitle(getString(R.string.sett_change_pw_title));
                        builder.setMessage(getString(R.string.sett_change_pw_q));
                        builder.setNegativeButton(getString(android.R.string.no), null);
                        builder.setNeutralButton(
                            getString(R.string.sett_change_pw_remove),
                            (dialog, id) -> {
                                try {
                                    pw_emergency_protection.setSummary(
                                        getString(R.string.sett_enable_pw_emergency_summary_off)
                                    );
                                    prefs.edit().putBoolean("pw_emergency_protection", false).apply();
                                    prefs.edit().putString("pw_emergency_protection_hash", "").apply();
                                    et_pw.setText("");
                                    et_pw_retype.setText("");
                                } catch (Exception e) {
                                    InboxPager.log = InboxPager.log.concat(
                                        e.getMessage() == null ? "!REKEY!ERROR!" : e.getMessage()
                                    );
                                }
                            }
                        );
                        builder.setPositiveButton(
                            getString(R.string.sett_change_pw_change),
                            (dialog, id) -> dialog_pw(false)
                        );
                        builder.show();
                    } else {
                        dialog_pw(false); // No emergency password is set. Offer to set password.
                    }

                    return true;
                }
            );
        }
    }

    private void dialog_pw(boolean app_or_emergency) {
        // Clean-up previous
        et_pw = null;
        et_pw_retype = null;

        LayoutInflater inflater = getLayoutInflater();
        View v = inflater.inflate(R.layout.pw_app, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
        if (app_or_emergency) {
            builder.setTitle(getString(R.string.sett_change_pw_title));
        } else {
            builder.setTitle(getString(R.string.sett_change_pw_emergency_title));
        }
        et_pw = v.findViewById(R.id.et_pw);
        et_pw_retype = v.findViewById(R.id.et_pw_retype);
        tv_description = v.findViewById(R.id.tv_description);
        final CheckBox cb_pw = v.findViewById(R.id.cb_pw);
        cb_pw.setOnCheckedChangeListener(
            (v1, isChecked) -> {
                if (isChecked) {
                    et_pw.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    et_pw_retype.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                } else {
                    et_pw.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                    );
                    et_pw_retype.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                    );
                }
            }
        );

        builder.setView(v);
        builder.setCancelable(false);
        builder.setPositiveButton(getString(R.string.sett_change_pw_change), null);
        builder.setNegativeButton(getString(R.string.btn_cancel), null);

        dialog_pw = builder.show();

        dialog_pw.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
            v2 -> {
                boolean match_retype = false;
                String npass = "", npassre;
                try {
                    npass = et_pw.getText().toString();
                    npassre = et_pw_retype.getText().toString();
                    if (npass.equals(npassre) && !npass.isEmpty()) {
                        match_retype = true;
                    }
                } catch (NullPointerException npe) {
                    //match_retype = false;
                }

                if (!match_retype) {
                    error();
                    return;
                }

                boolean pass_length = npass.length() >= 12;

                if (!pass_length) {
                    error();
                    return;
                }

                try {
                    if (app_or_emergency) {
                        pw_protection.setSummary(getString(R.string.sett_enable_pw_summary_on));
                        prefs.edit().putBoolean("pw_protection", true).apply();
                        InboxPager.get_db().rekey_db(et_pw.getText().toString());
                        pw_emergency_protection.setEnabled(true);
                    } else {
                        pw_emergency_protection.setSummary(
                            getString(R.string.sett_enable_pw_emergency_summary_on)
                        );
                        prefs.edit().putBoolean("pw_emergency_protection", true).apply();
                        prefs.edit().putString(
                            "pw_emergency_protection_hash",
                            Common.sha256(this.getContext(), et_pw.getText().toString())
                        ).apply();
                    }
                    et_pw.setText("");
                    et_pw_retype.setText("");
                    cb_pw.setChecked(true);
                    if (dialog_pw != null) dialog_pw.dismiss();
                } catch (Exception e) {
                    InboxPager.log = InboxPager.log.concat(
                        e.getMessage() == null ? "!REKEY!ERROR!" : e.getMessage()
                    );
                }
            }
        );
    }

    private void error() {
        et_pw.setTextColor(Color.parseColor("#BA0C0C"));
        et_pw.setHintTextColor(Color.parseColor("#BA0C0C"));
        et_pw_retype.setTextColor(Color.parseColor("#BA0C0C"));
        et_pw_retype.setHintTextColor(Color.parseColor("#BA0C0C"));
        tv_description.setTextColor(Color.parseColor("#BA0C0C"));
    }
}
