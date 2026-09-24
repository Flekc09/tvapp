package com.tvapp

import android.app.Application

class AppContainer(app: Application) {
    // Filled in by later tasks: db, http, catalogSync, playerController, settings.
    val application = app
}

class TvApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}
