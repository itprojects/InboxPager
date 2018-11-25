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
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.inbox.db.DBAccess;
import net.inbox.db.Inbox;
import net.inbox.dialogs.Dialogs;
import net.inbox.dialogs.SpinningStatus;
import net.inbox.server.Handler;
import net.inbox.server.IMAP;
import net.inbox.server.POP;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

public class Pager extends AppCompatActivity {

    public static boolean refresh;
    public static String log;

    // Show first use help
    private boolean show_help = false;

    public static Typeface tf;
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
    private RelativeLayout rv_main;
    private TextView tv_page_counter;
    private TextView tv_no_account;
    private ListView inbox_list_view;

    private InboxList inbox_adapter;
    private ArrayList<Integer> list_mass_refresh = new ArrayList<>();
    private ArrayList<InboxListItem> al_accounts_items = new ArrayList<>();

    private SpinningStatus spt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        // Restore existing state
        if (savedInstanceState != null) {
            refresh = savedInstanceState.getBoolean("sv_refresh");
            log = savedInstanceState.getString("sv_log");
            unlocked = savedInstanceState.getBoolean("sv_unlocked");
            over = savedInstanceState.getInt("sv_over");
            show_help = savedInstanceState.getBoolean("sv_show_help");
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

            rv_main = findViewById(R.id.app_main);
            rv_main.setVisibility(View.VISIBLE);
            rv_main.setAlpha(0.01f);
            rv_main.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {

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

            rv_main = findViewById(R.id.app_main);
            rv_main.setVisibility(View.VISIBLE);
            rv_main.setAlpha(0.01f);
            rv_main.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {

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
            rv_main = findViewById(R.id.app_main);
            et_pw = findViewById(R.id.pw);
            et_pw.setOnKeyListener((v1, key, event) -> {
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
            });
        }

        // Helper dialog
        if (show_help) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(true);
            builder.setTitle(getString(R.string.helper_title));
            builder.setMessage(getString(R.string.helper_msg));
            builder.setPositiveButton(getString(android.R.string.ok), null);
            builder.setNegativeButton(getString(R.string.btn_pw), (dialog, which) -> {
                startActivity(new Intent(getApplicationContext(), Settings.class));
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
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
        inflater.inflate(R.menu.home_action_btns, menu);
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
            case R.id.log_menu:
                Dialogs.dialog_view_log(this);
                break;
            case R.id.defaults_menu:
                startActivity(new Intent(getApplicationContext(), Settings.class));
                overridePendingTransition(R.anim.right_in, R.anim.right_out);
                break;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 || requestCode == 10) {
            if (refresh) {
                refresh = false;
                populate_list_view();
            } else if (resultCode == Activity.RESULT_OK) {
                if (data.getBooleanExtra("status", false)) populate_list_view();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle save) {
        super.onSaveInstanceState(save);
        save.putBoolean("sv_refresh", refresh);
        save.putString("sv_log", log);
        save.putBoolean("sv_unlocked", unlocked);
        save.putInt("sv_over", over);
        save.putBoolean("sv_show_help", show_help);
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
                llay_pw.setVisibility(View.GONE);
                rv_main.setAlpha(0.01f);
                rv_main.setVisibility(View.VISIBLE);
                rv_main.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {

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

        Toolbar tb = findViewById(R.id.home_toolbar);
        setSupportActionBar(tb);

        tf = Typeface.createFromAsset(getAssets(), "fonts/Dottz.ttf");

        // Find the title
        TextView tv_t;
        for (int i = 0; i < tb.getChildCount(); ++i) {
            int idd = tb.getChildAt(i).getId();
            if (idd == -1) {
                tv_t = (TextView) tb.getChildAt(i);
                tv_t.setTextColor(ContextCompat.getColor(this, R.color.color_title));
                tv_t.setTypeface(tf);
                break;
            }
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.activity_pager_title).toUpperCase());
        }

        // Unread Messages Counter
        tv_page_counter = findViewById(R.id.page_counter);
        tv_page_counter.setTypeface(tf);

        // Mass Refresh Button
        ImageButton iv_refresh = findViewById(R.id.refresh);
        iv_refresh.setOnClickListener(v -> mass_refresh_check());

        // No accounts message is visible if the user has not init-ed the app
        tv_no_account = findViewById(R.id.no_accounts);
        tv_no_account.setTypeface(tf);

        // Filling the ListView of the home window
        inbox_list_view = findViewById(R.id.accounts_list_view);
        populate_list_view();
    }

    public void populate_list_view() {
        ArrayList<Inbox> list_accounts = db.get_all_accounts();

        if (list_accounts.size() == 0) {
            tv_no_account.setVisibility(View.VISIBLE);

            if (inbox_list_view != null) inbox_list_view.setAdapter(null);
        } else {
            tv_no_account.setVisibility(View.GONE);

            // Update data set
            if (al_accounts_items.size() > 0) {
                al_accounts_items.clear();
            }

            // Sort accounts' list
            Collections.sort(list_accounts, (inn1, inn2) -> inn1.get_email().compareTo(inn2.get_email()));

            for (int i = 0; i < list_accounts.size(); i++) {
                Inbox nfo = list_accounts.get(i);
                al_accounts_items.add(new InboxListItem(nfo.get_id(), nfo.get_email(),
                        nfo.get_unseen()));
            }

            // Add list adapter
            if (inbox_list_view.getAdapter() == null) {
                inbox_adapter = new InboxList(this, al_accounts_items);
                inbox_list_view.setAdapter(inbox_adapter);
            }

            inbox_adapter.notifyDataSetChanged();
            inbox_list_view.setOnItemClickListener((parent, v, position, id) -> {
                InboxListItem inbox_itm = (InboxListItem) parent.getItemAtPosition(position);
                Intent i = new Intent(getApplicationContext(), InboxUI.class);
                Bundle b = new Bundle();
                b.putInt("db_id", inbox_itm.get_id());
                startActivityForResult(i.putExtras(b), 10);
                overridePendingTransition(R.anim.left_in, R.anim.left_out);
            });
        }

        // Update message counter
        set_count();
    }

    private void set_count() {
        int i = db.get_global_unseen_count();
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
        }
        if (i > 9 && i < 100) {
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

    public void mass_refresh_check() {
        if (list_mass_refresh.size() < 1) {
            // Starting a spinning animation dialog
            spt = new SpinningStatus(true, this, handler);
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
            populate_list_view();
        }
    }

    public static void notify_update() {
        boolean beeps = prefs.getBoolean("beeps", false);
        boolean vibrate = prefs.getBoolean("vibrates", false);
        if (beeps) beep.startTone(ToneGenerator.TONE_PROP_BEEP2);
        if (vibrate) vvv.vibrate(1000);
    }

    public static DBAccess get_db() {
        return db;
    }
}
