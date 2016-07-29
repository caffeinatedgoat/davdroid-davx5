package at.bitfire.davdroid;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.vending.billing.IInAppBillingService;

import java.util.logging.Level;

public class SubscriptionManager {

    static final int TRIAL_PERIOD = 7*24*3600*1000;

    Context context;
    IInAppBillingService billingService;


    public SubscriptionManager(Context context, IInAppBillingService billingService) {
        this.context = context;
    }

    public boolean inTrialPeriod() {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            App.log.fine("First installed at " + info.firstInstallTime);

            return info.firstInstallTime > System.currentTimeMillis() - TRIAL_PERIOD;

        } catch (PackageManager.NameNotFoundException e) {
            App.log.log(Level.SEVERE, "Can't find local package", e);
            return false;
        }
    }

}
