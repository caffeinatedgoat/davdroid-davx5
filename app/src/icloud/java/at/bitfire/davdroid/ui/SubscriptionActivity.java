package at.bitfire.davdroid.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.R;

public class SubscriptionActivity extends Activity {

    private IInAppBillingService billingService;

    private ServiceConnection billingServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            billingService = IInAppBillingService.Stub.asInterface(service);

            Bundle buyIntentBundle = null;
            try {
                buyIntentBundle = billingService.getBuyIntent(3, BuildConfig.APPLICATION_ID, "unlimited.monthly", "subs", null);
                PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                startIntentSender(pendingIntent.getIntentSender(), new Intent(), 0, 0, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            billingService = null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_subscription);

        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        if (bindService(serviceIntent, billingServiceConnection, Context.BIND_AUTO_CREATE) == false) {
            App.log.severe("Couldn't connect to Google Play billing service");
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(billingServiceConnection);
    }

}
