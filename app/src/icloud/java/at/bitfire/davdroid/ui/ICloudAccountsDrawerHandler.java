package at.bitfire.davdroid.ui;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.view.MenuItem;

import at.bitfire.davdroid.R;

public class ICloudAccountsDrawerHandler implements IAccountsDrawerHandler {

    @Override
    public boolean onNavigationItemSelected(@NonNull Activity activity, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_app_license:
                activity.startActivity(new Intent(activity, SubscriptionActivity.class));
                break;
            default:
                return false;
        }

        return true;
    }

}
