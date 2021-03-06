/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsTest {

    lateinit var settings: Settings

    @Before
    fun initialize() {
        settings = Settings.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun testHas() {
        assertFalse(settings.has("notExisting"))

        // provided by DefaultsProvider
        assertTrue(settings.has(Settings.OVERRIDE_PROXY))
    }

}