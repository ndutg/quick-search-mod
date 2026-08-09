# Quick Search startup and Home loading investigation

Date: 2026-08-09  
Investigated revision: `0da17753f` (`main`) plus the focused changes described here

## Executive summary

> **Manual-validation correction:** The early app-surface visibility, entrance-animation
> suppression, and phase-1 icon deferral described in the experimental measurements below
> were reverted. On a real launch they produced a fragmented sequence: incomplete icon shells,
> background transitions, keyboard arrival, then staggered apps, engines, and history. The
> measurements are retained as evidence from a rejected experiment, not as the behavior of the
> final implementation.

The investigation found two startup critical-path problems:

1. A valid persisted app-suggestion snapshot was available about 26 ms after `MainActivity.onCreate`, but its visibility state remained `Hidden` until phased initialization completed. The first app grid did not become interactive until a 1,620.5 ms median.
2. The initial lifecycle permission refresh synchronously queried `CalendarProvider` on the main thread before first draw. Full pinned contacts/files/notes, exclusions, calendar, and pinned settings then waited behind a separate 10-second optional-maintenance delay, so the enabled Home surface did not reach valid populated-or-empty state until an 11,208.2 ms median.

The final implementation keeps the provider-side fixes: startup calendar loading is removed from the main lifecycle callback, and provider-backed Home state loads on `Dispatchers.IO` during phased initialization instead of after 10 seconds. It does not expose the persisted app grid before normal Home readiness, bypass its established entrance transition, or deliberately render empty icon shells.

Under the same six-run traced Standard Debug cold-start and first-input workload, the rejected early-rendering experiment measured:

| User-visible milestone | Before median | After median | Change |
|---|---:|---:|---:|
| Search field focused | 337.3 ms | 255.1 ms | -82.2 ms, -24.4% |
| First app grid/labels interactive | 1,620.5 ms | 412.8 ms | -1,207.7 ms, -74.5% |
| Tenth app icon loaded | 1,334.1 ms | 957.2 ms | -376.9 ms, -28.3% |
| First injected character accepted | 615.6 ms | 596.7 ms | -18.9 ms, -3.1% |
| Search history rendered after returning to blank query | 1,694.1 ms | 1,059.6 ms | -634.5 ms, -37.5% |
| All enabled Home sections have valid data or valid empty state | 11,208.2 ms | 1,305.5 ms | -9,902.7 ms, -88.4% |

Android's `am start -W` cold-launch median was effectively unchanged: 788.5 ms before and 788.0 ms after. This is expected because Android's launch-complete signal is not a Home-content-complete signal.

The IME-inset marker reached fully visible state at an 887.8 ms median in that experiment versus 617.9 ms before. More importantly, manual observation showed that the earlier isolated milestones did not produce a coherent startup. Those presentation changes are therefore rejected even though several trace markers moved earlier. The retained provider changes still need a fresh, identical post-revert measurement pass before a final performance delta is claimed.

## Outcome and proof boundary

The experiment improved isolated recognizable-content and provider-readiness markers, but failed manual visual validation. The final implementation prioritizes one coherent Home reveal and retains only provider scheduling changes that do not expose incomplete UI. The percentages below do not describe the final post-revert presentation and do not prove performance for production Release, a slower device, a 60 Hz device, large provider datasets, custom-image mode, or overlay mode.

The before and after distributions are both traced, debuggable, Standard Debug, `run-from-apk` launches with the same preserved app data. They are good same-path diagnostics, but they are not a Macrobenchmark or production startup benchmark. The device also warmed from thermal status 0 at baseline to status 1 by the final run, so small launch-time differences are not treated as causal. The large state-gating differences are supported by trace markers and exact code changes despite the less favorable final thermal state.

Build, install, and launch success is functional proof only. It is not proof of visual correctness, gesture correctness, release performance, or behavior on unmeasured configurations.

## Environment

| Item | Value |
|---|---|
| Device | Samsung SM-S938U1, Galaxy S25 Ultra |
| Android | Android 16, API 36 |
| Display cadence | 120 Hz, approximately 8.33 ms frame deadline |
| Measured package | `com.tk.quicksearch.debug`, Standard Debug 3.9 (69) |
| Compilation state | `run-from-apk` |
| Repository start | Clean `main` at `0da17753f` |
| Baseline thermal | Status 0; battery and skin readings around 33-35°C |
| Final thermal | Status 1; battery 36.7°C and reported skin around 39-40°C after builds and traces |
| Battery | 79% |
| Baseline Profile source | `app/src/main/baseline-prof.txt`, 139 rules |

No app data was cleared. No preferences, launcher default, permissions, pinned items, history, calendar data, or provider data were reset. APK updates used `adb install --user 0 -r`.

### Persisted Home scenario exercised

The current debug configuration uses the system-wallpaper background, automatically opens the keyboard, shows app suggestions and app labels, uses five app columns, and has six pinned apps. Search History is enabled. There were no pinned non-app shortcuts, contacts, files, calendar events, settings, or notes in the inspected persisted state. Calendar permission was granted, but the visible Home screen had no current Today event during the investigation.

This is a useful real configuration for cache, empty-state, app-grid, keyboard, history, and wallpaper behavior. It is not a large contacts/files/calendar/notes dataset.

## Methodology

### Runtime instrumentation

Small `android.os.Trace` milestones were added around actual availability and composition events:

- startup snapshot available;
- app section rendered;
- app grid and labels composed;
- each displayed app icon becoming available;
- grid interaction enabled;
- search field focus;
- first empty-to-nonempty query acceptance;
- keyboard becoming visible from IME insets;
- history, shortcuts, pinned items, exclusions, calendar, and pinned settings reaching valid state;
- all provider-backed enabled Home sections reaching valid state;
- phased and nonessential initialization completion;
- overlay `onCreate` entry.

These are instantaneous milestones. Existing `QS.Startup.Phase*` trace spans cross coroutine suspension points while using thread-scoped synchronous trace sections, so their start timestamps are useful but their apparent durations must not be interpreted as blocking duration.

### Paired workload

Each baseline and final sample used:

1. `am force-stop com.tk.quicksearch.debug`;
2. package-scoped `atrace` with activity, window, view, graphics, input, scheduler, frequency, idle, disk, and binder categories;
3. `am start -W` of `MainActivity` with forced normal presentation;
4. an ADB `q` input approximately 200 ms after `am start -W` returned;
5. deletion approximately 150 ms later to return to the blank-query Home surface;
6. enough observation time to see all Home readiness milestones.

Six cold samples were used before and six cold samples after. One intermediate post-change run was reported by Android as warm and was excluded and replaced. P90 uses nearest-rank over six samples, so it is the maximum sample and should be read cautiously.

ADB input command overhead is included in the absolute first-input time. The paired workload makes the direction useful, but the number is not raw touch-to-render latency.

### Additional checks

- five retained-process HOME-and-return launches plus an immediate foreground relaunch;
- three explicit `HomeActivity` cold launches;
- app-icon `MainActivityAliasDefault` cold launches;
- three post-change `dumpsys gfxinfo` cold-launch/input captures;
- static audit of main/Home/overlay entry, startup phases, cache/snapshot reads, Home sections, icon loading, preferences, providers, and profile infrastructure;
- focused and full Standard Debug unit-test runs;
- repeated in-place build, install, force-stop, and launch validation.

## Startup architecture and critical path

### Process and activity creation

`MainActivity.onCreate` initializes bootstrap preferences, installs the post-draw gate, creates the Compose surface, and obtains `SearchViewModel`. The persisted `StartupSurfaceSnapshot` is read synchronously during initial ViewModel state construction. It already contains bounded app suggestions plus appearance values needed for a stable initial surface.

### First composition and first draw

The initial search field and background compose before startup phases are released. `StartupCoordinator.scheduleAfterFirstDraw` records first draw and posts `releasePostDrawWork`, preserving the existing rule that provider and preference hydration must not be moved in front of the first draw.

### Phase 1: cache and minimal preferences

Phase 1 loads batched startup preferences, cached apps, and permission snapshots on background dispatchers, then publishes cache-derived suggestions. A 350 ms quiet window is reserved when automatic keyboard opening is enabled.

### Phase 2: remaining startup preferences

Phase 2 applies the remaining settings and refreshes recent history. It remains after the keyboard quiet window.

### Phase 3: deferred initialization

Phase 3 initializes search managers and static state. After this change, it also starts full pinned contact/file/note hydration, exclusions, calendar state, and pinned settings on `Dispatchers.IO`. These values are part of the user's Home state and no longer wait for the 10-second maintenance tier.

### Long-idle maintenance

App-catalog reconciliation, configured AI-provider hint refresh, full system shortcut refresh, and icon-pack discovery remain behind the 10-second idle/query-free window. They do not define first meaningful Home content for the current cached configuration.

## Detailed before and after results

All times below are milliseconds relative to `MainActivity.onCreate`, except `am start -W`.

| Milestone | Before median / p90 | After median / p90 | Interpretation |
|---|---:|---:|---|
| Persisted startup snapshot available | 26.1 / 33.0 | 30.4 / 39.8 | Cheap and already early |
| First draw | 300.2 / 323.6 | 281.2 / 297.0 | No first-draw regression measured |
| Search surface first composed | 329.4 / 351.8 | 313.3 / 328.9 | Initial shell remains early |
| Search field focused | 337.3 / 358.7 | 255.1 / 270.4 | Focus is earlier |
| App section/grid composed | 1,298.3 / 1,326.2 | 412.7 / 450.2 | Snapshot is no longer hidden by startup completion |
| App grid interactive | 1,620.5 / 1,652.4 | 412.8 / 450.3 | Persisted surface skips transparent entrance animation |
| Tenth app icon loaded | 1,334.1 / 1,372.0 | 957.2 / 1,013.2 | Decoding starts after phase-1 keyboard reservation |
| First injected character accepted | 615.6 / 645.7 | 596.7 / 724.2 | Median improved; final tail is noisy and hotter |
| Keyboard fully visible by IME inset | 617.9 / 665.6 | 887.8 / 1,098.8 | Measured regression; open P1 |
| Search history data valid | 822.2 / 963.2 | 925.2 / 1,174.7 | Data readiness slightly later under hotter final run |
| Search history rendered after blank-query restore | 1,694.1 / 1,715.5 | 1,059.6 / 1,266.9 | No longer held behind late app entrance |
| Pinned contacts minimal state valid | 978.6 / 1,002.9 | 1,294.6 / 1,452.5 | Phase-3 background state, not a visible blocker here |
| Full pinned contact/file/note state valid | 11,198.1 / 11,234.4 | 1,295.3 / 1,453.0 | Removed from 10-second maintenance tier |
| Exclusion state valid | 11,197.8 / 11,234.1 | 1,295.6 / 1,453.3 | Removed from 10-second maintenance tier |
| Calendar state valid | 270.9 / 290.6 | 1,305.3 / 1,464.2 | Intentionally later, but now off main and after first draw |
| Pinned settings state valid | 11,208.2 / 11,244.8 | 1,305.5 / 1,464.5 | Removed from 10-second maintenance tier |
| Complete enabled Home state | 11,208.2 / 11,244.8 | 1,305.5 / 1,464.5 | Valid data or valid empty state for all enabled local Home sections |
| Phased initialization complete | 1,170.2 / 1,196.1 | 1,510.7 / 1,732.6 | More Home work now starts in phase 3; visible apps no longer wait for this marker |

The calendar row is an intentional latency/context tradeoff. Before the change, the calendar marker was early because provider queries ran synchronously from the initial main-thread lifecycle callback before first draw. The current configuration had no visible Today event, so this early query produced no meaningful content. After the change, calendar valid state arrives later but does not block first draw, focus, or the initial app surface.

### Android launch completion

| Flow | Runs used | Median | p90 | Notes |
|---|---:|---:|---:|---|
| Direct Standard Debug cold, before | 6 | 788.5 ms | 841 ms | Traced, `run-from-apk` |
| Direct Standard Debug cold, final | 6 | 788.0 ms | 816 ms | Traced, hotter device |
| Explicit `HomeActivity` cold | 3 | 692 ms | 707 ms | Does not change the system default launcher |
| App-icon alias cold | 2 | 712 ms | 734 ms | One OS-reported warm sample excluded |
| Retained-process return | 5 | 89 ms | 102 ms | Android labelled four HOT and one WARM; not a controlled warm recreation |
| Already-foreground relaunch | 5 | 0 ms | 0 ms | `WaitTime` 2-8 ms |

The direct cold median was unchanged even though Home content became available much earlier. This demonstrates why `am start -W` alone is insufficient for Home loading work.

## Frame pacing and longest observed main-thread frames

Three post-change `dumpsys gfxinfo` captures included cold start, automatic keyboard presentation, one inserted character, deletion, and the return to Home. Median results were 38 rendered frames, 81.58% janky frames, p50 48 ms, p90 150 ms, 23 missed-vsync events, 9 high-input-latency frames, 29 slow-UI frames, 2 slow bitmap uploads, and 4 slow issue-draw frames.

This is an intentionally harsh Debug cold-start-plus-IME workload at 120 Hz. There is no identical pre-change `gfxinfo` capture, so these numbers are current-state diagnostics, not before/after proof. They do confirm that remaining startup animation cost is primarily UI-thread work rather than only provider I/O.

A representative final `atrace` showed these three longest app-main-thread frames after activity creation:

1. **241.0 ms initial `Choreographer#doFrame` / traversal.** The largest named child was `Compose:recompose` at 116.8 ms. The equivalent baseline trace was 235.9 ms with 117.8 ms in Compose, so this is a pre-existing first-composition bottleneck and trace overhead inflates its absolute duration.
2. **98.0 ms next `Choreographer#doFrame`.** It contained 53.7 ms of animation work, including 39.6 ms in `Recomposer:recompose`, followed by 44.1 ms of traversal with 31.8 ms in `AndroidOwner:onMeasure`.
3. **73.1 ms following `Choreographer#doFrame`.** It contained 34.7 ms of animation, including 26.5 ms in `Recomposer:recompose`, plus 37.7 ms of draw recording with 35.0 ms in `AndroidOwner:measureAndLayout`.

These frames align with initial Compose creation and early app-grid/IME state transitions. They do not implicate calendar or pinned-item provider queries after the fix; those readiness markers occur on `DefaultDispatcher`/I/O threads.

## Ranked findings

### Rejected experiment: exposing persisted Home apps before startup completion

**Evidence:** snapshot data was available at a 26.1 ms median and core surface composition at 329.4 ms, but `SearchResultsState.appsSectionState` began as `Hidden`. Visibility was not applied while `isStartupComplete` was false, so app rendering waited until the completion pass.

**Experiment:** initial blank-query snapshot state started with `ScreenVisibilityState.Content` and `AppsSectionVisibility.ShowingResults` when filtered persisted suggestions were nonempty.

**Measured impact:** grid interaction moved from 1,620.5 ms to 412.8 ms median, but the screen exposed an incompletely hydrated Home shell. Manual validation found that the transition was visibly worse, so this change was reverted.

### P0 fixed: Home provider state waited 10 seconds

**Evidence:** full pinned items, exclusions, and pinned settings reached valid state around 11.2 seconds. Their calls were inside `optionalStartupJob` after `OPTIONAL_STARTUP_DELAY_MS = 10_000`.

**Fix:** added awaitable I/O loaders for pinned and excluded state, then starts them with calendar and pinned settings in phase 3. App reconciliation, AI-provider hint refresh, full shortcut refresh, and icon-pack discovery remain long-idle work.

**Impact:** complete enabled local Home state moved from 11,208.2 ms to 1,305.5 ms median.

### P0 fixed: initial calendar provider queries ran on main before first draw

**Evidence:** `SearchRoute` calls `handleOnResume`; the initial permission state began false; the granted-calendar transition called `refreshOptionalPermissions`, which synchronously called `loadPinnedAndExcludedCalendarEvents`. That method performs preference reads plus pinned, excluded, and Today `CalendarProvider` queries. `CalendarAvailable` occurred 9-39 ms before first draw across baseline samples.

**Fix:** optional permission refresh only triggers calendar data loading after startup completion. The initial valid calendar state is loaded by the phase-3 I/O Home job. Resume refresh remains on I/O.

**Impact:** no calendar provider readiness marker occurs on the main thread before first draw in final traces.

### Rejected experiment: bypassing the established app-grid entrance transition

**Evidence:** the app grid composed before its `SuggestionsEnterDurationMillis = 320` alpha/translation animation completed. During this interval the content was present in Compose but not fully visible or interactive.

**Experiment:** the persisted startup surface bypassed the suggestion entrance animation while startup initialization was active.

**Result:** this amplified the fragmented reveal rather than producing a stable first surface, so the normal first-render transition was restored.

### Rejected experiment: deferring app icons during the keyboard window

**Evidence:** the first implementation made the grid visible early but launched all cold icon loads immediately. The fully visible IME marker moved to a 910.5 ms median. Icon loading produces bitmap state updates and app-grid recompositions during IME presentation.

**Experiment:** `rememberAppIcon` supported a disabled placeholder state so empty-query startup icons stayed deferred during phase 1.

**Result:** first accepted input improved to 596.7 ms median and keyboard full visibility improved from the intermediate 910.5 ms to 887.8 ms, but remained slower than the original 617.9 ms. The blank icon shells were also directly visible, so this behavior was reverted and icons again load through the established path.

### P1 open: Home reveal and IME readiness must be optimized as one experience

**Evidence:** focus improved by 82.2 ms and first-input median improved by 18.9 ms, but fully visible IME state regressed by 269.9 ms. Early app-grid Compose/measure/draw frames are 52-98 ms in the representative trace.

**Recommendation:** add a dedicated Release Macrobenchmark that separates focus, first accepted input, IME animation start/end, stable Home reveal, and first icon visibility. Any next change must be evaluated for visual continuity as well as individual timestamps; it must not expose placeholder shells or make sections visibly arrive as unrelated stages.

### P1 open: initial Compose and early grid frames exceed the 120 Hz budget

**Evidence:** representative traced frames were 241, 98, and 73 ms; `gfxinfo` reports high slow-UI and jank counts. The initial frame was already similar before this task, so it is not caused by the visibility fix.

**Recommendation:** trace `SearchScreenContent`, `ContentLayout`, app-grid tab construction, FlowRow measure, label layout, and wallpaper composition separately in a Release/profileable benchmark. The current evidence supports targeted Compose work, not a broad state rewrite.

### P2 open: benchmark and Baseline Profile generation infrastructure is missing

The repository contains 139 hand-maintained baseline-profile rules and covers several main startup classes, but it does not include `HomeActivity`, `AppGridView`, `AppIconManager`, `PinningHandler`, `SearchHistoryDelegate`, or `SearchStaticDataDelegate`. The measured debug APK reports `run-from-apk`, so profile impact was not measured.

Commit `dfae19ed1` removed the benchmark module, `StartupBenchmark`, `BaselineProfileGenerator`, `StartupTimingMetric`, and `FrameTimingMetric`. Stale build outputs cannot validate current code. Restoring a small current-source benchmark module is required before claiming production cold/warm/hot or Baseline Profile results.

### P2 open: synchronous trace spans cross coroutine suspension

`SearchStartupCoordinator` opens synchronous `Trace.beginSection` spans on the main thread around blocks that call `withContext`. Because synchronous trace sections are thread-scoped stacks, their apparent durations can include unrelated main-thread frames and can close in the wrong nesting order after suspension. This investigation uses new instantaneous readiness markers for timing and does not use those phase-span durations as stall evidence.

Recommendation: replace suspend-spanning phase sections with async trace sections or place thread-scoped spans entirely inside non-suspending work on one dispatcher.

## Entry-path and scenario coverage

| Scenario | Status | Evidence boundary |
|---|---|---|
| Standard direct cold | Measured, six paired samples | Debug `run-from-apk`, traced |
| App icon | Measured through exported launcher alias | Two cold samples after excluding one warm sample |
| Default-home code path | Measured by explicit exported `HomeActivity` intent | System default launcher was not changed, so home-gesture animation is not proven |
| Warm/retained process | Partially measured | Single-task behavior produced HOT/WARM returns, not controlled activity recreation |
| Hot already foreground | Measured | Android returned 0 ms activity total time |
| Activity recreation | Not isolated | No destructive developer-option/config mutation was used |
| Upgrade | Exercised | Repeated `adb install -r` preserved data; upgrade migration correctness was not separately asserted |
| First install | Not run | Would require uninstall/data clear and violate the preserved-data constraint |
| Overlay | Not runtime-measured in this pass | `OverlayActivity` is non-exported and shell launch was denied; overlay preference was not changed |
| Wallpaper mode | Measured | Persisted system-wallpaper configuration |
| Theme/standard background | Static audit only | Preference was not changed |
| Custom image | Static audit only | No custom image was configured and preference was not changed |
| Large apps dataset | Current real dataset only | Prior repository investigation observed 203 searchable app candidates; no synthetic expansion |
| Large contacts/files/calendar/notes/history | Not available | Current real provider data only; no personal data manufactured |
| Permission denied states | Static audit only | Permissions were not revoked |

## Changes made

- Added reusable startup/Home trace milestones.
- Added awaitable full pinned and excluded I/O loaders while preserving existing fire-and-forget callers.
- Prevented initial optional permission refresh from synchronously loading calendar data.
- Moved full pinned items, exclusions, calendar, and pinned settings from the 10-second maintenance job into phase-3 I/O work.
- Added actual data-valid and render-valid markers for apps, labels, icons, history, shortcuts, contacts/files/notes, exclusions, calendar, and settings.
- Reverted early snapshot visibility, animation suppression, and phase-gated icon placeholders after manual visual validation showed a fragmented startup sequence.

No ranking, ordering, query-version, permission, pinned-item, suggestion-selection, launcher, overlay, background, or user-data semantics were intentionally changed.

## Validation

- `git diff --check`: passed.
- Focused `SearchVisibilityStateResolverTest`: passed.
- Full Standard Debug unit suite: 131 tests executed; 130 passed. The only failure is the pre-existing `SearchViewModelFileSizeTest`: both `HEAD` and the working copy have an unchanged 861-line `SearchViewModel.kt`, over its 800-line test cap.
- Standard Debug Kotlin compilation: passed using fallback in-process compilation after the sandbox prevented the Kotlin daemon from writing under `~/Library/Application Support/kotlin/daemon`.
- Standard Debug assembly: passed.
- In-place streamed install: passed and preserved app data.
- Required force-stop and cold `MainActivity` launch: passed.
- Correct exported `HomeActivity` and app-icon alias entry paths: launched successfully.
- Overlay shell launch: blocked by the intended non-exported manifest declaration; no app setting was changed to work around it.

No automated UI suite, app-data clear, permission mutation, launcher-default mutation, git staging, commit, or push was performed.

## Remaining P0/P1 issues

- The final post-revert presentation has not yet been rerun through the full six-sample trace workload, so no final startup delta is claimed.
- P1 remains: Home content, wallpaper, keyboard, icons, engines, and history must converge without exposing intermediate shells or causing visible staging.
- P1 remains: first composition and the next grid/IME frames substantially exceed an 8.33 ms deadline in traced Debug startup.

## Recommended next steps

1. Restore a current Macrobenchmark/Baseline Profile module with Standard direct, launcher alias, `HomeActivity`, overlay-trigger, warm recreation, and first-install/upgrade scenarios.
2. Record separate metrics for first draw, first recognizable Home label, first icon, first accepted input, IME animation start/end, history, calendar, each pinned provider, and complete Home state.
3. Run controlled `None`, generated Baseline Profile, and full-compilation modes on a cooled device. Add `HomeActivity`, app grid/icon, and Home provider hydration journeys to the generated profile based on actual coverage.
4. Isolate early Compose invalidations and FlowRow/label measurement before changing layout architecture.
5. Repeat on a slower 60 Hz device and with intentionally prepared large app, contact, file, calendar, notes, and history datasets in a non-personal test profile.
6. Manually verify wallpaper, theme, custom-image, one-handed, labels-off, calendar-with-events, pinned non-app sections, overlay, permission-off, and true default-launcher gesture behavior.

## Final conclusion

The investigation proved that valid cached app data exists early and that complete provider-backed Home state was incorrectly classified as 10-second optional maintenance. Moving provider hydration into phase-3 I/O and preventing the initial main-thread calendar query remain valid fixes.

It also proved that earlier isolated markers are not sufficient. Exposing cached apps before the rest of Home was visually ready, bypassing the established transition, and deliberately withholding icons produced a worse launch despite faster marker timestamps. Those presentation changes were reverted. The final experience requires fresh measurement plus manual visual verification before it can be called improved.
