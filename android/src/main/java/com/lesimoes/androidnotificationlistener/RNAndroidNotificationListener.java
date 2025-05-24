package com.lesimoes.androidnotificationlistener;

import android.app.Notification;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.os.Bundle;
import android.app.PendingIntent;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.gson.Gson;

public class RNAndroidNotificationListener extends NotificationListenerService {
    private static final String TAG = "RNAndroidNotificationListener";
    private static RNAndroidNotificationListener instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static RNAndroidNotificationListener getInstance() {
        return instance;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notification statusBarNotification = sbn.getNotification();
        if (statusBarNotification == null || statusBarNotification.extras == null) {
            Log.d(TAG, "The notification received has no data");
            return;
        }

        // Check if notification has actions (for potential auto-response)
        boolean hasReplyAction = false;
        if (statusBarNotification.actions != null) {
            for (Notification.Action action : statusBarNotification.actions) {
                if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                    hasReplyAction = true;
                    break;
                }
            }
        }

        // Create enhanced notification object with action info
        Context context = getApplicationContext();
        Intent serviceIntent = new Intent(context, RNAndroidNotificationListenerHeadlessJsTaskService.class);
        RNNotification notification = new RNNotification(context, sbn);
        notification.setHasReplyAction(hasReplyAction);
        notification.setNotificationKey(sbn.getKey());  // Store key for reply reference
        
        Gson gson = new Gson();
        String serializedNotification = gson.toJson(notification);
        serviceIntent.putExtra("notification", serializedNotification);
        HeadlessJsTaskService.acquireWakeLockNow(context);
        context.startService(serviceIntent);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}

    // Method to respond to a notification
    public void replyToNotification(String notificationKey, String replyMessage) {
        StatusBarNotification[] activeNotifications = this.getActiveNotifications();
        
        if (activeNotifications == null) {
            Log.d(TAG, "No active notifications found");
            return;
        }

        StatusBarNotification targetNotification = null;
        for (StatusBarNotification sbn : activeNotifications) {
            if (sbn.getKey().equals(notificationKey)) {
                targetNotification = sbn;
                break;
            }
        }

        if (targetNotification == null) {
            Log.d(TAG, "Target notification not found");
            return;
        }

        Notification.Action replyAction = null;
        if (targetNotification.getNotification().actions != null) {
            for (Notification.Action action : targetNotification.getNotification().actions) {
                if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                    replyAction = action;
                    break;
                }
            }
        }

        if (replyAction == null) {
            Log.d(TAG, "No reply action found in notification");
            return;
        }

        try {
            RemoteInput[] remoteInputs = replyAction.getRemoteInputs();
            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            
            for (RemoteInput remoteInput : remoteInputs) {
                bundle.putCharSequence(remoteInput.getResultKey(), replyMessage);
            }
            
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle);
            
            // Execute the pending intent with our reply
            try {
                replyAction.actionIntent.send(this, 0, intent);
                Log.d(TAG, "Reply sent successfully: " + replyMessage);
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "Failed to send reply: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error replying to notification: " + e.getMessage());
        }
    }
}