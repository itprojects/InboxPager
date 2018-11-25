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
package net.inbox.dialogs;

import android.widget.Toast;

import net.inbox.Pager;
import net.inbox.R;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class Dialogs {

    public static void dialog_server_ext(final String s1, final String s2, final AppCompatActivity ct) {
        ct.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(ct);
            builder.setTitle(s1);
            builder.setMessage(s2);
            builder.setCancelable(true);
            builder.setPositiveButton(ct.getString(android.R.string.ok), null);
            builder.show();
        });
    }

    public static void dialog_error_line(final String s, final AppCompatActivity ct) {
        ct.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(ct);
            builder.setTitle(ct.getString(R.string.app_name));
            builder.setMessage(s);
            builder.setCancelable(true);
            builder.setPositiveButton(ct.getString(android.R.string.ok), null);
            builder.show();
        });
    }

    public static void dialog_exception(final Exception e, final AppCompatActivity ct) {
        ct.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(ct);
            builder.setTitle(ct.getString(R.string.ex_title));
            String str = e.getMessage() + "\n\n";
            StackTraceElement[] stack = e.getStackTrace();
            for (int i = 0; i < e.getStackTrace().length; ++i) {
                str = str.concat(stack[i].getClassName() + ":" + stack[i].getLineNumber() + "\n");
            }
            builder.setMessage(str);
            builder.setCancelable(true);
            builder.setPositiveButton(ct.getString(android.R.string.ok), null);
            builder.show();
        });
    }

    public static void dialog_view_message(final String msg, final AppCompatActivity ct) {
        ct.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(ct);
            builder.setTitle(ct.getString(R.string.menu_see_full_message_title));
            builder.setMessage(msg);
            builder.setCancelable(true);
            builder.setPositiveButton(ct.getString(android.R.string.ok), null);
            builder.show();
        });
    }

    public static void dialog_view_log(AppCompatActivity ct) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ct);
        builder.setTitle(ct.getString(R.string.menu_log));
        builder.setMessage(Pager.log);
        builder.setCancelable(true);
        builder.setPositiveButton(ct.getString(android.R.string.ok), null);
        builder.setNeutralButton(ct.getString(R.string.btn_log_clear), (dialog, which) -> Pager.log = "");
        builder.show();
    }

    public static void toaster(final boolean time, final String msg, final AppCompatActivity ct) {
        ct.runOnUiThread(() -> {
            if (time) {
                Toast.makeText(ct, msg, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ct, msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
