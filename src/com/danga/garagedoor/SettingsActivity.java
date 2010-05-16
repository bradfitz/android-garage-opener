package com.danga.garagedoor;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity {
    private static final String TAG = "SettingsActivity";

    private EditTextPreference ssidPref;
    private EditTextPreference urlPref;
    private EditTextPreference secretPref;
   
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getPreferenceManager().setSharedPreferencesName(Preferences.NAME);
        addPreferencesFromResource(R.xml.preferences);

        ssidPref = (EditTextPreference) findPreference(Preferences.KEY_SSID);
        urlPref = (EditTextPreference) findPreference(Preferences.KEY_URL);
        secretPref = (EditTextPreference) findPreference(Preferences.KEY_SECRET);

        OnPreferenceChangeListener onChange = new OnPreferenceChangeListener() {
                @Override public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final String key = preference.getKey();
                    Log.v(TAG, "preference change for: " + key);
                    return true;  // yes, persist it.
                }
            };

        //ssidPref.setOnPreferenceChangeListener(onChange);
        //urlPref.setOnPreferenceChangeListener(onChange);
        //secretPref.setOnPreferenceChangeListener(onChange);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePreferenceSummaries();
    }

    private void updateSummary(EditTextPreference pref, String defaultText) {
        String value = pref.getText();
        if (value != null && value.length() > 0) {
            pref.setSummary(value);
        } else {
            pref.setSummary(defaultText);
        }
    }
       
    private void updatePreferenceSummaries() {
        updateSummary(urlPref, "URL to garage endpoint");
        updateSummary(ssidPref, "House wifi's SSID to look for");
    }

    // Convenience method.
    static void show(Context context) {
        final Intent intent = new Intent(context, SettingsActivity.class);
        context.startActivity(intent);
    }
}
