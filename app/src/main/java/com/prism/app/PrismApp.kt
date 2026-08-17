package com.prism.app

import android.app.Application
import com.google.firebase.FirebaseApp

class PrismApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
