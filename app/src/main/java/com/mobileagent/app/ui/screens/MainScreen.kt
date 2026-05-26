package com.mobileagent.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobileagent.app.R
import com.mobileagent.app.ui.theme.*
import com.mobileagent.app.util.PermissionChecker

data class StepLog(
    val step: Int,
    val phase: String,
    val content: String,
    val promptSnippet: String = "",
    val response: String = "",
    val durationMs: Long = 0,
    val imageCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val instruction by viewModel.instruction
    val isRunning by viewModel.isRunning
    val logs = viewModel.logs
    val missing by viewModel.missingPermissions
    val listState = rememberLazyListState()

    if (missing.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionDialog() },
            title = { Text(stringResource(R.string.dialog_missing_permissions_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (PermissionChecker.Permission.ACCESSIBILITY in missing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Red500, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.perm_accessibility), modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            }) { Text(stringResource(R.string.dialog_perm_fix)) }
                        }
                    }
                    if (PermissionChecker.Permission.OVERLAY in missing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Red500, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.perm_overlay), modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            }) { Text(stringResource(R.string.dialog_perm_fix)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPermissionDialog() }) {
                    Text("OK")
                }
            }
        )
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "MobileAgent", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = instruction,
            onValueChange = { viewModel.instruction.value = it },
            label = { Text(stringResource(R.string.instruction_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            enabled = !isRunning
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.startTask() },
                enabled = !isRunning && instruction.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BtnStart)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.btn_start))
            }

            Button(
                onClick = { viewModel.stopTask() },
                enabled = isRunning,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BtnStop)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.btn_stop))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val statusText = when {
            isRunning -> stringResource(R.string.status_running)
            logs.isNotEmpty() -> stringResource(R.string.status_finished)
            else -> stringResource(R.string.status_idle)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isRunning) Icons.Default.PlayArrow else if (logs.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Circle,
                contentDescription = null,
                tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = if (isRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.execution_log), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inbox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_logs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs.size, key = { it }) { index ->
                    StepLogCard(logs[index])
                }
            }
        }
    }
}

@Composable
private fun StepLogCard(log: StepLog) {
    var expanded by remember { mutableStateOf(false) }
    val hasDetail = log.promptSnippet.isNotBlank() || log.response.isNotBlank()

    val phaseIcon = when (log.phase) {
        "screenshot" -> Icons.Default.PhotoCamera
        "manager" -> Icons.Default.AccountTree
        "executor" -> Icons.Default.TouchApp
        "execute" -> Icons.Default.PlayArrow
        "reflector" -> Icons.Default.Visibility
        "notetaker" -> Icons.Default.NoteAlt
        "finished", "answer", "done" -> Icons.Default.CheckCircle
        "error" -> Icons.Default.Error
        else -> Icons.Default.Info
    }

    val phaseColor = when (log.phase) {
        "error" -> Red500
        "finished", "answer", "done" -> Green500
        "execute" -> Orange500
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasDetail) Modifier.clickable { expanded = !expanded } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(phaseIcon, contentDescription = null, tint = phaseColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                if (log.step >= 0) {
                    Text(
                        text = "Step ${log.step}",
                        style = MaterialTheme.typography.labelMedium,
                        color = phaseColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = log.phase.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                if (log.durationMs > 0) {
                    Text(
                        text = "${log.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasDetail) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = log.content, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 2)

            if (expanded && hasDetail) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))

                if (log.imageCount > 0) {
                    LogDetailRow("Images", "${log.imageCount} screenshot(s) attached")
                }
                if (log.promptSnippet.isNotBlank()) {
                    LogDetailRow("Prompt (tail)", log.promptSnippet)
                }
                if (log.response.isNotBlank()) {
                    LogDetailRow("Response", log.response)
                }
            }
        }
    }
}

@Composable
private fun LogDetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
