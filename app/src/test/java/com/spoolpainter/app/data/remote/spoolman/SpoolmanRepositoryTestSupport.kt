package com.spoolpainter.app.data.remote.spoolman

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.google.gson.Gson
import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.data.local.SettingsRepositoryImpl
import com.spoolpainter.app.data.local.SettingsSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.OkHttpClient
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryHarness(
    tempFolder: TemporaryFolder,
    private val initialUrl: String = "http://test.local/",
) {
    val fakeApi: FakeSpoolmanApi = FakeSpoolmanApi()
    private val factory: SpoolmanApiFactory = object : SpoolmanApiFactory(OkHttpClient(), Gson()) {
        override fun create(baseUrl: String): SpoolmanApi = fakeApi
    }

    private val dataStoreFile: File = tempFolder.newFile("settings.json").also { it.delete() }
    val scope: CoroutineScope = TestScope(UnconfinedTestDispatcher())
    private val store: DataStore<Settings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        scope = scope,
    ) { dataStoreFile }
    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(store, scope)

    val repository: SpoolmanRepository

    init {
        if (initialUrl.isNotBlank()) {
            kotlinx.coroutines.runBlocking { settingsRepository.setUrl(initialUrl) }
        }
        repository = SpoolmanRepository(
            settings = settingsRepository,
            apiFactory = factory,
            scope = scope,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        // Allow the URL collector to run synchronously under UnconfinedTestDispatcher.
    }
}
