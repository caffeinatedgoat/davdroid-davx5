package at.bitfire.davdroid.ui.setup

import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AccountsActivity
import kotlinx.android.synthetic.soldupe.login_credentials_fragment.view.*
import java.net.URI

class SoldupeLoginCredentialsFragment: Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val accountManager = AccountManager.get(activity)
        val accounts = accountManager.getAccountsByType(getString(R.string.account_type))
        if (accounts.isNotEmpty()) {
            // Soldupe account already exists
            requireActivity().finish()
            startActivity(Intent(activity, AccountsActivity::class.java), null)
        }

        val v = inflater.inflate(R.layout.login_credentials_fragment, container, false)

        v.login.setOnClickListener({ _ ->
            validateLoginData()?.let { info ->
                DetectConfigurationFragment.newInstance(info).show(fragmentManager, null)
            }
        })

        return v
    }

    private fun validateLoginData(): LoginInfo? {
        val view = requireNotNull(view)
        var ok = true

        val userName = view.user_name.text.toString()
        if (userName.isBlank()) {
            view.user_name.error = getString(R.string.login_user_name_required)
            ok = false;
        }

        val password = view.url_password.text.toString()
        if (password.isBlank()) {
            view.url_password.error = getString(R.string.login_password_required)
            ok = false;
        }

        return if (ok)
            LoginInfo(URI.create("https://cloud.soldupe.com/remote.php/dav/"), userName, password)
        else
            null
    }


    class Factory: ILoginCredentialsFragment {
        override fun getFragment() = SoldupeLoginCredentialsFragment()
    }

}