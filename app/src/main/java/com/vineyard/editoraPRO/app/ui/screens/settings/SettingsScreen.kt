package com.vineyard.editoraPRO.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vineyard.editoraPRO.app.data.model.ApiKeyEntry
import com.vineyard.editoraPRO.app.data.model.AppState
import com.vineyard.editoraPRO.app.data.model.ProviderMode
import com.vineyard.editoraPRO.app.data.model.ThinkingLevel
import com.vineyard.editoraPRO.app.ui.components.CardStepHeader
import com.vineyard.editoraPRO.app.ui.components.EditoraButton
import com.vineyard.editoraPRO.app.ui.components.EditoraCard
import com.vineyard.editoraPRO.app.ui.components.EditoraTextField
import com.vineyard.editoraPRO.app.ui.components.PillTone
import com.vineyard.editoraPRO.app.ui.components.StatusPill
import com.vineyard.editoraPRO.app.ui.theme.EditoraAccent
import com.vineyard.editoraPRO.app.ui.theme.EditoraAccentCyan
import com.vineyard.editoraPRO.app.ui.theme.EditoraBg
import com.vineyard.editoraPRO.app.ui.theme.EditoraBorder
import com.vineyard.editoraPRO.app.ui.theme.EditoraDangerText
import com.vineyard.editoraPRO.app.ui.theme.EditoraMonoStyle
import com.vineyard.editoraPRO.app.ui.theme.EditoraSurface
import com.vineyard.editoraPRO.app.ui.theme.EditoraSurface2
import com.vineyard.editoraPRO.app.ui.theme.EditoraTerminalBg
import com.vineyard.editoraPRO.app.ui.theme.EditoraTextMuted
import com.vineyard.editoraPRO.app.ui.theme.EditoraTextPrimary

@Composable
fun SettingsScreen(
    state: AppState,
    onSwitchProvider: (ProviderMode) -> Unit,
    onAddApiKey: (String) -> Unit,
    onRemoveApiKey: (Int) -> Unit,
    onFetchLiveModels: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectThinkingLevel: (ThinkingLevel) -> Unit,
    onSelectExportFps: (Int) -> Unit,
    onSelectMaxCanvasEdge: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var apiKeyInput by remember { mutableStateOf("") }
    val nextKeyIdx = if (state.apiKeys.isEmpty()) -1 else (state.cycleIdx % state.apiKeys.size)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. API Provider Mode
        EditoraCard {
            CardStepHeader(
                stepNumber = null,
                title = "API Provider Mode",
                pillText = state.apiProvider.shortLabel,
                pillTone = PillTone.READY,
                pillTag = "provider_badge"
            )
            Text(
                text = "Choose between Vertex AI (Gemini Platform) or AI Studio (v1beta). Each mode preserves its own API key rotation pool, model list, upload pipeline, and Visual QA behavior.",
                color = EditoraTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            EditoraDropdown(
                selectedLabel = state.apiProvider.fullLabel,
                options = ProviderMode.entries.map { it to it.fullLabel },
                onSelect = onSwitchProvider,
                testTag = "provider_select"
            )
        }

        // 2. Gemini API Keys (Auto-Cycle)
        EditoraCard {
            CardStepHeader(
                stepNumber = null,
                title = "Gemini API Keys (Auto-Cycle)",
                pillText = "${state.apiKeys.size} key${if (state.apiKeys.size == 1) "" else "s"}",
                pillTone = if (state.apiKeys.isNotEmpty()) PillTone.READY else PillTone.NEUTRAL,
                pillTag = "cycle_status_badge"
            )
            Text(
                text = if (state.apiProvider == ProviderMode.VERTEX_AI) {
                    "Add multiple Gemini Platform API keys. Each request automatically rotates to the next key in sequence to avoid single-key rate limits."
                } else {
                    "Add multiple AI Studio API keys. Each request automatically rotates keys in sequence across v1beta endpoints."
                },
                color = EditoraTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditoraTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    placeholder = if (state.apiProvider == ProviderMode.VERTEX_AI) {
                        "Paste Gemini Platform API Key"
                    } else {
                        "Paste AI Studio API Key"
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    testTag = "api_key_input",
                    modifier = Modifier.weight(1f)
                )
                EditoraButton(
                    text = "Add",
                    onClick = {
                        onAddApiKey(apiKeyInput)
                        if (apiKeyInput.trim().length >= 10) {
                            apiKeyInput = ""
                        }
                    },
                    testTag = "add_key_button"
                )
            }

            if (state.apiKeys.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.apiKeys.forEachIndexed { index, entry ->
                        ApiKeyRow(
                            entry = entry,
                            isNext = index == nextKeyIdx,
                            index = index,
                            onRemove = { onRemoveApiKey(index) }
                        )
                    }
                }
            }
        }

        // 3. Live Server Models + Thinking Level
        EditoraCard {
            CardStepHeader(
                stepNumber = null,
                title = "Live Server Models",
                pillText = if (state.models.isEmpty()) "Not fetched" else "${state.models.size} live",
                pillTone = if (state.models.isNotEmpty()) PillTone.READY else PillTone.NEUTRAL,
                pillTag = "model_count_badge"
            )
            Text(
                text = if (state.apiProvider == ProviderMode.VERTEX_AI) {
                    "Tap the fetch icon to verify available models directly against Gemini Platform (lunar-tag/global). Only your selected model is used."
                } else {
                    "Tap the fetch icon to verify available models directly against Google AI Studio (v1beta). Only your selected model is used."
                },
                color = EditoraTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val modelLabel = state.selectedModel.removePrefix("models/")
                    .ifBlank { "-- Fetch live models first --" }
                EditoraDropdown(
                    selectedLabel = modelLabel,
                    options = state.models.map { it.name to it.shortName() },
                    onSelect = onSelectModel,
                    enabled = state.models.isNotEmpty(),
                    testTag = "model_select",
                    modifier = Modifier.weight(1f)
                )

                val infiniteTransition = rememberInfiniteTransition(label = "fetch_spin")
                val spinAngle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 850, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "spin_deg"
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EditoraSurface2,
                    border = BorderStroke(1.dp, EditoraBorder)
                ) {
                    IconButton(
                        onClick = onFetchLiveModels,
                        enabled = !state.isFetchingModels,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("fetch_models_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Fetch live models from server",
                            tint = EditoraAccentCyan,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (state.isFetchingModels) spinAngle else 0f)
                        )
                    }
                }
            }

            val selectedInfo = state.models.firstOrNull { it.name == state.selectedModel }
            AnimatedVisibility(visible = selectedInfo != null) {
                if (selectedInfo != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("model_info_box"),
                        shape = RoundedCornerShape(10.dp),
                        color = EditoraTerminalBg,
                        border = BorderStroke(1.dp, EditoraBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (selectedInfo.displayName.isNotBlank()) {
                                Text(
                                    text = selectedInfo.displayName,
                                    color = EditoraTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (selectedInfo.description.isNotBlank()) {
                                Text(
                                    text = selectedInfo.description,
                                    color = EditoraTextMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            if (selectedInfo.inputTokenLimit != null || selectedInfo.outputTokenLimit != null) {
                                Text(
                                    text = "Limits: in=${selectedInfo.inputTokenLimit ?: "?"} • out=${selectedInfo.outputTokenLimit ?: "?"}",
                                    style = EditoraMonoStyle.copy(fontSize = 10.5.sp),
                                    color = EditoraAccentCyan
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = state.apiProvider == ProviderMode.AI_STUDIO) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("thinking_level_wrap"),
                    shape = RoundedCornerShape(12.dp),
                    color = EditoraAccent.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, EditoraAccent.copy(alpha = 0.22f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reasoning / Thinking Level (AI Studio)",
                                color = EditoraAccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusPill(
                                text = state.thinkingLevel.badgeLabel,
                                tone = PillTone.NEXT,
                                modifier = Modifier.testTag("thinking_level_badge")
                            )
                        }
                        EditoraDropdown(
                            selectedLabel = state.thinkingLevel.label,
                            options = ThinkingLevel.entries.map { it to it.label },
                            onSelect = onSelectThinkingLevel,
                            testTag = "thinking_level_select"
                        )
                        Text(
                            text = "Applies thinkingLevel (Gemini 3 / 3.5 / 4) or mapped thinkingBudget (Gemini 2.5) automatically for the selected AI Studio model.",
                            color = EditoraTextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // 4. Render Preferences
        EditoraCard {
            Text(
                text = "Render Preferences",
                color = EditoraTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Export FPS", color = EditoraTextMuted, fontSize = 12.sp)
                    EditoraDropdown(
                        selectedLabel = if (state.exportFps == 24) "24 fps (Cinematic)" else "30 fps (Smooth)",
                        options = listOf(24 to "24 fps (Cinematic)", 30 to "30 fps (Smooth)"),
                        onSelect = onSelectExportFps,
                        testTag = "fps_select"
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Max Canvas Edge", color = EditoraTextMuted, fontSize = 12.sp)
                    EditoraDropdown(
                        selectedLabel = if (state.maxCanvasEdge == 720) "720p (Fast Mobile)" else "1080p (HD)",
                        options = listOf(720 to "720p (Fast Mobile)", 1080 to "1080p (HD)"),
                        onSelect = onSelectMaxCanvasEdge,
                        testTag = "res_select"
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyRow(
    entry: ApiKeyEntry,
    isNext: Boolean,
    index: Int,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("api_key_item_$index"),
        shape = RoundedCornerShape(12.dp),
        color = if (isNext) EditoraAccentCyan.copy(alpha = 0.06f) else EditoraSurface2,
        border = BorderStroke(
            width = 1.dp,
            color = if (isNext) EditoraAccentCyan.copy(alpha = 0.5f) else EditoraBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.masked(),
                        style = EditoraMonoStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        color = EditoraTextPrimary
                    )
                    if (isNext) {
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusPill(text = "NEXT", tone = PillTone.NEXT)
                    }
                }
                Text(
                    text = "Requests: ${entry.requests} • Errors: ${entry.errors}",
                    color = EditoraTextMuted,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("remove_key_$index")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove API key ${entry.masked()}",
                    tint = EditoraDangerText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun <T> EditoraDropdown(
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag(testTag)
                .clickable(enabled = enabled && options.isNotEmpty(), role = Role.DropdownList) {
                    expanded = true
                },
            shape = RoundedCornerShape(12.dp),
            color = EditoraBg,
            border = BorderStroke(1.dp, EditoraBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLabel,
                    color = if (enabled) EditoraTextPrimary else EditoraTextMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Open selector",
                    tint = EditoraTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(EditoraSurface)
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = EditoraTextPrimary,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}
