package com.example.restricted_app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView resultTextView;
    private Button startButton;
    private WebView webView;
    private BroadcastReceiver sensorReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultTextView = findViewById(R.id.resultTextView);
        startButton = findViewById(R.id.startButton);
        webView = findViewById(R.id.webView);

        startButton.setOnClickListener(v -> handleVPNActivation("Kid"));

        // Register the BroadcastReceiver
        sensorReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean isKid = intent.getBooleanExtra("isKid", false);
                handleVPNActivation(isKid ? "Kid" : "NotKid");
            }
        };
        IntentFilter filter = new IntentFilter("com.example.prediction.SENSOR_VALUES");
        registerReceiver(sensorReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(sensorReceiver);
    }

    private void handleVPNActivation(String result) {
        if ("Kid".equals(result)) {
            startVPNService();
            MyVPNService.setBlockedUrls(new String[]{
                    "www.youtube.com",
                    "www.facebook.com"
            });
            Toast.makeText(this, "VPN for parental control activated", Toast.LENGTH_SHORT).show();
            webView.setVisibility(View.VISIBLE);
        } else {
            stopVPNService();
            webView.setVisibility(View.GONE);
        }
    }

    private void startVPNService() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Intent intent = new Intent(this, MyVPNService.class);
            startService(intent);
        }
    }

    private void stopVPNService() {
        Intent intent = new Intent(this, MyVPNService.class);
        stopService(intent);
    }
}
