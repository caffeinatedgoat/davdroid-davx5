package at.bitfire.davdroid.ui.setup

import android.accounts.AccountManager
import android.app.Fragment
import android.content.Intent
import android.os.Bundle
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
            activity.finish()
            startActivity(Intent(activity, AccountsActivity::class.java), null)
        }

        val v = inflater.inflate(R.layout.login_credentials_fragment, container, false)

        v.login.setOnClickListener({ _ ->
            validateLoginData()?.let { credentials ->
                DetectConfigurationFragment.newInstance(credentials).show(fragmentManager, null)
            }
        })

        return v
    }

    fun validateLoginData(): LoginCredentials? {
        var ok = true

        val userName = view.user_name.text.toString()
        if (userName.isBlank()) {
            view.user_name.setError(getString(R.string.login_user_name_required))
            ok = false;
        }

        val password = view.url_password.getText().toString()
        if (password.isBlank()) {
            view.url_password.setError(getString(R.string.login_password_required))
            ok = false;
        }

        return if (ok)
            LoginCredentials(URI.create("https://cloud.soldupe.com/remote.php/dav/"), userName, password)
        else
            null
    }


    class Factory: ILoginCredentialsFragment {
        override fun getFragment() = SoldupeLoginCredentialsFragment()
    }

}
