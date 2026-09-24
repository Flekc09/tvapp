package com.tvapp

import android.app.Application
import com.tvapp.data.catalog.CatalogSync
import com.tvapp.data.db.AppDatabase
import com.tvapp.playback.PlayerController

class AppContainer(app: Application) {
    // Filled in by later tasks: db, http, catalogSync, playerController, settings.
    val application = app
    // Placeholders so CatalogSyncWorker compiles (plan Task 10 step 4); Task 16 wires them.
    lateinit var playerController: PlayerController
    lateinit var db: AppDatabase
    lateinit var catalogSync: CatalogSync
}

class TvApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}
