/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.content.Context
import android.util.Base64
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.ISettings
import org.apache.commons.io.IOUtils
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.DateFormat
import java.util.*
import java.util.logging.Level
import kotlin.collections.LinkedHashSet

class LicenseChecker(
        val context: Context
) {

    companion object {
        val SETTING_LICENSE = "license"
        val SETTING_LICENSE_SIGNATURE = "license_signature"
    }

    private val publicKey: PublicKey

    var organization: String? = null
    var domains: Set<String>? = null
    var users: Int? = null
    var expiresAt: Long? = null


    init {
        val keyFactory = KeyFactory.getInstance("EC")!!
        val spec = X509EncodedKeySpec(IOUtils.toByteArray(context.resources.assets.open("licensePublic.der")))
        publicKey = keyFactory.generatePublic(spec)!!
    }

    fun verifyLicense(settings: ISettings): Boolean {
        val license = settings.getString(SETTING_LICENSE, null) ?: return false
        val licenseSignature = settings.getString(SETTING_LICENSE_SIGNATURE, null) ?: return false

        Logger.log.log(Level.FINE, "Got license and signature", arrayOf(license, licenseSignature))

        // verify license
        var validLicense = false
        try {
            val signature = Base64.decode(licenseSignature, 0)
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(license.toByteArray())
            validLicense = sig.verify(signature)
            Logger.log.info("License signature valid? -> $validLicense")
        } catch(e: Exception) {
            Logger.log.log(Level.WARNING, "Couldn't validate license", e)
        }

        if (validLicense)
            // check for expiration
            try {
                val json = JSONObject(license)
                organization = json.getString("organization")
                if (json.has("domains"))
                    json.getJSONArray("domains").let {
                        val domains = LinkedHashSet<String>(it.length())
                        for (i in 0 until it.length())
                            domains += it.optString(i)
                        this.domains = domains
                    }
                users = json.getInt("users")

                val expires = json.getLong("expires")
                if (expires <= System.currentTimeMillis()/1000) {
                    Logger.log.log(Level.WARNING, "License has expired at " + DateFormat.getInstance().format(Date(expires*1000)))
                    validLicense = false
                }
                expiresAt = expires
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't parse license JSON", e)
                validLicense = false
            }
        return validLicense
    }

}