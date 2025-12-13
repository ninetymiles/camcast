package com.rex.camcast.preference;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.rex.camcast.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import dagger.hilt.android.AndroidEntryPoint;

//@AndroidEntryPoint
public class FragmentAbout extends PreferenceFragmentCompat {

    private static final Logger logger = LoggerFactory.getLogger(FragmentAbout.class);
    public static final String TAG = "ABOUT";

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preference_about, rootKey);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.trace("");
        Preference prefsVersion = getPreferenceScreen().findPreference("PREFS_VERSION");
        if (prefsVersion != null) {
            int flags = PackageManager.GET_META_DATA;
            try {
                PackageInfo pkgInfo = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), flags);
                prefsVersion.setSummary(pkgInfo.versionName + " r" + pkgInfo.versionCode);
            } catch (PackageManager.NameNotFoundException e) {
                logger.warn("Failed to get package info - {}", e.toString());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        logger.trace("");
    }

    @Override
    public void onStart() {
        super.onStart();
        logger.trace("");

        // Need specified the title, PreferenceFragmentCompat will not auto apply from PreferenceScreen title
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.settings_about);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        logger.trace("");
    }
}
