package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Md3LightError
import com.example.ui.theme.Md3LightErrorContainer
import com.example.ui.theme.Md3LightOutline
import com.example.ui.theme.Md3LightOutlineVariant
import com.example.ui.theme.Md3LightPrimary
import com.example.ui.theme.Md3LightSurface
import com.example.ui.theme.Md3LightSurfaceVariant
import com.example.ui.theme.Md3LightTextPrimary
import com.example.ui.theme.Md3LightTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchActionsModal(
    selectedFiles: List<String>,
    isDeleting: Boolean,
    progress: Triple<Int, Int, String>?, // completed, total, currentFile
    onDismiss: () -> Unit,
    onConfirmDelete: (commitMessage: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commitMessage by remember { mutableStateOf("Delete ${selectedFiles.size} files") }

    ModalBottomSheet(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        sheetState = sheetState,
        containerColor = Md3LightSurface,
        modifier = Modifier.testTag("batch_actions_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Md3LightError,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Batch Delete Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextPrimary
                    )
                }

                if (!isDeleting) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Md3LightTextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Warning Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Md3LightErrorContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Md3LightError,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Permanently deleting ${selectedFiles.size} files from the selected branch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Md3LightError,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Files List
            Text(
                text = "Selected Files (${selectedFiles.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Md3LightTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp),
                shape = RoundedCornerShape(10.dp),
                color = Md3LightSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, Md3LightOutlineVariant)
            ) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(selectedFiles) { path ->
                        val fileName = path.substringAfterLast('/')
                        val meta = FileIcons.getMeta(fileName, false)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = meta.icon,
                                contentDescription = null,
                                tint = meta.color,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = Md3LightTextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Commit Message
            Text(
                text = "Commit Message Prefix",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Md3LightTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("batch_delete_commit_message_input"),
                singleLine = true,
                enabled = !isDeleting,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Md3LightError,
                    unfocusedBorderColor = Md3LightOutline,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Md3LightSurfaceVariant
                )
            )

            // Progress Bar
            if (isDeleting && progress != null) {
                val (done, total, cur) = progress
                val pct = if (total > 0) done.toFloat() / total else 0f

                Spacer(modifier = Modifier.height(14.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Deleting: $cur",
                            style = MaterialTheme.typography.labelSmall,
                            color = Md3LightTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$done / $total",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Md3LightPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        color = Md3LightError,
                        trackColor = Md3LightOutlineVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isDeleting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = Md3LightTextSecondary)
                }

                Button(
                    onClick = { onConfirmDelete(commitMessage) },
                    enabled = !isDeleting && selectedFiles.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Md3LightError),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("confirm_batch_delete_btn")
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Deleting Files...", color = Color.White)
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete (${selectedFiles.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
