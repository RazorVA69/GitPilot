package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitSurface2
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3

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
        containerColor = GitSurface,
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
                        tint = GitAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Commit Changes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GitText1
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = GitText2, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // File Path Info Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = GitSurface2,
                border = BorderStroke(1.dp, GitBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Target File",
                        style = MaterialTheme.typography.labelSmall,
                        color = GitText2
                    )
                    Text(
                        text = filePath,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = GitAccent,
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
                color = GitText1
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commit_message_input"),
                placeholder = { Text("Describe changes...", color = GitText3, fontSize = 13.sp) },
                maxLines = 3,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GitAccent,
                    unfocusedBorderColor = GitBorder,
                    focusedContainerColor = GitSurface,
                    unfocusedContainerColor = GitSurface2
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Target Branch
            Text(
                text = "Target Branch",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = GitText1
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = targetBranch,
                onValueChange = { targetBranch = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target_branch_input"),
                leadingIcon = {
                    Icon(Icons.Default.ForkRight, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GitAccent,
                    unfocusedBorderColor = GitBorder,
                    focusedContainerColor = GitSurface,
                    unfocusedContainerColor = GitSurface2
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isCommitting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GitText1),
                    border = BorderStroke(1.dp, GitBorder)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = { onConfirmCommit(commitMessage, targetBranch) },
                    enabled = !isCommitting && commitMessage.isNotBlank() && targetBranch.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GitAccent),
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
                        Text("Pushing...", color = Color.White)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Commit & Push", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
