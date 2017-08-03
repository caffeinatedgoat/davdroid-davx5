package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import at.bitfire.davdroid.R

class SoldupeAccountsDrawerHandler: IAccountsDrawerHandler {

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_about ->
                activity.startActivity(Intent(activity, AboutActivity::class.java))
            R.id.nav_app_settings ->
                activity.startActivity(Intent(activity, AppSettingsActivity::class.java))
            R.id.nav_twitter ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/soldupecloud")))
            R.id.nav_facebook ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/soldupecloudservices")))
            R.id.nav_website ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.homepage_url))))
            R.id.nav_faq ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.navigation_drawer_faq_url))))
            else ->
                return false
        }

        return true
    }

}