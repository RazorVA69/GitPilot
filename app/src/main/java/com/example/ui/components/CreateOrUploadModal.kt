package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GitHubGreen
import com.example.ui.theme.GitHubYellow
import com.example.ui.theme.Md3LightCodeBg
import com.example.ui.theme.Md3LightOutline
import com.example.ui.theme.Md3LightOutlineVariant
import com.example.ui.theme.Md3LightPrimary
import com.example.ui.theme.Md3LightPrimaryContainer
import com.example.ui.theme.Md3LightSurface
import com.example.ui.theme.Md3LightSurfaceVariant
import com.example.ui.theme.Md3LightTextPrimary
import com.example.ui.theme.Md3LightTextSecondary
import com.example.ui.theme.Md3LightTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrUploadModal(
    initialDirectory: String,
    currentBranch: String,
    isCommitting: Boolean,
    onDismiss: () -> Unit,
    onCreateOrUpload: (
        targetDir: String,
        fileName: String,
        content: String,
        commitMessage: String,
        branch: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var targetDirectory by remember { mutableStateOf(initialDirectory) }
    var fileName by remember { mutableStateOf("") }
    var fileContent by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("") }
    var targetBranch by remember { mutableStateOf(currentBranch) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val templates = listOf(
        "Kotlin File" to ("Main.kt" to "package com.example\n\nfun main() {\n    println(\"Hello from Kotlin!\")\n}\n"),
        "Compose Screen" to ("MyScreen.kt" to "package com.example.ui\n\nimport androidx.compose.runtime.Composable\nimport androidx.compose.material3.Text\n\n@Composable\nfun MyScreen() {\n    Text(\"Welcome to MyScreen\")\n}\n"),
        "README.md" to ("README.md" to "# Project Title\n\nFast and modern GitHub file management.\n\n## Features\n- Fast exploration\n- Code Editor\n- Direct Commits\n"),
        ".gitignore" to (".gitignore" to "*.class\n*.log\n.gradle/\nbuild/\nlocal.properties\n.DS_Store\n"),
        "config.json" to ("config.json" to "{\n  \"name\": \"app-config\",\n  \"version\": \"1.0.0\",\n  \"enabled\": true\n}\n"),
        "script.py" to ("script.py" to "def main():\n    print(\"Hello from Python!\")\n\nif __name__ == '__main__':\n    main()\n")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Md3LightSurface,
        modifier = Modifier.testTag("create_upload_modal")
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
                        imageVector = when (selectedTab) {
                            1 -> Icons.Default.CreateNewFolder
                            else -> Icons.Default.NoteAdd
                        },
                        contentDescription = null,
                        tint = GitHubGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (selectedTab) {
                            1 -> "Create New Folder"
                            2 -> "Starter Templates"
                            else -> "New File / Upload Content"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextPrimary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Md3LightTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Md3LightSurfaceVariant,
                contentColor = Md3LightPrimary,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Custom File", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        if (!fileName.endsWith("/.gitkeep")) {
                            fileName = if (fileName.isNotBlank()) "$fileName/.gitkeep" else "new_folder/.gitkeep"
                        }
                    },
                    text = { Text("New Folder", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Templates", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Target directory
            Text(
                text = "Target Directory Path (Point anywhere in repo)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Md3LightTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = targetDirectory,
                onValueChange = { targetDirectory = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target_directory_input"),
                placeholder = { Text("e.g. src/main/java or leave empty for root", color = Md3LightTextTertiary, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = GitHubYellow, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Md3LightPrimary,
                    unfocusedBorderColor = Md3LightOutline,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Md3LightSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // File Name
            Text(
                text = if (selectedTab == 1) "Folder Name (Will create .gitkeep inside)" else "File Name & Extension",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Md3LightTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = fileName,
                onValueChange = {
                    fileName = it
                    if (commitMessage.isBlank()) commitMessage = "Create $it"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("file_name_input"),
                placeholder = {
                    Text(
                        if (selectedTab == 1) "e.g. assets/icons/.gitkeep" else "e.g. DataService.kt, config.json",
                        color = Md3LightTextTertiary,
                        fontSize = 12.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Md3LightPrimary,
                    unfocusedBorderColor = Md3LightOutline,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Md3LightSurfaceVariant
                )
            )

            // Templates Strip
            if (selectedTab == 2) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((label, data) in templates) {
                        FilterChip(
                            selected = fileName == data.first,
                            onClick = {
                                fileName = data.first
                                fileContent = data.second
                                commitMessage = "Create ${data.first}"
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Md3LightPrimaryContainer,
                                selectedLabelColor = Md3LightPrimary
                            )
                        )
                    }
                }
            }

            // File Content Editor
            if (selectedTab != 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Initial Content / Code",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Md3LightTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = fileContent,
                    onValueChange = { fileContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("file_content_input"),
                    placeholder = { Text("Paste code or text here...", color = Md3LightTextTertiary, fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Md3LightTextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Md3LightPrimary,
                        unfocusedBorderColor = Md3LightOutline,
                        focusedContainerColor = Md3LightCodeBg,
                        unfocusedContainerColor = Md3LightCodeBg
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                    .testTag("create_commit_message_input"),
                placeholder = { Text("e.g. Add new file to repository", color = Md3LightTextTertiary, fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Md3LightPrimary,
                    unfocusedBorderColor = Md3LightOutline,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Md3LightSurfaceVariant
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
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = Md3LightTextSecondary)
                }

                Button(
                    onClick = {
                        val finalDir = targetDirectory.trim().trim('/')
                        val finalName = fileName.trim().trim('/')
                        val finalMsg = commitMessage.ifBlank { "Create $finalName" }
                        onCreateOrUpload(finalDir, finalName, fileContent, finalMsg, targetBranch)
                    },
                    enabled = !isCommitting && fileName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("confirm_create_upload_btn")
                ) {
                    if (isCommitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Creating...", color = Color.White)
                    } else {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create & Commit", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
