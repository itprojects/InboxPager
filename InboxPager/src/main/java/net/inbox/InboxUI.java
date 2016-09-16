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

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import net.inbox.db.DBAccess;
import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.dialogs.Dialogs;
import net.inbox.dialogs.SpinningStatus;
import net.inbox.server.IMAP;
import net.inbox.server.POP;

import java.util.ArrayList;

public class InboxUI extends AppCompatActivity {

    private DBAccess db;

    private TextView tv_page_counter;
    private TextView tv_no_account;
    private ListView msg_list_view;
    private ImageView iv_ssl_auth;

    private boolean good_incoming_server = false;

    // Reflect new unseen count changes
    private boolean change_unseen = false;
    private boolean msg_item_unseen = false;

    private Inbox current;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inbox);

        // Get the database
        db = Pager.get_db();

        current = db.get_account(getIntent().getExtras().getInt("db_id"));

        Toolbar tb = (Toolbar) findViewById(R.id.inbox_toolbar);
        setSupportActionBar(tb);

        // Find the title
        TextView tv_t;
        for (int i = 0; i < tb.getChildCount(); ++i) {
            int idd = tb.getChildAt(i).getId();
            if (idd == -1) {
                tv_t = (TextView) tb.getChildAt(i);
                tv_t.setTextColor(ContextCompat.getColor(this, R.color.color_title));
                tv_t.setTypeface(Pager.tf);
                tv_t.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        dialog_statistical();
                    }
                });
                break;
            }
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(current.get_email().toUpperCase());
        }

        // Unread Messages Counter
        tv_page_counter = (TextView) findViewById(R.id.inbox_page_counter);
        tv_page_counter.setTypeface(Pager.tf);
        tv_page_counter.setVisibility(View.GONE);

        // No accounts message is visible if the user has not init-ed the app
        tv_no_account = (TextView) findViewById(R.id.no_messages);
        tv_no_account.setTypeface(Pager.tf);

        // Setting up the SSL authentication application
        iv_ssl_auth = (ImageView) findViewById(R.id.ssl_auth_img_vw);
        iv_ssl_auth.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog_servers();
            }
        });

        // Filling the ListView of the current inbox window
        msg_list_view = (ListView) findViewById(R.id.msg_list_view);
        populate_list_view();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.inbox_action_btns, menu);
        if (current.get_smtp_server().trim().length() > 0) {
            menu.findItem(R.id.new_msg_menu).setVisible(true);
        } else {
            menu.findItem(R.id.new_msg_menu).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.inbox_action_refresh_btn:
                // Starting a spinning animation dialog
                SpinningStatus sp = new SpinningStatus(true, this);
                sp.execute();
                sp.onProgressUpdate(getString(R.string.progress_title),
                        getString(R.string.progress_refreshing));

                // Starting refresh INBOX
                if (current.get_imap_or_pop()) {
                    Pager.handler = new IMAP(this);
                } else {
                    Pager.handler = new POP(this);
                }
                Pager.handler.sp = sp;
                Pager.handler.start();
                Pager.handler.default_action(false, current, this);
                break;
            case R.id.mark_all_seen_menu:
                db.mark_all_seen(current.get_id());
                populate_list_view();
                break;
            case R.id.new_msg_menu:
                Intent in = new Intent(getApplicationContext(), InboxSend.class);
                Bundle bn = new Bundle();
                bn.putInt("db_id", current.get_id());
                bn.putString("title", current.get_email());
                startActivityForResult(in.putExtras(bn), 1000);
                overridePendingTransition(R.anim.left_in, R.anim.left_out);
                break;
            case R.id.log_menu:
                Dialogs.dialog_view_log(this);
                break;
            case R.id.defaults_menu:
                Intent i = new Intent(getApplicationContext(), InboxPreferences.class);
                Bundle b = new Bundle();
                b.putBoolean("add", false);
                b.putInt("db_id", current.get_id());
                b.putString("title", current.get_email());
                startActivityForResult(i.putExtras(b), 100);
                overridePendingTransition(R.anim.left_in, R.anim.left_out);
                break;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Update unread count
        if (msg_item_unseen) {
            current.set_unseen(db.update_account_unseen_count(current.get_id()));
            msg_item_unseen = false;
            populate_list_view();
        }
        if (requestCode == 100) {
            if (resultCode == Activity.RESULT_OK) {
                // If account has changed so go back to the main screen
                Intent ret_intent = new Intent();
                setResult(Activity.RESULT_OK, ret_intent.putExtra("status", true));
                finish();
            }
        } else if (resultCode == 10101) {
            // Prepare to write a reply message
            Intent send_intent = new Intent(getApplicationContext(), InboxSend.class);
            Bundle b = new Bundle();
            b.putInt("db_id", current.get_id());
            b.putString("title", current.get_email());
            b.putString("reply-to", data.getStringExtra("reply-to"));
            b.putString("subject", data.getStringExtra("subject"));
            startActivityForResult(send_intent.putExtras(b), 10001);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.right_in, R.anim.right_out);

        // Update unseen messages count
        Pager.refresh = change_unseen;
    }

    public void populate_list_view() {
        ArrayList<InboxMessageListItem> al_messages_items;
        ArrayList<Message> al_messages = db.get_all_messages(current.get_id());

        if (al_messages.size() == 0) {
            tv_no_account.setVisibility(View.VISIBLE);
            msg_list_view.setVisibility(View.GONE);
        } else {
            tv_no_account.setVisibility(View.GONE);
            msg_list_view.setVisibility(View.VISIBLE);

            al_messages_items = new ArrayList<>();
            // Adding messages backwards, to save on sort by date
            for (int i = al_messages.size() - 1; i >= 0; --i) {
                Message nfo = al_messages.get(i);
                al_messages_items.add(new InboxMessageListItem(nfo.get_id(), nfo.get_subject(),
                        nfo.get_from(), nfo.get_attachments(), nfo.get_seen()));
            }
            InboxMessageList adapter = new InboxMessageList(this, al_messages_items);
            msg_list_view.setAdapter(adapter);
            msg_list_view.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    InboxMessageListItem itm_new =  (InboxMessageListItem) parent
                            .getItemAtPosition(position);
                    if (!itm_new.get_seen()) {
                        msg_item_unseen = true;
                        change_unseen = true;
                    }
                    Intent i = new Intent(getApplicationContext(), InboxMessage.class);
                    Bundle b = new Bundle();
                    b.putInt("db_id", itm_new.get_id());
                    b.putString("title", current.get_email());
                    b.putBoolean("no_send", current.get_smtp_server().isEmpty());
                    b.putBoolean("imap_or_pop", current.get_imap_or_pop());
                    startActivityForResult(i.putExtras(b), 1000);
                    overridePendingTransition(R.anim.left_in, R.anim.left_out);
                }
            });
        }

        // Update message counter
        set_count();

        // Set server certificate details
        connection_security();
    }

    public void set_count() {
        int i = current.get_unseen();
        String str = "000";
        if (i < 1) {
            tv_page_counter.setText(str);
            tv_page_counter.setVisibility(View.GONE);
            return;
        } else {
            tv_page_counter.setVisibility(View.VISIBLE);
        }
        if (i < 10) {
            str = "00" + String.valueOf(i);
            tv_page_counter.setText(str);
        } if (i > 9 && i < 100) {
            str = "0" + String.valueOf(i);
            tv_page_counter.setText(str);
        } else if (i > 100 && i <= 999) {
            str = String.valueOf(i);
            tv_page_counter.setText(str);
        } else if (i > 999) {
            str = "+999";
            tv_page_counter.setText(str);
        }
    }

    /**
     * Dialog account information - i.e. # messages, # unread.
     **/
    public void dialog_statistical() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.stats_title));
        String msg = current.get_messages() + " " + getString(R.string.stats_messages) + ", "
                + current.get_unseen() + " " + getString(R.string.stats_unseen) + ", ";
        int sz = current.get_total_size();
        String total_size = "";
        if (sz < 1024) {
            total_size += sz + " " + getString(R.string.attch_bytes);
        } else if (sz >= 1024 && sz < 1048576) {
            total_size += (sz/1024) + " " + getString(R.string.attch_kilobytes);
        } else {
            total_size += (sz/1048576) + " " + getString(R.string.attch_megabytes);
        }
        msg += total_size;
        builder.setMessage(msg);
        builder.setPositiveButton(getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog,int id) {
                        dialog.dismiss();
                    }
                });
        builder.setCancelable(true);
        builder.show();
    }

    public void connection_security() {
        if (Pager.handler != null && Pager.handler.get_certificates() != null) {
            good_incoming_server = Pager.handler.get_certificates().length() > 0;
            iv_ssl_auth.setVisibility(View.VISIBLE);
            if (good_incoming_server) {
                good_incoming_server = true;
                iv_ssl_auth.setImageResource(R.drawable.ssl_auth);
            } else {
                good_incoming_server = false;
                iv_ssl_auth.setImageResource(R.drawable.ssl_no_auth);
            }
        } else {
            good_incoming_server = false;
            iv_ssl_auth.setVisibility(View.GONE);
        }
    }

    /**
     * Intermediaries' SSL certificates of the last live connection.
     **/
    public void dialog_servers() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.ssl_auth_popup_title));
        if (good_incoming_server) {
            builder.setMessage(getString(R.string.ssl_auth_popup_you)
                    + Pager.handler.get_certificates());
        } else {
            builder.setMessage(getString(R.string.ssl_auth_popup_bad_connection));
        }
        builder.setPositiveButton(getString(android.R.string.ok), null);
        builder.show();
    }
}
