/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.ISettings
import java.util.logging.Level

class ManagedAccountsDrawerHandler: IAccountsDrawerHandler {

    private var settings: ISettings? = null

    override fun onSettingsChanged(settings: ISettings?, menu: Menu) {
        this.settings = settings

        settings?.getString(App.ORGANIZATION, null)?.let {
            menu.findItem(R.id.nav_managed_configuration).title = it
        }

        menu.findItem(R.id.nav_web_support).isVisible = settings?.getString(App.SUPPORT_HOMEPAGE, null).isNullOrBlank() == false
        menu.findItem(R.id.nav_phone_support).isVisible = settings?.getString(App.SUPPORT_PHONE, null).isNullOrBlank() == false
        menu.findItem(R.id.nav_email_support).isVisible = settings?.getString(App.SUPPORT_EMAIL, null).isNullOrBlank() == false
    }

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_about ->
                activity.startActivity(Intent(activity, AboutActivity::class.java))

            R.id.nav_app_settings ->
                activity.startActivity(Intent(activity, AppSettingsActivity::class.java))

            R.id.nav_web_support ->
                settings?.getString(App.SUPPORT_HOMEPAGE, null)?.let {
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                    } catch(e: Exception) {
                        Logger.log.log(Level.WARNING, "Couldn't open support homepage", e)
                    }
                }

            R.id.nav_phone_support ->
                settings?.getString(App.SUPPORT_PHONE, null)?.let { phone ->
                    try {
                        activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.Builder()
                                .scheme("tel")
                                .opaquePart(phone)
                                .build()))
                    } catch(e: Exception) {
                        Logger.log.log(Level.WARNING, "Couldn't call phone support", e)
                    }
                }

            R.id.nav_email_support ->
                settings?.getString(App.SUPPORT_EMAIL, null)?.let { email ->
                    try {
                        activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.Builder()
                                .scheme("mailto")
                                .opaquePart(email)
                                .build()))
                    } catch(e: Exception) {
                        Logger.log.log(Level.WARNING, "Couldn't mail phone support", e)
                    }
                }

        }

        return true
    }

}
