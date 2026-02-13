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
package net.inbox.visuals;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import net.inbox.InboxPager;
import net.inbox.pager.R;
import net.inbox.server.EndToEnd;
import net.inbox.server.NetworkThread;

public class Dialogs {

    public static void dialog_simple(final String title, final String msg, final AppCompatActivity at) {
        at.runOnUiThread(
            () -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(at);
                if (title == null) builder.setTitle(at.getString(R.string.app_name));
                else builder.setTitle(title);
                builder.setMessage(msg);
                builder.setCancelable(true);
                builder.setPositiveButton(at.getString(android.R.string.ok), null);
                make_text_selectable(builder.show());
            }
        );
    }

    public static void dialog_exception(final Exception e, final AppCompatActivity at) {
        at.runOnUiThread(
            () -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(at);
                builder.setTitle(at.getString(R.string.ex_title));
                String str = e.getMessage() + "\n\n";
                StackTraceElement[] stack = e.getStackTrace();
                for (int i = 0;i < e.getStackTrace().length;++i) {
                    str = str.concat(stack[i].getClassName() +":"+ stack[i].getLineNumber() + "\n");
                }
                builder.setMessage(str);
                builder.setCancelable(true);
                builder.setPositiveButton(at.getString(android.R.string.ok), null);
                make_text_selectable(builder.show());
            }
        );
    }

    public static void dialog_view_message(final String msg, final AppCompatActivity at) {
        at.runOnUiThread(
            () -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(at);
                builder.setTitle(at.getString(R.string.menu_see_full_message_title));
                builder.setMessage(msg);
                builder.setCancelable(true);
                builder.setPositiveButton(at.getString(android.R.string.ok), null);
                make_text_selectable(builder.show());
            }
        );
    }

    public static void dialog_view_log(AppCompatActivity at) {
        AlertDialog.Builder builder = new AlertDialog.Builder(at);
        builder.setTitle(at.getString(R.string.menu_log));
        builder.setMessage(InboxPager.log);
        builder.setCancelable(true);
        builder.setPositiveButton(at.getString(android.R.string.ok),
            (dialog, id) -> dialog.dismiss()
        );
        builder.setNeutralButton(
            at.getString(R.string.btn_log_clear),
            (dialog, which) -> InboxPager.log = " "
        );
        make_text_selectable(builder.show());
    }

    private static void make_text_selectable(AlertDialog adg) {
        TextView text_box = adg.findViewById(android.R.id.message);
        if (text_box != null) text_box.setTextIsSelectable(true);
    }

    public static void dialog_pw_txt(AlertDialog.Builder builder, AppCompatActivity at) {
        builder.setTitle(at.getString(R.string.crypto_title));

        LayoutInflater inflater = at.getLayoutInflater();
        View v = inflater.inflate(R.layout.pw_txt, null);
        builder.setView(v);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(at);

        // Spinners for text encryption parameters
        Spinner spin_cipher_type = v.findViewById(R.id.spin_cipher);
        spin_cipher_type.setAdapter(
            new ArrayAdapter<CharSequence>(at, R.layout.spinner_item, EndToEnd.cipher_types)
        );

        Spinner spin_cipher_mode = v.findViewById(R.id.spin_cipher_mode);
        spin_cipher_mode.setAdapter(
            new ArrayAdapter<CharSequence>(at, R.layout.spinner_item, EndToEnd.cipher_modes)
        );

        Spinner spin_cipher_padding = v.findViewById(R.id.spin_cipher_padding);
        spin_cipher_padding.setAdapter(
            new ArrayAdapter<CharSequence>(at, R.layout.spinner_item, EndToEnd.cipher_paddings)
        );

        // Getting application current preferences
        String spin_type = prefs.getString("list_cipher_types", "AES");
        String spin_mode = prefs.getString("list_cipher_modes", "CBC");
        String spin_padding = prefs.getString("list_cipher_paddings", "PKCS7");

        for (int i = 0;i < EndToEnd.cipher_types.length;++i) {
            if (spin_cipher_type.getItemAtPosition(i).equals(spin_type)) {
                spin_cipher_type.setSelection(i);
                break;
            }
        }

        for (int i = 0;i < EndToEnd.cipher_modes.length;++i) {
            if (spin_cipher_mode.getItemAtPosition(i).equals(spin_mode)) {
                spin_cipher_mode.setSelection(i);
                break;
            }
        }

        for (int i = 0;i < EndToEnd.cipher_paddings.length;++i) {
            if (spin_cipher_padding.getItemAtPosition(i).equals(spin_padding)) {
                spin_cipher_padding.setSelection(i);
                break;
            }
        }

        CheckBox cb_pw = v.findViewById(R.id.cb_pw);
        cb_pw.setOnCheckedChangeListener(
            (v1, isChecked) -> {
                // Find EditText, this must change, is layout changes
                LinearLayout lv = (LinearLayout) v1.getParent().getParent();
                if (isChecked) {
                    ((EditText) lv.findViewById(R.id.et_key)).setInputType(
                            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    );
                } else {
                    ((EditText) lv.findViewById(R.id.et_key)).setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                    );
                }
            }
        );

        builder.setPositiveButton(at.getString(R.string.crypto_decrypt), null);
    }

    public static void toaster(final boolean time, final String msg, final AppCompatActivity ct) {
        ct.runOnUiThread(
            () -> {
                if (time) {
                    Toast.makeText(ct, msg, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ct, msg, Toast.LENGTH_LONG).show();
                }
            }
        );
    }

    public static void toaster(final boolean time, final String msg, final Context ct) {
        if (time) {
            Toast.makeText(ct, msg, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(ct, msg, Toast.LENGTH_LONG).show();
        }
    }
}
