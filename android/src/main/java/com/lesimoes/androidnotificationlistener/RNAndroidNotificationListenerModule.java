package com.lesimoes.androidnotificationlistener;

import androidx.core.app.NotificationManagerCompat;
import android.provider.Settings;
import android.content.Intent;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import java.util.Set;

public class RNAndroidNotificationListenerModule extends ReactContextBaseJavaModule {
    private static ReactApplicationContext reactContext;
    private static final String TAG = "RNAndroidNotificationListener";

    public RNAndroidNotificationListenerModule(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @ReactMethod
    public void getPermissionStatus(Promise promise) {
        if (this.reactContext == null) {
            promise.resolve("unknown");
        } else {
            String packageName = this.reactContext.getPackageName();
            Set enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this.reactContext);
            if (enabledPackages.contains(packageName)) {
                promise.resolve("authorized");
            } else {
                promise.resolve("denied");
            }
        }
    }
    
    @ReactMethod
    public void requestPermission() {
        if (this.reactContext != null) {
            final Intent i = new Intent();
            i.setAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            this.reactContext.startActivity(i);
        }
    }

    // New method to reply to a notification
    @ReactMethod
    public void replyToNotification(String notificationKey, String replyMessage, Promise promise) {
        try {
            RNAndroidNotificationListener listener = RNAndroidNotificationListener.getInstance();
            if (listener == null) {
                Log.e(TAG, "Notification listener service is not running");
                promise.reject("SERVICE_NOT_RUNNING", "Notification listener service is not running");
                return;
            }
            
            listener.replyToNotification(notificationKey, replyMessage);
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error replying to notification: " + e.getMessage());
            promise.reject("REPLY_ERROR", "Failed to reply to notification: " + e.getMessage());
        }
    }
}