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
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class NetworkConfigProvider(
        val settings: Settings
): Provider, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        val PREFS_FILE = "network_config"
        val PREF_CACHED_CONFIG = "cached_config"
        val PREF_CONFIG_URL = "config_url"
    }

    private val executor = Executors.newSingleThreadExecutor()

    private val prefs: SharedPreferences
    private var configURL: HttpUrl? = null

    private val httpCacheControl = CacheControl.Builder()
            .maxAge(1, TimeUnit.DAYS)
            .build()

    private lateinit var config: JSONObject

    init {
        prefs = settings.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

        // start with a cached configuration, if possible
        config = try {
            JSONObject(prefs.getString(PREF_CACHED_CONFIG, ""))
        } catch (ignored: JSONException) {
            JSONObject()
        }
        Logger.log.log(Level.INFO, "Using cached configuration", config)

        // reload configuration from network
        readConfigURL()
        reloadConfig()

        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)

        executor.shutdown()
        try {
            executor.awaitTermination(1, TimeUnit.HOURS)
        } catch(ignored: InterruptedException) {}
    }


    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String) {
        if (key == PREF_CONFIG_URL) {
            readConfigURL()
            reloadConfig()
        }
    }

    private fun readConfigURL() {
        configURL = prefs.getString(PREF_CONFIG_URL, null)?.let { HttpUrl.parse(it) }
        Logger.log.info("Using networking config URL: $configURL")
    }

    private fun reloadConfig(cacheControl: CacheControl = httpCacheControl) {
        configURL?.let { url ->
            executor.submit {
                HttpClient.Builder(settings)
                        .withDiskCache()
                        .build().use { client ->
                    try {
                        val request = Request.Builder()
                                .get()
                                .url(url)
                                .cacheControl(cacheControl)

                        client.okHttpClient.newCall(request.build()).execute().use { response ->
                            if (!response.isSuccessful)
                                throw IllegalArgumentException("Network config: HTTP status ${response.code()}")

                            response.body()?.let {
                                // parse configuration file
                                config = JSONObject(it.string())
                                Logger.log.log(Level.INFO, "Using network configuration", config)

                                // save into cache
                                prefs   .edit()
                                        .putString(PREF_CACHED_CONFIG, config.toString())
                                        .apply()

                                // notify SettingsManager about new config
                                settings.onReload()
                            }
                        }
                    } catch(e: Exception) {
                        Logger.log.log(Level.WARNING, "Couldn't load network config", e)
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