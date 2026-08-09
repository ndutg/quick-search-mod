package com.tk.quicksearch.search.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tk.quicksearch.search.models.AppInfo
import java.io.DataOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCachePersistenceTest {
    private lateinit var context: Context
    private lateinit var cache: AppCache
    private lateinit var originalPreferences: Map<String, *>
    private var originalCacheBytes: ByteArray? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalPreferences =
            context.getSharedPreferences("app_cache", Context.MODE_PRIVATE).all.toMap()
        originalCacheBytes =
            context.filesDir.resolve("app_cache_v1.bin").takeIf { it.exists() }?.readBytes()
        cache = AppCache(context)
        cache.clearCache()
    }

    @After
    fun tearDown() {
        cache.clearCache()
        context.getSharedPreferences("app_cache", Context.MODE_PRIVATE).edit().apply {
            originalPreferences.forEach { (key, value) -> putPreference(key, value) }
        }.commit()
        originalCacheBytes?.let { context.filesDir.resolve("app_cache_v1.bin").writeBytes(it) }
    }

    @Test
    fun usageMetadataSavePreservesInvalidationAndRemovedAppTombstone() {
        val removed = app("com.example.removed")
        val installed = app("com.example.installed")
        cache.recordCatalogChange(
            change = AppCatalogChange(removed.packageName, userHandleId = 0, isRemoval = true),
            currentUserHandleId = 0,
            commitSynchronously = true,
        )

        assertTrue(cache.saveApps(listOf(removed, installed), catalogReconciled = false))

        assertTrue(cache.isCatalogInvalidated())
        assertEquals(setOf(removed.launchCountKey()), cache.removedAppKeys())
        assertEquals(listOf(installed), cache.loadCachedApps())
    }

    @Test
    fun fullCatalogReconciliationClearsInvalidationAndTombstones() {
        val app = app("com.example.restored")
        cache.recordCatalogChange(
            change = AppCatalogChange(app.packageName, userHandleId = 0, isRemoval = true),
            currentUserHandleId = 0,
            commitSynchronously = true,
        )

        assertTrue(cache.saveApps(listOf(app), catalogReconciled = true))

        assertFalse(cache.isCatalogInvalidated())
        assertTrue(cache.removedAppKeys().isEmpty())
        assertEquals(listOf(app), cache.loadCachedApps())
        assertTrue(cache.getLastUpdateTime() > 0L)
    }

    @Test
    fun packageAdditionOnlyRemovesTheMatchingProfileTombstone() {
        val personal = app("com.example.shared")
        val work = app("com.example.shared", userHandleId = 10)
        cache.recordUnavailableApps(listOf(personal, work))

        cache.recordCatalogChange(
            change = AppCatalogChange(personal.packageName, userHandleId = 10, isRemoval = false),
            currentUserHandleId = 0,
            commitSynchronously = true,
        )

        assertEquals(setOf(personal.launchCountKey()), cache.removedAppKeys())
        assertTrue(cache.isCatalogInvalidated())
    }

    @Test
    fun corruptBinaryCacheFailsClosed() {
        context.filesDir.resolve("app_cache_v1.bin").writeBytes(byteArrayOf(0, 0, 0, 3, 0, 0, 0, 1))

        assertEquals(null, cache.loadCachedApps())
    }

    @Test
    fun versionOneBinaryCacheUsesLegacyDefaults() {
        writeLegacyCache(version = 1, lastUpdateTime = null)

        val loaded = cache.loadCachedApps().orEmpty().single()

        assertTrue(loaded.hasLaunchIntent)
        assertEquals(2L, loaded.lastUpdateTime)
        assertEquals(10, loaded.userHandleId)
        assertEquals("com.example.legacy/.Main", loaded.componentName)
    }

    @Test
    fun versionTwoBinaryCacheRestoresPersistedLastUpdateTime() {
        writeLegacyCache(version = 2, lastUpdateTime = 99L)

        val loaded = cache.loadCachedApps().orEmpty().single()

        assertTrue(loaded.hasLaunchIntent)
        assertEquals(99L, loaded.lastUpdateTime)
    }

    private fun writeLegacyCache(
        version: Int,
        lastUpdateTime: Long?,
    ) {
        DataOutputStream(context.filesDir.resolve("app_cache_v1.bin").outputStream()).use { output ->
            output.writeInt(version)
            output.writeInt(1)
            output.writeUTF("Legacy")
            output.writeUTF("com.example.legacy")
            output.writeLong(5L)
            output.writeLong(4L)
            output.writeInt(3)
            output.writeLong(2L)
            output.writeBoolean(false)
            output.writeBoolean(true)
            output.writeInt(10)
            output.writeBoolean(true)
            output.writeUTF("com.example.legacy/.Main")
            lastUpdateTime?.let(output::writeLong)
        }
    }

    private fun app(
        packageName: String,
        userHandleId: Int? = null,
    ) =
        AppInfo(
            appName = packageName,
            packageName = packageName,
            lastUsedTime = 5L,
            totalTimeInForeground = 4L,
            launchCount = 3,
            firstInstallTime = 2L,
            lastUpdateTime = 6L,
            isSystemApp = false,
            hasLaunchIntent = true,
            userHandleId = userHandleId,
            componentName = "$packageName/.Main",
        )

    private fun android.content.SharedPreferences.Editor.putPreference(
        key: String,
        value: Any?,
    ) {
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
}
