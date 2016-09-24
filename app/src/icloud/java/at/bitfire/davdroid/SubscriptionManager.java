package at.bitfire.davdroid;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import at.bitfire.davdroid.SubscriptionManager.SubscriptionInfo.Status;

public class SubscriptionManager {

    private static final int TRIAL_PERIOD = 7*24*3600*1000;

    protected Context context;
    protected IInAppBillingService billingService;

    public final SubscriptionInfo info = new SubscriptionInfo();


    public SubscriptionManager(@NonNull Context context, @NonNull IInAppBillingService billingService) throws BillingException {
        this.context = context;
        this.billingService = billingService;

        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            App.log.fine("First installed at " + packageInfo.firstInstallTime);

            info.trialExpiration = packageInfo.firstInstallTime + TRIAL_PERIOD;
            if (info.trialExpiration > System.currentTimeMillis())
                info.status = Status.TRIAL;
        } catch(PackageManager.NameNotFoundException e) {
            App.log.log(Level.SEVERE, "Can't find local package", e);
        }

        processPurchases("inapp");
        if (info.status != Status.ACTIVE)
            // legacy: check subscriptions, too
            processPurchases("subs");
    }

    public boolean isValid() {
        return info.status != Status.EXPIRED;
    }


    public void processPurchases(@NonNull String type) throws BillingException {
        Bundle result = null;
        try {
            result = billingService.getPurchases(3, BuildConfig.APPLICATION_ID, type, null);
            int code = result.getInt("RESPONSE_CODE");
            if (code != 0)
                throw new BillingException("Couldn't get purchases: " + code);
        } catch(RemoteException e) {
            throw new BillingException("Couldn't get purchases", e);
        }

        if (result != null)
            for (String json : result.getStringArrayList("INAPP_PURCHASE_DATA_LIST"))
                try {
                    JSONObject product = new JSONObject(json);
                    if (product.getInt("purchaseState") == 0 /* purchased */) {
                        info.status = Status.ACTIVE;
                        info.purchaseTime = product.getLong("purchaseTime");
                    }
                } catch(JSONException e) {
                    throw new BillingException("Couldn't parse product details", e);
                }
    }

    public ArrayList<JSONObject> getProductDetails(String[] skus) throws BillingException {
        Bundle skuBundle = new Bundle();
        skuBundle.putStringArrayList("ITEM_ID_LIST", new ArrayList(Arrays.asList(skus)));

        Bundle result = null;
        try {
            result = billingService.getSkuDetails(3, BuildConfig.APPLICATION_ID, "inapp", skuBundle);
        } catch (RemoteException e) {
            throw new BillingException("Couldn't get subscription details", e);
        }
        int code = result.getInt("RESPONSE_CODE");
        if (code != 0)
            throw new BillingException("Couldn't get subscription details: " + code);

        ArrayList<String> response = result.getStringArrayList("DETAILS_LIST");
        final ArrayList<JSONObject> details = new ArrayList<>(response.size());
        for (String json : response)
            try {
                details.add(new JSONObject(json));
            } catch(JSONException e) {
                throw new BillingException("Couldn't parse product details", e);
            }

        return details;
    }


    public static class SubscriptionInfo {

        public static enum Status {
            TRIAL,
            ACTIVE,
            EXPIRED;
        };

        public Status status = Status.EXPIRED;
        public long trialExpiration;
        public long purchaseTime;

    }

}
