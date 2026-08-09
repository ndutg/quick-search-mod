package com.tk.quicksearch.search.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class IconPackServiceTest {
    @Test
    fun applyingPackClearsOnlyOverridesForAppsExplicitlySupportedByPack() {
        val supportedPackages = setOf("com.example.supported")

        val result =
            findIconOverridesReplacedByPack(
                iconPackPackage = "com.example.iconpack",
                overriddenPackages =
                    setOf(
                        "com.example.supported",
                        "com.example.unsupported",
                    ),
            ) { _, appPackage -> appPackage in supportedPackages }

        assertEquals(setOf("com.example.supported"), result)
    }

    @Test
    fun selectingSystemIconsKeepsAllAppIconOverrides() {
        val result =
            findIconOverridesReplacedByPack(
                iconPackPackage = null,
                overriddenPackages = setOf("com.example.app"),
            ) { _, _ -> true }

        assertEquals(emptySet<String>(), result)
    }
}
