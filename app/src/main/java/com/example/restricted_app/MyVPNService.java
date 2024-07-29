package com.example.restricted_app;

import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class MyVPNService extends VpnService {

    private static final String TAG = "MyVPNService";
    private static final Set<String> blockedUrls = new HashSet<>();
    private ParcelFileDescriptor vpnInterface;
    private Thread mThread;
    private boolean isRunning = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mThread = new Thread(() -> {
            try {
                // Configure the VPN interface
                Builder builder = new Builder();
                builder.addAddress("10.0.0.2", 32);
                builder.addRoute("0.0.0.0", 0);
                vpnInterface = builder.setSession("MyVPNService").establish();

                // Print blocked URLs for debugging
                Log.d(TAG, "Blocked URLs: " + blockedUrls.toString());

                FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());

                isRunning = true;
                ByteBuffer packet = ByteBuffer.allocate(32767); // Allocate buffer for packet reading

                while (isRunning) {
                    int length = in.read(packet.array());
                    if (length > 0) {
                        packet.limit(length);

                        // Check for blocked URLs
                        String payload = new String(packet.array(), 0, length, StandardCharsets.UTF_8);
                        Log.d(TAG, "Payload: " + payload);

                        boolean blockPacket = false;
                        for (String url : blockedUrls) {
                            if (payload.contains(url)) {
                                blockPacket = true;
                                Log.d(TAG, "Blocking URL: " + url);
                                break;
                            }
                        }

                        if (!blockPacket) {
                            out.write(packet.array(), 0, length);
                        } else {
                            Log.d(TAG, "Packet blocked");
                        }
                    }
                    packet.clear();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error in VPN service", e);
            }
        });
        mThread.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (mThread != null) {
            mThread.interrupt();
        }
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing VPN interface", e);
        }
    }

    // Method to set blocked URLs
    public static void setBlockedUrls(String[] urls) {
        blockedUrls.clear();
        for (String url : urls) {
            blockedUrls.add(url);
        }
    }
}