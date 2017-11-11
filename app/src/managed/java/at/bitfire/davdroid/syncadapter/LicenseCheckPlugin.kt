package at.bitfire.davdroid.syncadapter

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.net.Uri
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import at.bitfire.davdroid.App
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.LicenseChecker
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.ServiceDB.Collections
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.ui.AboutActivity
import okhttp3.HttpUrl

class LicenseCheckPlugin: ISyncPlugin {

    override fun beforeSync(context: Context, settings: ISettings, syncResult: SyncResult): Boolean {
        val nm = NotificationManagerCompat.from(context)

        val checker = LicenseChecker(context)
        if (checker.verifyLicense(settings)) {
            // license valid
            checker.domains?.let { domains ->
                ServiceDB.OpenHelper(context).use {
                    val db = it.readableDatabase

                    // check whether there is a collection that is enabled for sync and has an unlicensed host name
                    db.query(Collections._TABLE, arrayOf(Collections.URL), Collections.SYNC, null, null, null, null).let { c ->
                        while (c.moveToNext())
                            HttpUrl.parse(c.getString(0))?.host()?.let { host ->
                                val inDomain = domains.any { host.endsWith(it) }
                                if (!inDomain) {
                                    Logger.log.warning("Host name $host not covered by license, aborting sync")
                                    notifyLicenseProblem(context, settings, nm, R.string.license_unlicensed_host_name, host)
                                    return false
                                }
                            }
                    }
                }
            }

        } else {
            Logger.log.warning("No valid license, aborting sync")
            notifyLicenseProblem(context, settings, nm, R.string.license_invalid)
            return false
        }

        nm.cancel(Constants.NOTIFICATION_SUBSCRIPTION)
        return true
    }

    override fun afterSync(context: Context, settings: ISettings, syncResult: SyncResult) {
    }


    private fun notifyLicenseProblem(context: Context, settings: ISettings, nm: NotificationManagerCompat, msgId: Int, subText: String? = null) {
        val notify = NotificationCompat.Builder(context)
                .setLargeIcon(App.getLauncherBitmap(context))
                .setSmallIcon(R.drawable.ic_sync_error_notification)
                .setContentTitle(context.getString(msgId))
                .setContentText(context.getString(R.string.license_contact_it_support))
                .setSubText(subText)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, AboutActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT))
                .setLocalOnly(true)

        settings.getString(App.SUPPORT_PHONE, null)?.let { phone ->
            notify.addAction(R.drawable.ic_phone_dark, context.getString(R.string.license_contact_it_support_phone),
                    PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_DIAL, Uri.Builder()
                            .scheme("tel")
                            .opaquePart(phone)
                            .build()), PendingIntent.FLAG_UPDATE_CURRENT))
        }

        settings.getString(App.SUPPORT_EMAIL, null)?.let { email ->
            notify.addAction(R.drawable.ic_email_dark, context.getString(R.string.license_contact_it_support_email),
                    PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_SENDTO, Uri.Builder()
                            .scheme("mailto")
                            .opaquePart(email)
                            .build()), PendingIntent.FLAG_UPDATE_CURRENT))
        }

        nm.notify(Constants.NOTIFICATION_SUBSCRIPTION, notify.build())
    }

}
