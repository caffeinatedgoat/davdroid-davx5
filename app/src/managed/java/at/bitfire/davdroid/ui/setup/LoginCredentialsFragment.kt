/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.security.KeyChain
import android.support.v4.app.Fragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.AsyncTaskLoader
import android.support.v4.content.Loader
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.settings.ISettingsObserver
import at.bitfire.davdroid.settings.Settings
import kotlinx.android.synthetic.managed.login_credentials_fragment.view.*
import okhttp3.Request
import java.net.URI
import java.util.logging.Level

class LoginCredentialsFragment: Fragment(), LoaderManager.LoaderCallbacks<Bitmap> {

    companion object {

        private const val ARG_LOGIN_SETTINGS = "login_settings"
        private const val SELECTED_CERTIFICATE = "selected_certificate"

        fun newInstance(loginSettings: LoginSettings): LoginCredentialsFragment {
            val frag = LoginCredentialsFragment()
            val args = Bundle(1)
            args.putParcelable(ARG_LOGIN_SETTINGS, loginSettings)
            frag.arguments = args
            return frag
        }

    }

    private var selectedCertificate: String? = null

    val handler = Handler()
    val settingsObserver = object: ISettingsObserver.Stub() {
        override fun onSettingsChanged() {
            handler.post {
                activity?.finish()
            }
        }
    }

    private var settingsSvc: ServiceConnection? = null
    var settings: ISettings? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsSvc = object: ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                settings = ISettings.Stub.asInterface(binder)
                settings!!.registerObserver(settingsObserver)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                settings!!.unregisterObserver(settingsObserver)
                settings = null
            }
        }
        requireActivity().bindService(Intent(context, Settings::class.java), settingsSvc, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsSvc?.let {
            requireActivity().unbindService(it)
            settingsSvc = null
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
            inflater.inflate(R.layout.login_credentials_fragment, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = arguments!![ARG_LOGIN_SETTINGS] as LoginSettings

        savedInstanceState?.let {
            selectedCertificate = it.getString(SELECTED_CERTIFICATE)
        }

        view.text.text = Html.fromHtml(settings.loginIntroduction ?:
                getString(R.string.login_managed_config_info_html, settings.organization))
        view.text.movementMethod = LinkMovementMethod.getInstance()

        // load logo
        view.logo.visibility = View.GONE
        if (settings.logoURL != null)
            loaderManager.initLoader(0, null, this)

        // validate base URL
        val baseURL: URI
        try {
            baseURL = URI(settings.baseURL)
        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Invalid base URL", e)
            return
        }

        if (settings.certificateAlias != null) {
            // login by client certificate
            view.details_username_password.visibility = View.GONE
            view.details_client_certificate.visibility = View.VISIBLE

            selectedCertificate?.let { certificateSelected() }
            view.select_certificate.setOnClickListener {
                KeyChain.choosePrivateKeyAlias(activity, { alias ->
                    alias?.let {
                        Handler(requireActivity().mainLooper).post {
                            selectedCertificate = alias
                            certificateSelected()
                        }
                    }
                }, null, null, null, -1, settings.certificateAlias)
            }

            view.login.setOnClickListener {
                val info = LoginInfo(baseURL, certificateAlias = settings.certificateAlias)
                DetectConfigurationFragment.newInstance(info).show(fragmentManager, null)
            }

        } else {
            // login by username/password
            view.details_username_password.visibility = View.VISIBLE
            view.details_client_certificate.visibility = View.GONE

            settings.userName?.let {
                view.user_name.setText(it)
            }

            view.login.isEnabled = true
            view.login.setOnClickListener {
                validateUsernamePassword(baseURL)?.let { info ->
                    DetectConfigurationFragment.newInstance(info).show(fragmentManager, null)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SELECTED_CERTIFICATE, selectedCertificate)
    }


    private fun certificateSelected() {
        // use selected alias
        val v = requireNotNull(view)
        v.select_certificate_info.visibility = View.GONE
        v.certificate_info.text = getString(R.string.login_managed_config_certificate_selected, selectedCertificate)
        v.certificate_info.visibility = View.VISIBLE
        v.login.isEnabled = true
    }

    private fun validateUsernamePassword(baseURL: URI): LoginInfo? {
        var valid = true

        val v = requireNotNull(view)
        val userName = v.user_name.text.toString()
        if (userName.isEmpty()) {
            v.user_name.error = getString(R.string.login_user_name_required)
            valid = false
        }

        val password = v.url_password.text.toString()
        if (password.isEmpty()) {
            v.url_password.error = getString(R.string.login_password_required)
            valid = false
        }

        return if (valid)
            LoginInfo(baseURL, userName, password)
        else {
            Logger.log.warning("Invalid login data")
            null
        }
    }


    override fun onCreateLoader(code: Int, args: Bundle?): Loader<Bitmap> {
        val settings = arguments!![ARG_LOGIN_SETTINGS] as LoginSettings
        return BitmapLoader(requireActivity(), settings.logoURL!!)
    }

    override fun onLoadFinished(loader: Loader<Bitmap>, result: Bitmap?) {
        view?.logo?.let {
            it.setImageBitmap(result)
            it.visibility = if (result != null) View.VISIBLE else View.GONE
        }
    }

    override fun onLoaderReset(loader: Loader<Bitmap>) {
        view?.logo?.let {
            it.setImageBitmap(null)
            it.visibility = View.GONE
        }
    }


    class BitmapLoader(
            context: Context,
            private val logoURL: String
    ): AsyncTaskLoader<Bitmap>(context) {

        var logo: Bitmap? = null

        override fun onStartLoading() {
            if (logo != null)
                deliverResult(logo)
            else
                forceLoad()
        }

        override fun loadInBackground(): Bitmap? {
            try {
                HttpClient.Builder(context)
                        .withDiskCache()
                        .build().use { client ->
                    client.okHttpClient.newCall(Request.Builder()
                            .get()
                            .url(logoURL)
                            .build()).execute().use { response ->
                        if (response.isSuccessful)
                            response.body()?.use {
                                logo = BitmapFactory.decodeStream(it.byteStream())
                            }
                    }
                }
            } catch(e: Exception) {
                Logger.log.log(Level.WARNING, "Couldn't load organization logo", e)
            }
            return logo
        }

    }

}