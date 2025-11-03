
package com.yiguihai.tun2socks;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;

    private EditText mtuEditText;
    private EditText dnsV4EditText;
    private EditText dnsV6EditText;
    private Switch ipv4Switch;
    private Switch ipv6Switch;
    private RadioGroup appFilterModeRadioGroup;
    private EditText excludedIpsEditText;

    public static final String PREF_MTU = "pref_mtu";
    public static final String PREF_DNS_V4 = "pref_dns_v4";
    public static final String PREF_DNS_V6 = "pref_dns_v6";
    public static final String PREF_IPV4_ENABLED = "pref_ipv4_enabled";
    public static final String PREF_IPV6_ENABLED = "pref_ipv6_enabled";
    public static final String PREF_APP_FILTER_MODE = "pref_app_filter_mode";
    public static final String PREF_EXCLUDED_IPS = "pref_excluded_ips";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mtuEditText = findViewById(R.id.edit_text_mtu);
        dnsV4EditText = findViewById(R.id.edit_text_dns_v4);
        dnsV6EditText = findViewById(R.id.edit_text_dns_v6);
        ipv4Switch = findViewById(R.id.switch_ipv4);
        ipv6Switch = findViewById(R.id.switch_ipv6);
        appFilterModeRadioGroup = findViewById(R.id.radio_group_app_filter_mode);
        excludedIpsEditText = findViewById(R.id.edit_text_excluded_ips);

        Button selectAppsButton = findViewById(R.id.button_select_apps);
        selectAppsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppSelectionActivity.class);
            startActivity(intent);
        });

        loadSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
    }

    private void loadSettings() {
        mtuEditText.setText(sharedPreferences.getString(PREF_MTU, "1500"));
        dnsV4EditText.setText(sharedPreferences.getString(PREF_DNS_V4, "8.8.8.8"));
        dnsV6EditText.setText(sharedPreferences.getString(PREF_DNS_V6, "2001:4860:4860::8888"));
        ipv4Switch.setChecked(sharedPreferences.getBoolean(PREF_IPV4_ENABLED, true));
        ipv6Switch.setChecked(sharedPreferences.getBoolean(PREF_IPV6_ENABLED, false));
        appFilterModeRadioGroup.check(sharedPreferences.getInt(PREF_APP_FILTER_MODE, R.id.radio_button_exclude_mode));
        excludedIpsEditText.setText(sharedPreferences.getString(PREF_EXCLUDED_IPS, ""));
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PREF_MTU, mtuEditText.getText().toString());
        editor.putString(PREF_DNS_V4, dnsV4EditText.getText().toString());
        editor.putString(PREF_DNS_V6, dnsV6EditText.getText().toString());
        editor.putBoolean(PREF_IPV4_ENABLED, ipv4Switch.isChecked());
        editor.putBoolean(PREF_IPV6_ENABLED, ipv6Switch.isChecked());
        editor.putInt(PREF_APP_FILTER_MODE, appFilterModeRadioGroup.getCheckedRadioButtonId());
        editor.putString(PREF_EXCLUDED_IPS, excludedIpsEditText.getText().toString());
        editor.apply();
        showToast("Settings saved.");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
