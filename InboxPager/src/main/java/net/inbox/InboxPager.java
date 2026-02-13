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

import static net.inbox.Common.set_activity_insets_listener;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.inbox.db.DBAccess;
import net.inbox.db.Inbox;
import net.inbox.db.Message;
import net.inbox.pager.R;
import net.inbox.visuals.Dialogs;
import net.inbox.visuals.SpinningStatus;
import net.inbox.server.NetworkThread;
import net.inbox.server.IMAP;
import net.inbox.server.POP;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.SortedMap;

public class InboxPager extends AppCompatActivity {

    public static String log = "\n";

    public static Vibrator vib;

    // Show first use help
    private boolean show_help = false;
    private boolean refresh;

    private static DBAccess db;
    private static SharedPreferences prefs;
    private static Boolean unread_focus_mode_state = false;

    private NetworkThread network_thread;
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
    private ImageView iv_unread_focus_mode;
    private ImageView iv_send_activity;
    private ImageView iv_ssl_auth;

    private int last_connection_data_id = -1;
    private String last_connection_data = null;

    private int current_inbox = -2;

    private Inbox current;

    private ActivityResultLauncher<Intent> start_activity_for_result;

    @Override
    protected void onCreate(Bundle saved_instance_state) {
        super.onCreate(saved_instance_state);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );

        // Set Application Activities to follow system style
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) // Android API >= 10
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        // For camera cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) // Android API >= 15
            EdgeToEdge.enable(this); // run before setContentView()

        // Restore existing state
        if (saved_instance_state != null) {
            log = saved_instance_state.getString("sv_log");
            unread_focus_mode_state = saved_instance_state.getBoolean("sv_unread_focus_mode_state");
            refresh = saved_instance_state.getBoolean("sv_refresh");
            unlocked = saved_instance_state.getBoolean("sv_unlocked");
            over = saved_instance_state.getInt("sv_over");
            show_help = saved_instance_state.getBoolean("sv_show_help");
            current_inbox = saved_instance_state.getInt("sv_current_inbox");
            if (current_inbox != -2 && db != null) {
                current = db.get_account(current_inbox); // Loading known ID inbox
            } else {
                current = null;
            }
            last_connection_data_id = saved_instance_state.getInt("sv_last_connection_data_id");
            last_connection_data = saved_instance_state.getString("sv_last_connection_data");
        }

        // Init SharedPreferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Common.set_prefs(prefs);
        if (!prefs.contains("initialized")) {
            PreferenceManager.setDefaultValues(this, R.xml.settings, false);
            prefs.edit().putBoolean("initialized", true).apply();

            // Initial values that don't have a preference screen
            prefs.edit().putBoolean("imap_or_pop", true).apply();
            prefs.edit().putBoolean("using_smtp", false).apply();
            prefs.edit().putBoolean("pw_protection", false).apply();
            prefs.edit().putBoolean("pw_emergency_protection", false).apply();
            prefs.edit().putString("pw_emergency_protection_hash", "").apply();
            prefs.edit().putString("list_cipher_types", "AES").apply();
            prefs.edit().putString("list_cipher_modes", "CBC").apply();
            prefs.edit().putString("list_cipher_paddings", "PKCS7").apply();

            show_help = true;
        }

        // Init Notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                getPackageName(),
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        if (unlocked && prefs.getBoolean("pw_protection", false)) {
            // Initial entry view
            View v = View.inflate(this, R.layout.pager, null);
            setContentView(v);

            DrawerLayout llay_drawer = findViewById(R.id.drawer_layout);
            llay_drawer.setVisibility(View.VISIBLE);
            llay_drawer.setAlpha(0.01f);
            llay_drawer.animate().alpha(1f).setListener(
                new AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        activity_load();
                    }
                }
            );
        } else if (show_help || !prefs.getBoolean("pw_protection", false)) {
            init_db("cleartext");

            // Initial entry view
            View v = View.inflate(this, R.layout.pager, null);
            v.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
            setContentView(v);

            DrawerLayout llay_drawer = findViewById(R.id.drawer_layout);
            llay_drawer.setVisibility(View.VISIBLE);
            llay_drawer.setAlpha(0.01f);
            llay_drawer.animate().alpha(1f).setListener(
                new AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        activity_load();
                    }
                }
            );
        } else {
            // Initial entry view
            View v = View.inflate(this, R.layout.pager, null);
            v.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
            setContentView(v);

            // Entry text edit
            llay_pw = findViewById(R.id.llay_pw);
            llay_pw.setVisibility(View.VISIBLE);
            et_pw = findViewById(R.id.pw);
            et_pw.setOnKeyListener(
                (v1, key, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                        && key == KeyEvent.KEYCODE_ENTER
                    ) {
                        boolean em = false;
                        String t = et_pw.getText().toString();
                        String h = prefs.getString("pw_emergency_protection_hash", "");

                        if (prefs.getBoolean("pw_emergency_protection", false)
                            && !h.isEmpty()
                        ) {
                            // Check emergency password hash matches, do sha256
                            if (Common.sha256(getApplicationContext(), et_pw.getText().toString())
                                .equals(h)
                            ) {
                                getApplicationContext().deleteDatabase("pages");
                                prefs.edit().putBoolean("pw_protection", false).apply();
                                prefs.edit().putBoolean("pw_emergency_protection", false).apply();
                                prefs.edit().putString("pw_emergency_protection_hash", "").apply();
                                em = true;
                                unlocked = true;
                            }
                        }

                        if (em) {
                            init_db("cleartext");
                        } else {
                            init_db(t);
                        }

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
            );
        }

        // Helper dialog
        if (show_help) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(true);
            builder.setTitle(getString(R.string.helper_title));
            builder.setMessage(getString(R.string.helper_msg));
            builder.setPositiveButton(getString(R.string.btn_continue), null);
            builder.setNegativeButton(
                getString(R.string.btn_pw),
                (dialog, which) -> {
                    startActivity(new Intent(getApplicationContext(), AppPreferences.class));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
                        overrideActivityTransition(
                            OVERRIDE_TRANSITION_OPEN,
                            R.anim.right_in,
                            R.anim.right_out
                        );
                    } else { // Android API <= 33
                        overridePendingTransition(R.anim.right_in, R.anim.right_out);
                    }
                }
            );
            builder.show();
        }

        RelativeLayout main_root = findViewById(R.id.root_view_pager);

        // Handle insets for cutout and system bars
        set_activity_insets_listener(main_root);

        start_activity_for_result = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                int result_code = result.getResultCode();
                Intent data = result.getData();
                if (result_code == 12) { // Add or Edit Account
                    if (refresh) {
                        refresh = false;
                        populate_accounts_list_view();
                    } else {
                        if (data.getBooleanExtra("status", false)) {
                            current.set_email(data.getStringExtra("new_name"));
                            tv_pager_title.setText(data.getStringExtra("new_name"));
                            populate_accounts_list_view();
                        }
                        int new_inbox = data.getIntExtra("new_inbox_id", -2);
                        if (new_inbox >= 0) {
                            current_inbox = new_inbox;
                            set_current_inbox();
                        }
                    }
                } else if (result_code == 24) { // Delete Account
                    if (data != null && data.hasExtra("inbox_deleted")
                        && data.getBooleanExtra("inbox_deleted", false)
                    ) {
                        populate_accounts_list_view();
                        current_inbox = -2;
                        set_current_inbox();
                    }
                } else if (result_code == 36) { // Delete Message
                    populate_messages_list_view(); // ListView re-populate after message deletion
                } else if (result_code == 48) { // Reply to Message (Send)
                    Intent send_intent = new Intent(getApplicationContext(), InboxSend.class);
                    //Bundle b = new Bundle();
                    Bundle b = data.getExtras();
                    b.putInt("db_id", current.get_id());
                    b.putString("title", current.get_email());
                    startActivity(send_intent.putExtras(b));
                }
            }
        );
    }

    @Override
    public void finish() {
        super.finish();
        DrawerLayout llay_drawer = findViewById(R.id.drawer_layout);
        if (llay_drawer.isDrawerOpen(GravityCompat.START)) {
            llay_drawer.closeDrawer(GravityCompat.START);
        }

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
        int item_id = item.getItemId();

        // if-statement ONLY, switch not allowed, anymore.
        if (item_id == R.id.about_menu) {
            startActivity(new Intent(getApplicationContext(), About.class));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
                overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    R.anim.right_in,
                    R.anim.right_out
                );
            } else { // Android API <= 33
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
            }
        } else if (item_id == R.id.add_menu) {
            Intent i = new Intent(getApplicationContext(), InboxPreferences.class);
            Bundle b = new Bundle();
            b.putBoolean("add", true);
            b.putInt("db_id", -1);
            start_activity_for_result.launch(i.putExtras(b));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API>= 34
                overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    R.anim.right_in,
                    R.anim.right_out
                );
            } else { // Android API <= 33
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
            }
        } else if (item_id == R.id.mass_refresh_menu) {
            mass_refresh_check();
        } else if (item_id == R.id.log_menu) {
            Dialogs.dialog_view_log(this);
        } else if (item_id == R.id.defaults_menu) {
            startActivity(new Intent(getApplicationContext(), AppPreferences.class));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
                overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    R.anim.right_in,
                    R.anim.right_out
                );
            } else { // Android API <= 33
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
            }
        } else if (item_id == R.id.status_menu) {
            dialog_statistical();
        } else if (item_id == R.id.mark_all_seen_menu) {
            db.mark_all_seen(current.get_id());
            set_count_top();
            populate_accounts_list_view();
            populate_messages_list_view();
        } else if (item_id == R.id.edit_account) {
            Intent ii = new Intent(getApplicationContext(), InboxPreferences.class);
            Bundle bb = new Bundle();
            bb.putBoolean("add", false);
            bb.putInt("db_id", current.get_id());
            bb.putString("title", current.get_email());
            start_activity_for_result.launch(ii.putExtras(bb));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
                overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    R.anim.right_in,
                    R.anim.right_out
                );
            } else { // Android API <= 33
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
            }
        }

        return true;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle saved_instance_state) {
        super.onSaveInstanceState(saved_instance_state);
        saved_instance_state.putString("sv_log", log);
        saved_instance_state.putBoolean("sv_unread_focus_mode_state", unread_focus_mode_state);
        saved_instance_state.putBoolean("sv_refresh", refresh);
        saved_instance_state.putBoolean("sv_unlocked", unlocked);
        saved_instance_state.putInt("sv_over", over);
        saved_instance_state.putBoolean("sv_show_help", show_help);
        saved_instance_state.putInt("sv_current_inbox", current_inbox);
        saved_instance_state.putInt("sv_last_connection_data_id", last_connection_data_id);
        saved_instance_state.putString("sv_last_connection_data", last_connection_data);
    }

    private void init_db(String s) {
        SQLiteDatabase.loadLibs(this);

        // Initializing database
        db = new DBAccess(this);
        try {
            if (getDatabasePath("pages").exists()) {
                db.activate_db(s);
            } else {
                db.activate_db("cleartext");
            }
            db.vacuum_db();
            unlocked = true;
        } catch (Exception e) {
            log = log.concat(e.getMessage() + "\n\n");
            e.printStackTrace(); // crash
            unlocked = false;
            if (et_pw != null) {
                et_pw.setBackgroundColor(Color.parseColor("#BA0C0C"));
                et_pw.setHintTextColor(Color.WHITE);
            }
        }
    }

    private void fade_in_ui() {
        // UI entry appearance
        llay_pw.animate().alpha(0f).setListener(
            new AnimatorListenerAdapter() {

                @Override
                public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                DrawerLayout llay_drawer = findViewById(R.id.drawer_layout);
                llay_pw.setVisibility(View.GONE);
                llay_drawer.setAlpha(0.01f);
                llay_drawer.setVisibility(View.VISIBLE);
                llay_drawer.animate().alpha(1f).setListener(
                    new AnimatorListenerAdapter() {

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                        }
                    }
                );
                }
            }
        );
    }

    private void activity_load() {
        // Init vibrations
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

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
        ib_refresh.setOnClickListener(
            v -> {
                if (current_inbox != -2) refresh_current();
            }
        );

        // No accounts message is visible if the user has not init-ed the app
        tv_background = findViewById(R.id.text_background);
        tv_background.setOnClickListener(v -> drawer_flip());

        // Setting up the SSL authentication application
        iv_ssl_auth = findViewById(R.id.ssl_auth_img_vw);
        iv_ssl_auth.setOnClickListener(v -> dialog_servers());
        if (last_connection_data_id > -1 && last_connection_data_id == current_inbox) {
            iv_ssl_auth.setVisibility(View.VISIBLE); // restore connection security icon
            if (last_connection_data != null && !last_connection_data.isEmpty())
                iv_ssl_auth.setImageResource(R.drawable.padlock_normal);
            else
                iv_ssl_auth.setImageResource(R.drawable.padlock_error);
        } else {
            iv_ssl_auth.setVisibility(View.GONE);
        }

        // ListView for Messages
        msg_list_view = findViewById(R.id.msg_list_view);

        // Floating unread messages' focus mode
        iv_unread_focus_mode = findViewById(R.id.iv_unread_focus_mode);
        iv_unread_focus_mode.setVisibility(View.VISIBLE);
        iv_unread_focus_mode.setOnClickListener(
            view -> {
                if (unread_focus_mode_state) {
                    unread_focus_mode_state = false;
                    iv_unread_focus_mode.setImageResource(R.drawable.focus_mode_allmsg);
                } else {
                    unread_focus_mode_state = true;
                    iv_unread_focus_mode.setImageResource(R.drawable.focus_mode_unread);
                }
                populate_messages_list_view();
            }
        );
        iv_unread_focus_mode.setImageResource(
            unread_focus_mode_state ? R.drawable.focus_mode_unread : R.drawable.focus_mode_allmsg
        );

        // Floating Send Suggestion
        iv_send_activity = findViewById(R.id.iv_send_activity);
        iv_send_activity.setVisibility(View.VISIBLE);
        iv_send_activity.setOnClickListener(
            view -> {
                Intent in = new Intent(getApplicationContext(), InboxSend.class);
                Bundle bn = new Bundle();
                bn.putInt("db_id", current.get_id());
                bn.putString("title", current.get_email());
                startActivity(in.putExtras(bn));
                overridePendingTransition(R.anim.left_in, R.anim.left_out);
            }
        );

        llay_drawer = findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle drawer_toggle = new ActionBarDrawerToggle(
        this, llay_drawer, tb, R.string.drawer_open, R.string.drawer_close
        );
        llay_drawer.addDrawerListener(drawer_toggle);
        drawer_toggle.syncState();
        drawer_toggle.setDrawerIndicatorEnabled(false);
        drawer_toggle.setHomeAsUpIndicator(R.drawable.drawer);
        drawer_toggle.setToolbarNavigationClickListener(v -> drawer_flip());

        // Filling the ListView accounts in the drawer
        inbox_list_view = findViewById(R.id.list_view_drawer);
        populate_accounts_list_view();

        // Set and load current account
        set_current_inbox();
    }

    private void drawer_flip() {
        if (llay_drawer.isDrawerVisible(GravityCompat.START)) {
            llay_drawer.closeDrawer(GravityCompat.START);
        } else {
            llay_drawer.openDrawer(GravityCompat.START);
        }
    }

    public void populate_accounts_list_view() {
        ArrayList<Inbox> list_accounts = db.get_all_accounts();

        if (list_accounts.isEmpty()) {
            current_inbox = -2;
            al_accounts_items = new ArrayList<>();
            tv_background.setText(getString(R.string.no_accounts));
            tv_background.setVisibility(View.VISIBLE);
            tv_pager_title.setText("");

            if (inbox_list_view != null) inbox_list_view.setAdapter(null);
        } else {
            // Update data set
            if (!al_accounts_items.isEmpty()) {
                al_accounts_items.clear();
            }

            // Sort accounts' list
            Collections.sort(
                list_accounts,
                (inn1, inn2) -> inn1.get_email().compareTo(inn2.get_email())
            );

            for (int i = 0; i < list_accounts.size(); i++) {
                // Check and update unseen message counts
                Inbox nfo = list_accounts.get(i);
                int unseen_count = db.count_unseen_account_messages(nfo.get_id());
                if (unseen_count != nfo.get_unseen()) {
                    list_accounts.get(i).set_unseen(unseen_count);
                    nfo.set_unseen(unseen_count);
                }

                al_accounts_items.add(
                    new InboxListItem(nfo.get_id(), nfo.get_email(), nfo.get_unseen())
                );
            }

            // Add list adapter
            if (inbox_list_view.getAdapter() == null) {
                inbox_adapter = new InboxList(this, al_accounts_items);
                inbox_list_view.setAdapter(inbox_adapter);
            }

            inbox_adapter.notifyDataSetChanged();
            inbox_list_view.setOnItemClickListener((parent, v, position, id) -> {
                InboxListItem inbox_itm = (InboxListItem) parent.getItemAtPosition(position);
                current_inbox = inbox_itm.get_id();
                set_current_inbox();
            });
        }
    }

    public void mass_refresh_check() {
        if (list_mass_refresh.isEmpty()) {
            // Prevents screen rotation crash
            Common.fixed_or_rotating_orientation(true, this);

            // Starting an animated dialog
            spt = new SpinningStatus(true, true, this, network_thread);
            spt.set_progress(getString(R.string.progress_title), "");
            list_mass_refresh = db.get_all_accounts_id();
            mass_refresh();
        }
    }

    /**
     * Handles new message checks to all participating accounts.
     **/
    public void mass_refresh() {
        if (!list_mass_refresh.isEmpty()) {
            Inbox inn = db.get_account(list_mass_refresh.get(0));
            list_mass_refresh.remove(0);
            if (inn.get_imap_or_pop()) {
                network_thread = new IMAP(this);
            } else {
                network_thread = new POP(this);
            }
            network_thread.sp = spt;
            network_thread.default_action(true, inn, this);
            network_thread.start();
        } else {
            spt.do_after();
            populate_accounts_list_view();
            set_current_inbox();
        }
    }

    public void set_current_inbox() {
        if (al_accounts_items != null) {
            if (al_accounts_items.isEmpty()) {
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
        if (current != null) {
            al_messages = db.get_all_messages(current.get_id(), unread_focus_mode_state);
        }

        // If there is any SMTP
        try {
            if (current.get_smtp_server().isEmpty()) {
                iv_send_activity.setVisibility(View.GONE);
            } else iv_send_activity.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            iv_send_activity.setVisibility(View.GONE);
        }

        tv_background.setText(getString(R.string.no_messages));

        if (al_messages == null || al_messages.isEmpty()) {
            tv_background.setVisibility(View.VISIBLE);
            msg_list_view.setVisibility(View.GONE);
        } else {
            tv_background.setVisibility(View.GONE);
            msg_list_view.setVisibility(View.VISIBLE);

            InboxMessageExpList msg_list_adapter = new InboxMessageExpList(
                this,
                new ArrayList<>(al_messages.keySet()),
                al_messages
            );
            msg_list_view.setAdapter(msg_list_adapter);
            msg_list_view.setOnChildClickListener(
                (parent, v, group_pos, child_pos, id) -> {
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
                    start_activity_for_result.launch(i.putExtras(b));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android API >= 34
                        overrideActivityTransition(
                            OVERRIDE_TRANSITION_OPEN,
                            R.anim.left_in,
                            R.anim.left_out
                        );
                    } else { // Android API <= 33
                        overridePendingTransition(R.anim.left_in, R.anim.left_out);
                    }

                    return false;
                }
            );
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
        if (current != null) {
            int i = current.get_unseen();

            // Checking for discrepancies
            int unseen_count = db.count_unseen_account_messages(current_inbox);
            if (i != unseen_count) {
                i = unseen_count;
                current.set_unseen(i);
            }

            if (i < 1) {
                tv_page_counter.setText("000");
                tv_page_counter.setVisibility(View.GONE);
                return;
            } else {
                tv_page_counter.setVisibility(View.VISIBLE);
            }
            if (i < 10) {
                tv_page_counter.setText("00" + i);
            } if (i > 9 && i < 100) {
                tv_page_counter.setText("0" + i);
            } else if (i > 100 && i <= 999) {
                tv_page_counter.setText(String.valueOf(i));
            } else if (i > 999) {
                tv_page_counter.setText("+999");
            }
        } else {
            tv_page_counter.setVisibility(View.GONE);
        }
    }

    public void refresh_current() {
        // Prevents screen rotation crash
        Common.fixed_or_rotating_orientation(true, this);

        // Starting a spinning animation dialog
        SpinningStatus sp = new SpinningStatus(true, false, this, network_thread);
        sp.set_progress(
            getString(R.string.progress_title),
            getString(R.string.progress_refreshing)
        );

        // Starting refresh INBOX
        if (current.get_imap_or_pop()) {
            network_thread = new IMAP(this);
        } else {
            network_thread = new POP(this);
        }
        network_thread.sp = sp;
        network_thread.start();
        network_thread.default_action(false, current, this);
    }

    /**
     * Dialog account information - i.e. # messages, # unread, # bytes.
     **/
    private void dialog_statistical() {
        int sz = current.get_total_size();
        String total_size;
        if (sz < 1024) {
            total_size = sz + " " + getString(R.string.attch_bytes);
        } else if (sz < 1048576) {
            total_size = (sz/1024) + " " + getString(R.string.attch_kilobytes);
        } else {
            total_size = (sz/1048576) + " " + getString(R.string.attch_megabytes);
        }

        String msg = String.format(
            Locale.getDefault(),
            "%d %s, %s %s, %s",
            current.get_messages(),
            getString(R.string.stats_messages),
            current.get_unseen(),
            getString(R.string.stats_unseen),
            total_size
        );

        Dialogs.dialog_simple(getString(R.string.stats_title), msg, this);
    }

    public void connection_security() {
        if (network_thread == null) return;
        last_connection_data_id = network_thread.last_connection_data_id;
        last_connection_data = network_thread.last_connection_data;
        if (last_connection_data_id > -1) {
            if (network_thread.last_connection_data_id == current_inbox) {
                iv_ssl_auth.setVisibility(View.VISIBLE);
                if (!network_thread.last_connection_data.isEmpty()) {
                    iv_ssl_auth.setImageResource(R.drawable.padlock_normal);
                } else {
                    iv_ssl_auth.setImageResource(R.drawable.padlock_error);
                }
            } else {
                iv_ssl_auth.setVisibility(View.GONE);
            }
        } else {
            iv_ssl_auth.setVisibility(View.GONE);
            iv_ssl_auth.setImageResource(R.drawable.padlock_error);
            Dialogs.toaster(false, getString(R.string.err_action_failed), this);
        }
    }

    /**
     * Intermediaries' SSL certificates of the last live connection.
     **/
    private void dialog_servers() {
        Dialogs.dialog_simple(
            getString(R.string.ssl_auth_popup_title),
            last_connection_data == null ? getString(R.string.ssl_auth_popup_bad_connection)
                : last_connection_data,
            this
        );
    }

    public static DBAccess get_db() {
        return db;
    }
}
