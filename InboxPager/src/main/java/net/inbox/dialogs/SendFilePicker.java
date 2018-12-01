/*
 * InboxPager, an android email client.
 * Copyright (C) 2018  ITPROJECTS
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import net.inbox.InboxPager;
import net.inbox.R;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;

public class SendFilePicker extends AppCompatActivity {

    private boolean warned_8_bit_absent = false;
    private boolean warned_files_too_big = false;

    private long attachments_size = 0;
    private long total_size_limit = 0;

    private String s_chosen_folder;
    private String s_temporary_uri;

    private File cwd_path;
    private File[] cwd_files;
    private SendFileList picked_adapter;
    private ArrayList<SendFileItem> picker_listings = new ArrayList<>();
    private ArrayList<SendFileItem> picked_attachments = new ArrayList<>();
    private ArrayList<String> s_attachment_paths = new ArrayList<>();

    private ListView list_view_picked_attachments;
    private ListView list_view_picker;

    private TextView tv_location;

    private View current_layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.file_picker);

        try {
            // Restore existing state
            if (savedInstanceState != null) {
                warned_8_bit_absent = savedInstanceState.getBoolean("sv_warned_8_bit_absent");
                warned_files_too_big = savedInstanceState.getBoolean("sv_warned_files_too_big");
                cwd_path = new File(savedInstanceState.getString("sv_current_path"));
                attachments_size = savedInstanceState.getLong("sv_attachments_size");
                total_size_limit = savedInstanceState.getLong("sv_total_size_limit");
                s_attachment_paths = savedInstanceState.getStringArrayList("sv_s_attachment_paths");
                s_temporary_uri = savedInstanceState.getString("sv_s_temporary_uri");
                s_chosen_folder = savedInstanceState.getString("sv_s_chosen_folder");
            } else {
                // Animation parameters
                current_layout = this.findViewById(R.id.picker_activity);
                current_layout.setVisibility(View.INVISIBLE);

                ViewTreeObserver viewTreeObserver = current_layout.getViewTreeObserver();
                if (viewTreeObserver.isAlive()) {
                    viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

                        @Override
                        public void onGlobalLayout() {
                            animation_in();
                            current_layout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    });
                }

                // Activity first start
                if (getIntent().getExtras() != null) {
                    if (getIntent().getExtras().containsKey("b_8_bit_absent") && !warned_8_bit_absent) {
                        warned_8_bit_absent = true;
                        getIntent().getExtras().getBoolean("b_8_bit_absent");
                        Dialogs.dialog_error_line(getString(R.string.err_no_8_bit_mime), this);
                    }
                    attachments_size = getIntent().getExtras().getLong("l_attachment_size", 0);
                    total_size_limit = getIntent().getExtras().getLong("l_total_size_limit", 0);
                    s_attachment_paths = getIntent().getExtras().getStringArrayList("str_array_paths");
                }

                // Initial current working directory
                if (Environment.getExternalStorageDirectory().exists()) {
                    cwd_path = Environment.getExternalStorageDirectory();
                } else {
                    cwd_path = new File("/");
                }
            }

            Toolbar tb = findViewById(R.id.picker_toolbar);
            setSupportActionBar(tb);

            // Find the title
            TextView pick_title = tb.findViewById(R.id.picker_title);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowHomeEnabled(false);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                pick_title.setText(getString(R.string.send_attachments).toUpperCase());
            }

            final TabLayout llay_tabs = findViewById(R.id.llay_tabs);
            llay_tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {

                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (tab.getPosition() == 0) {
                        list_view_picked_attachments.setVisibility(View.VISIBLE);
                        list_view_picker.setVisibility(View.GONE);
                        load_picked();
                    } else {
                        list_view_picked_attachments.setVisibility(View.GONE);
                        list_view_picker.setVisibility(View.VISIBLE);
                        cwd_files = cwd_path.listFiles();
                        prepare_adapter();
                    }
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            });

            picked_adapter = new SendFileList(true, this, picked_attachments);
            list_view_picked_attachments = findViewById(R.id.list_view_picked_attachments);
            list_view_picked_attachments.setAdapter(picked_adapter);
            load_picked();

            TextView tv_picker_save = findViewById(R.id.tv_picker_save);
            tv_picker_save.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    Intent data = new Intent();
                    data.putStringArrayListExtra("attachments", s_attachment_paths);
                    setResult(RESULT_OK, data);
                    onBackPressed();
                }
            });

            TextView tv_picker_up = findViewById(R.id.tv_picker_up);
            tv_picker_up.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    if (cwd_path.getParent() != null && cwd_path.isDirectory()) {
                        cwd_path = new File(cwd_path.getParent());
                        cwd_files = cwd_path.listFiles();
                        prepare_adapter();
                    }
                }
            });

            ImageButton ib_picker_home = findViewById(R.id.ib_picker_home);
            ib_picker_home.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    if (Environment.getExternalStorageDirectory().exists()) {
                        cwd_path = Environment.getExternalStorageDirectory();
                    } else {
                        cwd_path = new File("/");
                    }
                    cwd_files = cwd_path.listFiles();
                    prepare_adapter();
                }
            });

            tv_location = findViewById(R.id.tv_location);
            tv_location.setText(cwd_path.getAbsolutePath());

            list_view_picker = findViewById(R.id.list_view_picker);
            list_view_picker.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    SendFileItem itm = (SendFileItem) parent.getItemAtPosition(position);
                    if (!itm.get_file_or_directory()) {
                        cwd_path = new File(itm.get_file_uri());
                        cwd_files = cwd_path.listFiles();
                        prepare_adapter();
                    }
                }
            });
        } catch (Exception e) {
            InboxPager.log += e.getMessage() + "\n\n";
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle save) {
        super.onSaveInstanceState(save);
        save.putBoolean("sv_warned_8_bit_absent", warned_8_bit_absent);
        save.putBoolean("sv_warned_files_too_big", warned_files_too_big);
        save.putLong("sv_attachments_size", attachments_size);
        save.putLong("sv_total_size_limit", total_size_limit);
        if (cwd_path == null) {
            save.putString("sv_current_path", "");
        } else {
            save.putString("sv_current_path", cwd_path.getAbsolutePath());
        }
        save.putStringArrayList("sv_s_attachment_paths", s_attachment_paths);
        save.putString("sv_s_temporary_uri", s_temporary_uri);
        save.putString("sv_s_chosen_folder", s_chosen_folder);
    }

    @Override
    public void onBackPressed() {
        animation_out();
    }

    protected void animation_in() {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            float circle_radius = (float) (Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels));
            Animator circularReveal = ViewAnimationUtils.createCircularReveal(current_layout,
                    displayMetrics.widthPixels/2,
                    displayMetrics.heightPixels/2,
                    0, circle_radius);
            circularReveal.setDuration(350);
            circularReveal.setInterpolator(new AccelerateInterpolator());
            current_layout.setVisibility(View.VISIBLE);
            circularReveal.start();
    }

    protected void animation_out() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        float circle_radius = (float) (Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels));
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(current_layout,
                displayMetrics.widthPixels/2,
                displayMetrics.heightPixels/2,
                circle_radius, 0);
        circularReveal.setDuration(350);
        circularReveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                current_layout.setVisibility(View.INVISIBLE);
                finish();
            }
        });
        circularReveal.start();
    }

    private String s_file_size(long sz) {
        if (sz == 0) {
            return "0 " + getString(R.string.attch_bytes);
        } else if (sz < 0) {
            return "? " + getString(R.string.attch_bytes);
        } else {
            if (sz < 1000) {
                return sz + " " + getString(R.string.attch_bytes);
            } else if (sz < 1000000) {
                return  (sz/1000) + " " + getString(R.string.attch_kilobytes);
            } else {
                return (sz/1000000) + " " + getString(R.string.attch_megabytes);
            }
        }
    }

    private void calc_total_files_size() {
        attachments_size = 0;
        for (SendFileItem ffi : picked_attachments) attachments_size += ffi.get_file_size_l();
    }

    private void prepare_adapter() {
        if (cwd_files != null) {
            picker_listings.clear();
            for (File f : cwd_files) {
                if (f.isFile()) {
                    SendFileItem ff = new SendFileItem(true, f.length(),
                            s_file_size(f.length()), f.getName(), f.getAbsolutePath(), "");

                    picker_listings.add(0, ff);
                } else if (f.isDirectory()) {
                    picker_listings.add(0, new SendFileItem(false, 0,
                            "", f.getName(), f.getAbsolutePath(), ""));
                }
            }
            list_view_picker.setAdapter(null);
            list_view_picker.setAdapter(new SendFileList(false, this, picker_listings));
            tv_location.setText(cwd_path.getAbsolutePath());
        }
    }

    private void load_picked() {
        if (s_attachment_paths != null) {
            picked_attachments.clear();
            for (String s : s_attachment_paths) {
                try {
                    File f = new File(s);
                    picked_attachments.add(0, new SendFileItem(true,
                            f.length(), s_file_size(f.length()), f.getName(), f.getAbsolutePath(),
                            URLConnection.guessContentTypeFromName(f.getName())));
                } catch (Exception e) {
                    InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                }
            }
            picked_adapter.notifyDataSetChanged();
        }
    }

    public void add_or_remove_attachment(boolean add_or_remove, String uri) {
        if (add_or_remove) {
            // Adding file attachment
            // Missing read permission check
            File f_add = new File(uri);
            if (!f_add.canRead()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.err_title_android_permission));
                builder.setMessage(getString(R.string.err_read_file));
                builder.setPositiveButton(getString(android.R.string.ok), null);
                builder.show();
                return;
            }
            // File can be read
            // Check file is not duplicate
            for (SendFileItem ffi: picked_attachments) {
                if (ffi.get_file_uri().equals(uri)) {
                    Dialogs.dialog_error_line(getString(R.string.err_title_file_is_duplicate), this);
                    return;
                }
            }
            // File is not a duplicate
            // Over-sized SMTP server message quota check
            calc_total_files_size();
            if (((attachments_size + f_add.length()) >= total_size_limit)
                    && !warned_files_too_big) {
                // Server will refuse the message, attachments too big
                s_temporary_uri = uri;
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.err_size_attachments_title));
                builder.setMessage(getString(R.string.err_size_attachments));
                builder.setPositiveButton(getString(R.string.err_size_attachments_continue),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                warned_files_too_big = true;
                                add_or_remove_attachment_directly(true, s_temporary_uri);
                            }
                        }
                );
                builder.setNegativeButton(getString(android.R.string.cancel), null);
                builder.show();
            } else add_or_remove_attachment_directly(true, uri);
        } else {
            add_or_remove_attachment_directly(false, uri);
        }
    }

    public void add_or_remove_attachment_directly(boolean adding, String uri) {
        if (adding) {
            File f = new File(uri);
            picked_attachments.add(new SendFileItem(false, f.length(),
                    s_file_size(f.length()), f.getName(), f.getAbsolutePath(),
                    URLConnection.guessContentTypeFromName(f.getName())));
            s_attachment_paths.add(uri);
            Dialogs.toaster(true, getString(R.string.attch_added_attachment)
                    + " " + f.getName(), this);
        } else {
            if (s_attachment_paths != null && s_attachment_paths.size() > 0) {
                s_attachment_paths.remove(uri);
            }
            if (picked_attachments != null && picked_attachments.size() > 0) {
                for (int i = picked_attachments.size() - 1;i >= 0;i--) {
                    if (picked_attachments.get(i).get_file_uri().equals(uri)) {
                        picked_attachments.remove(picked_attachments.get(i));
                        picked_attachments.trimToSize();
                    }
                }
            }
        }
        picked_adapter.notifyDataSetChanged();
    }
}