package com.kristoffersol.materialtimer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    String themeLight;
    String themeDark;
    String themeBlack;
    OnThemeChangeListener themeChangeListener;
    SharedPreferences sharedPreferences;

    public interface OnThemeChangeListener{
        void onThemeChange();
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        themeLight = getString(R.string.LightTheme);
        themeDark = getString(R.string.DarkTheme);
        themeBlack = getString(R.string.BlackTheme);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onAttach(Context context){
        super.onAttach(context);
        try{
            themeChangeListener = (OnThemeChangeListener) context;
        } catch(ClassCastException e) {
            throw new ClassCastException(e.toString() + "implement OnThemeChangeListener");
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        try{
            themeChangeListener = (OnThemeChangeListener) activity;
        } catch(ClassCastException e) {
            throw new ClassCastException(e.toString() + "implement OnThemeChangeListener");
        }
    }
    /**
     * Trigger recreate of activity to create dynamic theme change
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key){

        Preference pref = findPreference(key);

        if(pref instanceof ListPreference){
            String value = sharedPreferences.getString(key,"");
            if(value.equals(themeLight) || value.equals(themeDark) || value.equals(themeBlack)){
                themeChangeListener.onThemeChange();
            }
        }
    }
}