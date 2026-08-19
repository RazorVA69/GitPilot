package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.ui.theme.GitHubBlue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrUploadModal(
    initialDirectory: String,
    currentBranch: String,
    existingDirectories: List<String> = emptyList(),
    isCommitting: Boolean,
    isUploading: Boolean = false,
    uploadProgress: Triple<Int, Int, String>? = null,
    onDismiss: () -> Unit,
    onCreateOrUpload: (
        targetDir: String,
        fileName: String,
        content: String,
        commitMessage: String,
        branch: String
    ) -> Unit,
    onUploadBatch: (
        targetDir: String,
        files: List<Pair<String, ByteArray>>,
        commitMessage: String,
        branch: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var targetDirectory by remember { mutableStateOf(initialDirectory) }
    var fileName by remember { mutableStateOf("") }
    var fileContent by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("") }
    var targetBranch by remember { mutableStateOf(currentBranch) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Files Upload, 1: Folder Upload, 2: New File, 3: New Folder, 4: Templates
    var showFolderDropdown by remember { mutableStateOf(false) }

    // Staged files for batch upload
    var stagedFiles by remember { mutableStateOf<List<Pair<String, ByteArray>>>(emptyList()) }
    var stagedSummary by remember { mutableStateOf("") }
    var isReadingFiles by remember { mutableStateOf(false) }

    // Device Storage Explorer Modal visibility
    var showDeviceStorageExplorer by remember { mutableStateOf(false) }

    // System File Picker (Fallback)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            isReadingFiles = true
            scope.launch(Dispatchers.IO) {
                val list = mutableListOf<Pair<String, ByteArray>>()
                for (uri in uris) {
                    val name = getFileNameFromUri(context, uri) ?: "uploaded_file_${System.currentTimeMillis()}"
                    val bytes = readBytesFromUri(context, uri)
                    if (bytes != null) {
                        list.add(name to bytes)
                    }
                }
                withContext(Dispatchers.Main) {
                    stagedFiles = list
                    stagedSummary = "${list.size} file(s) selected (${formatTotalBytes(list.sumOf { it.second.size.toLong() })})"
                    isReadingFiles = false
                }
            }
        }
    }

    // System Folder Picker (Fallback)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            isReadingFiles = true
            scope.launch(Dispatchers.IO) {
                val list = mutableListOf<Pair<String, ByteArray>>()
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.isDirectory) {
                    traverseDocumentTree(context, rootDoc, "", list)
                }
                withContext(Dispatchers.Main) {
                    stagedFiles = list
                    val folderName = rootDoc?.name ?: "Folder"
                    stagedSummary = "$folderName: ${list.size} file(s) (${formatTotalBytes(list.sumOf { it.second.size.toLong() })})"
                    isReadingFiles = false
                }
            }
        }
    }

    val templates = listOf(
        "Kotlin File" to ("Main.kt" to "package com.example\n\nfun main() {\n    println(\"Hello from Kotlin!\")\n}\n"),
        "Compose Screen" to ("MyScreen.kt" to "package com.example.ui\n\nimport androidx.compose.runtime.Composable\nimport androidx.compose.material3.Text\n\n@Composable\nfun MyScreen() {\n    Text(\"Welcome to MyScreen\")\n}\n"),
        "README.md" to ("README.md" to "# Project Title\n\nFast and modern GitHub file management.\n\n## Features\n- Fast exploration\n- Code Editor\n- Direct Commits\n"),
        ".gitignore" to (".gitignore" to "*.class\n*.log\n.gradle/\nbuild/\nlocal.properties\n.DS_Store\n"),
        "GitHub Workflow" to (".github/workflows/build.yml" to "name: Android CI\n\non: [push, pull_request]\n\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v4\n      - name: Set up JDK\n        uses: actions/setup-java@v4\n        with:\n          java-version: '17'\n          distribution: 'temurin'\n      - name: Build with Gradle\n        run: ./gradlew assembleDebug\n"),
        "config.json" to ("config.json" to "{\n  \"name\": \"app-config\",\n  \"version\": \"1.0.0\",\n  \"enabled\": true\n}\n"),
        "script.py" to ("script.py" to "def main():\n    print(\"Hello from Python!\")\n\nif __name__ == '__main__':\n    main()\n")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Md3LightSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = GitHubGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Add to Repository",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextPrimary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Md3LightTextSecondary)
                }
            }

            HorizontalDivider(color = Md3LightOutlineVariant, thickness = 0.5.dp)

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Md3LightSurfaceVariant,
                contentColor = Md3LightPrimary,
                edgePadding = 12.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Upload Files", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Upload Folder", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.DriveFolderUpload, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("New File", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("New Folder", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Templates", fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Target Directory Section with Quick Chips
                Text(
                    text = "Target Directory Path in Repository",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Md3LightTextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Path Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = targetDirectory.isBlank(),
                        onClick = { targetDirectory = "" },
                        label = { Text("Repo Root ( / )", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Md3LightPrimaryContainer,
                            selectedLabelColor = Md3LightPrimary
                        )
                    )

                    if (initialDirectory.isNotBlank()) {
                        FilterChip(
                            selected = targetDirectory == initialDirectory,
                            onClick = { targetDirectory = initialDirectory },
                            label = { Text("Current ($initialDirectory)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Md3LightPrimaryContainer,
                                selectedLabelColor = Md3LightPrimary
                            )
                        )
                    }

                    if (existingDirectories.isNotEmpty()) {
                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { showFolderDropdown = true },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Browse Repo Folders...", fontSize = 11.sp)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            )

                            DropdownMenu(
                                expanded = showFolderDropdown,
                                onDismissRequest = { showFolderDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Repo Root ( / )", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        targetDirectory = ""
                                        showFolderDropdown = false
                                    }
                                )
                                existingDirectories.take(20).forEach { dir ->
                                    DropdownMenuItem(
                                        text = { Text(dir) },
                                        onClick = {
                                            targetDirectory = dir
                                            showFolderDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Editable Target Directory Field
                OutlinedTextField(
                    value = targetDirectory,
                    onValueChange = { targetDirectory = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("target_dir_input"),
                    placeholder = { Text("e.g. src/main/kotlin or leave blank for root") },
                    leadingIcon = {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = GitHubYellow, modifier = Modifier.size(20.dp))
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Md3LightPrimary,
                        unfocusedBorderColor = Md3LightOutline
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // TAB 0: UPLOAD FILES (Device Storage Explorer & Pickers)
                if (selectedTab == 0) {
                    // Primary Custom Storage Explorer Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showDeviceStorageExplorer = true },
                        color = Color(0xFFE8F5E9),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, GitHubGreen)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = GitHubGreen,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Device Storage Explorer",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Browse device folders & multi-select multiple files / folders simultaneously",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary System File Picker Button
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Or choose via System File Picker", fontSize = 12.sp)
                    }

                    if (isReadingFiles) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Md3LightPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reading files...", fontSize = 12.sp, color = Md3LightTextSecondary)
                        }
                    }

                    if (stagedFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stagedSummary,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                stagedFiles.take(5).forEach { (name, bytes) ->
                                    Text(
                                        text = "• $name (${formatTotalBytes(bytes.size.toLong())})",
                                        fontSize = 11.sp,
                                        color = Md3LightTextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (stagedFiles.size > 5) {
                                    Text(
                                        text = "+ ${stagedFiles.size - 5} more files...",
                                        fontSize = 10.sp,
                                        color = Md3LightTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 1: UPLOAD FOLDERS (Device Storage Explorer & Pickers)
                if (selectedTab == 1) {
                    // Primary Custom Storage Explorer Card for Folders
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showDeviceStorageExplorer = true },
                        color = Color(0xFFE3F2FD),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, GitHubBlue)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFolderUpload,
                                contentDescription = null,
                                tint = GitHubBlue,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Device Storage Explorer",
                                    fontWeight = FontWeight.Bold,
                                    color = GitHubBlue,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Select single or multiple folders to recursively upload all files inside",
                                    fontSize = 11.sp,
                                    color = Md3LightTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary System Tree Picker
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DriveFolderUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Or choose via System Folder Picker", fontSize = 12.sp)
                    }

                    if (isReadingFiles) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Md3LightPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Indexing local directory...", fontSize = 12.sp, color = Md3LightTextSecondary)
                        }
                    }

                    if (stagedFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFE3F2FD),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stagedSummary,
                                    fontWeight = FontWeight.Bold,
                                    color = GitHubBlue,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                stagedFiles.take(5).forEach { (relPath, bytes) ->
                                    Text(
                                        text = "• $relPath (${formatTotalBytes(bytes.size.toLong())})",
                                        fontSize = 11.sp,
                                        color = Md3LightTextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (stagedFiles.size > 5) {
                                    Text(
                                        text = "+ ${stagedFiles.size - 5} more files...",
                                        fontSize = 10.sp,
                                        color = Md3LightTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 2: NEW FILE
                if (selectedTab == 2) {
                    Text(
                        text = "File Name & Extension",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_file_name_input"),
                        placeholder = { Text("e.g. MyService.kt, config.json, README.md") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Md3LightPrimary,
                            unfocusedBorderColor = Md3LightOutline
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Initial Content / Code",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("new_file_content_input"),
                        placeholder = { Text("Paste code or text content here...") },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Md3LightCodeBg,
                            unfocusedContainerColor = Md3LightCodeBg,
                            focusedBorderColor = Md3LightPrimary,
                            unfocusedBorderColor = Md3LightOutline
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // TAB 3: NEW FOLDER
                if (selectedTab == 3) {
                    Text(
                        text = "Folder Name",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. components, utils, assets") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Md3LightPrimary,
                            unfocusedBorderColor = Md3LightOutline
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Git requires a file to create a directory. A .gitkeep placeholder will be created inside.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Md3LightTextSecondary,
                        fontSize = 11.sp
                    )
                }

                // TAB 4: TEMPLATES
                if (selectedTab == 4) {
                    Text(
                        text = "Select a Starter Template",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { (label, data) ->
                            val (tName, tContent) = data
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        fileName = tName
                                        fileContent = tContent
                                        selectedTab = 2
                                    },
                                color = Md3LightSurfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Md3LightTextPrimary)
                                        Text(tName, fontSize = 11.sp, color = Md3LightPrimary, fontFamily = FontFamily.Monospace)
                                    }
                                    Text("Use", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Md3LightPrimary)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Commit Message
                Text(
                    text = "Commit Message",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Md3LightTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("commit_message_modal_input"),
                    placeholder = {
                        Text(
                            when (selectedTab) {
                                0 -> "Upload files to ${targetDirectory.ifBlank { "root" }}"
                                1 -> "Upload folder to ${targetDirectory.ifBlank { "root" }}"
                                3 -> "Create folder ${fileName.ifBlank { "new folder" }}"
                                else -> "Add ${fileName.ifBlank { "new file" }}"
                            }
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Md3LightPrimary,
                        unfocusedBorderColor = Md3LightOutline
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                // Upload Progress Bar if active
                if (isUploading && uploadProgress != null) {
                    val (completed, total, curFile) = uploadProgress
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Md3LightSurfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Uploading files to GitHub...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("$completed / $total", fontSize = 12.sp, color = Md3LightPrimary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(curFile, fontSize = 11.sp, color = Md3LightTextSecondary, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

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
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            when (selectedTab) {
                                0, 1 -> {
                                    if (stagedFiles.isNotEmpty()) {
                                        onUploadBatch(
                                            targetDirectory,
                                            stagedFiles,
                                            commitMessage.ifBlank { "Upload ${stagedFiles.size} file(s)" },
                                            targetBranch
                                        )
                                    }
                                }
                                3 -> {
                                    val folderName = fileName.trim().trim('/')
                                    if (folderName.isNotBlank()) {
                                        val folderPath = if (targetDirectory.isBlank()) folderName else "${targetDirectory.trim('/')}/$folderName"
                                        onCreateOrUpload(
                                            folderPath,
                                            ".gitkeep",
                                            "",
                                            commitMessage.ifBlank { "Create folder $folderName" },
                                            targetBranch
                                        )
                                    }
                                }
                                else -> {
                                    if (fileName.isNotBlank()) {
                                        onCreateOrUpload(
                                            targetDirectory,
                                            fileName.trim(),
                                            fileContent,
                                            commitMessage.ifBlank { "Create $fileName" },
                                            targetBranch
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("submit_create_file_btn"),
                        enabled = !isCommitting && !isUploading && (
                                ((selectedTab == 0 || selectedTab == 1) && stagedFiles.isNotEmpty()) ||
                                        (selectedTab == 2 && fileName.isNotBlank()) ||
                                        (selectedTab == 3 && fileName.isNotBlank()) ||
                                        (selectedTab == 4 && fileName.isNotBlank())
                                ),
                        colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isCommitting || isUploading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Committing...")
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (selectedTab == 0 || selectedTab == 1) "Upload & Commit" else "Create & Commit",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Built-in Device File & Folder Explorer Sheet
    if (showDeviceStorageExplorer) {
        DeviceStorageExplorerModal(
            onDismiss = { showDeviceStorageExplorer = false },
            onFilesSelected = { files, summary ->
                stagedFiles = files
                stagedSummary = summary
            }
        )
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = cursor.getString(index)
                }
            }
        }
    }
    return name ?: uri.lastPathSegment
}

private fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }
}

private fun traverseDocumentTree(
    context: Context,
    dirDoc: DocumentFile,
    relativePrefix: String,
    resultList: MutableList<Pair<String, ByteArray>>
) {
    for (file in dirDoc.listFiles()) {
        val relPath = if (relativePrefix.isEmpty()) file.name ?: "" else "$relativePrefix/${file.name}"
        if (file.isDirectory) {
            traverseDocumentTree(context, file, relPath, resultList)
        } else {
            val bytes = readBytesFromUri(context, file.uri)
            if (bytes != null) {
                resultList.add(relPath to bytes)
            }
        }
    }
}

private fun formatTotalBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val group = digitGroups.coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, group.toDouble())
    return String.format("%.1f %s", value, units[group]).replace(".0 ", " ")
}
