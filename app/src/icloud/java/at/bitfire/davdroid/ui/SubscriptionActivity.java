package at.bitfire.davdroid.ui;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.BillingException;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.SubscriptionManager;
import at.bitfire.davdroid.SubscriptionManager.SubscriptionInfo.Status;

public class SubscriptionActivity extends AppCompatActivity implements ServiceConnection {

    private final String[] SKU_LIST = { "unlimited.monthly" };

    private IInAppBillingService billingService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_subscription);

        Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/BebasNeue_Light.ttf");
        ((TextView)findViewById(R.id.title)).setTypeface(tf);

        Button btn = (Button)findViewById(R.id.get_in_google_play);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buyLicenseInMarket();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_subscription, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        if (bindService(serviceIntent, this, Context.BIND_AUTO_CREATE) == false) {
            App.log.severe("Couldn't connect to Google Play billing service");
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(this);
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        billingService = IInAppBillingService.Stub.asInterface(service);

        try {
            SubscriptionManager subscription = new SubscriptionManager(this, billingService);

            DateFormat df = DateFormat.getDateTimeInstance();
            TextView tv = (TextView)findViewById(R.id.license_status);
            if (subscription.info.status == Status.TRIAL)
                tv.setText(Html.fromHtml(getString(R.string.subscription_management_status_trial, df.format(new Date(subscription.info.trialExpiration)))));
            else if (subscription.info.status == Status.EXPIRED)
                tv.setText(Html.fromHtml(getString(R.string.subscription_management_status_expired)));
            else if (subscription.info.status == Status.ACTIVE)
                tv.setText(Html.fromHtml(getString(R.string.subscription_management_status_active, df.format(new Date(subscription.info.purchaseTime)))));

            ArrayList<JSONObject> products = subscription.getSubscriptionDetails(SKU_LIST);
            if (!products.isEmpty())
                try {
                    JSONObject product = products.get(0);

                    tv = (TextView)findViewById(R.id.product_title);
                    tv.setText(Html.fromHtml(getString(R.string.subscription_management_product_title_price,
                            product.getString("title"), product.getString("price"))));

                    tv = (TextView)findViewById(R.id.product_description);
                    tv.setText(product.getString("description"));
                } catch(JSONException e) {
                    throw new BillingException("Couldn't parse product details", e);
                }

            findViewById(R.id.get_license).setVisibility(subscription.info.status == Status.ACTIVE ? View.GONE : View.VISIBLE);
        } catch(BillingException e) {
            App.log.log(Level.WARNING, "Couldn't connect to Google Play", e);
            Snackbar.make(getWindow().getDecorView(), R.string.subscription_management_play_connection_error, Snackbar.LENGTH_INDEFINITE).show();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        billingService = null;
    }


    public void showInMarket(MenuItem item) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID));
        intent.setPackage("com.android.vending");
        startActivity(intent);
    }

    public void buyLicenseInMarket() {
        try {
            Bundle bundle = billingService.getBuyIntent(3, BuildConfig.APPLICATION_ID, SKU_LIST[0], "subs", null);
            PendingIntent intent = bundle.getParcelable("BUY_INTENT");
            startIntentSender(intent.getIntentSender(), new Intent(), 0, 0, 0);
        } catch(RemoteException|IntentSender.SendIntentException e) {
            App.log.log(Level.SEVERE, "Couldn't start in-app billing intent", e);
        }
    }

}
