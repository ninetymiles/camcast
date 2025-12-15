package com.rex.camcast.preference;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.rex.camcast.R;
import com.rex.camcast.databinding.ActivityPreferenceBinding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import dagger.hilt.android.AndroidEntryPoint;

//@AndroidEntryPoint
public class PreferenceViewActivity extends AppCompatActivity {

    private static final Logger logger = LoggerFactory.getLogger(PreferenceViewActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.trace("");

        ActivityPreferenceBinding binding = ActivityPreferenceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(false);
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.preferenceContent, (v, windowInsets) -> {
            int type = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
            Insets insets = windowInsets.getInsets(type);
            //mLogger.trace("insets={}", insets);

            v.setPadding(insets.left, 0, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Avoid rotate device when showing FragmentAbout, will force overlay a FragmentGeneral unexpected
        if (getSupportFragmentManager().findFragmentByTag(FragmentSettings.TAG) == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.preferenceContent, new FragmentSettings(), FragmentSettings.TAG)
                    .commit();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.trace("");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
