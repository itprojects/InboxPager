/*
 * InboxPager, an android email client.
 * Copyright (C) 2018-2020  ITPROJECTS
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
import net.inbox.db.Attachment;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

public class FileDownloadPicker extends AppCompatActivity {

    private boolean full_msg_download = false;
    private int full_msg_size = 0;
    private String full_msg_title = "ERROR.msg";

    private int temp_position = -1;
    private String temp_name;
    private String temp_id;

    private File cwd_path;
    private File[] cwd_files;
    private ArrayList<FileDownloadItem> picker_listings = new ArrayList<>();

    private AttachmentsList attachments_adapter;
    private ArrayList<AttachmentItem> attachments_array = new ArrayList<>();
    private ArrayList<Attachment> attachments_obj = new ArrayList<>();

    private ListView list_view_attachments;
    private ListView list_view_picker;

    private TextView tv_location;

    private View current_layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent Android Switcher leaking data via screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.folder_picker);

        try {
            // Restore existing state
            if (savedInstanceState != null) {
                full_msg_download = savedInstanceState.getBoolean("sv_full_msg_download");
                full_msg_size = savedInstanceState.getInt("sv_full_msg_size");
                full_msg_title = savedInstanceState.getString("sv_full_msg_title");
                temp_position = savedInstanceState.getInt("sv_temp_position", -1);
                cwd_path = new File(savedInstanceState.getString("sv_current_path"));
                attachments_obj = savedInstanceState.getParcelableArrayList("sv_attachments_obj");
            } else {
                // Animation
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
                    full_msg_download = getIntent().getBooleanExtra("full_msg_download", true);
                    full_msg_title = getIntent().getStringExtra("full_msg_title");
                    attachments_obj = getIntent().getParcelableArrayListExtra("msg_attachments");
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
                pick_title.setText(getString(R.string.attch_title).toUpperCase());
            }

            final TabLayout llay_tabs = findViewById(R.id.llay_tabs);
            llay_tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {

                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (tab.getPosition() == 0) {
                        if (!full_msg_download) {
                            list_view_attachments.setVisibility(View.VISIBLE);
                            list_view_picker.setVisibility(View.GONE);
                            load_attachments();
                        }
                    } else {
                        list_view_attachments.setVisibility(View.GONE);
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

            attachments_adapter = new AttachmentsList(this, attachments_array);
            list_view_attachments = findViewById(R.id.list_view_attachments);
            list_view_attachments.setAdapter(attachments_adapter);
            list_view_attachments.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    temp_position = position;
                    for (AttachmentItem a : attachments_array) a.set_picked(false);
                    attachments_array.get(temp_position).set_picked(true);
                    attachments_adapter.notifyDataSetChanged();
                }
            });
            load_attachments();

            TextView tv_picker_save = findViewById(R.id.tv_picker_save);
            tv_picker_save.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    prepare_save();
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
                    FileDownloadItem itm = (FileDownloadItem) parent.getItemAtPosition(position);
                    if (!itm.get_file_or_directory()) {
                        cwd_path = new File(itm.get_file_uri());
                        cwd_files = cwd_path.listFiles();
                        prepare_adapter();
                    }
                }
            });

            if (full_msg_download) {
                // Show only the folder picker
                llay_tabs.setVisibility(View.GONE);
                list_view_picker.setVisibility(View.VISIBLE);
                ib_picker_home.callOnClick();
            }
        } catch (Exception e) {
            InboxPager.log += e.getMessage() + "\n\n";
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle save) {
        super.onSaveInstanceState(save);
        save.putBoolean("sv_full_msg_download", full_msg_download);
        save.putInt("sv_full_msg_size", full_msg_size);
        save.putString("sv_full_msg_title", full_msg_title);
        save.putInt("sv_temp_position", temp_position);
        if (cwd_path == null) {
            save.putString("sv_current_path", "");
        } else {
            save.putString("sv_current_path", cwd_path.getAbsolutePath());
        }
        save.putParcelableArrayList("sv_attachments_obj", attachments_obj);
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

    private String s_file_size(int sz) {
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

    private void prepare_adapter() {
        if (cwd_files != null) {
            picker_listings.clear();
            for (File f : cwd_files) {
                if (f.isFile()) {
                    FileDownloadItem ff = new FileDownloadItem(true, f.getName(), f.getAbsolutePath());

                    picker_listings.add(0, ff);
                } else if (f.isDirectory()) {
                    picker_listings.add(0, new FileDownloadItem(false, f.getName(), f.getAbsolutePath()));
                }
            }
            list_view_picker.setAdapter(null);
            list_view_picker.setAdapter(new FileDownloadList(this, picker_listings));
            tv_location.setText(cwd_path.getAbsolutePath());
        }
    }

    private void load_attachments() {
        if (attachments_obj != null) {
            if  (attachments_obj.size() > 0) {
                attachments_array.clear();
                for (Attachment at : attachments_obj) {
                    try {
                        if (at.get_imap_uid() == null) {
                            attachments_array.add(0, new AttachmentItem(at.get_size(),
                                    s_file_size(at.get_size()), at.get_name(), at.get_mime_type(), at.get_pop_indx()));
                        } else {
                            attachments_array.add(0, new AttachmentItem(at.get_size(),
                                    s_file_size(at.get_size()), at.get_name(), at.get_mime_type(), at.get_imap_uid()));
                        }
                    } catch (Exception e) {
                        InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
                    }
                }

                // Set known picked item
                if (temp_position != -1) attachments_array.get(temp_position).set_picked(true);
                attachments_adapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Checks for a series of conditions that may prevent file saving.
     **/
    private void prepare_save() {
        if (full_msg_download) {
            // Cannot write to this folder, permissions on the device
            if (!cwd_path.canWrite()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.err_title_write_perms));
                builder.setMessage(getString(R.string.err_msg_write_perms));
                builder.setPositiveButton(getString(android.R.string.ok), null);
                builder.show();
                return;
            }

            // Not enough space, and also not an exact calculation
            if (cwd_path.getUsableSpace() < full_msg_size) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.err_title_no_space));
                builder.setMessage(getString(R.string.err_msg_no_space));
                builder.setPositiveButton(getString(android.R.string.ok), null);
                builder.show();
                return;
            }
            name_check(full_msg_title, "");
        } else {
            // Attachments
            boolean has_picked = false;
            int att_bytes = 0;
            String att_name = "";
            String att_mime_id = "";
            for (AttachmentItem att : attachments_array) {
                if (att.get_picked()) {
                    has_picked = true;
                    att_bytes = att.get_i_file_size();
                    att_name = att.get_file_name();
                    att_mime_id = att.get_file_uuid();
                }
            }
            if (has_picked) {
                // Cannot write to this folder, permissions on the device
                if (!cwd_path.canWrite()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.err_title_write_perms));
                    builder.setMessage(getString(R.string.err_msg_write_perms));
                    builder.setPositiveButton(getString(android.R.string.ok), null);
                    builder.show();
                    return;
                }

                // Not enough space, and also not an exact calculation
                if (cwd_path.getUsableSpace() < att_bytes) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.err_title_no_space));
                    builder.setMessage(getString(R.string.err_msg_no_space));
                    builder.setPositiveButton(getString(android.R.string.ok), null);
                    builder.show();
                    return;
                }
                name_check(att_name, att_mime_id);
            } else {
                Dialogs.toaster(true, getString(R.string.file_title), this);
            }
        }
    }

    private void name_check(final String name, final String id) {
        // File already exists, overwrite?
        boolean already_exists = (new File(cwd_path.getPath() + "/" + name)).exists();
        if (already_exists) {
            temp_name = name;
            temp_id = id;

            if (temp_name == null || temp_name.trim().isEmpty()) {
                // Empty Name
                // Renaming with /path/to/save/to/000000000.txt
                boolean success = false;
                File f_new_name;
                String s_temp_name;
                Random randomness = new Random();
                for (int i = 1; i < 10; i++) {
                    s_temp_name = String.valueOf(randomness.nextInt(999999999)) + ".txt";
                    f_new_name = new File(cwd_path.getAbsolutePath() + "/" + s_temp_name);
                    if (!f_new_name.exists()) {
                        temp_name = s_temp_name;
                        success = true;
                        break;
                    }
                }
                if (success) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.err_title_empty_name));
                    builder.setMessage(getString(R.string.err_msg_empty_name) + " '" + temp_name + "'");
                    builder.setNegativeButton(getString(android.R.string.no), null);
                    builder.setNeutralButton(getString(android.R.string.yes),
                            new AlertDialog.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    save_and_end(temp_name, temp_id);
                                }
                            });
                    builder.show();
                } else {
                    InboxPager.log += getString(R.string.err_error).replace(":", "") + "\n\n";
                }
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.err_title_already_exists));
                builder.setMessage("'" + temp_name + "' "+ getString(R.string.err_msg_already_exists));
                builder.setPositiveButton(getString(android.R.string.yes),
                        new AlertDialog.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                save_and_end(temp_name, temp_id);
                            }
                        });
                builder.setNeutralButton(getString(R.string.file_use_this),
                        new AlertDialog.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // Renaming with /path/to/save/to/(i) file.ext
                                boolean success = false;
                                File f_new_name;
                                String s_temp_name;
                                for (int i = 1; i < 10; i++) {
                                    s_temp_name = "(" + i + ") " + temp_name;
                                    f_new_name = new File(cwd_path.getAbsolutePath() + "/" + s_temp_name);
                                    if (!f_new_name.exists()) {
                                        temp_name = s_temp_name;
                                        success = true;
                                        break;
                                    }
                                }
                                if (success) {
                                    save_and_end(cwd_path.getAbsolutePath() + "/" + temp_name, temp_id);
                                } else {
                                    InboxPager.log += getString(R.string.err_error).replace(":", "") + "\n\n";
                                }
                            }
                        });
                builder.setNegativeButton(getString(android.R.string.no), null);
                builder.show();
            }
        } else {
            save_and_end(name, id);
        }
    }

    private void save_and_end(String name, String id) {
        Intent data = new Intent();
        data.putExtra("chosen_folder", cwd_path.getAbsolutePath());
        data.putExtra("chosen_name", name);
        if (full_msg_download) {
            setResult(101, data);
        } else {
            data.putExtra("chosen_attachment", id);
            setResult(102, data);
        }
        onBackPressed();
    }
}
