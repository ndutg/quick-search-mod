package com.tk.quicksearch.tools.aiSearch

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.shared.ui.components.AppPickerDrawer
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerRowSpacing
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerMessage
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerRow
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerSearch
import com.tk.quicksearch.shared.ui.theme.DesignTokens

data class LlmModelPickerOption(
    val providerId: AiSearchLlmProviderId,
    val model: LlmTextModel,
)

/** Bottom drawer for picking an AI model. */
@Composable
fun ModelPickerDialog(
    selectedModelId: String,
    models: List<LlmTextModel>,
    groundingEnabled: Boolean,
    onGroundingChange: (Boolean) -> Unit,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    showGroundingToggle: Boolean = true,
    selectedProviderId: AiSearchLlmProviderId = AiSearchLlmProviderId.GEMINI,
    modelsByProvider: Map<AiSearchLlmProviderId, List<LlmTextModel>> =
        mapOf(selectedProviderId to models),
    configuredProviderIds: Set<AiSearchLlmProviderId> = setOf(selectedProviderId),
    onProviderModelSelected: (AiSearchLlmProviderId, String) -> Unit = { _, modelId ->
        onModelSelected(modelId)
    },
) {
    val listState = rememberLazyListState()
    val selectedModel = models.firstOrNull { it.id == selectedModelId }
    val supportsGrounding = selectedModel?.supportsGrounding != false
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    val pickerOptions =
        remember(models, modelsByProvider, configuredProviderIds, selectedProviderId) {
            val configured = configuredProviderIds.takeIf { it.isNotEmpty() } ?: setOf(selectedProviderId)
            configured
                .flatMap { providerId ->
                    val providerModels =
                        modelsByProvider[providerId]
                            ?: if (providerId == selectedProviderId) models else emptyList()
                    providerModels.map { model ->
                        LlmModelPickerOption(providerId = providerId, model = model)
                    }
                }
                .distinctBy { it.providerId.storageValue + ":" + it.model.id }
                .sortedWith(
                    compareBy<LlmModelPickerOption> { providerSortOrder(it.providerId) }
                        .thenBy { it.model.displayName.lowercase() },
                )
        }
    // Without a query, show only each provider's latest models (plus the selection); search covers all.
    val featuredOptions =
        remember(pickerOptions, selectedProviderId, selectedModelId) {
            val featuredIds =
                pickerOptions
                    .groupBy { it.providerId }
                    .flatMap { (providerId, options) ->
                        featuredModels(providerId, options.map { it.model }).map { providerId to it.id }
                    }.toSet()
            pickerOptions.filter { option ->
                (option.providerId to option.model.id) in featuredIds ||
                    (option.providerId == selectedProviderId && option.model.id == selectedModelId)
            }
        }
    val hasHiddenModels = featuredOptions.size < pickerOptions.size
    val filteredOptions =
        remember(pickerOptions, featuredOptions, searchQuery) {
            val query = searchQuery.text.trim().lowercase()
            if (query.isEmpty()) {
                featuredOptions
            } else {
                pickerOptions.filter { option ->
                    modelSearchText(option.model).contains(query) ||
                        providerSearchName(option.providerId).lowercase().contains(query)
                }
            }
        }
    val showProviderLabels = configuredProviderIds.size > 1

    LaunchedEffect(Unit) {
        val index =
            featuredOptions.indexOfFirst {
                it.providerId == selectedProviderId && it.model.id == selectedModelId
            }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    AppPickerDrawer(
        title = stringResource(R.string.dialog_gemini_model_picker_title),
        onDismiss = onDismiss,
        search =
            AppPickerDrawerSearch(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = stringResource(R.string.settings_model_picker_search_hint),
            ),
        footer =
            if (showGroundingToggle && supportsGrounding) {
                {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(DesignTokens.SpacingMedium))
                                .clickable { onGroundingChange(!groundingEnabled) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
                    ) {
                        Checkbox(
                            checked = groundingEnabled,
                            onCheckedChange = onGroundingChange,
                        )
                        Text(
                            text = stringResource(R.string.settings_direct_search_grounding_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                null
            },
    ) { dismiss ->
        if (filteredOptions.isEmpty()) {
            AppPickerDrawerMessage(text = stringResource(R.string.widget_custom_buttons_no_results))
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(AppPickerDrawerRowSpacing),
            ) {
                itemsIndexed(filteredOptions) { _, option ->
                    AppPickerDrawerRow(
                        title = option.model.displayName,
                        selected = option.providerId == selectedProviderId && option.model.id == selectedModelId,
                        supportingContent =
                            if (showProviderLabels) {
                                {
                                    ProviderWordmark(
                                        providerId = option.providerId,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                null
                            },
                        onClick = {
                            onProviderModelSelected(option.providerId, option.model.id)
                            dismiss()
                        },
                    )
                }
                if (hasHiddenModels && searchQuery.text.isBlank()) {
                    item {
                        Text(
                            text = stringResource(R.string.settings_model_picker_more_models_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = DesignTokens.SpacingMedium,
                                        vertical = DesignTokens.SpacingMedium,
                                    ),
                        )
                    }
                }
            }
        }
    }
}

/** The provider's latest models shown before the user searches. */
private fun featuredModels(
    providerId: AiSearchLlmProviderId,
    models: List<LlmTextModel>,
): List<LlmTextModel> =
    when (providerId) {
        AiSearchLlmProviderId.GEMINI -> GeminiModelCatalog.pickerModels(models)
        AiSearchLlmProviderId.OPENAI -> OpenAiModelCatalog.pickerModels(models)
        AiSearchLlmProviderId.ANTHROPIC -> AnthropicModelCatalog.pickerModels(models)
        AiSearchLlmProviderId.META -> MetaModelCatalog.pickerModels(models)
        else -> models
    }

private fun providerSortOrder(providerId: AiSearchLlmProviderId): Int =
    when (providerId) {
        AiSearchLlmProviderId.GEMINI -> 0
        AiSearchLlmProviderId.OPENAI -> 1
        AiSearchLlmProviderId.ANTHROPIC -> 2
        AiSearchLlmProviderId.GROQ -> 3
        AiSearchLlmProviderId.META -> 4
        else -> 5
    }

@Composable
private fun ProviderWordmark(
    providerId: AiSearchLlmProviderId,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    when (providerId) {
        AiSearchLlmProviderId.OPENAI -> {
            Image(
                painter = painterResource(R.drawable.openai_wordmark),
                contentDescription = stringResource(R.string.settings_ai_provider_openai),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = Modifier.size(width = 56.dp, height = 15.dp),
            )
        }
        AiSearchLlmProviderId.ANTHROPIC -> {
            Box(modifier = Modifier.size(width = 72.dp, height = 15.dp)) {
                Image(
                    painter = painterResource(R.drawable.claude_wordmark_mark),
                    contentDescription = stringResource(R.string.search_engine_claude),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Image(
                    painter = painterResource(R.drawable.claude_wordmark_type),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(contentColor),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        AiSearchLlmProviderId.GROQ -> {
            Image(
                painter = painterResource(R.drawable.groq_wordmark),
                contentDescription = stringResource(R.string.settings_ai_provider_groq),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = Modifier.size(width = 41.dp, height = 15.dp),
            )
        }
        AiSearchLlmProviderId.META -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.meta_logo),
                    contentDescription = stringResource(R.string.settings_ai_provider_meta),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(R.string.settings_ai_provider_meta),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                )
            }
        }
        AiSearchLlmProviderId.GEMINI -> {
            Box(modifier = Modifier.size(width = 67.dp, height = 15.dp)) {
                Image(
                    painter = painterResource(R.drawable.gemini_wordmark_mark),
                    contentDescription = stringResource(R.string.search_engine_gemini),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Image(
                    painter = painterResource(R.drawable.gemini_wordmark_type),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(contentColor),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        else -> {
            Text(
                text = stringResource(R.string.common_custom),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

private fun providerSearchName(providerId: AiSearchLlmProviderId): String =
    when (providerId) {
        AiSearchLlmProviderId.GEMINI -> "Gemini"
        AiSearchLlmProviderId.OPENAI -> "OpenAI"
        AiSearchLlmProviderId.ANTHROPIC -> "Claude"
        AiSearchLlmProviderId.GROQ -> "Groq"
        AiSearchLlmProviderId.META -> "Meta AI"
        else -> "Custom"
    }

private fun modelSearchText(model: LlmTextModel): String =
    listOf(
        model.displayName,
        model.id,
        model.id.replace('-', ' '),
        model.id.replace('_', ' '),
    )
        .joinToString(" ")
        .lowercase()
