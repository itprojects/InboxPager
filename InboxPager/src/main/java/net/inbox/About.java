/**
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

import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

public class About extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);

        Toolbar tb = (Toolbar) findViewById(R.id.about_toolbar);
        setSupportActionBar(tb);

        // Find the title
        TextView tv_t;
        for (int i = 0;i < tb.getChildCount();++i) {
            int idd = tb.getChildAt(i).getId();
            if (idd == -1) {
                tv_t = (TextView) tb.getChildAt(i);
                tv_t.setTextColor(ContextCompat.getColor(this, R.color.color_title));
                tv_t.setTypeface(Pager.tf);
                break;
            }
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.menu_about).toUpperCase());
        }

        TextView tv_ver = (TextView) findViewById(R.id.about_ver);
        tv_ver.setText(String.valueOf(BuildConfig.VERSION_NAME));

        TextView tv_sql_cipher_java = (TextView) findViewById(R.id.tv_sql_cipher_java);
        tv_sql_cipher_java.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog_license(1);
            }
        });

        TextView tv_sql_cipher_other = (TextView) findViewById(R.id.tv_sql_cipher_other);
        tv_sql_cipher_other.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog_license(2);
            }
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.left_in, R.anim.left_out);
    }

    private void dialog_license(int i) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle(getString(R.string.dialog_license));
        switch (i) {
            case 1:
                builder.setMessage(getString(R.string.license_long_sql_cipher_java));
                break;
            case 2:
                builder.setMessage(getString(R.string.license_long_sql_cipher_other));
                break;
        }
        builder.setPositiveButton(getString(android.R.string.ok), null);
        builder.show();
    }
}
