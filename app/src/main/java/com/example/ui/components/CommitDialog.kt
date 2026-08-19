package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GitHubGreen
import com.example.ui.theme.Md3LightOutline
import com.example.ui.theme.Md3LightOutlineVariant
import com.example.ui.theme.Md3LightPrimary
import com.example.ui.theme.Md3LightSurface
import com.example.ui.theme.Md3LightSurfaceVariant
import com.example.ui.theme.Md3LightTextPrimary
import com.example.ui.theme.Md3LightTextSecondary
import com.example.ui.theme.Md3LightTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDialog(
    filePath: String,
    currentBranch: String,
    isCommitting: Boolean,
    onDismiss: () -> Unit,
    onConfirmCommit: (message: String, branch: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fileName = remember(filePath) { filePath.substringAfterLast('/') }
    var commitMessage by remember { mutableStateOf("Update $fileName") }
    var targetBranch by remember { mutableStateOf(currentBranch) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Md3LightSurface,
        modifier = Modifier.testTag("commit_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Commit,
                        contentDescription = null,
                        tint = GitHubGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Commit Changes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextPrimary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Md3LightTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // File Path Info Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Md3LightSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, Md3LightOutlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Target File",
                        style = MaterialTheme.typography.labelSmall,
                        color = Md3LightTextSecondary
                    )
                    Text(
                        text = filePath,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = Md3LightPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Commit Message
            Text(
                text = "Commit Message",
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
                    .testTag("commit_message_input"),
                placeholder = { Text("Describe changes...", color = Md3LightTextTertiary) },
                maxLines = 3,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Md3LightPrimary,
                    unfocusedBorderColor = Md3LightOutline,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Md3LightSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Target Branch
            Text(
                text = "Target Branch",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Md3LightTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = targetBranch,
                onValueChange = { targetBranch = it },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ForkRight,
                        contentDescription = null,
                        tint = Md3LightPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commit_branch_input"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Md3LightPrimary,
                    unfocusedBorderColor = Md3LightOutline,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Md3LightSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = Md3LightTextSecondary)
                }

                Button(
                    onClick = { onConfirmCommit(commitMessage, targetBranch) },
                    enabled = !isCommitting && commitMessage.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("confirm_commit_btn")
                ) {
                    if (isCommitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Committing...", color = Color.White)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Commit Directly", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
