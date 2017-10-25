/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import android.annotation.TargetApi
import android.content.*
import android.os.Bundle
import at.bitfire.davdroid.log.Logger
import java.util.logging.Level

@TargetApi(21)
class RestrictionsProvider(
        val settings: Settings
): Provider {

    companion object {
        val EMM_RESTRICTIONS = "emm_restrictions"
    }

    private var config: Bundle? = null

    private val emmReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadRestrictions()
            settings.onReload()
        }
    }

    init {
        settings.registerReceiver(emmReceiver, IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED))
        loadRestrictions()
    }

    override fun close() {
        settings.unregisterReceiver(emmReceiver)
    }

    fun loadRestrictions() {
        val manager = settings.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        val config = manager.applicationRestrictions

        if (!config.isEmpty)
            config.putBoolean(EMM_RESTRICTIONS, true)

        Logger.log.log(Level.INFO, "Active app restrictions", config)
        this.config = config
    }

    override fun forceReload() {
        loadRestrictions()
        settings.onReload()
    }

    fun hasRestrictions() = config?.let { !it.isEmpty } ?: false


    override fun has(key: String): Pair<Boolean, Boolean> {
        val has = config?.containsKey(key) ?: false
        return if (has)
            Pair(true, false)
        else
            Pair(false, true)
    }

    private fun<T> getValue(key: String, reader: (Bundle) -> T?): Pair<T?, Boolean> {
        config?.let {
            if (it.containsKey(key))
                return Pair(reader(it), false)
        }
        return Pair(null, true)
    }

    override fun getBoolean(key: String) =
            getValue(key, { config -> config.getBoolean(key) })

    override fun getInt(key: String) =
            getValue(key, { config -> config.getInt(key) })

    override fun getLong(key: String) =
            getValue(key, { config -> config.getLong(key) })

    override fun getString(key: String) =
            getValue(key, { config -> config.getString(key) })


    override fun isWritable(key: String) =
            Pair(false, !(config?.containsKey(key) ?: false))

    override fun putBoolean(key: String, value: Boolean?) = false
    override fun putInt(key: String, value: Int?) = false
    override fun putLong(key: String, value: Long?) = false
    override fun putString(key: String, value: String?) = false
    override fun remove(key: String) = false

}