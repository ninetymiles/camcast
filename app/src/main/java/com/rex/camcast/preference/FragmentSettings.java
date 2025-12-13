package com.rex.camcast.preference;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.rex.camcast.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

//import dagger.hilt.android.AndroidEntryPoint;

//@AndroidEntryPoint
public class FragmentSettings extends PreferenceFragmentCompat {

    private static final Logger logger = LoggerFactory.getLogger(FragmentSettings.class);

    public static final String TAG = "GENERAL";

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preference_settings, rootKey);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.trace("");

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        String defaultServerUri = getResources().getString(R.string.default_server_uri);
        //logger.trace("defaultServerUri=<{}>", defaultServerUri);
        EditTextPreference prefsUri = getPreferenceScreen().findPreference("PREFS_SERVER_URI");
        if (prefsUri != null) {
            prefsUri.setText(prefs.getString("PREFS_SERVER_URI", defaultServerUri)); // EditText value
            prefsUri.setSummary(prefs.getString("PREFS_SERVER_URI", defaultServerUri));
            prefsUri.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    logger.trace("key=<{}> newValue=<{}>", preference.getKey(), newValue);
                    boolean allowUpdate = false;
                    try {
                        String uri = URI.create((String) newValue).toString();
                        if (!TextUtils.isEmpty(uri)) {
                            allowUpdate = true;
                        } else {
                            // Input empty string will reset back to default value
                            prefs.edit()
                                    .putString("PREFS_SERVER_URI", defaultServerUri)
                                    .apply();
                        }
                        preference.setSummary(uri);
                    } catch (Exception ex) {
                        logger.warn("Failed to parse URI <{}> - {}", newValue, ex.getMessage());
                        Toast.makeText(getContext(), getString(R.string.settings_server_invalid_template, newValue, ex.getMessage()), Toast.LENGTH_LONG).show();
                    }
                    return allowUpdate;
                }
            });
        }

        Preference prefsExport = getPreferenceScreen().findPreference("PREFS_EXPORT_LOG");
        if (prefsExport != null) {
            prefsExport.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    logger.trace("");

                    File logDir = new File(requireContext().getFilesDir(), "log");
                    logger.trace("logDir=<{}>", logDir);
                    if (!logDir.isDirectory()) {
                        logger.warn("No logs folder");
                        return true;
                    }
                    File[] logFiles = logDir.listFiles();
                    if (logFiles != null) {
                        for (File f : logFiles) {
                            logger.trace("export - {}", f);
                            logUris.add(FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", f));
                        }
                    }
                    if (logUris.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.settings_export_log_no_files, Toast.LENGTH_LONG).show();
                        return true;
                    }

                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                            );
                    openDirLauncher.launch(intent);
                    return true;
                }
            });
        }

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

        Preference prefsAbout = getPreferenceScreen().findPreference("PREFS_ABOUT");
        if (prefsAbout != null) {
            prefsAbout.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    logger.trace("PREFS_ABOUT");
                    getParentFragmentManager()
                            .beginTransaction()
                            .replace(R.id.preferenceContent, new FragmentAbout())
                            .addToBackStack(null)
                            .commit();
                    return true;
                }
            });
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
            actionBar.setTitle(R.string.menu_settings);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        logger.trace("");
    }

    private final List<Uri> logUris = new ArrayList<>();

    private final ActivityResultLauncher<Intent> openDirLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            requireContext().getContentResolver().takePersistableUriPermission(uri, flags);

            DocumentFile targetDir = DocumentFile.fromTreeUri(requireContext(), uri);
            if (targetDir != null && logUris != null) {
                ContentResolver resolver = requireContext().getContentResolver();
                for (int i = 0; i < logUris.size(); i++) {
                    Uri item = logUris.get(i);
                    String name = "log_" + System.currentTimeMillis() + ".log";
                    try (Cursor cursor = resolver.query(item, null, null, null, null)) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0 && cursor.moveToFirst()) {
                            name = cursor.getString(nameIndex);
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to obtain file name - {}", ex.toString());
                    }

                    DocumentFile targetFile = targetDir.createFile("*/*", name);
                    if (targetFile == null) continue;

                    try (InputStream in = resolver.openInputStream(item);
                         OutputStream out = resolver.openOutputStream(targetFile.getUri())) {
                        if (in == null || out == null) continue;

                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                        out.flush();
                    } catch (IOException e) {
                        logger.warn("Failed to export log - {}", e.toString());
                    }
                }
                Toast.makeText(requireContext(), R.string.settings_export_log_success, Toast.LENGTH_LONG).show();
            }
        }
    });
}
