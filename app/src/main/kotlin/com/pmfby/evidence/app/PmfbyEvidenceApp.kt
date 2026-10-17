package com.pmfby.evidence.app

import android.app.Application
import com.pmfby.evidence.app.data.local.AppDatabase
import com.pmfby.evidence.app.data.remote.ApiService
import com.pmfby.evidence.app.data.remote.FileUploadApi
import com.pmfby.evidence.app.data.remote.RetrofitClient
import com.pmfby.evidence.app.data.repository.AuthRepository
import com.pmfby.evidence.app.data.repository.CaseActionsRepository
import com.pmfby.evidence.app.data.repository.FarmerRepository
import com.pmfby.evidence.app.location.LocationProvider
import com.pmfby.evidence.app.session.SessionManager
import com.pmfby.evidence.app.work.UploadWorker
import com.squareup.moshi.Moshi

/**
 * Hand-wired dependency container (no Hilt/Dagger in this pass — the dependency graph is
 * small enough that a plain Application subclass holding singletons is honest and
 * reviewable; see ROADMAP.md if this ever needs to grow into a real DI framework).
 * UploadWorker (work/UploadWorker.kt) reads `database`, `apiService`, `sessionManager` and
 * `moshi` off this class by name — keep those four names/types in sync with it.
 */
class PmfbyEvidenceApp : Application() {

    lateinit var database: AppDatabase
    lateinit var sessionManager: SessionManager
    lateinit var locationProvider: LocationProvider

    val moshi: Moshi get() = RetrofitClient.moshi
    val apiService: ApiService get() = RetrofitClient.apiService

    lateinit var authRepository: AuthRepository
    lateinit var farmerRepository: FarmerRepository
    lateinit var caseActionsRepository: CaseActionsRepository
    lateinit var fileUploadApi: FileUploadApi

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        sessionManager = SessionManager(this)
        locationProvider = LocationProvider(this)

        authRepository = AuthRepository(apiService, sessionManager)
        farmerRepository = FarmerRepository(apiService, sessionManager, database.farmerDao(), database.surveyCaseDao())
        caseActionsRepository = CaseActionsRepository(
            api = apiService,
            session = sessionManager,
            offlineEventDao = database.offlineEventDao(),
            caseDao = database.surveyCaseDao(),
            moshi = moshi,
        )
        fileUploadApi = FileUploadApi(RetrofitClient.okHttpClient, BuildConfig.API_BASE_URL, moshi)

        UploadWorker.schedulePeriodic(this)
    }
}
