package com.ps.psrider.aws_cognito

import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.results.Device
import com.amazonaws.regions.Region
import com.amazonaws.services.cognitoidentityprovider.AmazonCognitoIdentityProviderClient
import com.amazonaws.services.cognitoidentityprovider.model.*
import com.amplifyframework.auth.AuthSession
import com.amplifyframework.auth.AuthUserAttribute
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.options.AuthSignOutOptions
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.auth.result.AuthSignInResult
import com.amplifyframework.auth.result.AuthSignUpResult
import com.amplifyframework.auth.result.AuthUpdateAttributeResult
import com.amplifyframework.core.Amplify
import com.amplifyframework.rx.RxAmplify
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.json.JSONObject
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

object CognitoOperations {

    private const val COGNITO_USER_POOL = "CognitoUserPool"
    private const val REGION = "Region"
    private const val POOL_ID = "PoolId"
    private const val KEY_DEVICE_ID = "custom:Secret"

    fun signUp(phoneNumber: String): Single<AuthSignUpResult> {
        return RxAmplify.Auth.signUp(
                phoneNumber,
                getMD5(phoneNumber),
                AuthSignUpOptions.builder().userAttribute(AuthUserAttributeKey.phoneNumber(),
                        phoneNumber).build())
    }

    fun checkUserStatus(phoneNumber: String): @NonNull Single<AdminGetUserResult?> {
        return Single.fromCallable {
            getRiderStatus(phoneNumber)
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    fun adminConfirmUserSignUp(phoneNumber: String): @NonNull Single<AdminConfirmSignUpResult?> {
        return Single.fromCallable {
            adminConfirmUser(phoneNumber)
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    fun deleteUser(phoneNumber: String): @NonNull Single<Unit>? {
        return Single.fromCallable {
            deleteUserRequest(phoneNumber)
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    fun confirmSignInRequest(otp: String): Single<AuthSignInResult> {
        return RxAmplify.Auth.confirmSignIn(otp)
    }

    fun resendConfirmationCode(phoneNumber: String): Single<AuthSignUpResult> {
        return RxAmplify.Auth.resendSignUpCode(phoneNumber)
    }

    fun loginUser(phoneNumber: String): Single<AuthSignInResult> {
        return RxAmplify.Auth.signIn(phoneNumber, getMD5(phoneNumber))
    }

    fun logoutUser(): Completable {
        return RxAmplify.Auth.signOut()
    }

    fun updateUserDeviceId(deviceId: String): Single<AuthUpdateAttributeResult>? {
        val userEmail =
                AuthUserAttribute(AuthUserAttributeKey.custom(KEY_DEVICE_ID), deviceId)
        return RxAmplify.Auth.updateUserAttribute(userEmail)
    }

    fun isUserLoginFromSameDevice(): Single<MutableList<AuthUserAttribute>>? {
        return RxAmplify.Auth.fetchUserAttributes()
    }

    fun getAccessTokenExpiryTime(): @NonNull Single<Date?> {
        return Single.fromCallable {
            val awsMobileClient = Amplify.Auth.getPlugin("awsCognitoAuthPlugin").escapeHatch
                    as AWSMobileClient?
            awsMobileClient?.tokens?.accessToken?.expiration
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    fun getRiderAuthDetails(): Single<AuthSession> {
        return RxAmplify.Auth.fetchAuthSession()
    }

    private fun getRiderStatus(phoneNumber: String): AdminGetUserResult {
        val adminGetUserRequest = AdminGetUserRequest()
                .withUserPoolId(getUserPool()?.optString(POOL_ID)).withUsername(phoneNumber)
        return getProvider().adminGetUser(adminGetUserRequest)
    }

    private fun adminConfirmUser(phoneNumber: String): AdminConfirmSignUpResult {
        val adminConfirmSignUpRequest = AdminConfirmSignUpRequest()
                .withUserPoolId(getUserPool()?.optString(POOL_ID)).withUsername(phoneNumber)
        return getProvider().adminConfirmSignUp(adminConfirmSignUpRequest)
    }

    private fun deleteUserRequest(phoneNumber: String) {
        val adminDeleteUserRequest = AdminDeleteUserRequest()
                .withUserPoolId(getUserPool()?.optString(POOL_ID)).withUsername(phoneNumber)
        return getProvider().adminDeleteUser(adminDeleteUserRequest)
    }

    private fun getUserPool(): JSONObject? {
        return AWSCognitoAuthPlugin().escapeHatch.configuration.optJsonObject(COGNITO_USER_POOL)
    }

    private fun getProvider(): AmazonCognitoIdentityProviderClient {
        val provider = AmazonCognitoIdentityProviderClient(AWSCognitoAuthPlugin().escapeHatch.awsCredentials)
        provider.setRegion(Region.getRegion(getUserPool()?.optString(REGION)))
        return provider
    }

    private fun getMD5(phoneNumber: String): String {
        val algorithm = "MD5"
        try {
            // Create MD5 Hash
            val digest: MessageDigest = MessageDigest
                    .getInstance(algorithm)
            digest.update(phoneNumber.toByteArray())
            val messageDigest: ByteArray = digest.digest()

            // Create Hex String
            val hexString = StringBuilder()
            for (digest1 in messageDigest) {
                var hex: String = Integer.toHexString(0xFF and digest1.toInt())
                while (hex.length < 2) hex = "0$hex"
                hexString.append(hex)
            }
            return hexString.toString()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return ""
    }

}
