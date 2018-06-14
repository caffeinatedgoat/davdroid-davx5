/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.app.Dialog
import android.app.ProgressDialog
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.DialogFragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.AsyncTaskLoader
import android.support.v4.content.Loader
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.settings.NetworkConfigProvider
import at.bitfire.davdroid.settings.RestrictionsProvider
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.ui.ManagedConfigActivity.ManagedConfigInfo
import kotlinx.android.synthetic.managed.activity_managed_config.*
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.util.logging.Level

class ManagedConfigActivity: AppCompatActivity(), LoaderManager.LoaderCallbacks<ManagedConfigInfo> {

    private var settings: ISettings? = null
    private val settingsSvc = object: ServiceConnection {
        override fun onServiceConnected(component: ComponentName?, binder: IBinder) {
            settings = ISettings.Stub.asInterface(binder)
            invalidateOptionsMenu()
        }
        override fun onServiceDisconnected(component: ComponentName?) {
            settings = null
        }
    }

    var canResetConfigURL = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_managed_config)

        if (savedInstanceState == null)
            intent?.dataString?.let {
                SetConfigUrlFragment.newInstance(it).show(supportFragmentManager, null)
            }

        supportLoaderManager.initLoader(0, null, this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_managed_config, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.reload).isEnabled = settings != null
        menu.findItem(R.id.reset).isVisible = canResetConfigURL
        return true
    }

    override fun onResume() {
        super.onResume()
        bindService(Intent(this, Settings::class.java), settingsSvc, Context.BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()
        settings?.let { unbindService(settingsSvc) }
    }


    fun reloadConfig(item: MenuItem) {
        Toast.makeText(this, R.string.managed_config_reloading_configuration, Toast.LENGTH_SHORT).show()
        settings?.forceReload()
    }

    fun resetConfigURL(item: MenuItem) {
        getSharedPreferences(NetworkConfigProvider.PREFS_FILE, Context.MODE_PRIVATE).edit()
                .remove(NetworkConfigProvider.PREF_CONFIG_URL)
                .remove(NetworkConfigProvider.PREF_CACHED_CONFIG)
                .apply()
        supportLoaderManager.restartLoader(0, null, this)
    }


    override fun onCreateLoader(code: Int, args: Bundle?): Loader<ManagedConfigInfo> =
            ManagedConfigLoader(this)

    override fun onLoadFinished(loader: Loader<ManagedConfigInfo>, result: ManagedConfigInfo) {
        config_type.text = getString(if (result.emm) R.string.managed_config_configuration_type_emm else R.string.managed_config_configuration_type_network)

        if (result.networkConfigURL != null) {
            network_config.visibility = View.VISIBLE
            network_config_url.text = result.networkConfigURL
        } else
            network_config.visibility = View.GONE

        canResetConfigURL = result.networkConfigURL != null
        invalidateOptionsMenu()
    }

    override fun onLoaderReset(loader: Loader<ManagedConfigInfo>) {
    }

    data class ManagedConfigInfo(
            var emm: Boolean,
            var networkConfigURL: String?
    )

    class ManagedConfigLoader(
            context: Context
    ): AsyncTaskLoader<ManagedConfigInfo>(context), SharedPreferences.OnSharedPreferenceChangeListener {

        private val networkPrefs = context.getSharedPreferences(NetworkConfigProvider.PREFS_FILE, Context.MODE_PRIVATE)

        override fun onStartLoading() {
            networkPrefs.registerOnSharedPreferenceChangeListener(this)
            forceLoad()
        }

        override fun onStopLoading() {
            networkPrefs.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, s: String?) {
            forceLoad()
        }

        override fun loadInBackground(): ManagedConfigInfo {
            val settings = Settings.getInstance(context)
            return ManagedConfigInfo(
                settings?.has(RestrictionsProvider.EMM_RESTRICTIONS) ?: false,
                networkPrefs.getString(NetworkConfigProvider.PREF_CONFIG_URL, null)
            )
        }

    }


    class SetConfigUrlFragment : DialogFragment(), LoaderManager.LoaderCallbacks<String> {

        companion object {

            private const val ARG_URL = "url"

            fun newInstance(url: String): DialogFragment {
                val args = Bundle(1)
                args.putString(ARG_URL, url)
                val frag = SetConfigUrlFragment()
                frag.arguments = args
                return frag
            }

        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            loaderManager.initLoader(0, arguments, this)
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = ProgressDialog(activity)
            dialog.setCancelable(false)
            dialog.setMessage(requireActivity().getString(R.string.managed_config_verifying_network_config))
            isCancelable = false
            return dialog
        }

        override fun onCreateLoader(code: Int, args: Bundle?): Loader<String> =
                VerifyNetworkConfigLoader(requireActivity(), args!!.getString(ARG_URL))

        override fun onLoaderReset(loader: Loader<String>) {
        }

        override fun onLoadFinished(loader: Loader<String>, error: String?) {
            Toast.makeText(activity, error ?: getString(R.string.managed_config_network_config_url_saved), Toast.LENGTH_LONG).show()
            dismiss()
        }

    }

    class VerifyNetworkConfigLoader(
            context: Context,
            val url: String
    ): AsyncTaskLoader<String>(context) {

        override fun onStartLoading() {
            forceLoad()
        }

        override fun loadInBackground(): String? {
            val httpUrl = HttpUrl.parse(url) ?: return "Invalid URL"

            HttpClient.Builder().build().use { client ->
                try {
                    client.okHttpClient.newCall(Request.Builder()
                            .get()
                            .url(httpUrl)
                            .build()).execute().use { response ->

                        if (!response.isSuccessful)
                            return "HTTP ${response.code()}"

                        response.body()?.let {
                            // try to parse JSON file
                            JSONObject(it.string())

                            // no error, save URL
                            context.getSharedPreferences(NetworkConfigProvider.PREFS_FILE, Context.MODE_PRIVATE).edit()
                                    .putString(NetworkConfigProvider.PREF_CONFIG_URL, url)
                                    .apply()

                            return null
                        }

                        return "Empty HTTP response"
                    }
                } catch(e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't verify network configuration URL", e)
                    return e.localizedMessage
                }
            }
        }

    }

}
