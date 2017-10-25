/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.v4.app.Fragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.AsyncTaskLoader
import android.support.v4.content.Loader
import android.text.Html
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.*
import at.bitfire.davdroid.LicenseChecker
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.settings.ISettingsObserver
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.ui.LicenseFragment.LicenseInfo
import kotlinx.android.synthetic.managed.license_fragment.view.*
import java.text.DateFormat
import java.util.*
import java.util.logging.Level

class LicenseFragment: Fragment(), LoaderManager.LoaderCallbacks<LicenseInfo> {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.license_fragment, container, false)!!
        setHasOptionsMenu(true)
        return v
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_license, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.managed_configuration -> {
                startActivity(Intent(activity, ManagedConfigActivity::class.java))
                return true
            }
        }
        return false
    }


    override fun onCreateLoader(code: Int, args: Bundle?) =
            LicenseInfoLoader(activity)

    override fun onLoadFinished(loader: Loader<LicenseInfo>?, info: LicenseInfo?) {
        Logger.log.log(Level.INFO, "LICENSE", info)
        view?.let { v ->
            if (info != null && info.valid) {
                v.license_info.text = Html.fromHtml(getString(R.string.about_license_info_html, info.users, info.organization))

                v.license_expires.visibility = View.VISIBLE
                val dateStr = DateFormat.getDateInstance().format(Date(info.expires!! * 1000))
                v.license_expires.text = getString(R.string.about_license_valid_through, dateStr)

                v.license_short_terms.visibility = View.VISIBLE
                v.license_short_terms.autoLinkMask = Linkify.WEB_URLS
                v.license_short_terms.movementMethod = LinkMovementMethod.getInstance()
            } else {
                v.license_info.text = getString(R.string.about_license_invalid_license)
                v.license_expires.visibility = View.GONE
                v.license_short_terms.visibility = View.GONE
            }
        }
    }

    override fun onLoaderReset(loader: Loader<LicenseInfo>?) {
        onLoadFinished(loader, null)
    }


    data class LicenseInfo(
            val valid: Boolean,
            val users: Int? = null,
            var organization: String? = null,
            var expires: Long? = null
    )

    class LicenseInfoLoader(
            context: Context
    ): AsyncTaskLoader<LicenseInfo>(context) {

        val handler = Handler()
        val settingsObserver = object: ISettingsObserver.Stub() {
            override fun onSettingsChanged() {
                handler.post {
                    forceLoad()
                }
            }
        }

        var settings: ISettings? = null
        private val settingsSvc = object: ServiceConnection {

            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                settings = ISettings.Stub.asInterface(binder)
                settings!!.registerObserver(settingsObserver)
                forceLoad()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                settings!!.unregisterObserver(settingsObserver)
                settings = null
            }

        }

        override fun onStartLoading() {
            context.bindService(Intent(context, Settings::class.java), settingsSvc, Context.BIND_AUTO_CREATE)
        }

        override fun onStopLoading() {
            context.unbindService(settingsSvc)
        }

        override fun loadInBackground(): LicenseInfo {
            val checker = LicenseChecker(context)
            return if (settings?.let { checker.verifyLicense(it) } == true) {
                LicenseInfo(true,
                        checker.users,
                        checker.organization,
                        checker.expiresAt)
            } else
                LicenseInfo(false)
        }

    }


    class Factory: AboutActivity.ILicenseFragment {
        override fun getFragment() = LicenseFragment()
    }

}