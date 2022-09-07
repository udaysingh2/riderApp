package com.ps.psrider

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.annotation.NonNull
import com.amazonaws.services.cognitoidentityprovider.model.AdminGetUserResult
import com.amazonaws.services.cognitoidentityprovider.model.UserNotFoundException
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.AuthSession
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.result.AuthSessionResult
import com.amplifyframework.auth.result.AuthSignInResult
import com.amplifyframework.auth.result.AuthSignUpResult
import com.amplifyframework.auth.result.step.AuthSignInStep
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.ps.psrider.aws_cognito.AuthResponse
import com.ps.psrider.aws_cognito.CognitoOperations
import com.ps.psrider.sms_autofill.AppSignatureHashHelper
import com.ps.psrider.sms_autofill.SMSReceiver
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant
import java.lang.Exception
import java.util.*



class MainActivity : FlutterFragmentActivity(), MethodChannel.MethodCallHandler {

    companion object {
        // Status codes
        private const val STATUS_CODE_USERNAME_EXISTS_EXCEPTION = "400"
        private const val STATUS_CODE_DEFAULT = "500"
        private const val DEVICE_ID_NOT_FOUND = "402"
        private const val STATUS_CODE_USER_NOT_FOUND = "401"

        // Keys
        private const val PHONE_NUMBER = "phoneNumber"
        private const val ACCESS_TOKEN = "accessToken"
        private const val KEY_DEVICE_ID = "custom:Secret"
        private const val KEY_PHONE_NUMBER_VERIFIED = "phone_number_verified"
        private const val USER_OTP = "userOTP"
        private const val SOMETHING_WENT_WRONG = "Something went wrong"

        // Channels
        private const val CHANNEL = "riderappdev.authcode/channel"
        private const val CHANNEL_COGNITO_OPERATIONS = "riderappdev.cognito/channel"
        private const val EVENTS = "riderappdev.authcode/events"
        private const val EVENTS_SMS_LISTENER = "riderappdev.sms_listener/events"
    }

    private var startString: String? = null
    private var linksReceiver: BroadcastReceiver? = null
    private var jobNotificationHandler: JobNotificationHandler? = null
    private var smsReceiver: SMSReceiver? = null
    private val handler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }
    
    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        GeneratedPluginRegistrant.registerWith(flutterEngine)
        this.registerPushReceiver(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "initialLink") {
                if (startString != null) {
                    result.success(startString)
                }
            }
        }

        MethodChannel(flutterEngine.dartExecutor, CHANNEL_COGNITO_OPERATIONS).setMethodCallHandler(this)

        EventChannel(flutterEngine.dartExecutor, EVENTS).setStreamHandler(
                object : EventChannel.StreamHandler {
                    override fun onListen(args: Any?, events: EventSink) {
                        linksReceiver = createChangeReceiver(events)
                    }

                    override fun onCancel(args: Any?) {
                        linksReceiver = null
                    }
                })
        try {
            EventChannel(flutterEngine.dartExecutor, EVENTS_SMS_LISTENER).setStreamHandler(
                    object : EventChannel.StreamHandler {
                        override fun onListen(args: Any?, events: EventSink) {
                            smsReceiver?.setOTPListener(object : SMSReceiver.OTPReceiveListener {
                                override fun onOTPReceived(otp: String?) {
                                    events.success(otp)
                                }

                                override fun onOTPTimeOut() {
                                    events.error(STATUS_CODE_DEFAULT, "OTP timeout", "OTP timeout");
                                }

                                override fun onOTPReceivedError(error: String?) {
                                    events.error(STATUS_CODE_DEFAULT, "OTP Received Error", "OTPReceivedError");
                                }

                            })
                        }

                        override fun onCancel(args: Any?) {
                            smsReceiver = null
                        }
                    })
        } catch (exception: Exception) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Handle deep linking
        if (intent.data != null) {
            startString = intent.data?.toString()
        }
    }

    /**
     * Starts SmsRetriever, which waits for ONE matching SMS message until timeout
     * (5 minutes). The matching SMS message will be sent via a Broadcast Intent with
     * action SmsRetriever#SMS_RETRIEVED_ACTION.
     */
    private fun startSMSListener() {
        try {
            val appSignatureHashHelper = AppSignatureHashHelper(this)
            Log.i("Main123", "HashKey: " + appSignatureHashHelper.getAppSignatures()?.get(0))

            smsReceiver = SMSReceiver()
            val intentFilter = IntentFilter()
            intentFilter.addAction(SmsRetriever.SMS_RETRIEVED_ACTION)
            this.registerReceiver(smsReceiver, intentFilter)
            val client = SmsRetriever.getClient(this)
            val task = client.startSmsRetriever()
            task.addOnSuccessListener { // API successfully started
                Log.i("Main123", "API successfully started")
            }
            task.addOnFailureListener { // Fail to start API
                Log.i("Main123", "Fail to start API")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Handle deep linking
        if (intent.action === Intent.ACTION_VIEW) {
            linksReceiver?.onReceive(this.applicationContext, intent)
        }
        if (intent.action == SmsRetriever.SMS_RETRIEVED_ACTION) {
            smsReceiver?.onReceive(this.applicationContext, intent)
        }
        // Handle push notification
        if (intent.extras != null) {
            this.jobNotificationHandler?.onNotificationBackground(intent.extras, intent.action)
        }
    }

    fun createChangeReceiver(events: EventSink): BroadcastReceiver? {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { // NOTE: assuming intent.getAction() is Intent.ACTION_VIEW
                val dataString = intent.dataString
                        ?: events.error("UNAVAILABLE", "Link unavailable", null)
                events.success(dataString)
            }
        }
    }

    // Initialize Push handler and register broadcast receiver
    private fun registerPushReceiver(flutterEngine: FlutterEngine) {
        jobNotificationHandler = JobNotificationHandler(flutterEngine.dartExecutor)
        if (intent.extras != null) {
            this.jobNotificationHandler?.onNotificationOnResume(intent.extras, intent.action)
        }
    }

    override fun onStart() {
        super.onStart()

        // Register intent for broadcast receiver
        val pushIntent = IntentFilter()
        pushIntent.addAction("com.rider.job.notification")
        registerReceiver(jobNotificationHandler, pushIntent)
    }


    // Unregister the receiver when not required
    override fun onStop() {
        unregisterReceiver(jobNotificationHandler)
        super.onStop()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            CognitoOperations::signUp.name -> {
                CognitoOperations.signUp(call.getArgumentFromKey(PHONE_NUMBER) ?: "")
                        .subscribe(
                                {
                                    result.sendSuccess(true)
                                }
                        ) { error: Throwable? ->
                            if (error is AuthException.UsernameExistsException) {
                                result.sendError(STATUS_CODE_USERNAME_EXISTS_EXCEPTION, error.message)
                            } else {
                                result.sendError(STATUS_CODE_DEFAULT, error?.message)
                            }
                        }

            }
            CognitoOperations::checkUserStatus.name -> {
                CognitoOperations.checkUserStatus(call.getArgumentFromKey(PHONE_NUMBER) ?: "")
                        .subscribe(
                                { authResult: AdminGetUserResult? ->
                                    val isUserPhoneNumberVerified = authResult?.userAttributes?.filter { element -> element.name == KEY_PHONE_NUMBER_VERIFIED }
                                    result.sendSuccess(AuthResponse.getAdminGetUserResultToJson(authResult?.userStatus, isUserPhoneNumberVerified?.get(0)?.value?.toBoolean()))
                                }
                        ) { error: Throwable? ->
                            if (error is UserNotFoundException) {
                                result.sendError(STATUS_CODE_USER_NOT_FOUND, error.message)
                            } else {
                                result.sendError(STATUS_CODE_DEFAULT, error?.message)
                            }
                        }

            }
            CognitoOperations::adminConfirmUserSignUp.name -> {
                CognitoOperations.adminConfirmUserSignUp(call.getArgumentFromKey(PHONE_NUMBER)
                        ?: "")
                        .subscribe(
                                {
                                    result.sendSuccess(true)
                                }
                        ) { error: Throwable? ->
                            result.sendError(STATUS_CODE_DEFAULT, error?.message)
                        }

            }
            CognitoOperations::getRiderAuthDetails.name -> {
                CognitoOperations.getRiderAuthDetails()
                        .subscribe(
                                { authResult: AuthSession? ->
                                    val cognitoAuthSession = authResult as AWSCognitoAuthSession
                                    when (cognitoAuthSession.identityId.type) {
                                        AuthSessionResult.Type.SUCCESS -> {
                                            result.sendSuccess(
                                                    AuthResponse.getUserSessionDetails(
                                                            authResult.userPoolTokens.value?.accessToken
                                                                    ?: "",
                                                            authResult.userPoolTokens.value?.refreshToken
                                                                    ?: "",
                                                            authResult.userPoolTokens.value?.idToken
                                                                    ?: "",
                                                            "",
                                                            authResult.isSignedIn))

                                        }
                                        AuthSessionResult.Type.FAILURE -> result.sendError(STATUS_CODE_DEFAULT, cognitoAuthSession.identityId.error.toString())
                                    }
                                }
                        ) { error: Throwable? ->
                            result.sendError(STATUS_CODE_DEFAULT, error?.message)
                        }
            }

            CognitoOperations::getAccessTokenExpiryTime.name -> {
                CognitoOperations.getAccessTokenExpiryTime()
                        .subscribe(
                                { date: Date? ->
                                    result.sendSuccess(date?.time.toString())
                                }
                        ) { error: Throwable? ->
                            result.sendError(STATUS_CODE_DEFAULT, error?.message)
                        }
            }
            CognitoOperations::confirmSignInRequest.name -> {
                CognitoOperations.confirmSignInRequest(call.getArgumentFromKey(USER_OTP) ?: "")
                        .subscribe(
                                { authSignUpResult: AuthSignInResult ->
                                    result.sendSuccess(authSignUpResult.isSignInComplete)
                                }
                        ) { error: Throwable ->
                            result.sendError(STATUS_CODE_DEFAULT, error.message)
                        }

            }
            CognitoOperations::resendConfirmationCode.name -> {
                CognitoOperations.resendConfirmationCode(call.getArgumentFromKey(PHONE_NUMBER)
                        ?: "")
                        .subscribe(
                                { authSignUpResult: AuthSignUpResult ->
                                    result.sendSuccess(authSignUpResult.isSignUpComplete)
                                }
                        ) { error: Throwable ->
                            result.sendError(STATUS_CODE_DEFAULT, error.message)
                        }

            }
            CognitoOperations::loginUser.name -> {
                CognitoOperations.loginUser(call.getArgumentFromKey(PHONE_NUMBER) ?: "")
                        .subscribe(
                                { authSignInResult ->
                                    when (authSignInResult.nextStep.signInStep) {
                                        AuthSignInStep.CONFIRM_SIGN_IN_WITH_SMS_MFA_CODE -> {
                                            startSMSListener()
                                            result.sendSuccess(AuthSignInStep.CONFIRM_SIGN_IN_WITH_SMS_MFA_CODE.name)
                                        }
                                        AuthSignInStep.DONE -> {
                                            result.sendSuccess(AuthSignInStep.DONE.name)
                                        }
                                        else -> {
                                            result.sendSuccess(SOMETHING_WENT_WRONG)
                                        }
                                    }
                                }
                        ) { error: Throwable ->
                            result.sendError(STATUS_CODE_DEFAULT, error.message)
                        }

            }
            CognitoOperations::logoutUser.name -> {
                CognitoOperations.logoutUser()
                        .subscribe(
                                { result.sendSuccess(true) }
                        ) { error: Throwable -> result.sendError(STATUS_CODE_DEFAULT, error.message) }

            }
            CognitoOperations::deleteUser.name -> {
                CognitoOperations.deleteUser(call.getArgumentFromKey(PHONE_NUMBER) ?: "")
                        ?.subscribe(
                                { result.sendSuccess(true) }
                        ) { error: Throwable -> result.sendError(STATUS_CODE_DEFAULT, error.message) }

            }

            CognitoOperations::updateUserDeviceId.name -> {
                CognitoOperations.updateUserDeviceId(getDeviceIdentifierId() ?: "")
                        ?.subscribe(
                                {
                                    result.sendSuccess(it.isUpdated)
                                }
                        ) { error: Throwable ->
                            result.sendError(STATUS_CODE_DEFAULT, error.message)
                        }

            }
            CognitoOperations::isUserLoginFromSameDevice.name -> {
                CognitoOperations.isUserLoginFromSameDevice()
                        ?.subscribe(
                                {
                                    try {
                                        val deviceIdAttribute = it.filter { element -> element.key == AuthUserAttributeKey.custom(KEY_DEVICE_ID) }[0]
                                        val deviceID = deviceIdAttribute.value //abc
                                        result.sendSuccess(deviceID?.equals(getDeviceIdentifierId())
                                                ?: false)
                                    } catch (exception: Exception) {
                                        result.sendError(DEVICE_ID_NOT_FOUND, exception.message)
                                    }
                                }
                        ) { error: Throwable ->
                            result.sendError(STATUS_CODE_DEFAULT, error.message)
                        }

            }
            else -> result.notImplemented()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (smsReceiver != null) {
            unregisterReceiver(smsReceiver)
        }
    }

    @SuppressLint("HardwareIds")
    private fun getDeviceIdentifierId(): String? {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) //def
    }

    private fun MethodCall.getArgumentFromKey(argument: String): String? {
        return this.argument(argument)
    }

    private fun MethodChannel.Result.sendSuccess(result: Any) {
        handler.post {
            this.success(result)
        }
    }

    private fun MethodChannel.Result.sendError(errorCode: String, errorMessage: String?) {
        handler.post {
            this.error(errorCode, errorMessage, errorMessage)
        }
    }
}



