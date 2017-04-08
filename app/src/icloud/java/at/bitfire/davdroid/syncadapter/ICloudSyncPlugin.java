package at.bitfire.davdroid.syncadapter;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SyncResult;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.android.vending.billing.IInAppBillingService;

import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.BillingException;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.SubscriptionManager;
import at.bitfire.davdroid.ui.DebugInfoActivity;
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
    public boolean beforeSync(@NonNull Context context, SyncResult syncResult) {
        // connect to Google Play billing service
        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        if (!context.bindService(serviceIntent, billingServiceConnection, Context.BIND_AUTO_CREATE)) {
            App.log.severe("Couldn't connect to Google Play billing service");
            return false;
        }

        // wait for billing service
        synchronized(billingServiceLock) {
            if (billingService == null)
                try {
                    billingServiceLock.wait();
                } catch(InterruptedException e) {
                    App.log.log(Level.SEVERE, "Couldn't wait for Google Play billing service", e);
                    return false;
                }
        }

        Exception exception = null;

        try {
            SubscriptionManager subscription = new SubscriptionManager(context, billingService);
            if (subscription.isValid()) {
                App.log.info("Valid license found: " + subscription.info.status);
                return true;
            }
        } catch(BillingException e) {
            App.log.log(Level.WARNING, "Couldn't determine subscription state", e);
            if (e.getCause() instanceof DeadObjectException) {
                // ignore DeadObjectExceptions and restart sync as soon as possible
                syncResult.stats.numIoExceptions++;
                return false;
            } else
                exception = e;
        }

        // no valid license found, show notification
        NotificationCompat.Builder notify = new NotificationCompat.Builder(context)
                .setLargeIcon(App.getLauncherBitmap(context))
                .setSmallIcon(R.drawable.ic_account_circle_white)
                .setContentTitle(context.getString(R.string.subscription_notification_title))
                .setContentText(context.getString(R.string.subscription_notification_text))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, SubscriptionActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));

        if (exception != null) {
            Intent intent = new Intent(context, DebugInfoActivity.class);
            intent.putExtra(DebugInfoActivity.KEY_THROWABLE, exception);
            notify.addAction(R.drawable.ic_error_light, context.getString(R.string.subscription_notification_show_details),
                    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.notify(Constants.NOTIFICATION_SUBSCRIPTION, notify.build());

        return false;
    }

    @Override
    public void afterSync(@NonNull Context context, SyncResult syncResult) {
        if (billingService != null)
            try {
                context.unbindService(billingServiceConnection);
            } catch(IllegalArgumentException e) {
                App.log.log(Level.SEVERE, "Couldn't unbind Google Play service", e);
            }
    }

}
