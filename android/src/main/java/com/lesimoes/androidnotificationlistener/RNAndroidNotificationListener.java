package com.lesimoes.androidnotificationlistener;

import android.content.Intent;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.app.Notification;
import com.google.gson.Gson;
import com.facebook.react.HeadlessJsTaskService;

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
        
        Context context = getApplicationContext();
        Intent serviceIntent = new Intent(context, RNAndroidNotificationListenerHeadlessJsTaskService.class);
        RNNotification notification = new RNNotification(context, sbn);
        
        // Add notification key to the notification object
        notification.key = sbn.getKey();
        notification.packageName = sbn.getPackageName();
        
        // Check if notification has reply action
        boolean hasReplyAction = false;
        if (statusBarNotification.actions != null) {
            for (Notification.Action action : statusBarNotification.actions) {
                if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                    hasReplyAction = true;
                    break;
                }
            }
        }
        notification.canReply = hasReplyAction;
        
        Gson gson = new Gson();
        String serializedNotification = gson.toJson(notification);
        serviceIntent.putExtra("notification", serializedNotification);
        HeadlessJsTaskService.acquireWakeLockNow(context);
        context.startService(serviceIntent);
    }
    
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}
}