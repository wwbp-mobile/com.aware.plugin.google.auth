package com.aware.plugin.google.auth;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Plugin;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

public class Plugin extends Aware_Plugin {

    public static final String ACTION_AWARE_GOOGLE_LOGIN_COMPLETE = "ACTION_AWARE_GOOGLE_LOGIN_COMPLETE";
    public static final String EXTRA_ACCOUNT = "google_account";

    public static ContextProducer contextProducer;
    public static ContentValues accountDetails;

    private static final int GOOGLE_LOGIN_NOTIFICATION_ID = 5675687;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();

        TAG = "AWARE: Google Login";

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                Intent logged = new Intent(ACTION_AWARE_GOOGLE_LOGIN_COMPLETE);
                logged.putExtra(EXTRA_ACCOUNT, accountDetails);
                sendBroadcast(logged);
            }
        };
        contextProducer = CONTEXT_PRODUCER;

        DATABASE_TABLES = Provider.DATABASE_TABLES;
        TABLES_FIELDS = Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Provider.Google_Account.CONTENT_URI};

        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_PHONE_STATE);

        if (!is_google_services_available()) {
            if (DEBUG)
                Log.e(TAG, "Google Services APIs are not available on this device");
        }
    }

    private boolean is_google_services_available() {
        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int result = googleApi.isGooglePlayServicesAvailable(this);
        return (result == ConnectionResult.SUCCESS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (permissions_ok) {

            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            String[] projection = new String[]{Provider.Google_Account.EMAIL, Provider.Google_Account.NAME};
            Cursor cursor = getContentResolver().query(Provider.Google_Account.CONTENT_URI, projection, null, null, null);
            if (cursor != null && !cursor.moveToLast()) {
                showGoogleLoginPopup();
                cursor.close();
            }

            Aware.setSetting(this, Settings.STATUS_PLUGIN_GOOGLE_LOGIN, true);

            Aware.startAWARE(this);

        } else {
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permissions);

            //Sign-in to Google?
            showGoogleLoginPopup();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (notificationManager != null)
            notificationManager.cancel(GOOGLE_LOGIN_NOTIFICATION_ID);

        Aware.setSetting(this, Settings.STATUS_PLUGIN_GOOGLE_LOGIN, false);
        Aware.stopAWARE(this);
    }

    private void showGoogleLoginPopup() {
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_app_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.noti_desc))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        Notification notification = notificationBuilder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;
        notificationManager.notify(GOOGLE_LOGIN_NOTIFICATION_ID, notification);
    }
}
