package at.bitfire.davdroid

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

class SubscriptionManager(
        val context: Context,
        val billingService: IInAppBillingService
) {

    val TRIAL_PERIOD = 7*24*3600*1000

    val info = SubscriptionInfo()


    init {
        val pm = context.packageManager
        try {
            val packageInfo = pm.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
            App.log.fine("First installed at ${packageInfo.firstInstallTime}")

            info.trialExpiration = packageInfo.firstInstallTime + TRIAL_PERIOD
            info.trialExpiration?.let {
                if (it > System.currentTimeMillis())
                    info.status = Status.TRIAL
            }
        } catch(e: PackageManager.NameNotFoundException) {
            App.log.log(Level.SEVERE, "Can't find local package", e)
        }

        processPurchases("inapp")
        if (info.status != Status.ACTIVE)
            // legacy: check subscriptions, too
            processPurchases("subs")
    }

    fun isValid() = info.status != Status.EXPIRED


    @Throws(BillingException::class)
    fun processPurchases(type: String) {
        val result: Bundle?
        try {
            result = billingService.getPurchases(3, BuildConfig.APPLICATION_ID, type, null)
            val code = result.getInt("RESPONSE_CODE")
            if (code != 0)
                throw BillingException("Couldn't get purchases: $code")
        } catch(e: RemoteException) {
            throw BillingException("Couldn't get purchases", e)
        }

        result?.let {
            for (json in result.getStringArrayList("INAPP_PURCHASE_DATA_LIST"))
                try {
                    val product = JSONObject(json)
                    if (product.getInt("purchaseState") == 0 /* purchased */) {
                        info.status = Status.ACTIVE
                        info.purchaseTime = product.getLong("purchaseTime")
                    }
                } catch(e: JSONException) {
                    throw BillingException("Couldn't parse product details", e)
                }
        }
    }

    @Throws(BillingException::class, JSONException::class)
    fun getProductDetails(skus: Array<String>): ArrayList<JSONObject> {
        val skuBundle = Bundle()
        skuBundle.putStringArrayList("ITEM_ID_LIST", ArrayList(skus.asList()))

        val result: Bundle
        try {
            result = billingService.getSkuDetails(3, BuildConfig.APPLICATION_ID, "inapp", skuBundle)
        } catch (e: RemoteException) {
            throw BillingException("Couldn't get product details", e)
        }
        val code = result.getInt("RESPONSE_CODE")
        if (code != 0)
            throw BillingException("Couldn't get product details: $code")

        val response = result.getStringArrayList("DETAILS_LIST")
        val details = ArrayList<JSONObject>(response.size)
        response.mapTo(details, { JSONObject(it) })
        return details
    }


    class SubscriptionInfo {

        enum class Status {
            TRIAL,
            ACTIVE,
            EXPIRED
        }

        var status = Status.EXPIRED
        var trialExpiration: Long? = null
        var purchaseTime: Long? = null

    }

}
