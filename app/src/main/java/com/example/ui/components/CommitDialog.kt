package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GitHubBlue
import com.example.ui.theme.GitHubDarkBorder
import com.example.ui.theme.GitHubDarkSurface
import com.example.ui.theme.GitHubDarkSurfaceVariant
import com.example.ui.theme.GitHubDarkTextMuted
import com.example.ui.theme.GitHubDarkTextPrimary
import com.example.ui.theme.GitHubDarkTextSecondary
import com.example.ui.theme.GitHubGreenBright

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
        containerColor = GitHubDarkSurface,
        modifier = Modifier.testTag("commit_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
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
                        tint = GitHubGreenBright,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Commit Changes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GitHubDarkTextPrimary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = GitHubDarkTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // File Path Info Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = GitHubDarkSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, GitHubDarkBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Target File",
                        style = MaterialTheme.typography.labelSmall,
                        color = GitHubDarkTextMuted
                    )
                    Text(
                        text = filePath,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = GitHubBlue,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Commit Message
            Text(
                text = "Commit Message",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = GitHubDarkTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commit_message_input"),
                placeholder = { Text("Describe your changes...", color = GitHubDarkTextMuted) },
                maxLines = 3,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GitHubBlue,
                    unfocusedBorderColor = GitHubDarkBorder,
                    focusedContainerColor = GitHubDarkSurfaceVariant,
                    unfocusedContainerColor = GitHubDarkSurfaceVariant,
                    focusedTextColor = GitHubDarkTextPrimary,
                    unfocusedTextColor = GitHubDarkTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Target Branch
            Text(
                text = "Target Branch",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = GitHubDarkTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = targetBranch,
                onValueChange = { targetBranch = it },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ForkRight,
                        contentDescription = null,
                        tint = GitHubBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commit_branch_input"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GitHubBlue,
                    unfocusedBorderColor = GitHubDarkBorder,
                    focusedContainerColor = GitHubDarkSurfaceVariant,
                    unfocusedContainerColor = GitHubDarkSurfaceVariant,
                    focusedTextColor = GitHubDarkTextPrimary,
                    unfocusedTextColor = GitHubDarkTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", color = GitHubDarkTextSecondary)
                }

                Button(
                    onClick = { onConfirmCommit(commitMessage, targetBranch) },
                    enabled = !isCommitting && commitMessage.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GitHubGreenBright),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("confirm_commit_btn")
                ) {
                    if (isCommitting) {
                        CircularProgressIndicator(
                            color = GitHubDarkSurface,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Committing...", color = GitHubDarkSurface)
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
