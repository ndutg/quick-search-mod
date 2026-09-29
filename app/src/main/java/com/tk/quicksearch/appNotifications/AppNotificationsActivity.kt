package com.tk.quicksearch.appNotifications

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tk.quicksearch.R
import com.tk.quicksearch.search.core.AppThemeMode
import com.tk.quicksearch.search.data.AppNotificationFilters
import com.tk.quicksearch.search.data.preferences.GlancePreferences
import com.tk.quicksearch.search.data.userAppPreferences.UserAppPreferences
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.rememberAppLabel
import com.tk.quicksearch.settings.settingsDetailScreen.CalendarSettingsBarCornerShape
import com.tk.quicksearch.settings.settingsDetailScreen.SettingsDetailHeader
import com.tk.quicksearch.settings.shared.SettingsCard
import com.tk.quicksearch.settings.shared.SettingsScreenBackground
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import com.tk.quicksearch.shared.ui.theme.QuickSearchTheme
import com.tk.quicksearch.shared.util.AppLanguageManager

/**
 * App notifications settings for At a Glance: the apps to show (each optionally narrowed by
 * keywords) and keywords that match any app. Changes save to [GlancePreferences] through
 * [AppNotificationsViewModel] as they're made, and Home picks them up right away.
 */
class AppNotificationsActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    private fun finishWithSlide() {
        finish()
        overridePendingTransition(R.anim.custom_info_slide_in_left, R.anim.custom_info_slide_out_right)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguageManager.applySavedAppLanguage(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWithSlide()
        })
        val preferences = UserAppPreferences(applicationContext)
        setContent {
            val useDarkSystemBars = when (preferences.getAppThemeMode()) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SideEffect {
                val style = if (useDarkSystemBars) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            QuickSearchTheme(
                fontScaleMultiplier = preferences.getFontScaleMultiplier(),
                useSystemFont = preferences.shouldUseSystemFont(),
                appTheme = preferences.getAppTheme(),
                appThemeMode = preferences.getAppThemeMode(),
                backgroundSource = preferences.getBackgroundSource(),
                customImageUri = preferences.getCustomImageUri(),
                accentColorMode = preferences.getAccentColorMode(),
                customAccentColorArgb = preferences.getCustomAccentColorArgb(),
                deviceThemeEnabled = preferences.isDeviceThemeEnabled(),
            ) {
                SettingsScreenBackground(
                    appTheme = preferences.getAppTheme(),
                    overlayThemeIntensity = preferences.getOverlayThemeIntensity(),
                    deviceThemeEnabled = preferences.isDeviceThemeEnabled(),
                    amoledThemeEnabled = preferences.isAmoledThemeEnabled(),
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                ) {
                    AppNotificationsScreen(onBack = ::finishWithSlide)
                }
            }
        }
    }
}

/** Which source's keywords drawer is open: an app by package name, or [ANY_APP]. */
private const val ANY_APP = ""

/**
 * One list of sources: Any App (keywords that match every app) and the added apps, with Add app
 * pinned at the bottom. Tapping a source opens its keywords drawer; Recent matches below shows what
 * the setup would catch. The list waits until the saved settings have been read.
 */
@Composable
private fun AppNotificationsScreen(
    onBack: () -> Unit,
    viewModel: AppNotificationsViewModel = viewModel(),
) {
    val loadedFilters by viewModel.filters.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    var editing by remember { mutableStateOf<String?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    // The app just added from the picker; its keywords drawer opens once the picker has closed.
    var addedApp by remember { mutableStateOf<String?>(null) }
    val filters = loadedFilters ?: AppNotificationFilters()
    val keywordsSummary: (List<String>) -> String = { words -> words.joinToString(", ") }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        containerColor = Color.Transparent,
        topBar = {
            SettingsDetailHeader(
                title = stringResource(R.string.settings_at_a_glance_app_notifications_title),
                onBack = onBack,
            )
        },
        bottomBar = {
            // Matches the Create new tool button in Tools settings.
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.loadInstalledApps()
                    showAppPicker = true
                },
                expanded = true,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = DesignTokens.ContentHorizontalPadding)
                    .padding(bottom = DesignTokens.SpacingLarge),
                shape = CalendarSettingsBarCornerShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                text = { Text(text = stringResource(R.string.app_notifications_add_app)) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(AddAppIconSize),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.ContentHorizontalPadding)
                .padding(bottom = DesignTokens.SpacingLarge),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
        ) {
            if (loadedFilters == null) return@Column
            Text(
                text = stringResource(R.string.app_notifications_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = DesignTokens.SpacingXSmall),
            )

            SettingsCard(modifier = Modifier.fillMaxWidth()) {
                SourceRow(
                    packageName = null,
                    title = stringResource(R.string.app_notifications_any_app),
                    subtitle = if (filters.keywords.isEmpty()) {
                        stringResource(R.string.app_notifications_keyword_placeholder)
                    } else {
                        keywordsSummary(filters.keywords)
                    },
                    onClick = { editing = ANY_APP },
                )
                filters.apps.forEach { app ->
                    HorizontalDivider(color = AppColors.SettingsDivider)
                    SourceRow(
                        packageName = app.packageName,
                        title = rememberAppLabel(app.packageName),
                        subtitle = if (app.keywords.isEmpty()) {
                            stringResource(R.string.app_notifications_all_from_app)
                        } else {
                            keywordsSummary(app.keywords)
                        },
                        onClick = { editing = app.packageName },
                        onRemove = { viewModel.removeApp(app.packageName) },
                    )
                }
            }

            if (!filters.isEmpty) AppNotificationRecentMatches(filters)
        }
    }

    val editingApp = filters.apps.firstOrNull { it.packageName == editing }
    when {
        editing == ANY_APP -> AppNotificationKeywordsDrawer(
            title = stringResource(R.string.app_notifications_any_app),
            keywords = filters.keywords,
            onKeywordsChange = viewModel::setAnyAppKeywords,
            onDismiss = { editing = null },
        )
        editingApp != null -> {
            val appLabel = rememberAppLabel(editingApp.packageName)
            AppNotificationKeywordsDrawer(
                title = appLabel,
                keywords = editingApp.keywords,
                appName = appLabel,
                packageName = editingApp.packageName,
                onKeywordsChange = { words -> viewModel.setAppKeywords(editingApp.packageName, words) },
                onDismiss = { editing = null },
            )
        }
    }

    if (showAppPicker) {
        AppNotificationAppPicker(
            allApps = installedApps,
            added = filters.apps.map { it.packageName }.toSet(),
            onPick = { packageName ->
                viewModel.addApp(packageName)
                addedApp = packageName
            },
            onDismiss = {
                showAppPicker = false
                addedApp?.let { editing = it }
                addedApp = null
            },
        )
    }
}

/**
 * A source in the list: icon, name, a summary of what it shows (up to two lines), and a chevron.
 * With [onRemove], a divider and a delete button follow the chevron, like a navigation toggle row.
 */
@Composable
private fun SourceRow(
    packageName: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.SpacingLarge, vertical = DesignTokens.SpacingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
    ) {
        AppNotificationSourceIcon(packageName)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingXSmall)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRemove != null) {
            VerticalDivider(modifier = Modifier.height(DesignTokens.IconSize), color = AppColors.SettingsDivider)
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.app_notifications_remove_app, title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val AddAppIconSize = 18.dp
