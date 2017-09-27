/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.AccountSettings
import at.bitfire.vcard4android.GroupMethod

object CloudSettingsProvider: DefaultsProvider(false) {

    override val booleanDefaults = mapOf<String, Boolean>(
            Pair(AccountSettings.KEY_MANAGE_CALENDAR_COLORS, true),
            Pair(AccountSettings.KEY_EVENT_COLORS, false)
    )

    override val stringDefaults = mapOf<String, String>(
            Pair(AccountSettings.KEY_CONTACT_GROUP_METHOD, GroupMethod.GROUP_VCARDS.name)
    )


    class Factory: ISettingsProviderFactory {

        override fun getProviders(settings: Settings) = listOf(CloudSettingsProvider)

    }

}