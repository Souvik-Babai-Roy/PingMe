package com.pingme.android.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

public class ViewPagerAdapter extends FragmentStateAdapter {
    private final List<Fragment> fragments = new ArrayList<>();
    private final List<String> titles = new ArrayList<>();

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    public void addFragment(Fragment fragment, String title) {
        fragments.add(fragment);
        titles.add(title);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragments.get(position);
    }

    @Override
    public int getItemCount() {
        return fragments.size();
    }

    public CharSequence getPageTitle(int position) {
        return titles.get(position);
    }

    public Fragment getFragment(int position) {
        if (position >= 0 && position < fragments.size()) {
            return fragments.get(position);
        }
        return null;
    }

    public String getTabTitle(int position) {
        if (position >= 0 && position < titles.size()) {
            return titles.get(position);
        }
        return "";
    }
}