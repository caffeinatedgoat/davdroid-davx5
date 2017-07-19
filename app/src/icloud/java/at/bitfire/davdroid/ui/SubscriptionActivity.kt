package at.bitfire.davdroid.ui

import android.app.LoaderManager
import android.app.PendingIntent
import android.content.*
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.text.Html
import android.view.Menu
import android.view.MenuItem
import android.view.View
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.SubscriptionManager
import at.bitfire.davdroid.SubscriptionManager.SubscriptionInfo
import at.bitfire.davdroid.SubscriptionManager.SubscriptionInfo.Status
import com.android.vending.billing.IInAppBillingService
import kotlinx.android.synthetic.icloud.activity_subscription.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.*
import java.util.logging.Level

class SubscriptionActivity: AppCompatActivity(), LoaderManager.LoaderCallbacks<SubscriptionActivity.BillingData> {

    var buyIntent: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_subscription)

        val tf = Typeface.createFromAsset(assets, "fonts/BebasNeue_Light.ttf")
        heading.typeface = tf

        get_in_google_play.setOnClickListener { buyLicenseInMarket() }

        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_subscription, menu)
        return true
    }


    fun showInMarket(item: MenuItem) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}"))
        intent.`package` = "com.android.vending"
        startActivity(intent)
    }

    fun buyLicenseInMarket() {
        buyIntent?.let {
            try {
                startIntentSender(it.intentSender, Intent(), 0, 0, 0)
            } catch(e: IntentSender.SendIntentException) {
                App.log.log(Level.SEVERE, "Couldn't start in-app billing intent", e)
            }
        }
    }


    override fun onCreateLoader(id: Int, args: Bundle?) = LicenseLoader(this)

    override fun onLoadFinished(loader: Loader<BillingData>, data: BillingData) {
        progress.visibility = View.GONE

        data.info?.let { info ->
            val df = DateFormat.getDateTimeInstance()
            license_status.text = when (info.status) {
                Status.TRIAL ->
                    Html.fromHtml(getString(R.string.subscription_management_status_trial, df.format(Date(info.trialExpiration!!))))
                Status.EXPIRED ->
                    Html.fromHtml(getString(R.string.subscription_management_status_expired))
                Status.ACTIVE ->
                    Html.fromHtml(getString(R.string.subscription_management_status_active, df.format(Date(info.purchaseTime!!))))
            }

            get_license.visibility = if (info.status == Status.ACTIVE) View.GONE else View.VISIBLE
        }

        data.productDetails?.let {
            if (it.isNotEmpty()) {
                val product = it.first()

                product_title.text = product.getString("title")
                product_price.text = product.getString("price")
                product_description.text = product.getString("description")
            } else
                get_license.visibility = View.GONE
        }

        buyIntent = data.buyIntent

        data.error?.let { error ->
            Snackbar.make(findViewById(R.id.license_status), error, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.subscription_management_try_again, {
                        loaderManager.restartLoader(0, null, this@SubscriptionActivity)
                    })
                    .show()
        }
    }

    override fun onLoaderReset(loader: Loader<BillingData>) {
        buyIntent = null
    }


    class BillingData {
        var info: SubscriptionInfo? = null
        var productDetails: List<JSONObject>? = null
        var buyIntent: PendingIntent? = null

        var error: String? = null
    }

    class LicenseLoader(
            context: Context
    ): AsyncTaskLoader<BillingData>(context), ServiceConnection {

        val SKU_LIST = arrayOf("unlimited.onetime1")

        var billingService: IInAppBillingService? = null

        override fun onStartLoading() {
            val serviceIntent = Intent("com.android.vending.billing.InAppBillingService.BIND")
            serviceIntent.`package` = "com.android.vending"
            if (!context.bindService(serviceIntent, this, Context.BIND_AUTO_CREATE)) {
                App.log.severe("Couldn't connect to Google Play billing service")

                val data = BillingData()
                data.error = context.getString(R.string.subscription_management_play_connection_error)
                deliverResult(data)
            }
        }

        override fun onStopLoading() = context.unbindService(this)

        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            billingService = IInAppBillingService.Stub.asInterface(service)
            forceLoad()
        }

        override fun loadInBackground(): BillingData {
            val data = BillingData()
            billingService?.let { service ->
                try {
                    val subscription = SubscriptionManager(context, service)
                    data.info = subscription.info

                    // fetch product details
                    data.productDetails = subscription.getProductDetails(SKU_LIST)

                    // get buy intent
                    val bundle = service.getBuyIntent(3, BuildConfig.APPLICATION_ID, SKU_LIST[0], "inapp", null)
                    data.buyIntent = bundle.getParcelable("BUY_INTENT")
                } catch(e: Exception) {
                    App.log.log(Level.WARNING, "Couldn't retrieve product data from Google Play", e)
                    data.error = context.getString(R.string.subscription_management_play_connection_error)
                }
            }
            return data
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            billingService = null
        }

    }

}
