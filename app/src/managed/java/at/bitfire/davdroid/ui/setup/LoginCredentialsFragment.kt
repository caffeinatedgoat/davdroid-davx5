/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.app.Fragment
import android.os.Bundle
import android.os.Handler
import android.security.KeyChain
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import kotlinx.android.synthetic.managed.login_credentials_fragment.view.*
import java.net.URI
import java.util.logging.Level

class LoginCredentialsFragment: Fragment() {

    companion object {

        private val ARG_LOGIN_SETTINGS = "login_settings"
        private val SELECTED_CERTIFICATE = "selected_certificate"

        fun newInstance(loginSettings: LoginSettings): LoginCredentialsFragment {
            val frag = LoginCredentialsFragment()
            val args = Bundle(1)
            args.putParcelable(ARG_LOGIN_SETTINGS, loginSettings)
            frag.arguments = args
            return frag
        }

    }

    private var selectedCertificate: String? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
            inflater.inflate(R.layout.login_credentials_fragment, container, false)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = arguments[ARG_LOGIN_SETTINGS] as LoginSettings

        savedInstanceState?.let {
            selectedCertificate = it.getString(SELECTED_CERTIFICATE)
        }

        // set logo
        if (settings.logo != null) {
            view.logo.setImageBitmap(settings.logo)
            view.logo.visibility = View.VISIBLE
        } else
            view.logo.visibility = View.GONE
        view.text.text = Html.fromHtml(getString(R.string.login_managed_config_info_html, settings.organization ?: "DAVdroid"))

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
                        Handler(activity.mainLooper).post {
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
        view.select_certificate_info.visibility = View.GONE
        view.certificate_info.text = getString(R.string.login_managed_config_certificate_selected, selectedCertificate)
        view.certificate_info.visibility = View.VISIBLE
        view.login.isEnabled = true
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

}