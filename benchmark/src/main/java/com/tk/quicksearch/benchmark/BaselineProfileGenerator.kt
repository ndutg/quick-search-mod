package com.tk.quicksearch.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates profiles from the distinct entry points that users encounter at startup.
 *
 * Run the following on a configured API 28+ device:
 * `./gradlew :app:generateStandardReleaseBaselineProfile -PprofileCapture=true`
 * `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile`.
 * Keeping startup and broader critical-user-journey collection separate prevents non-startup
 * settings code from being marked for startup DEX layout.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun launcherStartupAndImmediateSearch() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = MAX_PROFILE_ITERATIONS,
            stableIterations = STABLE_PROFILE_ITERATIONS,
            includeInStartupProfile = true,
            filterPredicate = ::isQuickSearchProfileRule,
        ) {
            killProcess()
            pressHome()
            launchFromAppIcon(initialQuery = "settings")
        }
    }

    @Test
    fun homeStartupAndImmediateSearch() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = MAX_PROFILE_ITERATIONS,
            stableIterations = STABLE_PROFILE_ITERATIONS,
            includeInStartupProfile = true,
            filterPredicate = ::isQuickSearchProfileRule,
        ) {
            killProcess()
            pressHome()
            launchAsHome(initialQuery = "settings")
        }
    }

    @Test
    fun overlayStartupAndImmediateSearch() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = MAX_PROFILE_ITERATIONS,
            stableIterations = STABLE_PROFILE_ITERATIONS,
            includeInStartupProfile = true,
            filterPredicate = ::isQuickSearchProfileRule,
        ) {
            killProcess()
            pressHome()
            launchOverlay(initialQuery = "settings")
        }
    }

    @Test
    fun criticalSearchAndSettingsJourneys() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = MAX_PROFILE_ITERATIONS,
            stableIterations = STABLE_PROFILE_ITERATIONS,
            includeInStartupProfile = false,
            filterPredicate = ::isQuickSearchProfileRule,
        ) {
            killProcess()
            pressHome()
            launchFromAppIcon(initialQuery = "settings")

            pressHome()
            launchWithInitialQuery("settngs")

            pressHome()
            launchWithInitialQuery("2+2")

            pressHome()
            launchSettings()
        }
    }

    private companion object {
        const val MAX_PROFILE_ITERATIONS = 5
        const val STABLE_PROFILE_ITERATIONS = 2
    }
}

private fun isQuickSearchProfileRule(rule: String): Boolean =
    rule.trimStart('H', 'S', 'P', 'R').startsWith("Lcom/tk/quicksearch/")
