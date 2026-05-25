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
        repository = SettingsRepository(store, scope)
    }

    @After
    fun tearDown() {
        // TestScope cleans itself; DataStore file lives in tempFolder.
    }

    @Test
    fun `default settings are emitted before any writes`() = runTest {
        repository.settings.test {
            val first = awaitItem()
            assertEquals("", first.url)
            assertEquals(SortOrder.Default, first.sortOrder)
            assertEquals(ThemeOverride.System, first.themeOverride)
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
    fun `setSortOrder updates settings flow`() = runTest {
        repository.settings.test {
            assertEquals(SortOrder.Default, awaitItem().sortOrder)
            repository.setSortOrder(SortOrder.Alphabetical)
            assertEquals(SortOrder.Alphabetical, awaitItem().sortOrder)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setThemeOverride updates settings flow`() = runTest {
        repository.settings.test {
            assertEquals(ThemeOverride.System, awaitItem().themeOverride)
            repository.setThemeOverride(ThemeOverride.Dark)
            assertEquals(ThemeOverride.Dark, awaitItem().themeOverride)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
