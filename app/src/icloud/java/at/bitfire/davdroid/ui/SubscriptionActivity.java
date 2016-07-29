package at.bitfire.davdroid.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;

import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.R;

public class SubscriptionActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_subscription);

        /*Bundle buyIntentBundle = billingService.getBuyIntent(3, BuildConfig.APPLICATION_ID, "unlimited.monthly", "subs", null);
        PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
        context.startIntentSender(pendingIntent.getIntentSender(), new Intent(), 0, 0, 0);*/

    }
}
