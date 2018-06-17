/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import at.bitfire.cert4android.CustomCertService
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.log.Logger
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import org.xbill.DNS.Lookup
import org.xbill.DNS.Type
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class NetworkConfigProvider(
        val settings: Settings
): Provider, SharedPreferences.OnSharedPreferenceChangeListener, NsdManager.DiscoveryListener {

    companion object {
        val PREFS_FILE = "network_config"
        val PREF_CACHED_CONFIG = "cached_config"
        val PREF_CONFIG_URL = "config_url"

        val SETTING_TRUSTED_CERTS = "trusted_certs"

        val SERVICE_NAME = "davdroid-configs"
        val UNICAST_NAME = "$SERVICE_NAME.local"
        val ZEROCONF_NAME = "_$SERVICE_NAME._tcp"
    }

    private val executor = Executors.newSingleThreadExecutor()

    private val prefs: SharedPreferences
    private var configURL: HttpUrl? = null
    private var nsdManager: NsdManager? = null

    private val httpCacheControl = CacheControl.Builder()
            .maxAge(1, TimeUnit.DAYS)
            .build()

    private var config: JSONObject

    init {
        prefs = settings.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

        // start with a cached configuration, if possible
        config = try {
            JSONObject(prefs.getString(PREF_CACHED_CONFIG, ""))
        } catch (ignored: JSONException) {
            JSONObject()
        }
        Logger.log.log(Level.INFO, "Using cached configuration", config)

        // reload configuration from network / start service discovery
        reinitConfig()

        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)

        try {
            nsdManager?.stopServiceDiscovery(null)
        } catch(ignored: Exception) {}

        executor.shutdown()
    }


    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String) {
        if (key == PREF_CONFIG_URL)
            reinitConfig()
    }

    private fun reinitConfig() {
        configURL = prefs.getString(PREF_CONFIG_URL, null)?.let { HttpUrl.parse(it) }

        try {
            if (configURL != null) {
                nsdManager?.stopServiceDiscovery(this)
                nsdManager = null

                Logger.log.info("Using fixed networking config URL: $configURL")
                reloadConfig()
            } else
                executor.submit {
                    // no custom configuration URL, try auto-discovery:
                    // 1. Unicast DNS (SRV/TXT)
                    val srvLookup = Lookup(UNICAST_NAME, Type.SRV)
                    DavUtils.prepareLookup(settings, srvLookup)
                    DavUtils.selectSRVRecord(srvLookup.run())?.let { srv ->
                        val txtLookup = Lookup(UNICAST_NAME, Type.TXT)
                        DavUtils.prepareLookup(settings, txtLookup)
                        val path = DavUtils.pathsFromTXTRecords(txtLookup.run()).firstOrNull() ?: "/"
                        val uri = URI("https", null, srv.target.toString(true), srv.port, path, null, null)
                        Logger.log.info("Found network configuration URL by $UNICAST_NAME SRV/TXT: $uri")
                        configURL = HttpUrl.get(uri)
                        reloadConfig()
                    }

                    // 2. Zeroconf (DNS-SD)
                    if (nsdManager == null)
                        (settings.getSystemService(Context.NSD_SERVICE) as NsdManager).let { nsd ->
                            nsdManager = nsd
                            nsd.discoverServices(ZEROCONF_NAME, NsdManager.PROTOCOL_DNS_SD, this)
                        }
                }
        } catch(ignored: Exception) {}
    }

    private fun reloadConfig(cacheControl: CacheControl = httpCacheControl) {
        configURL?.let { url ->
            Logger.log.fine("Trying to fetch $url")
            try {
                executor.submit {
                    // don't use custom certificates or other settings for network configuration
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

                                    // save trusted custom certificates for cert4android
                                    if (config.has(SETTING_TRUSTED_CERTS)) {
                                        val certs = config.getJSONArray(SETTING_TRUSTED_CERTS)
                                        Logger.log.log(Level.FINER, "Adding ${certs.length()} trusted certificate(s)")
                                        for (i in 0 until certs.length()) {
                                            val raw = Base64.decode(certs.getString(i), Base64.DEFAULT)

                                            val intent = Intent(settings, CustomCertService::class.java)
                                            intent.action = CustomCertService.CMD_CERTIFICATION_DECISION
                                            intent.putExtra(CustomCertService.EXTRA_CERTIFICATE, raw)
                                            intent.putExtra(CustomCertService.EXTRA_TRUSTED, true)
                                            settings.startService(intent)
                                        }
                                    }

                                    // notify SettingsManager about new config
                                    settings.onReload()
                                }
                            }
                        } catch(e: Exception) {
                            Logger.log.log(Level.WARNING, "Couldn't load network config", e)
                        }
                    }
                }
            } catch(ignored: RejectedExecutionException) {}
        }
    }

    override fun forceReload() {
        prefs   .edit()
                .remove(PREF_CACHED_CONFIG)
                .apply()
        reloadConfig(CacheControl.FORCE_NETWORK)
    }


    override fun onServiceFound(service: NsdServiceInfo) {
        Logger.log.log(Level.INFO, "Zeroconf service found", service)
        nsdManager?.resolveService(service, object: NsdManager.ResolveListener {
            override fun onServiceResolved(service: NsdServiceInfo) {
                Logger.log.log(Level.INFO, "Zeroconf service resolved", service)
                val host = service.attributes["host"]?.let { String(it) } ?: service.host.hostName
                val path = service.attributes["path"]?.let { String(it) } ?: "/"
                val uri = URI("https", null, host, service.port, path, null, null)

                Logger.log.info("Found network configuration URL by $ZEROCONF_NAME DNS-SD: $uri")
                configURL = HttpUrl.get(uri)
                reloadConfig()
            }

            override fun onResolveFailed(service: NsdServiceInfo, error: Int) {
                Logger.log.warning("Couldn't resolve Zeroconf service: $error")
            }
        })
    }

    override fun onStopDiscoveryFailed(service: String, error: Int) {
        Logger.log.warning("Stopping zeroconf service discovery of $service failed: $error")
    }

    override fun onStartDiscoveryFailed(service: String, error: Int) {
        Logger.log.warning("Starting zeroconf service discovery of $service failed: $error")
    }

    override fun onDiscoveryStarted(service: String) {
        Logger.log.info("Zeroconf service discovery of $service started")
    }

    override fun onDiscoveryStopped(service: String) {
        Logger.log.fine("Zeroconf service discovery of $service stopped")
    }

    override fun onServiceLost(service: NsdServiceInfo) {
        Logger.log.info("Zeroconf service lost")
        // keep configURL until we have a better one
    }


    override fun has(key: String): Pair<Boolean, Boolean> {
        val has = config.has(key)
        return if (has)
            Pair(true, false)
        else
            Pair(false, true)
    }

    private fun<T> getValue(key: String, reader: (JSONObject) -> T?): Pair<T?, Boolean> {
        if (config.has(key))
            return Pair(reader(config), false)
        return Pair(null, true)
    }

    override fun getBoolean(key: String) =
            getValue(key) { config -> config.getBoolean(key) }

    override fun getInt(key: String) =
            getValue(key) { config -> config.getInt(key) }

    override fun getLong(key: String) =
            getValue(key) { config -> config.getLong(key) }

    override fun getString(key: String) =
            getValue(key) { config -> config.getString(key) }


    override fun isWritable(key: String) =
            Pair(false, !config.has(key))

    override fun putBoolean(key: String, value: Boolean?) = false
    override fun putInt(key: String, value: Int?) = false
    override fun putLong(key: String, value: Long?) = false
    override fun putString(key: String, value: String?) = false
    override fun remove(key: String) = false

}