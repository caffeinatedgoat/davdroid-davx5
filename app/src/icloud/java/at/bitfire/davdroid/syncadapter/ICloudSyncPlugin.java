package at.bitfire.davdroid.syncadapter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v7.app.NotificationCompat;

import com.android.vending.billing.IInAppBillingService;

import java.util.ArrayList;
import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.SubscriptionManager;
import at.bitfire.davdroid.ui.SubscriptionActivity;

public class ICloudSyncPlugin implements ISyncPlugin {

    private IInAppBillingService billingService;
    private final Object billingServiceLock = new Object();

    private ServiceConnection billingServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized(billingServiceLock) {
                billingService = IInAppBillingService.Stub.asInterface(service);
                billingServiceLock.notify();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            billingService = null;
        }
    };


    @Override
    public boolean beforeSync(@NonNull Context context) {
        // connect to Google Play billing service
        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        if (context.bindService(serviceIntent, billingServiceConnection, Context.BIND_AUTO_CREATE) == false) {
            App.log.severe("Couldn't connect to Google Play billing service");
            return false;
        }

        // wait for billing service
        synchronized(billingServiceLock) {
            if (billingService == null)
                try {
                    billingServiceLock.wait();
                } catch (InterruptedException e) {
                    App.log.log(Level.SEVERE, "Couldn't wait for Google Play billing service", e);
                    return false;
                }
        }

        SubscriptionManager subscription = new SubscriptionManager(context, billingService);
        if (subscription.inTrialPeriod()) {
            App.log.info("Running as free trial");
            return true;
        }

        // check for license / bought products
        try {
            Bundle subs = billingService.getPurchases(3, BuildConfig.APPLICATION_ID, "subs", null);
            int response = subs.getInt("RESPONSE_CODE");
            if (response == 0) {
                ArrayList<String> skus = subs.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
                App.log.info(skus.size() + " purchased product(s)");
                for (String sku : skus)
                    App.log.info("   - purchased product: " + sku);

                if (skus.isEmpty()) {
                    // no subscription found
                    Notification notify = new NotificationCompat.Builder(context)
                            .setSmallIcon(R.drawable.ic_account_circle_white)
                            .setContentTitle(context.getString(R.string.subscription_notification_title))
                            .setContentText(context.getString(R.string.subscription_notification_text))
                            .setCategory(NotificationCompat.CATEGORY_STATUS)
                            .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, SubscriptionActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
                            .setLocalOnly(true)
                            .build();
                    NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.notify(Constants.NOTIFICATION_SUBSCRIPTION, notify);

                    return false;
                }

            } else {
                App.log.severe("Couldn't query Google Play subscriptions: response code " + response);
                return false;
            }
        } catch (RemoteException e) {
            App.log.log(Level.SEVERE, "Couldn't query Google Play subscriptions", e);
            return false;
        }

        return true;
    }

    @Override
    public void afterSync(@NonNull Context context) {
        context.unbindService(billingServiceConnection);
    }

}
