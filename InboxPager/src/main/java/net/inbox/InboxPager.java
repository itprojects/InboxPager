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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import net.inbox.db.DBAccess;
import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.dialogs.Dialogs;
import net.inbox.dialogs.SpinningStatus;
import net.inbox.server.Handler;
import net.inbox.server.IMAP;
import net.inbox.server.POP;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.SortedMap;

public class InboxPager extends AppCompatActivity {

    public static String log;

    // Show first use help
    private boolean show_help = false;
    private boolean refresh;

    public static int orientation = -1;

    public static String open_key_chain = "org.sufficientlysecure.keychain";

    private static DBAccess db;
    private static SharedPreferences prefs;
    private static ToneGenerator beep;
    private static Vibrator vvv;

    private Handler handler;
    private boolean unlocked;
    private int over;

    private EditText et_pw;
    private LinearLayout llay_pw;
    private DrawerLayout llay_drawer;
    private TextView tv_pager_title;
    private TextView tv_page_counter;
    private TextView tv_background;
    private ListView inbox_list_view;

    private InboxList inbox_adapter;
    private ArrayList<Integer> list_mass_refresh = new ArrayList<>();
    private ArrayList<InboxListItem> al_accounts_items = new ArrayList<>();
    private SortedMap<String, ArrayList<Message>> al_messages;

    private SpinningStatus spt;

    private ExpandableListView msg_list_view;
    private ImageButton ib_refresh;
    private ImageView iv_send_activity;
    private ImageView iv_ssl_auth;

    private boolean good_incoming_server = false;

    private int current_inbox = -2;

    private Inbox current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        // Restore existing state
        if (savedInstanceState != null) {
            log = savedInstanceState.getString("sv_log");
            orientation = savedInstanceState.getInt("sv_orientation");
            refresh = savedInstanceState.getBoolean("sv_refresh");
            unlocked = savedInstanceState.getBoolean("sv_unlocked");
            over = savedInstanceState.getInt("sv_over");
            show_help = savedInstanceState.getBoolean("sv_show_help");
            good_incoming_server = savedInstanceState.getBoolean("sv_good_incoming_server");
            current_inbox = savedInstanceState.getInt("sv_current_inbox");
            if (current_inbox != -2) {
                // Loading known ID inbox
                current = db.get_account(current_inbox);
            } else {
                current = null;
            }
        }

        // Init SharedPreferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.contains("initialized")) {
            PreferenceManager.setDefaultValues(this, R.xml.settings, false);
            prefs.edit().putBoolean("initialized", true).apply();

            // Initial values that don't have a preference screen
            prefs.edit().putBoolean("imap_or_pop", true).apply();
            prefs.edit().putBoolean("using_smtp", false).apply();
            prefs.edit().putBoolean("enable_pw", false).apply();
            show_help = true;
        }

        if (unlocked && prefs.getBoolean("enable_pw", false)) {
            // Initial entry view
            View v = View.inflate(this, R.layout.pager, null);
            setContentView(v);

            DrawerLayout llay_drawer = findViewById(R.id.drawer_layout);
            llay_drawer.setVisibility(View.VISIBLE);
            llay_drawer.setAlpha(0.01f);
            llay_drawer.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    activity_load();
                }
            });
        } else if (show_help || !prefs.getBoolean("enable_pw", false)) {
            init_db("cleartext");

            // Initial entry view
            View v = View.inflate(this, R.layout.pager, null);
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in));
            setContentView(v);

            DrawerLayout llay_drawer = findViewById(R.id.drawer_layout);
            llay_drawer.setVisibility(View.VISIBLE);
            llay_drawer.setAlpha(0.01f);
            llay_drawer.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    activity_load();
                }
            });
        } else {
            // Initial entry view
            View v = View.inflate(this, R.layout.pager, null);
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in));
            setContentView(v);

            // Entry text edit
            llay_pw = findViewById(R.id.llay_pw);
            llay_pw.setVisibility(View.VISIBLE);
            et_pw = findViewById(R.id.pw);
            et_pw.setOnKeyListener(new View.OnKeyListener() {
                public boolean onKey(View v, int key, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && key == KeyEvent.KEYCODE_ENTER) {
                        init_db(et_pw.getText().toString());
                        et_pw.setText("");
                        if (unlocked) {
                            activity_load();
                            fade_in_ui();
                        } else {
                            if (++over >= 3) finish();
                        }
                        return true;
                    }
                    return false;
                }
            });
        }

        // Helper dialog
        if (show_help) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(true);
            builder.setTitle(getString(R.string.helper_title));
            builder.setMessage(getString(R.string.helper_msg));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.setNegativeButton(getString(R.string.btn_pw),
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(getApplicationContext(), Settings.class));
                            overridePendingTransition(R.anim.right_in, R.anim.right_out);
                        }
                    });
            builder.show();
        }
    }

    @Override
    public void finish() {
        if (db != null) db.close();
        super.finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pager_action_btns, menu);

        // Specific inbox menu items
        MenuItem item1 = menu.findItem(R.id.status_menu);
        MenuItem item2 = menu.findItem(R.id.mark_all_seen_menu);
        MenuItem item3 = menu.findItem(R.id.edit_account);

        // Show/hide relevant menus
        if (current_inbox == -2) {
            if (item1 != null) item1.setVisible(false);
            if (item2 != null) item2.setVisible(false);
            if (item3 != null) item3.setVisible(false);
        } else {
            if (item1 != null) item1.setVisible(true);
            if (item2 != null) item2.setVisible(true);
            if (item3 != null) item3.setVisible(true);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about_menu:
                startActivity(new Intent(getApplicationContext(), About.class));
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
                break;
            case R.id.add_menu:
                Intent i = new Intent(getApplicationContext(), InboxPreferences.class);
                Bundle b = new Bundle();
                b.putBoolean("add", true);
                b.putInt("db_id", -1);
                startActivityForResult(i.putExtras(b), 1);
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
                break;
            case R.id.mass_refresh_menu:
                mass_refresh_check();
                break;
            case R.id.log_menu:
                Dialogs.dialog_view_log(this);
                break;
            case R.id.defaults_menu:
                startActivity(new Intent(getApplicationContext(), Settings.class));
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
                break;
            case R.id.status_menu:
                dialog_statistical();
                break;
            case R.id.mark_all_seen_menu:
                db.mark_all_seen(current.get_id());
                set_count_top();
                populate_accounts_list_view();
                populate_messages_list_view();
                break;
            case R.id.edit_account:
                Intent ii = new Intent(getApplicationContext(), InboxPreferences.class);
                Bundle bb = new Bundle();
                bb.putBoolean("add", false);
                bb.putInt("db_id", current.get_id());
                bb.putString("title", current.get_email());
                startActivityForResult(ii.putExtras(bb), 100);
                overridePendingTransition(R.anim.left_in, R.anim.left_out);
                break;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 || requestCode == 10) {
            if (refresh) {
                refresh = false;
                populate_accounts_list_view();
            } else if (resultCode == Activity.RESULT_OK) {
                if (data.getBooleanExtra("status", false)) populate_accounts_list_view();
            }
        } else if (requestCode == 100) {
            populate_accounts_list_view();
            current_inbox = -2;
            set_current_inbox();
        } else if (resultCode == 10101) {
            // Prepare to write a reply message
            Intent send_intent = new Intent(getApplicationContext(), InboxSend.class);
            Bundle b = new Bundle();
            b.putInt("db_id", current.get_id());
            b.putString("title", current.get_email());
            b.putString("reply-to", data.getStringExtra("reply-to"));
            if (data.hasExtra("reply-cc")) b.putString("reply-cc", data.getStringExtra("reply-cc"));
            b.putString("subject", data.getStringExtra("subject"));
            b.putString("previous_letter", data.getStringExtra("previous_letter"));
            startActivityForResult(send_intent.putExtras(b), 10001);
        } else if (resultCode == 1010101) {
            // Request ListView reload after message deletion
            populate_messages_list_view();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle save) {
        super.onSaveInstanceState(save);
        save.putString("sv_log", log);
        save.putInt("sv_orientation", orientation);
        save.putBoolean("sv_refresh", refresh);
        save.putBoolean("sv_unlocked", unlocked);
        save.putInt("sv_over", over);
        save.putBoolean("sv_show_help", show_help);
        save.putBoolean("sv_good_incoming_server", good_incoming_server);
        save.putInt("sv_current_inbox", current_inbox);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout llay_drawer = findViewById(R.id.drawer_layout);
        if (llay_drawer.isDrawerOpen(GravityCompat.START)) {
            llay_drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void init_db(String s) {
        boolean db_exists = getDatabasePath("pages").exists();

        SQLiteDatabase.loadLibs(this);

        // Initializing database
        db = new DBAccess(this);
        try {
            if (db_exists) {
                db.activate_db(s);
            } else {
                db.activate_db("cleartext");
            }
            unlocked = true;
        } catch (Exception e) {
            log += e.getMessage() + "\n\n";
            unlocked = false;
            et_pw.setBackgroundColor(Color.parseColor("#BA0C0C"));
            et_pw.setHintTextColor(Color.WHITE);
        }
    }

    private void fade_in_ui() {
        // UI entry appearance
        llay_pw.animate().alpha(0f).setListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                DrawerLayout llay_drawer = findViewById(R.id.drawer_layout);
                llay_pw.setVisibility(View.GONE);
                llay_drawer.setAlpha(0.01f);
                llay_drawer.setVisibility(View.VISIBLE);
                llay_drawer.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                    }
                });
            }
        });
    }

    private void activity_load() {
        // Init notification sound
        beep = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 1000);

        // Init vibrations
        vvv = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            tv_pager_title = findViewById(R.id.pager_title);
            tv_pager_title.setText(getString(R.string.activity_pager_title).toUpperCase());
        }

        // Unread Messages Counter
        tv_page_counter = findViewById(R.id.pager_page_counter);

        // Current Inbox Refresh Button
        ib_refresh = findViewById(R.id.refresh);
        ib_refresh.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (current_inbox != -2) refresh_current();
            }
        });

        // No accounts message is visible if the user has not init-ed the app
        tv_background = findViewById(R.id.text_background);

        // Setting up the SSL authentication application
        iv_ssl_auth = findViewById(R.id.ssl_auth_img_vw);
        iv_ssl_auth.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog_servers();
            }
        });

        // ListView for Messages
        msg_list_view = findViewById(R.id.msg_list_view);

        // Floating Send Suggestion
        iv_send_activity = findViewById(R.id.iv_send_activity);
        iv_send_activity.setVisibility(View.VISIBLE);
        iv_send_activity.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent in = new Intent(getApplicationContext(), InboxSend.class);
                Bundle bn = new Bundle();
                bn.putInt("db_id", current.get_id());
                bn.putString("title", current.get_email());
                startActivityForResult(in.putExtras(bn), 1000);
                overridePendingTransition(R.anim.left_in, R.anim.left_out);
            }
        });

        llay_drawer = findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle drawer_toggle = new ActionBarDrawerToggle(this, llay_drawer,
                tb, R.string.drawer_open, R.string.drawer_close);
        llay_drawer.addDrawerListener(drawer_toggle);
        drawer_toggle.syncState();
        drawer_toggle.setDrawerIndicatorEnabled(false);
        drawer_toggle.setHomeAsUpIndicator(R.drawable.drawer);
        drawer_toggle.setToolbarNavigationClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (llay_drawer.isDrawerVisible(GravityCompat.START)) {
                    llay_drawer.closeDrawer(GravityCompat.START);
                } else {
                    llay_drawer.openDrawer(GravityCompat.START);
                }
            }
        });

        // Filling the ListView accounts in the drawer
        inbox_list_view = findViewById(R.id.list_view_drawer);
        populate_accounts_list_view();

        // Set and load current account
        set_current_inbox();
    }

    public void populate_accounts_list_view() {
        ArrayList<Inbox> list_accounts = db.get_all_accounts();

        if (list_accounts.size() == 0) {
            tv_background.setText(getString(R.string.no_accounts));
            tv_background.setVisibility(View.VISIBLE);

            if (inbox_list_view != null) inbox_list_view.setAdapter(null);
        } else {
            // Update data set
            if (al_accounts_items.size() > 0) {
                al_accounts_items.clear();
            }

            // Sort accounts' list
            Collections.sort(list_accounts, new Comparator<Inbox>() {
                public int compare(Inbox inn1, Inbox inn2) {
                    return inn1.get_email().compareTo(inn2.get_email());
                }
            });

            for (int i = 0; i < list_accounts.size(); i++) {
                // Check and update unseen message counts
                Inbox nfo = list_accounts.get(i);
                int unseen_count = db.count_unseen_account_messages(nfo.get_id());
                if (unseen_count != nfo.get_unseen()) {
                    list_accounts.get(i).set_unseen(unseen_count);
                    nfo.set_unseen(unseen_count);
                }

                al_accounts_items.add(new InboxListItem(nfo.get_id(), nfo.get_email(),
                        nfo.get_unseen()));
            }

            // Add list adapter
            if (inbox_list_view.getAdapter() == null) {
                inbox_adapter = new InboxList(this, al_accounts_items);
                inbox_list_view.setAdapter(inbox_adapter);
            }

            inbox_adapter.notifyDataSetChanged();
            inbox_list_view.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    InboxListItem inbox_itm = (InboxListItem) parent.getItemAtPosition(position);
                    current_inbox = inbox_itm.get_id();
                    set_current_inbox();
                    onBackPressed();
                }
            });
        }
    }

    public void mass_refresh_check() {
        if (list_mass_refresh.size() < 1) {
            // Prevents screen rotation crash
            handle_orientation(true);

            // Starting a spinning animation dialog
            spt = new SpinningStatus(true, true, this, handler);
            spt.execute();
            spt.onProgressUpdate(getString(R.string.progress_title), "");
            list_mass_refresh = db.get_all_accounts_id();
            mass_refresh();
        }
    }

    /**
     * Handles new message checks to all participating accounts.
     **/
    public void mass_refresh() {
        if (list_mass_refresh.size() > 0) {
            Inbox inn = db.get_account(list_mass_refresh.get(0));
            list_mass_refresh.remove(0);
            if (inn.get_imap_or_pop()) {
                handler = new IMAP(this);
            } else {
                handler = new POP(this);
            }
            handler.sp = spt;
            handler.default_action(true, inn, this);
            handler.start();
        } else {
            spt.unblock = true;
            populate_accounts_list_view();
            set_current_inbox();
        }
    }

    public void set_current_inbox() {
        if (al_accounts_items != null) {
            if (al_accounts_items.size() == 0) {
                // No accounts exist, load nothing
                tv_pager_title.setText(getString(R.string.activity_pager_title).toUpperCase());
                tv_background.setText(getString(R.string.no_accounts));
                tv_background.setVisibility(View.VISIBLE);
                msg_list_view.setVisibility(View.GONE);
                tv_page_counter.setVisibility(View.GONE);
                iv_ssl_auth.setVisibility(View.GONE);
                ib_refresh.setVisibility(View.GONE);
                iv_send_activity.setVisibility(View.GONE);
            } else if (al_accounts_items.size() == 1) {
                // Set current inbox from the only account available
                current_inbox = al_accounts_items.get(0).get_id();
                current = db.get_account(current_inbox);
                tv_pager_title.setText(current.get_email());
                ib_refresh.setVisibility(View.VISIBLE);
                populate_messages_list_view();
            } else {
                if (current_inbox != -2) {
                    // Set current inbox with known current
                    current = db.get_account(current_inbox);
                    tv_pager_title.setText(current.get_email());
                    ib_refresh.setVisibility(View.VISIBLE);
                    populate_messages_list_view();
                } else {
                    // Many accounts, prompt for a selection
                    tv_pager_title.setText(getString(R.string.activity_pager_title).toUpperCase());
                    tv_background.setText(getString(R.string.select_account));
                    tv_background.setVisibility(View.VISIBLE);
                    msg_list_view.setVisibility(View.GONE);
                    tv_page_counter.setVisibility(View.GONE);
                    ib_refresh.setVisibility(View.GONE);
                    iv_ssl_auth.setVisibility(View.GONE);
                    iv_send_activity.setVisibility(View.GONE);
                }
            }
        }

        // Show/hide menu items
        invalidateOptionsMenu();
    }

    public void populate_messages_list_view() {
        al_messages = db.get_all_messages(current.get_id());

        // If there is any SMTP
        try {
            if (current.get_smtp_server().isEmpty()) {
                iv_send_activity.setVisibility(View.GONE);
            } else iv_send_activity.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            iv_send_activity.setVisibility(View.GONE);
        }

        tv_background.setText(getString(R.string.no_messages));

        if (al_messages.size() == 0) {
            tv_background.setVisibility(View.VISIBLE);
            msg_list_view.setVisibility(View.GONE);
        } else {
            tv_background.setVisibility(View.GONE);
            msg_list_view.setVisibility(View.VISIBLE);

            InboxMessageExpList msg_list_adapter = new InboxMessageExpList(this,
                    new ArrayList<>(al_messages.keySet()), al_messages);
            msg_list_view.setAdapter(msg_list_adapter);
            msg_list_view.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v,
                                            int group_pos, int child_pos, long id) {

                    Object key = al_messages.keySet().toArray()[group_pos];
                    Message m = al_messages.get(key).get(child_pos);

                    // Set unseen -> seen
                    if (!m.get_seen()) {
                        db.seen_unseen_message(m.get_account(), m.get_uid(), true);
                        m.set_seen(true);
                        ImageView imv = v.findViewById(R.id.message_list_title_unseen_mark);
                        imv.setVisibility(m.get_seen() ? View.GONE : View.VISIBLE);
                        db.seen_unseen_message(current_inbox, m.get_uid(), true);
                        update_unread_messages_count(current_inbox);
                    }

                    Intent i = new Intent(getApplicationContext(), InboxMessage.class);
                    Bundle b = new Bundle();
                    b.putInt("db_id", m.get_id());
                    b.putInt("db_inbox", current_inbox);
                    b.putString("title", current.get_email());
                    b.putBoolean("no_send", current.get_smtp_server().isEmpty());
                    b.putBoolean("imap_or_pop", current.get_imap_or_pop());
                    startActivityForResult(i.putExtras(b), 1000);
                    overridePendingTransition(R.anim.left_in, R.anim.left_out);

                    return false;
                }
            });
        }

        // Update message counter
        set_count_top();

        // Set server certificate details
        connection_security();
    }

    private void update_unread_messages_count(int account_id) {
        for (InboxListItem ilm : al_accounts_items) {
            if (ilm.get_id() == account_id) {
                ilm.set_count(db.count_unseen_account_messages(ilm.get_id()));
                inbox_adapter.notifyDataSetChanged();
                set_count_top();
            }
        }
    }

    private void set_count_top() {
        int i = current.get_unseen();

        // Checking for discrepancies
        int unseen_count = db.count_unseen_account_messages(current_inbox);
        if (i != unseen_count) {
            i = unseen_count;
            current.set_unseen(i);
        }

        String str = "000";
        if (i < 1) {
            tv_page_counter.setText(str);
            tv_page_counter.setVisibility(View.GONE);
            return;
        } else {
            tv_page_counter.setVisibility(View.VISIBLE);
        }
        if (i < 10) {
            str = "00" + i;
            tv_page_counter.setText(str);
        } if (i > 9 && i < 100) {
            str = "0" + i;
            tv_page_counter.setText(str);
        } else if (i > 100 && i <= 999) {
            str = String.valueOf(i);
            tv_page_counter.setText(str);
        } else if (i > 999) {
            str = "+999";
            tv_page_counter.setText(str);
        }
    }

    public void refresh_current() {
        // Prevents screen rotation crash
        handle_orientation(true);

        // Starting a spinning animation dialog
        SpinningStatus sp = new SpinningStatus(true, false, this, handler);
        sp.execute();
        sp.onProgressUpdate(getString(R.string.progress_title),
                getString(R.string.progress_refreshing));

        // Starting refresh INBOX
        if (current.get_imap_or_pop()) {
            handler = new IMAP(this);
        } else {
            handler = new POP(this);
        }
        handler.sp = sp;
        handler.start();
        handler.default_action(false, current, this);
    }

    @SuppressLint("WrongConstant")
    public void handle_orientation(boolean fixed_or_rotating) {
        if (fixed_or_rotating) {
            orientation = getResources().getConfiguration().orientation;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else setRequestedOrientation(orientation);
    }

    /**
     * Dialog account information - i.e. # messages, # unread.
     **/
    private void dialog_statistical() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.stats_title));
        String msg = current.get_messages() + " " + getString(R.string.stats_messages) + ", "
                + current.get_unseen() + " " + getString(R.string.stats_unseen) + ", ";
        int sz = current.get_total_size();
        String total_size = "";
        if (sz < 1024) {
            total_size += sz + " " + getString(R.string.attch_bytes);
        } else if (sz < 1048576) {
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

    private void connection_security() {
        if (handler == null) return;
        good_incoming_server = handler.get_hostname_verify();
        if (good_incoming_server) {
            if (handler != null && handler.get_last_connection_data() != null
                    && (handler.get_last_connection_data_id() == current.get_id())) {
                good_incoming_server = !handler.get_last_connection_data().isEmpty();
                iv_ssl_auth.setVisibility(View.VISIBLE);
                if (good_incoming_server) {
                    iv_ssl_auth.setImageResource(R.drawable.padlock_normal);
                } else {
                    iv_ssl_auth.setImageResource(R.drawable.padlock_error);
                }
            } else {
                good_incoming_server = false;
                iv_ssl_auth.setVisibility(View.GONE);
            }
        } else {
            iv_ssl_auth.setVisibility(View.VISIBLE);
            iv_ssl_auth.setImageResource(R.drawable.padlock_error);
            Dialogs.toaster(true, getString(R.string.err_action_failed), this);
        }
    }

    /**
     * Intermediaries' SSL certificates of the last live connection.
     **/
    private void dialog_servers() {
        if (good_incoming_server) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.ssl_auth_popup_title));
            builder.setCancelable(true);
            builder.setMessage(handler.get_last_connection_data());
            builder.show();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.ssl_auth_popup_title));
            builder.setMessage(getString(R.string.ssl_auth_popup_bad_connection));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.show();
        }
    }

    public static DBAccess get_db() {
        return db;
    }

    public static void notify_update() {
        boolean beeps = prefs.getBoolean("beeps", false);
        boolean vibrate = prefs.getBoolean("vibrates", false);
        if (beeps) beep.startTone(ToneGenerator.TONE_PROP_BEEP2);
        if (vibrate) vvv.vibrate(1000);
    }
}
