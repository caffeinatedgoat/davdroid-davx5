/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.app.Fragment
import android.app.LoaderManager
import android.content.Context
import android.content.Loader
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import at.bitfire.davdroid.App
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.SettingsLoader
import at.bitfire.davdroid.ui.setup.ManagedLoginCredentialsFragment.LoginSettings
import kotlinx.android.synthetic.managed.login_credentials_fragment.view.*
import okhttp3.Request
import org.apache.commons.lang3.StringUtils
import java.net.URI
import java.util.logging.Level

class ManagedLoginCredentialsFragment: Fragment(), LoaderManager.LoaderCallbacks<LoginSettings> {

    companion object {
        val BASE_URL = "login_base_url"
        val USER_NAME = "login_user_name"
    }

    private var baseURL: URI? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
            inflater.inflate(R.layout.login_credentials_fragment, container, false)!!

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateLoader(code: Int, args: Bundle?) = LoginSettingsLoader(activity)

    override fun onLoadFinished(loader: Loader<LoginSettings>, result: LoginSettings?) {
        val view = view ?: return
        val settings = result ?: return

        if (settings.logo != null) {
            view.logo.setImageBitmap(settings.logo)
            view.logo.visibility = View.VISIBLE
        } else
            view.logo.visibility = View.GONE
        view.text.text = Html.fromHtml(getString(R.string.login_managed_config_info_html, settings.organization ?: "DAVdroid"))

        StringUtils.stripToNull(settings.userName)?.let {
            view.user_name.setText(it)
            view.user_name.clearFocus()
        }

        baseURL = null
        try {
            baseURL = URI(settings.baseURL)
        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Invalid base URL", e)
        }

        view.waiting.visibility = if (baseURL == null) View.VISIBLE else View.GONE
        view.details.visibility = if (baseURL != null) View.VISIBLE else View.GONE
        view.login.isEnabled = baseURL != null

        baseURL?.let {
            view.login.setOnClickListener {
                validateLoginData()?.let { credentials ->
                    DetectConfigurationFragment.newInstance(credentials).show(fragmentManager, null)
                }
            }
        }
    }

    override fun onLoaderReset(loader: Loader<LoginSettings>?) {
    }


    private fun validateLoginData(): LoginCredentials? {
        var valid = true

        val v = requireNotNull(view)
        val userName = v.user_name.text.toString()
        if (userName.isEmpty()) {
            v.user_name.error = getString(R.string.login_user_name_required)
            valid = false
        }

        val password = v.url_password.getText().toString()
        if (password.isEmpty()) {
            v.url_password.setError(getString(R.string.login_password_required))
            valid = false
        }

        return if (valid)
            baseURL?.let { LoginCredentials(it, userName, password) }
        else {
            Logger.log.warning("Invalid login data")
            null
        }
    }


    data class LoginSettings(
        val organization: String?,
        val logo: Bitmap?,
        val baseURL: String?,
        val userName: String?
    )

    class LoginSettingsLoader(
            context: Context
    ): SettingsLoader<LoginSettings>(context) {

        override fun loadInBackground(): LoginSettings? {
            settings?.let {
                var logo: Bitmap? = null
                it.getString(App.ORGANIZATION_LOGO_URL, null)?.let { url ->
                    try {
                        val client = HttpClient.Builder(context)
                                .withDiskCache()
                                .build().okHttpClient
                        client.newCall(Request.Builder()
                                .get()
                                .url(url)
                                .build()).execute().use { response ->
                            if (response.isSuccessful)
                                response.body()?.use {
                                    logo = BitmapFactory.decodeStream(it.byteStream())
                                }
                        }
                    } catch(e: Exception) {
                        Logger.log.log(Level.WARNING, "Couldn't load organization logo", e)
                    }
                }

                return LoginSettings(
                        it.getString(App.ORGANIZATION, null),
                        logo,
                        it.getString(BASE_URL, null),
                        it.getString(USER_NAME, null)
                )
            }
            return null
        }
    }


    class Factory: ILoginCredentialsFragment {
        override fun getFragment() = ManagedLoginCredentialsFragment()
    }

}