/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import android.os.FileObserver
import at.bitfire.davdroid.log.Logger
import org.apache.commons.io.IOUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.logging.Level

class ConfigFileProvider(
        settings: Settings
): Provider {

    val fileName = "davdroid-config.json"

    val configDir = settings.getExternalFilesDir(null)!!
    val fileObserver = object: FileObserver(configDir.path, FileObserver.CLOSE_WRITE or FileObserver.DELETE) {
        override fun onEvent(event: Int, name: String?) {
            if (name == fileName) {
                reload()
                settings.onReload()
            }
        }
    }

    var json: JSONObject? = null

    init {
        fileObserver.startWatching()
        reload()
    }

    override fun close() {
        fileObserver.stopWatching()
    }


    private fun reload() {
        json = null

        val file = File(configDir, fileName)
        Logger.log.info("Looking for configuration in $file")
        if (file.exists()) {
            try {
                val raw = IOUtils.toString(file.inputStream(), Charsets.UTF_8)
                json = JSONObject(raw)

                Logger.log.log(Level.INFO, "Found configuration by file", json)
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't read configuration file", e)
            }
        }
    }


    override fun has(key: String): Pair<Boolean, Boolean> {
        val has = json?.has(key) ?: false
        return if (has)
            Pair(true, false)
        else
            Pair(false, true)
    }

    private fun<T> getValue(key: String, reader: (JSONObject) -> T?) =
            if (json?.has(key) == true)
                Pair(try { json?.let { reader(it) } } catch(e: JSONException) { null }, false)
            else
                Pair(null, true)

    override fun getBoolean(key: String) =
            getValue(key, { json -> json.getBoolean(key) })

    override fun getInt(key: String) =
            getValue(key, { json -> json.getInt(key) })

    override fun getLong(key: String) =
            getValue(key, { json -> json.getLong(key) })

    override fun getString(key: String) =
            getValue(key, { json -> json.getString(key) })

    override fun isWritable(key: String) = Pair(false, !(json?.has(key) == true))
    override fun putBoolean(key: String, value: Boolean?) = false
    override fun putInt(key: String, value: Int?) = false
    override fun putLong(key: String, value: Long?) = false
    override fun putString(key: String, value: String?) = false
    override fun remove(key: String) = false

}