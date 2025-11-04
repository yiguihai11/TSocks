
package com.yiguihai.tun2socks;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;

    // Proxy Configuration UI
    private AutoCompleteTextView protocolSpinner;
    private EditText serverEditText;
    private EditText portEditText;
    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button saveButton;
    private com.google.android.material.textfield.TextInputLayout serverInputLayout;

    // Network Configuration UI
    private EditText mtuEditText;
    private EditText dnsV4EditText;
    private EditText dnsV6EditText;
    private Switch ipv4Switch;
    private Switch ipv6Switch;
    private RadioGroup appFilterModeRadioGroup;
    private EditText excludedIpsEditText;
    private Button selectAppsButton;

    public static final String PREF_MTU = "pref_mtu";
    public static final String PREF_DNS_V4 = "pref_dns_v4";
    public static final String PREF_DNS_V6 = "pref_dns_v6";
    public static final String PREF_IPV4_ENABLED = "pref_ipv4_enabled";
    public static final String PREF_IPV6_ENABLED = "pref_ipv6_enabled";
    public static final String PREF_APP_FILTER_MODE = "pref_app_filter_mode";
    public static final String PREF_EXCLUDED_IPS = "pref_excluded_ips";

    // Proxy Configuration Keys
    public static final String PREF_PROXY_PROTOCOL = "pref_proxy_protocol";
    public static final String PREF_PROXY_SERVER = "pref_proxy_server";
    public static final String PREF_PROXY_PORT = "pref_proxy_port";
    public static final String PREF_PROXY_USERNAME = "pref_proxy_username";
    public static final String PREF_PROXY_PASSWORD = "pref_proxy_password";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Initialize Proxy Configuration UI
        protocolSpinner = findViewById(R.id.spinner_protocol);
        serverEditText = findViewById(R.id.edit_text_server);
        serverInputLayout = findViewById(R.id.layout_server);
        portEditText = findViewById(R.id.edit_text_port);
        usernameEditText = findViewById(R.id.edit_text_username);
        passwordEditText = findViewById(R.id.edit_text_password);
        saveButton = findViewById(R.id.button_save_settings);

        // Setup protocol dropdown
        String[] protocols = getResources().getStringArray(R.array.protocol_types);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_dropdown_item_1line, protocols);
        protocolSpinner.setAdapter(adapter);

        // Initialize Network Configuration UI
        mtuEditText = findViewById(R.id.edit_text_mtu);
        dnsV4EditText = findViewById(R.id.edit_text_dns_v4);
        dnsV6EditText = findViewById(R.id.edit_text_dns_v6);
        ipv4Switch = findViewById(R.id.switch_ipv4);
        ipv6Switch = findViewById(R.id.switch_ipv6);
        appFilterModeRadioGroup = findViewById(R.id.radio_group_app_filter_mode);
        excludedIpsEditText = findViewById(R.id.edit_text_excluded_ips);

        selectAppsButton = findViewById(R.id.button_select_apps);
        selectAppsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppSelectionActivity.class);
            startActivity(intent);
        });

        // Set up save button listener
        saveButton.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(this, "Configuration saved!", Toast.LENGTH_SHORT).show();
        });

        // Set up protocol spinner listener
        protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                String protocol = (String) parent.getItemAtPosition(position);
                handleProtocolChange(protocol);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        loadSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
    }

    private void loadSettings() {
        // Load Proxy Configuration
        String protocol = sharedPreferences.getString(PREF_PROXY_PROTOCOL, "SOCKS5");
        protocolSpinner.setText(protocol, false);

        serverEditText.setText(sharedPreferences.getString(PREF_PROXY_SERVER, ""));
        portEditText.setText(sharedPreferences.getString(PREF_PROXY_PORT, ""));
        usernameEditText.setText(sharedPreferences.getString(PREF_PROXY_USERNAME, ""));
        passwordEditText.setText(sharedPreferences.getString(PREF_PROXY_PASSWORD, ""));

        // Load Network Configuration
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

        // Save Proxy Configuration
        String selectedProtocol = protocolSpinner.getText().toString();
        editor.putString(PREF_PROXY_PROTOCOL, selectedProtocol);
        editor.putString(PREF_PROXY_SERVER, serverEditText.getText().toString());
        editor.putString(PREF_PROXY_PORT, portEditText.getText().toString());
        editor.putString(PREF_PROXY_USERNAME, usernameEditText.getText().toString());
        editor.putString(PREF_PROXY_PASSWORD, passwordEditText.getText().toString());

        // Save Network Configuration
        editor.putString(PREF_MTU, mtuEditText.getText().toString());
        editor.putString(PREF_DNS_V4, dnsV4EditText.getText().toString());
        editor.putString(PREF_DNS_V6, dnsV6EditText.getText().toString());
        editor.putBoolean(PREF_IPV4_ENABLED, ipv4Switch.isChecked());
        editor.putBoolean(PREF_IPV6_ENABLED, ipv6Switch.isChecked());
        editor.putInt(PREF_APP_FILTER_MODE, appFilterModeRadioGroup.getCheckedRadioButtonId());
        editor.putString(PREF_EXCLUDED_IPS, excludedIpsEditText.getText().toString());
        editor.apply();
    }

    private void handleProtocolChange(String protocol) {
        // Enable/disable authentication fields based on protocol
        boolean needsAuth = protocol.equals("SOCKS5") || protocol.equals("HTTP");
        boolean needsServer = !"Direct".equals(protocol) && !"Reject".equals(protocol);

        usernameEditText.setEnabled(needsAuth);
        passwordEditText.setEnabled(needsAuth);
        serverEditText.setEnabled(needsServer);
        portEditText.setEnabled(needsServer);

        // Set default ports for common protocols
        if (portEditText.getText().toString().isEmpty()) {
            switch (protocol) {
                case "SOCKS5":
                    portEditText.setText("1080");
                    break;
                case "HTTP":
                    portEditText.setText("8080");
                    break;
                case "SOCKS4":
                    portEditText.setText("1080");
                    break;
                case "Shadowsocks":
                    portEditText.setText("8388");
                    break;
                case "Relay":
                    portEditText.setText("8080");
                    break;
                default:
                    // Direct and Reject don't need server/port
                    break;
            }
        }

        // Show protocol-specific hints and descriptions
        switch (protocol) {
            case "SOCKS5":
                serverInputLayout.setHint("SOCKS5 proxy server (supports username/password auth)");
                break;
            case "HTTP":
                serverInputLayout.setHint("HTTP proxy server (must support CONNECT method)");
                break;
            case "SOCKS4":
                serverInputLayout.setHint("SOCKS4 proxy server (supports USERID auth only)");
                break;
            case "Shadowsocks":
                serverInputLayout.setHint("Shadowsocks server (format: ss://method:password@host:port)");
                break;
            case "Relay":
                serverInputLayout.setHint("Relay proxy server (supports UDP/TCP relay)");
                break;
            case "Direct":
                serverInputLayout.setHint("Direct connection (no proxy)");
                break;
            case "Reject":
                serverInputLayout.setHint("Reject all connections (block mode)");
                break;
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
