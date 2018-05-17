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

import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.inbox.R;

import java.util.ArrayList;

public class DialogsCerts {

    private static int nd_num;
    private static ArrayList<String[]> hops;
    private static TextView[] tv_nd;

    public static void dialog_certs(final AppCompatActivity act, ArrayList<String[]> hops_given) {
        hops = hops_given;
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(act);
                builder.setTitle(act.getString(R.string.ssl_auth_popup_title));
                builder.setCancelable(true);
                View layout = act.getLayoutInflater().inflate(R.layout.session_info, null);
                builder.setView(layout);
                builder.show();
                build_certs_info(act, layout);
            }
        });
    }

    private static void build_certs_info(final AppCompatActivity act, View layout) {
        ViewPager v_pager = layout.findViewById(R.id.v_pager);

        NodeAdapter nd_adapter = new NodeAdapter(hops, act.getLayoutInflater());
        v_pager.setAdapter(nd_adapter);
        v_pager.setCurrentItem(0);
        v_pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageSelected(int position) {
                for (int i = 0; i < nd_num; i++) {
                    tv_nd[i].setTextColor(act.getResources().getColor(R.color.color_green_pressed));
                }
                tv_nd[position].setTextColor(act.getResources().getColor(R.color.color_green));
            }

            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) {}

            @Override
            public void onPageScrollStateChanged(int arg0) {}
        });

        LinearLayout llay_hops = layout.findViewById(R.id.v_pager_dots);
        nd_num = nd_adapter.getCount();
        tv_nd = new TextView[nd_num];

        for (int i = 0; i < nd_num; i++) {
            tv_nd[i] = new TextView(act);
            tv_nd[i].setText("•");
            tv_nd[i].setTextSize(25);
            tv_nd[i].setTextColor(act.getResources().getColor(R.color.color_green_pressed));
            llay_hops.addView(tv_nd[i]);
        }
        tv_nd[0].setTextColor(act.getResources().getColor(R.color.color_green));
    }
}

class NodeAdapter extends PagerAdapter {

    private ArrayList<String[]> nodes;
    private LayoutInflater lay_inflate;

    NodeAdapter(ArrayList<String[]> data, LayoutInflater li) {
        nodes = data;
        lay_inflate = li;
    }

    @Override
    public Object instantiateItem(ViewGroup vg, int position) {
        String[] st = nodes.get(position);

        View view = lay_inflate.inflate(R.layout.session_info_row, vg, false);

        ImageView tv_01 = view.findViewById(R.id.tv_this_device_img);
        TextView tv_02 = view.findViewById(R.id.tv_this_device_title);

        TextView tv_03 = view.findViewById(R.id.tv_name_title);
        TextView tv_04 = view.findViewById(R.id.tv_name);
        TextView tv_05 = view.findViewById(R.id.tv_organization_title);
        TextView tv_06 = view.findViewById(R.id.tv_organization);
        TextView tv_07 = view.findViewById(R.id.tv_type_title);
        TextView tv_08 = view.findViewById(R.id.tv_type);
        TextView tv_09 = view.findViewById(R.id.tv_location_title);
        TextView tv_10 = view.findViewById(R.id.tv_location);
        TextView tv_11 = view.findViewById(R.id.tv_state_title);
        TextView tv_12 = view.findViewById(R.id.tv_state);
        TextView tv_13 = view.findViewById(R.id.tv_country_title);
        TextView tv_14 = view.findViewById(R.id.tv_country);
        TextView tv_15 = view.findViewById(R.id.tv_security_title);
        TextView tv_16 = view.findViewById(R.id.tv_security);
        TextView tv_17 = view.findViewById(R.id.tv_key_size_title);
        TextView tv_18 = view.findViewById(R.id.tv_key_size);

        if (position == 0) {
            tv_01.setVisibility(View.VISIBLE);
            tv_02.setVisibility(View.VISIBLE);
            tv_03.setVisibility(View.GONE);
            tv_04.setVisibility(View.GONE);
            tv_05.setVisibility(View.GONE);
            tv_06.setVisibility(View.GONE);
            tv_07.setVisibility(View.GONE);
            tv_08.setVisibility(View.GONE);
            tv_09.setVisibility(View.GONE);
            tv_10.setVisibility(View.GONE);
            tv_11.setVisibility(View.GONE);
            tv_12.setVisibility(View.GONE);
            tv_13.setVisibility(View.GONE);
            tv_14.setVisibility(View.GONE);
            tv_15.setVisibility(View.GONE);
            tv_16.setVisibility(View.GONE);
            tv_17.setVisibility(View.GONE);
            tv_18.setVisibility(View.GONE);
        } else {
            tv_01.setVisibility(View.GONE);
            tv_02.setVisibility(View.GONE);

            if (st[0] != null && st[0].length() > 0) {
                tv_03.setVisibility(View.VISIBLE);
                tv_04.setVisibility(View.VISIBLE);
                tv_04.setText(st[0]);
            } else {
                tv_03.setVisibility(View.GONE);
                tv_04.setVisibility(View.GONE);
            }

            if (st[1] != null && st[1].length() > 0) {
                tv_05.setVisibility(View.VISIBLE);
                tv_06.setVisibility(View.VISIBLE);
                tv_06.setText(st[1]);
            } else {
                tv_05.setVisibility(View.GONE);
                tv_06.setVisibility(View.GONE);
            }

            if (st[2] != null && st[2].length() > 0) {
                tv_07.setVisibility(View.VISIBLE);
                tv_08.setVisibility(View.VISIBLE);
                tv_08.setText(st[2]);
            } else {
                tv_07.setVisibility(View.GONE);
                tv_08.setVisibility(View.GONE);
            }

            if (st[3] != null && st[3].length() > 0) {
                tv_09.setVisibility(View.VISIBLE);
                tv_10.setVisibility(View.VISIBLE);
                tv_10.setText(st[3]);
            } else {
                tv_09.setVisibility(View.GONE);
                tv_10.setVisibility(View.GONE);
            }

            if (st[4] != null && st[4].length() > 0) {
                tv_11.setVisibility(View.VISIBLE);
                tv_12.setVisibility(View.VISIBLE);
                tv_12.setText(st[4]);
            } else {
                tv_11.setVisibility(View.GONE);
                tv_12.setVisibility(View.GONE);
            }

            if (st[5] != null && st[5].length() > 0) {
                tv_13.setVisibility(View.VISIBLE);
                tv_14.setVisibility(View.VISIBLE);
                tv_14.setText(st[5]);
            } else {
                tv_14.setVisibility(View.GONE);
                tv_14.setVisibility(View.GONE);
            }

            if (st[6] != null && st[6].length() > 0) {
                tv_15.setVisibility(View.VISIBLE);
                tv_16.setVisibility(View.VISIBLE);
                tv_16.setText(st[6]);
            } else {
                tv_15.setVisibility(View.GONE);
                tv_16.setVisibility(View.GONE);
            }

            if (st[7] != null && st[7].length() > 0) {
                tv_17.setVisibility(View.VISIBLE);
                tv_18.setVisibility(View.VISIBLE);
                tv_18.setText(st[7]);
            } else {
                tv_17.setVisibility(View.GONE);
                tv_18.setVisibility(View.GONE);
            }
        }

        vg.addView(view);

        return view;
    }

    @Override
    public int getCount() {
        return nodes.size();
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View)object);
    }

    @Override
    public boolean isViewFromObject(View view, Object obj) {
        return view == obj;
    }
}