# Quick Search performance investigation

Date: 2026-08-08  
Investigated revision: `82f3913ee` (`main`) plus the focused changes described below

## Executive summary

Most of the measured interactive slowdown came from the primary app-search hot path, not startup I/O, wallpaper decoding, the GPU, or secondary providers:

1. Primary app results were deliberately held for 150 ms before ranking began.
2. Fuzzy app ranking repeated policy resolution and typo-distance work for every candidate.
3. Search UI recomposition resolved the default browser through `PackageManager` for ordinary non-URL keystrokes.
4. Returning to Quick Search synchronously queried `CalendarProvider` on the main thread and could issue the same calendar refresh twice.

The fixes reduce the primary app debounce to 50 ms, prepare fuzzy policy once per query, avoid the duplicated typo check, restrict default-browser resolution to URL handling, and coalesce resume calendar refreshes on `Dispatchers.IO`.

Under the same aggressive production Release typing/deleting workload, jank fell from **41.63% to 30.05–31.54%** across three post-change trials. The median post-change result was 30.26%, a reduction of 11.37 percentage points or 27.3% relative. Under the same 20-return workload, jank fell from **4.65% to 3.24%**, slow-UI frames fell from 45 to 32, and p99 fell from 21 ms to 14 ms.

The most important remaining issue is still the CPU/UI-thread cost of rendering and recomposing search results during deliberately faster-than-human input. It remains a P1 because the post-change workload still produces approximately 30–32% deadline misses at a 120 Hz deadline. The exact remaining Compose invalidation source was not isolated sufficiently to justify a broad state/UI refactor in this investigation.

## Environment

| Item | Value |
|---|---|
| Device | Samsung SM-S938U1 (Galaxy S25 Ultra) |
| Android | Android 16, API 36 |
| Display cadence observed in traces | 120 Hz, approximately 8.33 ms frame deadlines |
| Production package | `com.tk.quicksearch`, Standard Release, version 3.9 (69) |
| Verification package | `com.tk.quicksearch.debug`, Standard Debug |
| App dataset | 203 searchable app candidates in debug fuzzy-search logs; existing user data and permissions were preserved |
| Baseline battery/temperature | About 79%, approximately 31°C |
| Later startup rerun temperature | 34.9–35.2°C after builds/profiling; treated as thermally invalid for regression comparison |
| Baseline profile status | `status=verify`, `reason=install`; the sideloaded Release was not profile-compiled |
| Repository state at start | Clean worktree; no user changes to preserve |

No app data was cleared. No preference, personal-data, permission, or dataset reset was performed. The updated Release APK was installed with `adb install -r`, preserving production app data.

## Methodology and evidence boundaries

### Runtime methods

- `am force-stop` plus `am start -W` for cold Standard and overlay launches, 12 repetitions each.
- `atrace` using the existing `QS.Startup.*` trace sections for first draw, readiness markers, startup phases, Compose work, and AndroidX Startup.
- `dumpsys gfxinfo ... reset` plus fixed ADB input sequences for fast typing, deletion, replacement, external-app returns, overlay interaction, and settings navigation.
- Existing debug-only `FuzzySearchPerformanceLogger` for app/secondary search compute time.
- A focused `simpleperf --app` run. It confirmed UI/render/GC activity but did not symbolize enough app Kotlin/JIT frames to assign method-level cost, so it is not used for hotspot attribution.
- A direct-launch marker followed by an input tap and `ActivityTaskManager` logs. This provides only an upper bound because ADB injection overhead is included.

### Static audit

The following paths were traced end to end:

- `MainActivity`, `StartupCoordinator`, `SearchStartupCoordinator`, startup preference/cache phases, and post-draw fan-out.
- `SearchQueryCoordinator`, `AppSearchAlgorithm`, fuzzy strategy/engine, and `SecondarySearchOrchestrator`.
- `SearchRoute`, aggregate `SearchUiState`, `SearchScreenContent`, result rendering, settings destinations, and overlay rendering.
- `AppsRepository`, `LauncherApps`, direct app launch, browser target resolution, icon/wallpaper loading, SharedPreferences, Calendar/Contacts/Files providers, and Room-backed notes.

### Important limitations

- `am start -W` reports Android launch completion, not keyboard readiness or useful-interaction readiness.
- ADB input is intentionally harsher than human typing and adds input-command overhead. Its absolute jank rate is not a claim about ordinary typing; identical before/after workloads make it useful for comparison.
- Warm startup could not be isolated reliably. Quick Search's single-task/overlay behavior kept bringing an existing activity/task to the front (`HOT` or `UNKNOWN`) instead of recreating an activity in a live process. No app/task state was destructively altered to manufacture a warm launch.
- The post-change cold-start rerun was 4°C warmer than baseline. Those results are recorded but are not used to claim an improvement or regression.
- Wallpaper/custom-image preferences were not mutated. The persisted configuration was exercised, and both wallpaper/custom decoding paths were audited statically.
- No slower physical device or artificially enlarged contacts/files/calendar/notes/history dataset was available. Scaling conclusions for those cases remain code-backed rather than measured.
- Network-backed AI/weather/web tools were not invoked because they were not part of the measured local query flow and would introduce network variance.

## Performance infrastructure audit

The current app still has useful production trace markers:

- `QS.Startup.MainActivity.OnCreate`
- `QS.Startup.MainActivity.SetContent`
- `QS.Startup.MainActivity.SearchSurfaceFirstCompose`
- `QS.Startup.FirstDraw`
- `QS.Startup.FirstFrameGate`
- `QS.Startup.CoreSurface.Ready`
- `QS.Startup.WallpaperPreview.Ready`
- `QS.Startup.Suggestions.Ready`
- `QS.Startup.Phase1.CachePrefs`
- `QS.Startup.Phase2.HeavyInit`
- `QS.Startup.Phase3.DeferredInit`
- permission sync and background preload markers

`StartupSla.kt` defines a 16 ms search-bar first-frame target, 250 ms phase-1 target, and 2,000 ms phase-2 target. `app/src/main/baseline-prof.txt` exists with 139 rules.

The source benchmark module is no longer present. Commit `dfae19ed1` removed `StartupBenchmark.kt`, `BaselineProfileGenerator.kt`, the benchmark module, and its configuration. Stale benchmark build artifacts are not valid evidence for the current source. Consequently, current `StartupTimingMetric`, `FrameTimingMetric`, Baseline Profile, and trace-section Macrobenchmark scenarios cannot be rerun from this checkout. Restoring a small current benchmark module is a P2 engineering-infrastructure recommendation.

## Baseline measurements

### Cold startup

| Flow | Runs | Median | p90 | Range | Notes |
|---|---:|---:|---:|---:|---|
| Standard Release cold launch | 12 | 122 ms | 141 ms | 107–146 ms | Forced normal presentation |
| Overlay Release cold launch | 12 | 119.5 ms | 137 ms | 103–139 ms | Main entry routed to overlay |

One focused Standard trace placed the startup markers approximately as follows, relative to `MainActivity.onCreate`:

| Marker | Approximate relative time |
|---|---:|
| `SetContent` | 3.8 ms |
| `FirstDraw` | 56.4 ms |
| Search surface / wallpaper preview / suggestions / core surface ready | 69 ms |
| `FirstFrameGate` | 93 ms |
| Phase 1 starts | 93 ms |
| Permission sync | 344 ms |
| Phase 2 starts | 468 ms |
| Phase 3 starts | 479 ms |

The 350 ms gap before phase 2 is intentional (`KeyboardStartupQuietWindowMs`) and protects initial keyboard/input responsiveness. A separate trace showed an approximately 21 ms initial Compose recompose at 120 Hz. Trace overhead means that number is diagnostic rather than a benchmark percentile.

AndroidX Startup work, including WorkManager/ProfileInstaller initialization, was approximately 2 ms in the trace and was not a dominant bottleneck. Wallpaper and custom image decoding already use `Dispatchers.IO` and memory caching. App/package reconciliation is deferred/off-main after cached startup data is available.

### Hot return

Twelve baseline returns after showing Android Settings completed in 39–47 ms, median 42.5 ms and p90 45 ms. A separate 20-return frame workload produced:

- 989 rendered frames
- 46 janky frames, 4.65%
- p50 7 ms, p90 9 ms, p95 12 ms, p99 21 ms
- 45 slow-UI frames and 6 missed-vsync events

### Search compute and input rendering

Representative pre-change debug app-search samples over 203 candidates were:

- typo-like length 4: 52.90 ms
- length 6: 36.86 ms
- length 3: 15.95 ms

Other secondary debug samples were typically much smaller for the same queries: Settings approximately 0.7–12.3 ms, app shortcuts approximately 2.4–17.3 ms, and app settings approximately 2.9–19.9 ms. These providers intentionally remained on a separate 150 ms debounce.

The pre-change primary app path always waited 150 ms before app ranking. Therefore useful app results could not arrive before 150 ms plus worker scheduling and ranking, even when the cached app list was ready.

The fixed five-round Release workload (`settngs`, delete, `youtube`, delete) produced:

- 209 rendered frames
- 87 janky frames, 41.63%
- p50 11 ms, p90 18 ms, p95 21 ms, p99 26 ms
- 82 slow-UI frames, 1 missed-vsync event
- GPU p90 3 ms

The low GPU time and high slow-UI count point to CPU/UI-thread/recomposition pressure rather than fill-rate or shader cost.

### Settings, app launch, and overlay

- A preliminary open-settings/back workload produced 7.08% jank, p90 14 ms, and p99 73 ms, but it did not reliably traverse every requested detail page; it is retained only as a noisy baseline indicator.
- Direct YouTube result handoff reached `ActivityTaskManager START` 79 ms after an ADB log marker. This is an upper bound that includes the marker-to-input-command gap. Quick Search's configured overlay immediately resumed, so destination display time was not isolated.
- Direct primary-profile app launching uses the stored component and `Context.startActivity`; it only falls back to `getLaunchIntentForPackage` when the component is absent. Work-profile launches correctly use `LauncherApps`.

## Changes made

### 1. Primary results no longer wait as long as secondary providers

`SearchViewModel.kt` changes `APP_SEARCH_DEBOUNCE_MS` from 150 ms to 50 ms. Query-version checks and job cancellation remain unchanged in `SearchQueryCoordinator.kt`; stale results still cannot overwrite a newer query. The secondary-provider debounce remains 150 ms.

Expected direct latency reduction: 100 ms before ranking begins. This is code-exact, while full query-to-visible time still includes input dispatch, ranking, state delivery, and rendering.

### 2. Fuzzy app queries prepare shared policy once and do not repeat typo eligibility

`AppSearchAlgorithm.kt` now prepares the effective fuzzy policy once per query and passes it through candidate evaluation. `FuzzyAppSearchStrategy.kt` separates eligibility from scoring so the edit-distance tolerance check is not repeated immediately after an eligible candidate passes it.

Ranking thresholds, scores, candidate limits, nickname/initial matching, sort order, result limits, and low-RAM/fuzzy-disabled behavior are preserved. Focused `AppSearchAlgorithmTest` and `AppSearchManagerNoMatchPrefixTest` passed.

Post-change debug app-search compute across 25 samples on the same 203-app dataset was:

- median 14.56 ms
- mean 14.97 ms
- p90 23.68 ms
- range 5.65–25.80 ms

The pre-change samples were not collected as the same 25-sample distribution, so this compute comparison is supportive rather than a strict benchmark. The production frame workload below is the strict same-workload comparison.

### 3. Ordinary typing no longer resolves the default browser

`SearchScreenContent.kt` previously called `resolveDefaultBrowserPackage(context)` from predicted-target recomputation while the IME was visible, including blank and ordinary app queries. The resolver performs a `PackageManager.resolveActivity` binder call.

It now resolves the default browser only when the query is URL-like. Explicit submission likewise performs the lookup only inside the URL branch. URL routing behavior is unchanged; ordinary app, shortcut, contact, file, settings, calendar, notes, and search-engine predictions no longer pay for that binder call.

### 4. Calendar refresh on resume is coalesced and moved off main

`SearchRoute` invokes `handleOnResume` on the lifecycle callback. The old path called `loadPinnedAndExcludedCalendarEvents()` synchronously and could call it once for an optional-permission change and again in the unconditional startup-complete block. That function performs SharedPreferences reads and synchronous `CalendarProvider` queries for pinned, excluded, and today's events.

The resume path now owns one cancellable refresh job on `Dispatchers.IO`, removing the duplicate call and main-thread provider work. Permission behavior and final state updates are unchanged.

## Before/after results

### Identical Standard Release fast typing/deleting workload

| Metric | Before, 1 trial | After trial 1 | After trial 2 | After trial 3 | Post median |
|---|---:|---:|---:|---:|---:|
| Frames | 209 | 213 | 228 | 241 | 228 |
| Janky frames | 87 | 64 | 69 | 76 | 69 |
| Jank rate | 41.63% | 30.05% | 30.26% | 31.54% | 30.26% |
| p50 | 11 ms | 7 ms | 9 ms | 8 ms | 8 ms |
| p90 | 18 ms | 15 ms | 17 ms | 20 ms | 17 ms |
| p95 | 21 ms | 18 ms | 21 ms | 23 ms | 21 ms |
| p99 | 26 ms | 23 ms | 32 ms | 34 ms | 32 ms |
| Missed vsync | 1 | 1 | 0 | 0 | 0 |
| Slow-UI frames | 82 | 63 | 67 | 75 | 67 |

The median jank-rate improvement is 11.37 percentage points, or 27.3% relative. p50 and median p90 improved. p95 was unchanged at the median. p99 was noisy and regressed in two of three trials; this prevents claiming that all tail latency is solved.

### Identical 20-return workload

| Metric | Before | After | Change |
|---|---:|---:|---:|
| Jank rate | 4.65% | 3.24% | -1.41 points, -30.3% relative |
| Janky frames | 46 | 32 | -14 |
| p50 | 7 ms | 6 ms | -1 ms |
| p90 | 9 ms | 8 ms | -1 ms |
| p95 | 12 ms | 9 ms | -3 ms |
| p99 | 21 ms | 14 ms | -7 ms |
| Slow-UI frames | 45 | 32 | -13 |
| Missed vsync | 6 | 2 | -4 |

This comparison supports the resume-calendar fix. It does not isolate calendar work from normal frame variance, but it uses the same device, package, data, command sequence, return count, and 200 ms spacing.

### Current overlay interaction

The same five-round typing/deleting workload in overlay mode produced 241 frames, 31.54% jank, p50 8 ms, p90 18 ms, p95 21 ms, p99 32 ms, one missed-vsync event, and 74 slow-UI frames. Overlay therefore benefits from the shared search fixes but retains the same CPU/UI rendering pressure as Standard presentation.

### Current settings detail navigation

Five rounds across Appearance, Search Results, Search Engines, and Tools (20 opens plus 20 backs) produced:

- 983 frames
- 49 janky frames, 4.98%
- p50 9 ms, p90 11 ms, p95 22 ms, p99 32 ms
- no missed-vsync events
- 48 slow-UI frames

No settings implementation changed, so this is current-state coverage rather than a before/after claim.

### Cold startup regression check

After the changes, 12 observed Standard cold starts were 107–170 ms, median 131 ms and p90 163 ms. Twelve overlay cold starts were 124–161 ms, median 141 ms and p90 161 ms. The device was 34.9°C versus approximately 31°C at baseline after sustained builds/profiling, and the Release had just been reinstalled with `verify` compilation.

These observations are slower than baseline but are **not a valid same-condition comparison**. None of the changed ranking code runs for an empty query; the browser change removes work from the blank-query compose path; and the resume calendar job is gated on startup completion. A thermally controlled Macrobenchmark is required before accepting or rejecting a startup regression.

## Ranked findings

### P0 — Primary app-result latency and fuzzy candidate duplication — fixed

**Affected flow:** exact/prefix/contains, typo, acronym, nickname, fuzzy, and continuous app search.

**Evidence:** code-exact 150 ms pre-ranking wait; representative 16–53 ms debug app ranking over 203 candidates; 41.63% jank in the aggressive Release input workload; duplicated per-candidate policy and edit-distance work in the fuzzy strategy.

**Root cause:** primary cached app results shared an overly conservative delay with expensive secondary providers, then repeated query-invariant and eligibility work per candidate.

**Impact:** every nonempty app query was delayed; typo/fuzzy queries paid the most CPU cost; faster typing could overlap repeated search/result recomposition.

**Fix:** 50 ms primary-only debounce plus prepared fuzzy query policy and single eligibility check. Query versions, final ranking, and provider separation remain intact.

**Measured impact:** 27.3% median relative reduction in production input-workload jank; post-change app fuzzy compute median 14.56 ms and p90 23.68 ms across 25 samples.

### P1 — PackageManager binder lookup in ordinary query recomposition — fixed

**Affected flow:** empty query with IME visible, ordinary typing, predicted top result, and non-URL submit.

**Evidence:** direct code path from `SearchScreenContent` recomputation to `resolveDefaultBrowserPackage`, which calls `PackageManager.resolveActivity`. The combined production typing workload improved materially after removal from the hot path.

**Root cause:** URL-specific routing metadata was resolved before the code knew that it had a URL.

**Impact:** main-thread binder work and allocation during a latency-sensitive Compose recomposition, multiplied by keystrokes/result updates.

**Fix:** perform browser resolution only in URL-like predicted/submission branches.

**Proof boundary:** the combined frame comparison supports the change; an isolated binder-duration distribution was not obtained.

### P1 — Main-thread and duplicate CalendarProvider work on return — fixed

**Affected flow:** returning after another app, permission changes, and any resume after startup completes.

**Evidence:** lifecycle callback ran the synchronous calendar loader; the loader issues up to three provider queries and preference reads; the permission-change branch could invoke it twice. The identical return workload improved from 4.65% to 3.24% jank and p99 21 ms to 14 ms.

**Root cause:** provider I/O was executed inline from the main lifecycle callback without coalescing.

**Impact:** return-animation/input jank, worse on providers with more event instances or slower storage.

**Fix:** one cancellable `Dispatchers.IO` resume refresh.

### P1 — Remaining search render/recomposition CPU pressure — open

**Affected flow:** fast typing, deletion, replacement, Standard and overlay search rendering.

**Evidence:** post-change Release still misses 30.05–31.54% of frame deadlines in the aggressive workload; slow-UI frames remain 63–75; GPU p90 was only 3 ms in baseline; overlay shows the same pattern. `SearchRoute` collects the aggregate 70+ field `SearchUiState`, and `SearchScreenContent` derives many query/result-dependent values.

**Root cause:** not isolated. The strongest code-backed candidate is broad Compose invalidation/derived work after query and result updates, potentially combined with icon/result layout. The existing four internal state flows reduce copying, but the route recombines them into one aggregate UI flow.

**Recommended next step:** restore a benchmarkable build and add Compose recomposition/trace counters around `SearchScreenContent`, result sections, and icon rows. Split UI collection only after a trace shows which consumers recompose unnecessarily. Do not broadly rewrite state architecture from the current evidence.

**Expected impact:** potentially meaningful, but unmeasured. The p99 regression/noise means tail work deserves priority.

### P2 — Startup benchmark and Baseline Profile verification gap — open

**Evidence:** production trace markers and 139 profile rules exist, but `StartupBenchmark`, `BaselineProfileGenerator`, and the benchmark module were removed. The sideloaded Release reports `verify`, not profile compilation.

**Impact:** current cold/warm/hot startup distributions and real profile coverage cannot be validated reproducibly in CI/local source. This is a measurement/delivery risk, not proof that end users lack profile compilation through their installer.

**Recommendation:** restore a minimal Macrobenchmark module with cold/warm/hot Standard and overlay scenarios, `StartupTimingMetric`, `FrameTimingMetric`, `TraceSectionMetric`, and realistic baseline-profile journeys covering launch, typing, one fuzzy query, settings, and app launch.

### P2 — Initial Compose frame exceeds a 120 Hz deadline in trace — open

**Evidence:** one trace showed an approximately 21 ms initial recompose, while the device deadline was approximately 8.33 ms. `FirstDraw` appeared about 56 ms after `onCreate`; useful surface readiness markers appeared at about 69 ms.

**Impact:** first-frame animation smoothness and keyboard presentation, especially on slower devices.

**Recommendation:** benchmark under controlled compilation/thermal state before changing UI. Trace the first composition by subtree and preserve the existing post-draw startup gate and 350 ms keyboard quiet window.

### P2 — Explicit full GC when the last activity is destroyed — open, low-confidence

`MainActivity.onDestroy` clears bitmap caches and calls `Runtime.getRuntime().gc()` when the last tracked activity is destroyed. The comment documents a memory-accumulation rationale, and no GC pause was tied to a measured visible frame in this investigation.

The call may affect a true warm relaunch if GC overlaps activity recreation, but warm startup could not be isolated. Do not remove it without a memory/relaunch experiment comparing retained heap, GC pause, and repeated HOME launches.

### P2 — Synchronous wrapper around Room notes store can block callers — open, code-backed

`NotesRoomStore` serializes work on a dedicated executor but exposes synchronous methods using `submit(...).get()`. Search calls are currently routed through I/O, so it was not a measured typing bottleneck. A future or settings main-thread caller can still block on database initialization, migration, or a large notes query.

Recommendation: audit every call site and migrate UI-facing APIs to suspend functions only if a main-thread call is confirmed. No change was made because the current measured flows did not implicate notes.

### P3 — Recomputed app initials and other small per-candidate allocations — not changed

`AppSearchInitials.initialsFor` and some normalization/alternate-name construction still run per candidate/query. They are credible micro-optimizations, but after removing duplicated fuzzy work the compute distribution was already 14.56 ms median on 203 apps. Caching requires invalidation/memory policy and was not justified ahead of the remaining UI/render P1.

## Other audited areas with no dominant issue found

- **Empty query:** cached pinned/recent app state is used; app/package reconciliation is not required before first draw.
- **Secondary results:** remain debounced at 150 ms, run on I/O/background dispatchers, and retain query-version checks. Allowing these nonessential providers to arrive later protects input.
- **Apps/PackageManager scanning:** `LauncherApps.getActivityList`, legacy intent queries, and optional installed-app scans exist in `AppsRepository`, but startup uses cache-first/deferred reconciliation. No scan appeared as a first-frame dominant section.
- **Wallpaper/custom image:** decodes and appearance analysis are suspend/off-main with mutexed memory caches. No main-thread bitmap decode was found in the measured overlay path.
- **Icons:** app and shortcut icon loading uses asynchronous/IO paths and caching. Bitmap-upload counts were zero or one in the compared workloads.
- **Preferences:** startup snapshots and per-query preference prefetching already avoid repeated full `SharedPreferences.getAll()` work in the hot query path.
- **Files/contacts/calendar secondary search:** provider queries are background/debounced. Dataset scaling was not measured beyond the current device data.
- **Network tools:** not active in the local searches tested; no irrelevant network or bundle-loading work was manufactured.
- **App launch:** the normal primary-profile component path does not scan all packages. Work-profile activity-list lookup is necessary for profile-correct launching.
- **Settings:** the measured current detail traversal is generally healthy; no broad settings refactor is supported by the frame data.

## Regression and correctness checks

- Ranking and fuzzy behavior: focused app-search unit tests passed.
- Stale query protection: existing version checks before and after ranking remain unchanged.
- Secondary provider timing: still 150 ms and independent from the 50 ms primary app path.
- Permission degradation and calendar results: permission checks and empty-state behavior remain; only resume execution context/coalescing changed.
- URL/default-browser behavior: unchanged for URL-like queries; non-URL queries no longer resolve a browser they do not use.
- Overlay path: shared search workload rerun successfully.
- Settings navigation: four representative detail pages repeatedly opened and returned successfully.
- Standard Release: built and installed over the existing package with data preserved.
- Required Standard Debug command: completed successfully after the final code changes; build, streamed install, force-stops, and cold launch all succeeded.
- Focused tests: `AppSearchAlgorithmTest` and `AppSearchManagerNoMatchPrefixTest` passed.
- `git diff --check`: clean.
- No broad UI suite, data clear, commit, push, or staging operation was performed.

Build/install/launch success is listed only as functional validation, not performance proof.

## Remaining P0/P1 issues

- No remaining P0 was proven in the investigated device/dataset.
- P1 remains: CPU/UI-thread result rendering and Compose recomposition during fast query churn. It is measured, but its exact subtree/root cause is not yet isolated enough for a safe architectural change.

## Recommended next steps

1. Restore the current-source Macrobenchmark/Baseline Profile module and run controlled cold, warm, hot, overlay, input, settings, and app-launch scenarios with temperature/compilation recorded.
2. Add focused Compose trace/recomposition instrumentation for `SearchRoute`, `SearchScreenContent`, result sections, and icon rows. Use it to isolate the remaining 30–32% stress-test deadline misses.
3. Generate and validate a Baseline Profile that includes actual search/fuzzy/settings paths, then compare `None`, profile, and full compilation modes.
4. Repeat on a 60 Hz midrange/low-RAM device and with intentionally large contacts/files/calendar/notes/history datasets. Record primary-result latency separately from each secondary provider's completion.
5. Run a dedicated repeated HOME/warm-recreation memory experiment before changing the explicit GC behavior.

## Final conclusion

The few issues responsible for most measured latency were a too-long primary app debounce, duplicated fuzzy candidate work, URL-specific PackageManager work on ordinary keystrokes, and synchronous duplicate calendar refreshes on return. Focused fixes produced a measurable 27.3% median relative reduction in the aggressive typing jank rate and a 30.3% relative reduction in return-path jank without changing final ranking, result limits, query-version safety, permissions, or URL/app-launch semantics. The next meaningful target is the still-measured Compose/UI-thread cost during query churn; startup and database micro-optimizations should wait for restored controlled benchmarks and stronger attribution.
