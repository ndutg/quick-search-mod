package com.tk.quicksearch.settings.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.userAppPreferences.UserAppPreferences
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import com.tk.quicksearch.tools.aiSearch.AiSearchLlmProviderId
import com.tk.quicksearch.tools.aiSearch.ModelPickerDialog
import com.tk.quicksearch.tools.aiSearch.LlmTextModel
import com.tk.quicksearch.tools.aiSearch.isWebSearchAvailable
import com.tk.quicksearch.tools.aiSearch.modelSupportsGrounding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Whether a Tavily key is stored, with `Unknown` while the encrypted read is still in flight. */
enum class TavilyKeyState {
    Unknown,
    Present,
    Absent,
    ;

    val isPresent: Boolean
        get() = this == Present
}

/**
 * Reads the stored Tavily key off the main thread. Stays [TavilyKeyState.Unknown] until the read
 * lands, so callers can avoid acting on a key that is merely not loaded yet.
 */
@Composable
fun rememberTavilyKeyState(): TavilyKeyState {
    val context = LocalContext.current
    val preferences = remember(context) { UserAppPreferences(context.applicationContext) }
    var state by remember(preferences) { mutableStateOf(TavilyKeyState.Unknown) }
    LaunchedEffect(preferences) {
        val hasKey = withContext(Dispatchers.IO) { !preferences.getTavilyApiKey().isNullOrBlank() }
        state = if (hasKey) TavilyKeyState.Present else TavilyKeyState.Absent
    }
    return state
}

@Composable
fun ModelFeatureSettingsCard(
    selectedModelId: String,
    selectedProviderId: AiSearchLlmProviderId = AiSearchLlmProviderId.GEMINI,
    availableModels: List<LlmTextModel>,
    availableModelsByProvider: Map<AiSearchLlmProviderId, List<LlmTextModel>> =
        mapOf(selectedProviderId to availableModels),
    configuredProviderIds: Set<AiSearchLlmProviderId> = setOf(selectedProviderId),
    thinkingLabel: String,
    webSearchLabel: String,
    thinkingEnabled: Boolean,
    groundingEnabled: Boolean,
    onModelSelected: (String) -> Unit,
    onThinkingChange: (Boolean) -> Unit,
    onGroundingChange: (Boolean) -> Unit,
    onProviderModelSelected: (AiSearchLlmProviderId, String) -> Unit = { _, modelId ->
        onModelSelected(modelId)
    },
    modifier: Modifier = Modifier,
    showThinkingCheckbox: Boolean = true,
    showGroundingCheckbox: Boolean = true,
    groundingCheckboxEnabled: Boolean = true,
    tavilyKeyState: TavilyKeyState = rememberTavilyKeyState(),
    isLoading: Boolean = false,
) {
    var showModelDialog by remember { mutableStateOf(false) }

    val modelOptions =
        remember(availableModels) {
            availableModels.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
        }

    val selectedModel = modelOptions.firstOrNull { it.id == selectedModelId }
    val isLoadingModels =
        isLoading ||
            configuredProviderIds.isNotEmpty() &&
            configuredProviderIds.any { it !in availableModelsByProvider }
    val selectedModelLabel =
        when {
            isLoadingModels -> stringResource(R.string.settings_loading_models)
            selectedModel != null -> selectedModel.displayName
            else -> stringResource(R.string.settings_select_model)
        }
    val webSearchAvailable =
        isWebSearchAvailable(
            providerId = selectedProviderId,
            modelSupportsGrounding =
                modelSupportsGrounding(selectedModelId, availableModels, selectedProviderId),
            hasTavilyKey = tavilyKeyState.isPresent,
        )

    SettingsCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = DesignTokens.CardHorizontalPadding,
                    vertical = DesignTokens.CardVerticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(DesignTokens.ShapeMedium)
                        .clickable(enabled = !isLoadingModels) { showModelDialog = true }
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProviderIconTile(providerId = selectedProviderId)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = selectedModelLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = aiProviderDisplayName(selectedProviderId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.UnfoldMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val showGroundingChip = showGroundingCheckbox && webSearchAvailable
            if (!isLoadingModels && selectedModel != null && (showThinkingCheckbox || showGroundingChip)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showThinkingCheckbox) {
                        ModelFeatureChip(
                            label = thinkingLabel,
                            selected = thinkingEnabled,
                            onSelectedChange = onThinkingChange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (showGroundingChip) {
                        ModelFeatureChip(
                            label = webSearchLabel,
                            selected = groundingEnabled,
                            onSelectedChange = onGroundingChange,
                            enabled = groundingCheckboxEnabled,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showModelDialog) {
        ModelPickerDialog(
            selectedModelId = selectedModelId,
            models = modelOptions,
            groundingEnabled = groundingEnabled,
            onGroundingChange = { checked ->
                onGroundingChange(checked)
            },
            onModelSelected = { modelId ->
                onModelSelected(modelId)
            },
            onProviderModelSelected = { providerId, modelId ->
                onProviderModelSelected(providerId, modelId)
            },
            onDismiss = { showModelDialog = false },
            showGroundingToggle = false,
            selectedProviderId = selectedProviderId,
            modelsByProvider = availableModelsByProvider,
            configuredProviderIds = configuredProviderIds,
        )
    }
}

@Composable
private fun ProviderIconTile(providerId: AiSearchLlmProviderId) {
    val logoResId =
        when (providerId) {
            AiSearchLlmProviderId.GEMINI -> R.drawable.ic_gemini_sparkle_search_engine
            AiSearchLlmProviderId.ANTHROPIC -> R.drawable.claude
            AiSearchLlmProviderId.META -> R.drawable.meta_logo
            else -> null
        }
    Box(
        modifier =
            Modifier.size(40.dp)
                .clip(DesignTokens.ShapeMedium)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        if (logoResId != null) {
            Image(
                painter = painterResource(logoResId),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Short provider name shown under the selected model. */
@Composable
fun aiProviderDisplayName(providerId: AiSearchLlmProviderId): String =
    stringResource(
        when (providerId) {
            AiSearchLlmProviderId.GEMINI -> R.string.search_engine_gemini
            AiSearchLlmProviderId.OPENAI -> R.string.settings_ai_provider_openai
            AiSearchLlmProviderId.ANTHROPIC -> R.string.search_engine_claude
            AiSearchLlmProviderId.GROQ -> R.string.settings_ai_provider_groq
            AiSearchLlmProviderId.META -> R.string.settings_ai_provider_meta
            else -> R.string.common_custom
        },
    )

@Composable
private fun ModelFeatureChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        modifier = modifier,
        label = {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        enabled = enabled,
        shape = DesignTokens.ShapeFull,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = enabled,
                selected = selected,
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                selectedBorderWidth = 1.dp,
            ),
        leadingIcon =
            if (selected) {
                {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            } else {
                null
            },
    )
}
