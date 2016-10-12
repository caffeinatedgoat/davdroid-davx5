package at.bitfire.davdroid.ui;

import android.app.LoaderManager;
import android.app.PendingIntent;
import android.content.AsyncTaskLoader;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.Loader;
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
import at.bitfire.davdroid.SubscriptionManager.SubscriptionInfo;
import at.bitfire.davdroid.SubscriptionManager.SubscriptionInfo.Status;

public class SubscriptionActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<SubscriptionActivity.BillingData> {

    PendingIntent buyIntent;

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

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_subscription, menu);
        return true;
    }


    public void showInMarket(MenuItem item) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID));
        intent.setPackage("com.android.vending");
        startActivity(intent);
    }

    public void buyLicenseInMarket() {
        if (buyIntent != null)
            try {
                startIntentSender(buyIntent.getIntentSender(), new Intent(), 0, 0, 0);
            } catch(IntentSender.SendIntentException e) {
                App.log.log(Level.SEVERE, "Couldn't start in-app billing intent", e);
            }
    }


    @Override
    public Loader<BillingData> onCreateLoader(int id, Bundle args) {
        return new LicenseLoader(this);
    }

    @Override
    public void onLoadFinished(Loader<BillingData> loader, BillingData data) {
        findViewById(R.id.progress).setVisibility(View.GONE);

        if (data.info != null) {
            DateFormat df = DateFormat.getDateTimeInstance();
            TextView tv = (TextView)findViewById(R.id.license_status);
            if (data.info.status == Status.TRIAL)
                tv.setText(Html.fromHtml(getString(R.string.subscription_management_status_trial, df.format(new Date(data.info.trialExpiration)))));
            else if (data.info.status == Status.EXPIRED)
                tv.setText(Html.fromHtml(getString(R.string.subscription_management_status_expired)));
            else if (data.info.status == Status.ACTIVE)
                tv.setText(Html.fromHtml(getString(R.string.subscription_management_status_active, df.format(new Date(data.info.purchaseTime)))));

            findViewById(R.id.get_license).setVisibility(data.info.status == SubscriptionInfo.Status.ACTIVE ? View.GONE : View.VISIBLE);
        }

        if (data.productDetails != null && !data.productDetails.isEmpty())
            try {
                JSONObject product = data.productDetails.get(0);

                TextView tv = (TextView)findViewById(R.id.product_title);
                tv.setText(product.getString("title"));

                tv = (TextView)findViewById(R.id.product_price);
                tv.setText(product.getString("price"));

                tv = (TextView)findViewById(R.id.product_description);
                tv.setText(product.getString("description"));
            } catch(JSONException e) {
                App.log.log(Level.SEVERE, "Couldn't parse product details", e);
            }
        else
            findViewById(R.id.get_license).setVisibility(View.GONE);

        if (data.error != null)
            Snackbar.make(findViewById(R.id.license_status), data.error, Snackbar.LENGTH_INDEFINITE).show();
    }

    @Override
    public void onLoaderReset(Loader<BillingData> loader) {
        buyIntent = null;
    }


    static class BillingData {
        SubscriptionInfo info;
        ArrayList<JSONObject> productDetails;
        PendingIntent buyIntent;

        String error;
    }

    static class LicenseLoader extends AsyncTaskLoader<BillingData> implements ServiceConnection {

        private final String[] SKU_LIST = { "unlimited.onetime1" };

        private IInAppBillingService billingService;

        public LicenseLoader(Context context) {
            super(context);
        }

        @Override
        protected void onStartLoading() {
            Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
            serviceIntent.setPackage("com.android.vending");
            if (!getContext().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE)) {
                App.log.severe("Couldn't connect to Google Play billing service");

                BillingData data = new BillingData();
                data.error = getContext().getString(R.string.subscription_management_play_connection_error);
                deliverResult(data);
            }
        }

        @Override
        protected void onStopLoading() {
            getContext().unbindService(this);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            billingService = IInAppBillingService.Stub.asInterface(service);
            forceLoad();
        }

        @Override
        public BillingData loadInBackground() {
            BillingData data = new BillingData();
            try {
                SubscriptionManager subscription = new SubscriptionManager(getContext(), billingService);
                data.info = subscription.info;

                // fetch product details
                data.productDetails = subscription.getProductDetails(SKU_LIST);

                // get buy intent
                Bundle bundle = billingService.getBuyIntent(3, BuildConfig.APPLICATION_ID, SKU_LIST[0], "inapp", null);
                data.buyIntent = bundle.getParcelable("BUY_INTENT");
            } catch(RemoteException|BillingException e) {
                App.log.log(Level.WARNING, "Couldn't retrieve product data from Google Play", e);
                data.error = getContext().getString(R.string.subscription_management_play_connection_error);
            }
            return data;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            billingService = null;
        }

    }

}
