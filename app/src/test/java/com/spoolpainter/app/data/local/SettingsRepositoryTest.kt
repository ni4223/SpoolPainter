package com.spoolpainter.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStoreFile: File
    private lateinit var store: DataStore<Settings>
    private lateinit var scope: CoroutineScope
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        dataStoreFile = tempFolder.newFile("settings.json").also { it.delete() }
        scope = TestScope(UnconfinedTestDispatcher())
        store = DataStoreFactory.create(
            serializer = SettingsSerializer,
            scope = scope,
        ) { dataStoreFile }
        repository = SettingsRepositoryImpl(store, scope)
    }

    @After
    fun tearDown() {
    }

    @Test
    fun `default settings are emitted before any writes`() = runTest {
        repository.settings.test {
            val first = awaitItem()
            assertEquals("", first.url)
            assertEquals(SpoolSortKey.Id, first.spoolSortKey)
            assertEquals(SortDirection.Desc, first.spoolSortDirection)
            assertEquals(FilamentSortKey.Id, first.filamentSortKey)
            assertEquals(SortDirection.Desc, first.filamentSortDirection)
            assertEquals(ThemeOverride.Light, first.themeOverride)
            assertEquals(Currency.Dollar, first.currency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setUrl updates settings flow`() = runTest {
        repository.settings.test {
            assertEquals("", awaitItem().url)
            repository.setUrl("http://nas.local:7912")
            assertEquals("http://nas.local:7912", awaitItem().url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSpoolSortKey updates settings flow`() = runTest {
        repository.settings.test {
            assertEquals(SpoolSortKey.Id, awaitItem().spoolSortKey)
            repository.setSpoolSortKey(SpoolSortKey.LastUsed)
            assertEquals(SpoolSortKey.LastUsed, awaitItem().spoolSortKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSpoolSortDirection updates settings flow`() = runTest {
        repository.settings.test {
            assertEquals(SortDirection.Desc, awaitItem().spoolSortDirection)
            repository.setSpoolSortDirection(SortDirection.Asc)
            assertEquals(SortDirection.Asc, awaitItem().spoolSortDirection)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFilamentSortKey updates settings flow`() = runTest {
        repository.settings.test {
            assertEquals(FilamentSortKey.Id, awaitItem().filamentSortKey)
            repository.setFilamentSortKey(FilamentSortKey.Brand)
            assertEquals(FilamentSortKey.Brand, awaitItem().filamentSortKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFilamentSortDirection updates settings flow`() = runTest {
        repository.settings.test {
            assertEquals(SortDirection.Desc, awaitItem().filamentSortDirection)
            repository.setFilamentSortDirection(SortDirection.Asc)
            assertEquals(SortDirection.Asc, awaitItem().filamentSortDirection)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setThemeOverride updates settings flow`() = runTest {
        repository.settings.test {
            assertEquals(ThemeOverride.Light, awaitItem().themeOverride)
            repository.setThemeOverride(ThemeOverride.Dark)
            assertEquals(ThemeOverride.Dark, awaitItem().themeOverride)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCurrency updates settings flow through Dollar Euro Generic`() = runTest {
        repository.settings.test {
            assertEquals(Currency.Dollar, awaitItem().currency)
            repository.setCurrency(Currency.Euro)
            assertEquals(Currency.Euro, awaitItem().currency)
            repository.setCurrency(Currency.Generic)
            assertEquals(Currency.Generic, awaitItem().currency)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
