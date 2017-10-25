/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import android.content.Context
import android.content.SharedPreferences
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.log.Logger
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class NetworkConfigProvider(
        val settings: Settings
): Provider, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        val PREFS_FILE = "network_config"
        val PREF_CONFIG_URL = "config_url"
    }

    private val executor = Executors.newSingleThreadExecutor()

    private val prefs: SharedPreferences
    private var configURL: HttpUrl? = null

    private var config: JSONObject? = null

    init {
        prefs = settings.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        readConfigURL()
        reloadConfig()

        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun close() {
        executor.shutdown()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }


    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, s: String?) {
        readConfigURL()
        reloadConfig()
    }

    private fun readConfigURL() {
        configURL = prefs.getString(PREF_CONFIG_URL, null)?.let { HttpUrl.parse(it) }
        Logger.log.info("Using networking config URL: $configURL")
    }

    private fun reloadConfig(cacheMode: CacheControl? = null) {
        config = null

        configURL?.let { url ->
            executor.submit {
                HttpClient.Builder(settings)
                        .withDiskCache()
                        .build().use { client ->
                    try {
                        val request = Request.Builder()
                                .get()
                                .url(url)

                        if (cacheMode == null) {
                            request.cacheControl(CacheControl.Builder()
                                    .maxAge(1, TimeUnit.SECONDS)
                                    .build())
                        } else
                            request.cacheControl(cacheMode)

                        client.okHttpClient.newCall(request.build()).execute().use { response ->
                            if (!response.isSuccessful)
                                throw IllegalArgumentException("Network config: HTTP status ${response.code()}")

                            response.body()?.let {
                                // parse configuration file
                                config = JSONObject(it.string())

                                Logger.log.log(Level.INFO, "Using network configuration", config)

                                // notify SettingsManager about new config
                                settings.onReload()
                            }
                        }
                    } catch(e: Exception) {
                        if (cacheMode == null) {
                            Logger.log.log(Level.WARNING, "Couldn't load network config, trying cached version", e)
                            reloadConfig(CacheControl.FORCE_CACHE)
                        } else
                            Logger.log.log(Level.WARNING, "Couldn't load network config")
                    }
                }
            }
        }
    }

    override fun forceReload() {
        reloadConfig(CacheControl.FORCE_NETWORK)
    }


    override fun has(key: String): Pair<Boolean, Boolean> {
        val has = config?.has(key) ?: false
        return if (has)
            Pair(true, false)
        else
            Pair(false, true)
    }

    private fun<T> getValue(key: String, reader: (JSONObject) -> T?): Pair<T?, Boolean> {
        config?.let {
            if (it.has(key))
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
            Pair(false, !(config?.has(key) ?: false))

    override fun putBoolean(key: String, value: Boolean?) = false
    override fun putInt(key: String, value: Int?) = false
    override fun putLong(key: String, value: Long?) = false
    override fun putString(key: String, value: String?) = false
    override fun remove(key: String) = false

}