package at.bitfire.davdroid.ui.setup;

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.net.URI;

import at.bitfire.davdroid.R;
import at.bitfire.davdroid.ui.widget.EditPassword;

public class ICloudLoginCredentialsFragment extends Fragment {

    EditText editUserName;
    EditPassword editUrlPassword;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.login_credentials_fragment, container, false);

        Typeface tf = Typeface.createFromAsset(getContext().getAssets(), "fonts/BebasNeue_Light.ttf");
        ((TextView)v.findViewById(R.id.login_title)).setTypeface(tf);

        editUserName = (EditText)v.findViewById(R.id.user_name);
        editUrlPassword = (EditPassword)v.findViewById(R.id.url_password);

        final Button login = (Button)v.findViewById(R.id.login);
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoginCredentials credentials = validateLoginData();
                if (credentials != null)
                    DetectConfigurationFragment.newInstance(credentials).show(getFragmentManager(), null);
            }
        });

        return v;
    }

    protected LoginCredentials validateLoginData() {
        boolean valid = true;

        String userName = editUserName.getText().toString();
        if (userName.isEmpty()) {
            editUserName.setError(getString(R.string.login_user_name_required));
            valid = false;
        }

        String password = editUrlPassword.getText().toString();
        if (password.isEmpty()) {
            editUrlPassword.setError(getString(R.string.login_password_required));
            valid = false;
        }

        return valid ? new LoginCredentials(URI.create("https://icloud.com/"), userName, password) : null;
    }


    public static class Factory implements ILoginCredentialsFragment {

        @Override
        public Fragment getFragment() {
            return new ICloudLoginCredentialsFragment();
        }

    }

}
