package plus.dnsfilter.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

public class VpnStateReceiver extends BroadcastReceiver {

    private static final String TAG = "VpnStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            Log.d(TAG, "Connectivity changed — checking VPN state...");

            if (!isVpnActive(context)) {
                Log.d(TAG, "VPN not active — reconnecting...");
                reconnectVpn(context);
            } else {
                Log.d(TAG, "VPN is active — no action needed.");
            }
        }
    }

    private boolean isVpnActive(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(
                        NetworkCapabilities.TRANSPORT_VPN)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void reconnectVpn(Context context) {
        try {
            Intent serviceIntent = new Intent(context, DNSFilterService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.d(TAG, "VPN reconnect triggered.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to reconnect VPN: " + e.getMessage());
        }
    }
}