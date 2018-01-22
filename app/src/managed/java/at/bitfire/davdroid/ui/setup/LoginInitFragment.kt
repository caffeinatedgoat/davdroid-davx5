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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import at.bitfire.davdroid.App
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.SettingsLoader
import okhttp3.Request
import org.apache.commons.lang3.StringUtils
import java.net.URI
import java.util.logging.Level

class LoginInitFragment: Fragment(), LoaderManager.LoaderCallbacks<LoginSettings> {

    private var baseURL: URI? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
            inflater.inflate(R.layout.login_init_fragment, container, false)!!

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateLoader(code: Int, args: Bundle?) = LoginSettingsLoader(activity)

    override fun onLoadFinished(loader: Loader<LoginSettings>, result: LoginSettings?) {
        result?.let {
            if (it.baseURL != null)
                fragmentManager.beginTransaction()
                        .replace(android.R.id.content, LoginCredentialsFragment.newInstance(it))
                        .commitAllowingStateLoss()
        }
    }

    override fun onLoaderReset(loader: Loader<LoginSettings>?) {
    }


    class LoginSettingsLoader(
            context: Context
    ): SettingsLoader<LoginSettings>(context) {

        override fun loadInBackground(): LoginSettings? {
            settings?.let {
                var logo: Bitmap? = null
                it.getString(App.ORGANIZATION_LOGO_URL, null)?.let { url ->
                    try {
                        HttpClient.Builder(context)
                                .withDiskCache()
                                .build().use { client ->
                            client.okHttpClient.newCall(Request.Builder()
                                    .get()
                                    .url(url)
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
                }

                return LoginSettings(
                        StringUtils.stripToNull(it.getString(App.ORGANIZATION, null)),
                        logo,
                        StringUtils.stripToNull(it.getString(LoginSettings.BASE_URL, null)),
                        StringUtils.stripToNull(it.getString(LoginSettings.USER_NAME, null)),
                        StringUtils.stripToNull(it.getString(LoginSettings.CERTIFICATE_ALIAS, null))
                )
            }
            return null
        }
    }


    class Factory: ILoginCredentialsFragment {
        override fun getFragment() = LoginInitFragment()
    }

}