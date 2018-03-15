package at.bitfire.davdroid;

import android.accounts.Account
import android.accounts.AccountManager
import android.support.test.InstrumentationRegistry.getInstrumentation
import android.support.test.espresso.Espresso.onData
import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.*
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.intent.Intents
import android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.filters.LargeTest
import android.support.test.rule.ActivityTestRule
import android.support.test.rule.GrantPermissionRule
import android.support.test.runner.AndroidJUnit4
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.ui.AccountsActivity
import at.bitfire.davdroid.ui.StartupDialogFragment
import at.bitfire.davdroid.ui.setup.LoginActivity
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class AddAccountTest {

    @Rule
    @JvmField
    val activityRule = object: ActivityTestRule<AccountsActivity>(AccountsActivity::class.java) {
        override fun beforeActivityLaunched() {
            super.beforeActivityLaunched()
            Settings.getInstance(getInstrumentation().targetContext)?.use { settings ->
                settings.putBoolean(StartupDialogFragment.HINT_BATTERY_OPTIMIZATIONS, false)
                settings.putBoolean(StartupDialogFragment.HINT_GOOGLE_PLAY_ACCOUNTS_REMOVED, false)
                settings.putBoolean(StartupDialogFragment.HINT_OPENTASKS_NOT_INSTALLED, false)
            }
        }
    }

    @Rule
    @JvmField
    public val permissionRule = GrantPermissionRule.grant(
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS
    )!!

    @Before
    fun deleteAccounts() {
        val target = getInstrumentation().targetContext
        val am = AccountManager.get(target)
        am.getAccountsByType(target.getString(R.string.account_type)).forEach {
            am.removeAccount(it, null, null)
        }
    }

    @Before
    fun initIntents() {
        Intents.init()
    }

    @After
    fun releaseIntents() {
        Intents.release()
    }


    @Test
    fun testLaunch() {
        onView(withText("Welcome to DAVdroid!\n\nYou can add a CalDAV/CardDAV account now.")).check(matches(isDisplayed()))
    }

    private fun testService(baseURL: String?, username: String, password: String, addressBooks: Array<String>, calendars: Array<String>,
                            accountName: String? = username) {
        onView(withId(R.id.fab)).perform(click())
        Intents.intended(hasComponent(LoginActivity::class.java.name))

        if (baseURL == null) {
            onView(withId(R.id.email_address)).perform(click(), typeText(username))
            onView(withId(R.id.email_password)).perform(scrollTo(), click(), typeText(password))
        } else {
            onView(withId(R.id.login_type_urlpwd)).perform(click())
            onView(withId(R.id.urlpwd_base_url)).perform(typeText(baseURL))
            onView(withId(R.id.urlpwd_user_name)).perform(scrollTo(), click(), typeText(username))
            onView(withId(R.id.urlpwd_password)).perform(scrollTo(), click(), typeText(password))
        }
        onView(withText("Login")).perform(click())
        onView(allOf(withId(R.id.create_account), withText("Create account"))).perform(click())

        onData(`is`(Account(accountName, getInstrumentation().targetContext.getString(R.string.account_type))))
                .perform(click())
        for (addressBook in addressBooks)
            onView(withText(containsString(addressBook)))
                    .perform(scrollTo(), click())
        for (calendar in calendars)
            onView(withText(containsString(calendar)))
                    .perform(scrollTo(), click())
        onView(withResourceName("sync_now")).perform(click())
    }

    @LargeTest
    @Test
    fun testFastmail() = testService(
            null, "dah2zosu@fastmail.com", "vkd83x53txkew2fl",
            arrayOf("personal"),
            arrayOf("Espresso")
    )

    @LargeTest
    @Test
    fun testICloud() = testService(
            "https://icloud.com",
            "gabriela.stockmann@bezirksblaetter.at", "soks-zisx-ylpr-lkrs",
            arrayOf(),
            arrayOf("Privat", "Erinnerungen"),
            accountName = "gstockmann@me.com"
    )

    @LargeTest
    @Test
    fun testMailboxOrg() = testService(
            null,
            "davdroid@mailbox.org", "queePh1e",
            arrayOf("Gesammelte Adressen", "Globales Adressbuch", "Kontakte"),
            arrayOf("Kalender", "Aufgaben")
    )

    @LargeTest
    @Test
    fun testMyKolab() = testService(
            null,
            "davdroid@mykolab.com", "futfut12",
            arrayOf("Contacts"),
            arrayOf("Calendar", "Tasks")
    )

    @LargeTest
    @Test
    fun testNextcloud() = testService(
            "https://nc.dev001.net",
            "test", "shineeW7",
            arrayOf("Contacts"),
            arrayOf("Personal")
    )

    @LargeTest
    @Test
    fun testSoldupe() = testService(
            "https://cloud.soldupe.com/remote.php/dav/",
            "bitfire_test", "frPH27",
            arrayOf("Kontakte"),
            arrayOf("Persönlich")
    )

    @LargeTest
    @Test
    fun testPosteo() = testService(
            "https://posteo.de:8443",
            "bitfire@posteo.de", "FutFut88",
            arrayOf("default addressbook"),
            arrayOf("Wurstkalender", "wawa")
    )

}