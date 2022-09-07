package com.ps.psrider.aws_cognito

import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.AmplifyConfiguration
import com.amplifyframework.rx.RxAmplify
import io.flutter.app.FlutterApplication


class RiderApplication : FlutterApplication() {
    override fun onCreate() {
        super.onCreate()
        try {
            RxAmplify.addPlugin(AWSCognitoAuthPlugin())
            val config = AmplifyConfiguration.builder(applicationContext)
                    .devMenuEnabled(false)
                    .build()
            RxAmplify.configure(config, applicationContext)
        } catch (error: AmplifyException) {
            Log.e("MyAmplifyApp", "Could not initialize Amplify", error)
        }
    }
}