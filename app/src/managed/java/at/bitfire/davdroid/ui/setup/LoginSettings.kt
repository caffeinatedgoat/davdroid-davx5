/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup

import android.os.Parcel
import android.os.Parcelable

data class LoginSettings(
        val organization: String?,
        val logoURL: String?,
        val loginIntroduction: String?,
        val baseURL: String?,
        val userName: String?,
        var certificateAlias: String?
): Parcelable {

    companion object {
        const val INTRODUCTION = "login_introduction"
        const val BASE_URL = "login_base_url"
        const val USER_NAME = "login_user_name"
        const val CERTIFICATE_ALIAS = "login_certificate_alias"
    }

    constructor(parcel: Parcel): this(
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(organization)
        parcel.writeString(logoURL)
        parcel.writeString(loginIntroduction)
        parcel.writeString(baseURL)
        parcel.writeString(userName)
        parcel.writeString(certificateAlias)
    }

    override fun describeContents() = 0

    @JvmField
    val CREATOR = object: Parcelable.Creator<LoginSettings> {

        override fun createFromParcel(parcel: Parcel) = LoginSettings(parcel)
        override fun newArray(size: Int) = arrayOfNulls<LoginSettings?>(size)
    }

}