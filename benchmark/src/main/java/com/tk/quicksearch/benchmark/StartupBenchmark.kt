package com.tk.quicksearch.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures all supported startup entry points. Pass `-PprofileCapture=true` when running this
 * suite so the otherwise-private overlay activity can be launched directly by the benchmark APK.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun launcherCold_withBaselineProfile() =
        measureStartup(
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        ) { launchFromAppIcon() }

    @Test
    fun launcherCold_withoutCompilation() =
        measureStartup(
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.None(),
        ) { launchFromAppIcon() }

    @Test
    fun launcherWarm_withBaselineProfile() =
        measureStartup(
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        ) { launchFromAppIcon() }

    @Test
    fun launcherHot_withBaselineProfile() =
        measureStartup(
            startupMode = StartupMode.HOT,
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        ) { launchFromAppIcon() }

    @Test
    fun homeCold_withBaselineProfile() =
        measureStartup(
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        ) { launchAsHome() }

    @Test
    fun overlayCold_withBaselineProfile() =
        measureStartup(
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        ) { launchOverlay() }

    @OptIn(ExperimentalMetricApi::class)
    private fun measureStartup(
        startupMode: StartupMode,
        compilationMode: CompilationMode,
        setup: (androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit)? = null,
        launch: androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit,
    ) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics =
                listOf(
                    StartupTimingMetric(),
                    FrameTimingMetric(),
                    TraceSectionMetric("QS.Startup.MainActivity.OnCreate"),
                    TraceSectionMetric("QS.Startup.MainActivity.SetContent"),
                    TraceSectionMetric("QS.Startup.MainActivity.SearchSurfaceFirstCompose"),
                    TraceSectionMetric("QS.Startup.OverlayActivity.OnCreate"),
                    TraceSectionMetric("QS.Startup.FirstFrameGate"),
                    TraceSectionMetric("QS.Startup.CoreSurface.Ready"),
                    TraceSectionMetric("QS.Startup.Phase1.CachePrefs"),
                    TraceSectionMetric("QS.Startup.Phase2.HeavyInit"),
                    TraceSectionMetric("QS.Startup.Phase3.DeferredInit"),
                ),
            iterations = ITERATIONS,
            startupMode = startupMode,
            compilationMode = compilationMode,
            setupBlock = {
                pressHome()
                setup?.invoke(this)
            },
            measureBlock = launch,
        )
    }

    private companion object {
        const val ITERATIONS = 8
    }
}
