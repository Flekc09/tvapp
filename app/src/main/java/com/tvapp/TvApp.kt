package com.tvapp

import android.app.Application
import com.tvapp.core.SystemClockImpl
import com.tvapp.core.SystemDateProvider
import com.tvapp.data.catalog.CatalogImporter
import com.tvapp.data.catalog.CatalogParser
import com.tvapp.data.catalog.CatalogSync
import com.tvapp.data.catalog.ImporterSink
import com.tvapp.data.db.AppDatabase
import com.tvapp.net.AndroidNetworkState
import com.tvapp.net.Http
import com.tvapp.net.NetworkState
import com.tvapp.net.SwitchableNetworkState
import com.tvapp.playback.PlayerController
import com.tvapp.playback.Prefetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(val application: Application) {
    val http = Http.client()
    val db = AppDatabase.build(application)
    val clock = SystemClockImpl()
    val dates = SystemDateProvider()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val parser = CatalogParser()
    val importer = CatalogImporter(db, parser)
    val network: SwitchableNetworkState = SwitchableNetworkState(AndroidNetworkState(application))
    val catalogSync = CatalogSync(http, ImporterSink(importer, db)) { db.local().setting("catalog_base_url") ?: BuildConfig.CATALOG_BASE_URL }
    val playerController = PlayerController(application, http, db, importer, clock, dates, network, scope)
    val prefetcher = Prefetcher(http, scope)

    init {
        scope.launch { importer.cleanupOrphans(); catalogSync.recordPendingUpdate() }
        CatalogSync.schedulePeriodic(application)
    }
}

class TvApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}
