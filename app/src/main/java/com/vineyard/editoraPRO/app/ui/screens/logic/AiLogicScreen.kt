package com.vineyard.editoraPRO.app.ui.screens.logic

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vineyard.editoraPRO.app.data.model.AppState
import com.vineyard.editoraPRO.app.data.model.ProcessStatus
import com.vineyard.editoraPRO.app.ui.components.CardStepHeader
import com.vineyard.editoraPRO.app.ui.components.CodeViewer
import com.vineyard.editoraPRO.app.ui.components.EditoraButton
import com.vineyard.editoraPRO.app.ui.components.EditoraButtonTone
import com.vineyard.editoraPRO.app.ui.components.EditoraCard
import com.vineyard.editoraPRO.app.ui.components.PillTone
import com.vineyard.editoraPRO.app.ui.components.StatusPill
import com.vineyard.editoraPRO.app.ui.theme.EditoraCyanStreamText
import com.vineyard.editoraPRO.app.ui.theme.EditoraTextMuted
import com.vineyard.editoraPRO.app.ui.theme.EditoraTextPrimary

@Composable
fun AiLogicScreen(
    state: AppState,
    onBuildReportText: () -> String,
    onWriteReportUri: () -> Uri,
    onClearExecutionLog: () -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val (streamLabel, streamTone) = when (state.streamMonitorStatus) {
        ProcessStatus.RUNNING -> "Streaming" to PillTone.NEXT
        ProcessStatus.SUCCESS -> "Done" to PillTone.READY
        ProcessStatus.ERROR -> "Error" to PillTone.WARN
        ProcessStatus.IDLE -> "Idle" to PillTone.NEUTRAL
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Diagnostic Report Card
        EditoraCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Diagnostic Report",
                    color = EditoraTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditoraButton(
                        text = "Copy All",
                        onClick = {
                            clipboard.setText(AnnotatedString(onBuildReportText()))
                            onShowToast("Diagnostic report copied!")
                        },
                        tone = EditoraButtonTone.SECONDARY,
                        testTag = "copy_report_button",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                    EditoraButton(
                        text = "Download",
                        onClick = {
                            runCatching {
                                val reportUri = onWriteReportUri()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, reportUri)
                                    putExtra(Intent.EXTRA_SUBJECT, "editoraPRO Diagnostic Report")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Export Diagnostic Report")
                                )
                            }.onFailure { err ->
                                onShowToast("Export report failed: ${err.message}")
                            }
                        },
                        tone = EditoraButtonTone.SECONDARY,
                        testTag = "download_report_button",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
            Text(
                text = "Copy or download all logs, Visual QA reports, blueprint JSON, synthesized tools, and generated pipeline code in one file.",
                color = EditoraTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        // Live AI Stream & Thought Monitor
        EditoraCard {
            CardStepHeader(
                stepNumber = null,
                title = "Live AI Stream & Thought Monitor",
                pillText = streamLabel,
                pillTone = streamTone,
                pillTag = "stream_monitor_badge"
            )
            CodeViewer(
                text = state.liveStreamText,
                textColor = EditoraCyanStreamText,
                maxHeight = 190.dp,
                testTag = "live_stream_view"
            )
        }

        // Deep Execution Log
        EditoraCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Deep Execution Log",
                    color = EditoraTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                EditoraButton(
                    text = "Clear",
                    onClick = onClearExecutionLog,
                    tone = EditoraButtonTone.SECONDARY,
                    testTag = "clear_log_button"
                )
            }
            CodeViewer(
                text = state.executionLogs.joinToString("\n"),
                testTag = "execution_log_view"
            )
        }

        // 1. Blueprint JSON
        EditoraCard {
            Text(
                text = "1. AI Reference / Creative Blueprint (JSON)",
                color = EditoraTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            CodeViewer(
                text = state.blueprintJsonText,
                testTag = "blueprint_code_view"
            )
        }

        // 2. Tools JS
        EditoraCard {
            Text(
                text = "2. AI-Generated Internal Tools (JS)",
                color = EditoraTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            CodeViewer(
                text = state.toolsCodeText,
                testTag = "tools_code_view"
            )
        }

        // 3. Pipeline JS
        EditoraCard {
            Text(
                text = "3. AI-Generated Edit Pipeline (JS)",
                color = EditoraTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            CodeViewer(
                text = state.pipelineCodeText,
                testTag = "pipeline_code_view"
            )
        }
    }
}
