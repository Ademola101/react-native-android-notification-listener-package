package com.lesimoes.androidnotificationlistener;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.service.notification.StatusBarNotification;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

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
            Set<String> enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this.reactContext);
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
    
    /**
     * Replies to a notification
     * 
     * @param notificationKey The key of the notification to reply to
     * @param packageName The package name of the app that sent the notification
     * @param replyText The text to send as a reply
     * @param promise Promise to return the result of the operation
     */
    @ReactMethod
    public void replyToNotification(String notificationKey, String packageName, String replyText, Promise promise) {
        if (this.reactContext == null) {
            promise.reject("ERROR", "React context is null");
            return;
        }
        
        RNAndroidNotificationListener service = RNAndroidNotificationListener.getInstance();
        if (service == null) {
            promise.reject("ERROR", "Notification listener service is not running");
            return;
        }
        
        try {
            // Get active notifications from the service
            StatusBarNotification[] activeNotifications = service.getActiveNotifications();
            
            if (activeNotifications == null) {
                promise.reject("ERROR", "Could not get active notifications");
                return;
            }
            
            StatusBarNotification targetNotification = null;
            
            // Find the notification with matching key
            for (StatusBarNotification sbn : activeNotifications) {
                if (sbn.getKey().equals(notificationKey) && sbn.getPackageName().equals(packageName)) {
                    targetNotification = sbn;
                    break;
                }
            }
            
            if (targetNotification == null) {
                promise.reject("ERROR", "Notification not found");
                return;
            }
            
            // Get the actions from the notification
            Notification notification = targetNotification.getNotification();
            if (notification.actions == null || notification.actions.length == 0) {
                promise.reject("ERROR", "No actions found in notification");
                return;
            }
            
            // Find action with RemoteInput for reply
            Notification.Action replyAction = null;
            RemoteInput remoteInput = null;
            
            for (Notification.Action action : notification.actions) {
                if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                    replyAction = action;
                    remoteInput = action.getRemoteInputs()[0];
                    break;
                }
            }
            
            if (replyAction == null || remoteInput == null) {
                promise.reject("ERROR", "No reply action found in notification");
                return;
            }
            
            // Send the reply
            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putCharSequence(remoteInput.getResultKey(), replyText);
            
            RemoteInput.addResultsToIntent(new RemoteInput[]{remoteInput}, intent, bundle);
            
            try {
                replyAction.actionIntent.send(this.reactContext, 0, intent);
                promise.resolve(true);
            } catch (PendingIntent.CanceledException e) {
                promise.reject("ERROR", "Failed to send reply: " + e.getMessage());
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Exception in replyToNotification: " + e.getMessage());
            promise.reject("ERROR", "Failed to reply to notification: " + e.getMessage());
        }
    }
    
    /**
     * Gets all active notifications that have reply actions
     * 
     * @param promise Promise to return the result of the operation
     */
    @ReactMethod
    public void getNotificationsWithReplyAction(Promise promise) {
        if (this.reactContext == null) {
            promise.reject("ERROR", "React context is null");
            return;
        }
        
        RNAndroidNotificationListener service = RNAndroidNotificationListener.getInstance();
        if (service == null) {
            promise.reject("ERROR", "Notification listener service is not running");
            return;
        }
        
        try {
            StatusBarNotification[] activeNotifications = service.getActiveNotifications();
            
            if (activeNotifications == null) {
                promise.reject("ERROR", "Could not get active notifications");
                return;
            }
            
            WritableArray notificationsArray = Arguments.createArray();
            
            for (StatusBarNotification sbn : activeNotifications) {
                Notification notification = sbn.getNotification();
                
                if (notification.actions != null) {
                    boolean hasReplyAction = false;
                    
                    for (Notification.Action action : notification.actions) {
                        if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                            hasReplyAction = true;
                            break;
                        }
                    }
                    
                    if (hasReplyAction) {
                        WritableMap notificationMap = Arguments.createMap();
                        
                        // Get notification details
                        String title = "";
                        String text = "";
                        
                        if (notification.extras != null) {
                            CharSequence titleSequence = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
                            CharSequence textSequence = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
                            
                            if (titleSequence != null) {
                                title = titleSequence.toString();
                            }
                            
                            if (textSequence != null) {
                                text = textSequence.toString();
                            }
                        }
                        
                        notificationMap.putString("key", sbn.getKey());
                        notificationMap.putString("packageName", sbn.getPackageName());
                        notificationMap.putString("title", title);
                        notificationMap.putString("text", text);
                        notificationMap.putDouble("time", sbn.getPostTime());
                        
                        notificationsArray.pushMap(notificationMap);
                    }
                }
            }
            
            promise.resolve(notificationsArray);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to get notifications with reply action: " + e.getMessage());
        }
    }
}