package com.ps.psrider.aws_cognito

import org.json.JSONObject

object AuthResponse {
    private const val KEY_USER_STATUS = "userStatus"
    private const val KEY_PHONE_NUMBER_VERIFIED_STATUS = "phoneNumberVerified"
    private const val KEY_ACCESS_TOKEN = "accessToken"
    private const val KEY_REFRESH_TOKEN = "refreshToken"
    private const val KEY_ID_TOKEN = "idToken"
    private const val KEY_EXPIRY_TIME = "expireTime"
    private const val KEY_IS_SIGNED = "isSignedIn"


    fun getAdminGetUserResultToJson(userStatus: String?, phoneNumberVerified: Boolean?): String {
        return object : JSONObject() {
            init {
                put(KEY_USER_STATUS, userStatus)
                put(KEY_PHONE_NUMBER_VERIFIED_STATUS, phoneNumberVerified)
            }
        }.toString()
    }

    fun getUserSessionDetails(accessToken: String, refreshToken: String, idToken:String,expiryTime: String, signedIn: Boolean): String {
        return object : JSONObject() {
            init {
                put(KEY_ACCESS_TOKEN, accessToken)
                put(KEY_REFRESH_TOKEN, refreshToken)
                put(KEY_ID_TOKEN, idToken)
                put(KEY_EXPIRY_TIME, expiryTime)
                put(KEY_IS_SIGNED, signedIn)
            }
        }.toString()
    }
}