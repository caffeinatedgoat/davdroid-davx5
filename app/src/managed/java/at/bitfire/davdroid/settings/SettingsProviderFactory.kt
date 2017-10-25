/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import java.util.*

class SettingsProviderFactory: ISettingsProviderFactory {

    override fun getProviders(settings: Settings): List<Provider> {
        val providers = LinkedList<Provider>()

        // try EMM restrictions (Android for work)
        val restrictions = RestrictionsProvider(settings)
        if (restrictions.hasRestrictions()) {
            providers += restrictions
            return providers
        } else {
            // shut down EMM provider
            restrictions.close()

            // use network config provider
            providers += NetworkConfigProvider(settings)
        }

        // use local config file instead
        providers += ConfigFileProvider(settings)

        return providers
    }
}