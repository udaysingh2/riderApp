package com.ps.psrider

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlin.random.Random


// Streaming event channel name
private const val eventChannelName = "com.rider.push/task"

// Method Channel
private const val methodChannelName = "com.rider.push.resume/killedtask"

object Dispatch {
    fun asyncOnBackground(call: () -> Unit) {
        AsyncTask.execute {
            call()
        }
    }

    fun asyncOnMain(call: () -> Unit) {
        Handler(Looper.getMainLooper()).post {
            call()
        }
    }
}

class JobNotificationHandler(messenger: DartExecutor) : EventChannel.StreamHandler, MethodChannel.MethodCallHandler, BroadcastReceiver() {

    // Declare event channel
    private var eventSink: EventChannel.EventSink? = null

    // Holding binary messanger
    private var binaryMessenger: DartExecutor? = null

    // Property holds push data during kill and launch scenario
    private var resumePushInfo: Map<String, Any>? = null

    init {
        this.binaryMessenger = messenger
        EventChannel(this.binaryMessenger, eventChannelName).setStreamHandler(this)
        MethodChannel(this.binaryMessenger, methodChannelName).setMethodCallHandler(this)
    }


    // Method call handler
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {

        when (call.method) {

            "checkPushMessages" -> {
                if (resumePushInfo != null) {
                    this.eventSink?.success(resumePushInfo)
                }
                result.success(0)
            }
            "getDeviceToken" -> {
                Dispatch.asyncOnBackground {
                    FirebaseMessaging.getInstance().token.addOnCompleteListener {
                        if (it.isComplete) {
                            val firebaseToken = it.result.toString()
                            Log.i("FToken", firebaseToken)
                            Dispatch.asyncOnMain {
                                result.success(firebaseToken)
                            }
                        }
                    }
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    // Event  channel onListen delegate
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        this.eventSink = events
    }

    // Event channel onCancel delegate
    override fun onCancel(arguments: Any?) {
        this.eventSink = null
    }

    // Process notification when application is in killed state
    fun onNotificationOnResume(bundle: Bundle?, action: String?) {
        Log.i("RemoteMessage", "onNotificationOnResume-$action")
        val pushInfo = this.processPushNotificationData(bundle, action)
        if (pushInfo != null) {
            resumePushInfo = pushInfo
            this.resumePushInfo = pushInfo
        }
    }

    // Process notification when application is in background
    fun onNotificationBackground(bundle: Bundle?, action: String?) {
        Log.i("RemoteMessage", "onNotificationBackground-$action")
        val pushInfo = this.processPushNotificationData(bundle, action)
        if (pushInfo != null) {
            this.eventSink?.success(pushInfo)
        }
    }

    // Method to process push notification date onresume, onload
    private fun processPushNotificationData(bundle: Bundle?, action: String?): HashMap<String, Any>? {
        val pushInfo: HashMap<String, Any> = HashMap()
        bundle?.let { payload ->
            for (key in payload.keySet()) {
                payload[key]?.let { value ->
                    pushInfo.put(key, value)
                }
            }

            // The action value as "MainActivity" is set for MainActivity in Manifestflutter
            // and also in push notification payload it's returned
            // in "click_action" parameter inside "notification"
            if (action != null && action == "MainActivity" &&
                    !pushInfo.containsKey("data") &&
                    pushInfo.containsKey("type") &&
                    pushInfo["type"] == "NEWS_PROMOTIONS") {
                pushInfo["isNotificationClicked"] = true
                val dataMap: HashMap<String, Any> = HashMap()
                dataMap["data"] = pushInfo
                Log.i("RemoteMessage", "processPushNotificationData$dataMap")
                return dataMap
            } else if (pushInfo["jobId"] != null || pushInfo["data"] != null) {
                Log.i("RemoteMessage", "processPushNotificationData-$pushInfo")
                return pushInfo
            }
        }
        return null
    }

    // Broadcast receiver from push notification service
    override fun onReceive(context: Context, intent: Intent?) {

        intent?.let { pIntent ->
            when {
                // Check if new token or new push notification received
                pIntent.hasExtra("token") -> {
                    val newToken = pIntent.getStringExtra("token")
                    if(newToken != null) {
                        Log.i("RemoteMessage", newToken)
                    } else {
                        Log.i("RemoteMessage", "")
                    }
                    
                }
                // Check if data message received
                pIntent.hasExtra("data") -> {
                    val jobInfo = (pIntent.getSerializableExtra("data") as HashMap<String, Any>).toMap()
                    Log.i("RemoteMessage", jobInfo.toString())
                    this.eventSink?.success(jobInfo)
                }
                else -> {
                }
            }
        }
    }
}
