/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.SettingsLoader
import org.apache.commons.lang3.StringUtils
import java.net.URI

class LoginInitFragment: Fragment(), LoaderManager.LoaderCallbacks<LoginSettings> {

    private var baseURL: URI? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
            inflater.inflate(R.layout.login_init_fragment, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateLoader(code: Int, args: Bundle?) = LoginSettingsLoader(requireActivity())

    override fun onLoadFinished(loader: Loader<LoginSettings>, result: LoginSettings?) {
        result?.let {
            if (it.baseURL != null)
                requireFragmentManager().beginTransaction()
                        .replace(android.R.id.content, LoginCredentialsFragment.newInstance(it))
                        .commit()
        }
    }

    override fun onLoaderReset(loader: Loader<LoginSettings>) {
    }


    class LoginSettingsLoader(
            context: Context
    ): SettingsLoader<LoginSettings>(context) {

        override fun loadInBackground() =
                settings?.let {
                    LoginSettings(
                        StringUtils.stripToNull(it.getString(App.ORGANIZATION, null)),
                        StringUtils.stripToNull(it.getString(App.ORGANIZATION_LOGO_URL, null)),
                        StringUtils.stripToNull(it.getString(LoginSettings.BASE_URL, null)),
                        StringUtils.stripToNull(it.getString(LoginSettings.USER_NAME, null)),
                        StringUtils.stripToNull(it.getString(LoginSettings.CERTIFICATE_ALIAS, null))
                    )
                }

    }


    class Factory: ILoginCredentialsFragment {
        override fun getFragment() = LoginInitFragment()
    }

}