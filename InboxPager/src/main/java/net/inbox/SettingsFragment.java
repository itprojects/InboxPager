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
package net.inbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsFragment extends PreferenceFragment {

    private boolean dialog_choice = false;
    private AlertDialog dialog_pw;
    private SharedPreferences prefs;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        Preference p1 = getPreferenceManager().findPreference("change_pw");
        if (p1 != null) {
            p1.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference p2) {
                    prefs = PreferenceManager.getDefaultSharedPreferences(p2.getContext());
                    dialog_pw(p2.getContext());
                    return true;
                }
            });
        }
    }

    private void dialog_pw(final Context ctx) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(getString(R.string.sett_change_pw_title));
        final ViewGroup vg = (ViewGroup) ((ViewGroup) getActivity()
                .findViewById(android.R.id.content)).getChildAt(0);
        View v = (LayoutInflater.from(ctx)).inflate(R.layout.pw, vg, false);
        final Switch sw_enabled = v.findViewById(R.id.sw_pw_enable);
        final TextView tv_description = v.findViewById(R.id.tv_description);
        final EditText et_pw = v.findViewById(R.id.et_pw);
        et_pw.setHint(getString(R.string.sett_change_pw_new));
        final CheckBox cb_pw = v.findViewById(R.id.cb_pw);
        cb_pw.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton v, boolean isChecked) {
                if (isChecked) {
                    et_pw.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                } else {
                    et_pw.setInputType(InputType.TYPE_CLASS_TEXT
                            |InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
            }
        });
        sw_enabled.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton cb, boolean isChecked) {
                if (isChecked) {
                    tv_description.setVisibility(View.VISIBLE);
                    et_pw.setVisibility(View.VISIBLE);
                    cb_pw.setVisibility(View.VISIBLE);
                    dialog_choice = true;
                } else {
                    tv_description.setVisibility(View.GONE);
                    et_pw.setVisibility(View.GONE);
                    cb_pw.setVisibility(View.GONE);
                    prefs.edit().putBoolean("enable_pw", false).apply();
                    InboxPager.get_db().rekey_db("cleartext");
                    et_pw.setText("");
                    cb_pw.setChecked(false);
                }
            }
        });

        builder.setView(v);
        builder.setCancelable(false);
        builder.setPositiveButton(getString(android.R.string.ok), null);
        builder.setNegativeButton(getString(R.string.btn_cancel), null);

        // Set initial conditions
        if (prefs.getBoolean("enable_pw", false)) {
            sw_enabled.setChecked(true);
            tv_description.setVisibility(View.VISIBLE);
            et_pw.setVisibility(View.VISIBLE);
            cb_pw.setVisibility(View.VISIBLE);
        } else {
            sw_enabled.setChecked(false);
            tv_description.setVisibility(View.GONE);
            et_pw.setVisibility(View.GONE);
            cb_pw.setVisibility(View.GONE);
        }

        dialog_pw = builder.show();
        dialog_pw.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener
                (new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                //dialog_choice = 0;1 - true , 2 - false
                if (dialog_choice) {
                    if (!sw_enabled.isChecked()) {
                        prefs.edit().putBoolean("enable_pw", false).apply();
                        InboxPager.get_db().rekey_db("cleartext");
                        if (dialog_pw != null) dialog_pw.dismiss();
                    } else if (et_pw.getText().toString().length() < 12) {
                        et_pw.setTextColor(Color.parseColor("#BA0C0C"));
                        et_pw.setHintTextColor(Color.parseColor("#BA0C0C"));
                        tv_description.setTextColor(Color.parseColor("#BA0C0C"));
                    } else {
                        prefs.edit().putBoolean("enable_pw", true).apply();
                        InboxPager.get_db().rekey_db(et_pw.getText().toString());
                        et_pw.setText("");
                        cb_pw.setChecked(true);
                        if (dialog_pw != null) dialog_pw.dismiss();
                    }
                }
            }
        });
    }
}
