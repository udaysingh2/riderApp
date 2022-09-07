package com.ps.psrider


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class PushNotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage) //com.rider.job.notification
        Log.d("notification service", "new notification received")
        this.broadcastInfo(remoteMessage.data)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        val intent = Intent()
        intent.action = "com.rider.job.notification"
        intent.putExtra("token", token)
        sendBroadcast(intent)
    }

    private fun broadcastInfo(info: Map<String, Any>) {
        generateNotification(applicationContext, info)
        val intent = Intent()
        intent.action = "com.rider.job.notification"
        intent.putExtra("data", HashMap(info))
        sendBroadcast(intent)
    }

    private fun generateNotification(context: Context, jobInfo: Map<String, Any>) {
        createNotificationChannel(context)
        val channelId = "all_notifications" // Use same Channel ID
        val intent = Intent(context, MainActivity::class.java)
        val dataMap = HashMap(jobInfo)
        // As notification received and clicked same dataMap is passed,
        // so to determine notification click adding entry as "isNotificationClicked"
        dataMap["isNotificationClicked"] = true
        intent.putExtra("data", dataMap)
        val id = Random.nextInt(1000, 2000);
        val pendingIntent = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Log.i("notification", "generateNotification - "+HashMap(jobInfo).toString());
        val builder = NotificationCompat.Builder(context, channelId) // Create notification with channel Id
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("${jobInfo.getValue("title")}")
                .setContentText("${jobInfo.getValue("body")}")
                .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("${jobInfo.getValue("body")}"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAllowSystemGeneratedContextualActions(false)
        builder.setContentIntent(pendingIntent).setAutoCancel(true)
        val mNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        with(mNotificationManager) {
            notify(id, builder.build())
        }
    }

    private fun createNotificationChannel(context: Context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "all_notifications"
            val mChannel = NotificationChannel(
                    channelId,
                    "General Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            )
            mChannel.description = "This is default channel used for all other notifications"

            val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }
}