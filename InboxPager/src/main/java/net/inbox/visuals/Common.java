/*
 * InboxPager, an android email client.
 * Copyright (C) 2020  ITPROJECTS
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AccelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

public class Common {

    // Circular reveal animation of activity intent.
    public static void animation_in(AppCompatActivity a, View current_layout) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        float circle_radius = (float) (Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels));
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(current_layout,
                displayMetrics.widthPixels/2,
                displayMetrics.heightPixels/2,
                0, circle_radius);
        circularReveal.setDuration(420);
        circularReveal.setInterpolator(new AccelerateInterpolator());
        current_layout.setVisibility(View.VISIBLE);
        circularReveal.start();
    }

    // Circular reveal animation of activity intent.
    public static void animation_out(final AppCompatActivity a, final View current_layout) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        float circle_radius = (float) (Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels));
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(current_layout,
                displayMetrics.widthPixels/2,
                displayMetrics.heightPixels/2,
                circle_radius, 0);
        circularReveal.setDuration(420);
        circularReveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                current_layout.setVisibility(View.INVISIBLE);
                a.finish();
            }
        });
        circularReveal.start();
    }
}
