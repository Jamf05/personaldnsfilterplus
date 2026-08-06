package plus.dnsfilter.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class VpnMonitorService extends Service {

    private static final String TAG = "VpnMonitorService";
    private static final String CHANNEL_ID = "VpnMonitorChannel";
    private static final int CHECK_INTERVAL_MS = 5000; // Cada 5 segundos
    private static final int NOTIFICATION_ID = 2;

    private Handler handler;
    private Runnable monitorRunnable;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            isRunning = true;
            startForegroundWithNotification();
            startMonitoring();
            Log.d(TAG, "VPN Monitor started.");
        }
        return START_STICKY; // Se reinicia automáticamente si el sistema lo mata
    }

    private void startMonitoring() {
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isVpnActive()) {
                    Log.d(TAG, "VPN down — triggering reconnect...");
                    reconnectVpn();
                }

                if (isRunning) {
                    handler.postDelayed(this, CHECK_INTERVAL_MS);
                }
            }
        };

        handler.postDelayed(monitorRunnable, CHECK_INTERVAL_MS);
    }

    private boolean isVpnActive() {
        ConnectivityManager cm = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

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

    private void reconnectVpn() {
        try {
            Intent serviceIntent = new Intent(this, DNSFilterService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            Log.d(TAG, "VPN reconnect triggered.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to reconnect VPN: " + e.getMessage());
        }
    }

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "VPN Monitor",
                    NotificationManager.IMPORTANCE_LOW
            );
            nm.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Protección activa")
                    .setContentText("Monitoreando conexión VPN...")
                    .setSmallIcon(R.drawable.icon)
                    .setOngoing(true)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;

        if (handler != null && monitorRunnable != null) {
            handler.removeCallbacks(monitorRunnable);
        }

        // Auto-reiniciar el monitor si se destruye
        Intent restartIntent = new Intent(this, VpnMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }

        Log.d(TAG, "VPN Monitor destroyed — restarting...");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}