package at.bitfire.davdroid.ui.setup;

import android.app.Fragment
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import at.bitfire.davdroid.R
import kotlinx.android.synthetic.icloud.login_credentials_fragment.view.*
import java.net.URI

class ICloudLoginCredentialsFragment: Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.login_credentials_fragment, container, false)

        val tf = Typeface.createFromAsset(activity.assets, "fonts/BebasNeue_Light.ttf")
        v.login_title.typeface = tf

        v.login_password_hint.movementMethod = LinkMovementMethod.getInstance()
        v.login_password_hint.text = Html.fromHtml(getString(R.string.login_app_specific_password_hint))

        v.login.setOnClickListener {
            validateLoginData()?.let { credentials ->
                DetectConfigurationFragment.newInstance(credentials).show(fragmentManager, null)
            }
        }

        return v
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
            LoginCredentials(URI.create("https://icloud.com/"), userName, password)
        else
            null
    }


    class Factory: ILoginCredentialsFragment {
        override fun getFragment() = ICloudLoginCredentialsFragment()
    }

}
