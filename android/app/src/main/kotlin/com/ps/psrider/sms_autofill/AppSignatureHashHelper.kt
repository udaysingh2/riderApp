package com.ps.psrider.sms_autofill

import android.annotation.TargetApi
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

open class AppSignatureHashHelper(base: Context?) : ContextWrapper(base) {
    val TAG = AppSignatureHashHelper::class.java.simpleName

    private val HASH_TYPE = "SHA-256"
    private val NUM_HASHED_BYTES = 9
    private val NUM_BASE64_CHAR = 11

    /**
     * Get all the app signatures for the current package
     *
     * @return
     */
    fun getAppSignatures(): ArrayList<String>? {
        val appSignaturesHashs = ArrayList<String>()
        try {
            // Get all package details
            val packageName: String = packageName
            val packageManager: PackageManager = packageManager
            val signatures = packageManager.getPackageInfo(packageName,
                    PackageManager.GET_SIGNATURES).signatures
            for (signature in signatures) {
                val hash: String? = hash(packageName, signature.toCharsString())
                if (hash != null) {
                    appSignaturesHashs.add(String.format("%s", hash))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Package not found", e)
        }
        return appSignaturesHashs
    }

    @TargetApi(19)
    open fun hash(packageName: String, signature: String): String? {
        val appInfo = "$packageName $signature"
        try {
            val messageDigest = MessageDigest.getInstance(HASH_TYPE)
            messageDigest.update(appInfo.toByteArray(StandardCharsets.UTF_8))
            var hashSignature = messageDigest.digest()

            // truncated into NUM_HASHED_BYTES
            hashSignature = Arrays.copyOfRange(hashSignature, 0, NUM_HASHED_BYTES)
            // encode into Base64
            var base64Hash = Base64.encodeToString(hashSignature, Base64.NO_PADDING or Base64.NO_WRAP)
            base64Hash = base64Hash.substring(0, NUM_BASE64_CHAR)
            return base64Hash
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "No Such Algorithm Exception", e)
        }
        return null
    }
}