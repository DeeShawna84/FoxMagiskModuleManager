package com.fox2code.mmm;

import static com.fox2code.mmm.utils.IntentHelper.getActivity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentActivity;

import com.fox2code.foxcompat.app.FoxActivity;
import com.fox2code.mmm.androidacy.AndroidacyRepoData;
import com.fox2code.mmm.databinding.ActivitySetupBinding;
import com.fox2code.mmm.repo.RepoManager;
import com.fox2code.mmm.utils.realm.ReposList;
import com.fox2code.rosettax.LanguageActivity;
import com.fox2code.rosettax.LanguageSwitcher;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;

public class SetupActivity extends FoxActivity implements LanguageActivity {
    @SuppressLint({"ApplySharedPref", "RestrictedApi"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setTitle(R.string.setup_title);
        // set action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // back button is close button
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_HOME);
            actionBar.setLogo(R.drawable.ic_foreground);
            // set title
            actionBar.setTitle(R.string.setup_title);
            actionBar.show();
        }
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, 0);
        createFiles();
        disableUpdateActivityForFdroidFlavor();
        // Set theme
        SharedPreferences prefs = MainApplication.getSharedPreferences();
        switch (prefs.getString("theme", "system")) {
            case "light" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Light);
            case "dark" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Dark);
            case "system" -> setTheme(R.style.Theme_MagiskModuleManager_Monet);
            case "black" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Black);
            case "transparent_light" ->
                    setTheme(R.style.Theme_MagiskModuleManager_Transparent_Light);
        }

        ActivitySetupBinding binding = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        View view = binding.getRoot();
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).setChecked(BuildConfig.ENABLE_AUTO_UPDATER);
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).setChecked(BuildConfig.DEFAULT_ENABLE_CRASH_REPORTING);
        // assert that both switches match the build config on debug builds
        if (BuildConfig.DEBUG) {
            assert ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).isChecked() == BuildConfig.ENABLE_AUTO_UPDATER;
            assert ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).isChecked() == BuildConfig.DEFAULT_ENABLE_CRASH_REPORTING;
        }
        // Repos are a little harder, as the enabled_repos build config is an arraylist
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_androidacy_repo))).setChecked(BuildConfig.ENABLED_REPOS.contains("androidacy_repo"));
        ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_magisk_alt_repo))).setChecked(BuildConfig.ENABLED_REPOS.contains("magisk_alt_repo"));
        // On debug builds, log when a switch is toggled
        if (BuildConfig.DEBUG) {
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).setOnCheckedChangeListener((buttonView, isChecked) -> Timber.i("Automatic update Check: %s", isChecked));
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).setOnCheckedChangeListener((buttonView, isChecked) -> Timber.i("Crash Reporting: %s", isChecked));
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_androidacy_repo))).setOnCheckedChangeListener((buttonView, isChecked) -> Timber.i("Androidacy Repo: %s", isChecked));
            ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_magisk_alt_repo))).setOnCheckedChangeListener((buttonView, isChecked) -> Timber.i("Magisk Alt Repo: %s", isChecked));
        }
        // Setup popup dialogue for the setup_theme_button
        MaterialButton themeButton = view.findViewById(R.id.setup_theme_button);
        themeButton.setOnClickListener(v -> {
            // Create a new dialog for the theme picker
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle(R.string.setup_theme_title);
            // Create a new array of theme names (system, light, dark, black, transparent light)
            String[] themeNames = new String[]{getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark), getString(R.string.theme_black), getString(R.string.theme_transparent_light)};
            // Create a new array of theme values (system, light, dark, black, transparent_light)
            String[] themeValues = new String[]{"system", "light", "dark", "black", "transparent_light"};
            // if pref_theme is set, check the relevant theme_* menu item, otherwise check the default (theme_system)
            String prefTheme = prefs.getString("pref_theme", "system");
            int checkedItem = 0;
            switch (prefTheme) {
                case "system":
                    break;
                case "light":
                    checkedItem = 1;
                    break;
                case "dark":
                    checkedItem = 2;
                    break;
                case "black":
                    checkedItem = 3;
                    break;
                case "transparent_light":
                    checkedItem = 4;
                    break;
            }
            builder.setCancelable(true);
            // Create the dialog
            builder.setSingleChoiceItems(themeNames, checkedItem, (dialog, which) -> {
                // Set the theme
                prefs.edit().putString("pref_theme", themeValues[which]).commit();
                // Dismiss the dialog
                dialog.dismiss();
                // Set the theme
                UiThreadHandler.handler.postDelayed(() -> {
                    switch (prefs.getString("pref_theme", "system")) {
                        case "light" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Light);
                        case "dark" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Dark);
                        case "system" -> setTheme(R.style.Theme_MagiskModuleManager_Monet);
                        case "black" -> setTheme(R.style.Theme_MagiskModuleManager_Monet_Black);
                        case "transparent_light" ->
                                setTheme(R.style.Theme_MagiskModuleManager_Transparent_Light);
                    }
                    // restart the activity because switching to transparent pisses the rendering engine off
                    Intent intent = new Intent(this, SetupActivity.class);
                    finish();
                    // ensure intent originates from the same package
                    intent.setPackage(getPackageName());
                    startActivity(intent);
                }, 100);
            });
            builder.show();
        });
        // Setup language selector
        MaterialButton languageSelector = view.findViewById(R.id.setup_language_button);
        languageSelector.setOnClickListener(preference -> {
            LanguageSwitcher ls = new LanguageSwitcher(Objects.requireNonNull(getActivity(this)));
            ls.setSupportedStringLocales(MainApplication.supportedLocales);
            ls.showChangeLanguageDialog((FragmentActivity) getActivity(this));
        });
        // Set up the buttons
        // Setup button
        MaterialButton setupButton = view.findViewById(R.id.setup_continue);
        setupButton.setOnClickListener(v -> {
            // Set first launch to false
            // get instance of editor
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("last_shown_setup", "v1");
            // Set the Automatic update check pref
            editor.putBoolean("pref_background_update_check", ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_background_update_check))).isChecked());
            // Set the crash reporting pref
            editor.putBoolean("pref_crash_reporting", ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_crash_reporting))).isChecked());
            // Set the repos in the ReposList realm db
            RealmConfiguration realmConfig = new RealmConfiguration.Builder().name("ReposList.realm").directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).allowQueriesOnUiThread(true).allowWritesOnUiThread(true).build();
            boolean androidacyRepo = ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_androidacy_repo))).isChecked();
            boolean magiskAltRepo = ((MaterialSwitch) Objects.requireNonNull(view.findViewById(R.id.setup_magisk_alt_repo))).isChecked();
            Realm realm = Realm.getInstance(realmConfig);
            realm.beginTransaction();
            Objects.requireNonNull(realm.where(ReposList.class).equalTo("id", "androidacy_repo").findFirst()).setEnabled(androidacyRepo);
            Objects.requireNonNull(realm.where(ReposList.class).equalTo("id", "magisk_alt_repo").findFirst()).setEnabled(magiskAltRepo);
            // commit the changes
            realm.commitTransaction();
            realm.close();
            // Commit the changes
            editor.commit();
            // Sleep for 1 second to allow the user to see the changes
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Log the changes if debug
            if (BuildConfig.DEBUG) {
                Timber.d("Automatic update check: %s", prefs.getBoolean("pref_background_update_check", false));
                Timber.i("Crash reporting: %s", prefs.getBoolean("pref_crash_reporting", false));
            }
            // Restart the activity
            MainActivity.doSetupRestarting = true;
            PendingIntent intent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            try {
                intent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
            finish();
        });
        // Cancel button
        MaterialButton cancelButton = view.findViewById(R.id.setup_cancel);
        cancelButton.setText(R.string.cancel);
        cancelButton.setOnClickListener(v -> {
            // Set first launch to false and restart the activity
            prefs.edit().putString("last_shown_setup", "v1").commit();
            MainActivity.doSetupRestarting = true;
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();
        // Set the theme
        SharedPreferences prefs = MainApplication.getSharedPreferences();
        switch (prefs.getString("pref_theme", "system")) {
            case "light" -> theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet_Light, true);
            case "dark" -> theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet_Dark, true);
            case "system" -> theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet, true);
            case "black" -> theme.applyStyle(R.style.Theme_MagiskModuleManager_Monet_Black, true);
            case "transparent_light" ->
                    theme.applyStyle(R.style.Theme_MagiskModuleManager_Transparent_Light, true);
        }
        return theme;
    }

    @Override
    @SuppressLint({"InlinedApi", "RestrictedApi"})
    public void refreshRosettaX() {
        // refresh app language
        runOnUiThread(() -> {
            // refresh activity
            Intent intent = new Intent(this, SetupActivity.class);
            finish();
            startActivity(intent);
        });
    }

    // creates the realm database
    private void createRealmDatabase() {
        Timber.d("Creating Realm databases");
        long startTime = System.currentTimeMillis();
        // create encryption key
        Timber.d("Creating encryption key");
        // create the realm database for ReposList
        // next, create the realm database for ReposList
        RealmConfiguration config2 = new RealmConfiguration.Builder().name("ReposList.realm").allowQueriesOnUiThread(true).allowWritesOnUiThread(true).directory(MainApplication.getINSTANCE().getDataDirWithPath("realms")).schemaVersion(1).build();
        // get the instance
        Realm.getInstanceAsync(config2, new Realm.Callback() {
            @Override
            public void onSuccess(@NonNull Realm realm1) {
                // create androidacy_repo and magisk_alt_repo if they don't exist under ReposList
                // each has id, name, donate, website, support, enabled, and lastUpdate and name
                // create androidacy_repo
                realm1.beginTransaction();
                if (realm1.where(ReposList.class).equalTo("id", "androidacy_repo").findFirst() == null) {
                    // cant use createObject because it crashes because reasons. use copyToRealm instead
                    ReposList androidacy_repo = realm1.createObject(ReposList.class, "androidacy_repo");
                    androidacy_repo.setName("Androidacy Repo");
                    androidacy_repo.setDonate(AndroidacyRepoData.getInstance().getDonate());
                    androidacy_repo.setSupport(AndroidacyRepoData.getInstance().getSupport());
                    androidacy_repo.setSubmitModule(AndroidacyRepoData.getInstance().getSubmitModule());
                    androidacy_repo.setUrl(RepoManager.ANDROIDACY_MAGISK_REPO_ENDPOINT);
                    androidacy_repo.setEnabled(true);
                    androidacy_repo.setLastUpdate(0);
                    androidacy_repo.setWebsite(RepoManager.ANDROIDACY_MAGISK_REPO_HOMEPAGE);
                    // now copy the data from the data class to the realm object using copyToRealmOrUpdate
                    realm1.insertOrUpdate(androidacy_repo);
                }
                // create magisk_alt_repo
                if (realm1.where(ReposList.class).equalTo("id", "magisk_alt_repo").findFirst() == null) {
                    ReposList magisk_alt_repo = realm1.createObject(ReposList.class, "magisk_alt_repo");
                    magisk_alt_repo.setName("Magisk Alt Repo");
                    magisk_alt_repo.setDonate(null);
                    magisk_alt_repo.setWebsite(RepoManager.MAGISK_ALT_REPO_HOMEPAGE);
                    magisk_alt_repo.setSupport(null);
                    magisk_alt_repo.setEnabled(true);
                    magisk_alt_repo.setUrl(RepoManager.MAGISK_ALT_REPO);
                    magisk_alt_repo.setSubmitModule(RepoManager.MAGISK_ALT_REPO_HOMEPAGE + "/submission");
                    magisk_alt_repo.setLastUpdate(0);
                    // commit the changes
                    realm1.insertOrUpdate(magisk_alt_repo);
                }
                realm1.commitTransaction();
            }
        });
        long endTime = System.currentTimeMillis();
        Timber.d("Realm databases created in %d ms", endTime - startTime);
    }

    public void createFiles() {
        try {
            String cookieFileName = "cookies";
            // initial set of cookies, only really used to create the file
            String initialCookie = "is_foxmmm=true; expires=Fri, 31 Dec 9999 23:59:59 GMT; path=/; domain=production-api.androidacy.com; SameSite=None; Secure;|foxmmm_version=" + BuildConfig.VERSION_CODE + "; expires=Fri, 31 Dec 9999 23:59:59 GMT; path=/; domain=production-api.androidacy.com; SameSite=None; Secure;";
            File cookieFile = new File(MainApplication.getINSTANCE().getFilesDir(), cookieFileName);
            // if the file exists, delete it
            if (cookieFile.exists()) {
                if (!cookieFile.delete()) {
                    Timber.e("Failed to delete cookie file");
                    throw new IllegalStateException("Failed to create cookie file. This probably means that the app doesn't have permission to write to the files directory");
                }
            }
            // create the file
            if (!cookieFile.createNewFile()) {
                Timber.e("Failed to create cookie file");
                throw new IllegalStateException("Failed to create cookie file. This probably means that the app doesn't have permission to write to the files directory");
            }
            // create the file output stream
            FileOutputStream fileOutputStream = new FileOutputStream(cookieFile);
            // write the initial cookie to the file
            fileOutputStream.write(Base64.encode(initialCookie.getBytes(), Base64.DEFAULT));
            // close the file output stream
            fileOutputStream.close();
        } catch (IOException e) {
            Timber.e(e);
        }
        // we literally only use these to create the http cache folders
        File httpCacheDir = MainApplication.getINSTANCE().getDataDirWithPath("cache/WebView/Default/HTTP Cache/Code Cache/wasm");
        File httpCacheDir2 = MainApplication.getINSTANCE().getDataDirWithPath("cache/WebView/Default/HTTP Cache/Code Cache/js");
        if (!httpCacheDir.exists()) {
            if (httpCacheDir.mkdirs()) {
                Timber.d("Created http cache dir");
            }
        }
        if (!httpCacheDir2.exists()) {
            if (httpCacheDir2.mkdirs()) {
                Timber.d("Created http cache dir");
            }
        }
        createRealmDatabase();
    }

    @SuppressWarnings("ConstantConditions")
    public void disableUpdateActivityForFdroidFlavor() {
        if (BuildConfig.FLAVOR.equals("fdroid")) {
            // check if the update activity is enabled
            PackageManager pm = getPackageManager();
            ComponentName componentName = new ComponentName(this, UpdateActivity.class);
            int componentEnabledSetting = pm.getComponentEnabledSetting(componentName);
            if (componentEnabledSetting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                Timber.d("Disabling update activity for fdroid flavor");
                // disable update activity through package manager
                pm.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
    }
}