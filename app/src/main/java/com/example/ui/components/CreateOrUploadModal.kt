package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitAccentDeep
import com.example.ui.theme.GitAccentSoft
import com.example.ui.theme.GitBg
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitBorderStrong
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitSurface2
import com.example.ui.theme.GitSurface3
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3
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
    var showFolderPickerDialog by remember { mutableStateOf(false) }

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
    ) { uri ->
        if (uri != null) {
            isReadingFiles = true
            scope.launch(Dispatchers.IO) {
                val dirDoc = DocumentFile.fromTreeUri(context, uri)
                val list = mutableListOf<Pair<String, ByteArray>>()
                if (dirDoc != null) {
                    val rootFolderName = dirDoc.name ?: "folder"
                    traverseDocumentTree(context, dirDoc, rootFolderName, list)
                }
                withContext(Dispatchers.Main) {
                    stagedFiles = list
                    stagedSummary = "Folder: ${dirDoc?.name ?: ""} (${list.size} files, ${formatTotalBytes(list.sumOf { it.second.size.toLong() })})"
                    isReadingFiles = false
                }
            }
        }
    }

    val templates = remember {
        listOf(
            "README.md" to ("README.md" to "# Project\n\nA modern project repository.\n\n## Getting Started\n- Setup instructions\n"),
            ".gitignore" to (".gitignore" to "*.log\n.DS_Store\nbuild/\n.gradle/\nnode_modules/\n.env\n"),
            "LICENSE (MIT)" to ("LICENSE" to "MIT License\n\nCopyright (c) ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)}\n\nPermission is hereby granted, free of charge..."),
            "package.json" to ("package.json" to "{\n  \"name\": \"project\",\n  \"version\": \"1.0.0\",\n  \"main\": \"index.js\"\n}\n"),
            "build.gradle.kts" to ("build.gradle.kts" to "plugins {\n    kotlin(\"jvm\") version \"2.0.0\"\n}\n")
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GitSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(GitBorderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Header Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GitSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Add to Repository",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GitText1
                        )
                        Text(
                            text = "Branch: $targetBranch",
                            style = MaterialTheme.typography.labelSmall,
                            color = GitText2,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = GitText2)
                    }
                }
            }

            HorizontalDivider(color = GitBorder, thickness = 1.dp)

            // Segmented Scrollable Tabs (Light Modern tab bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(GitSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabTitles = listOf("Upload Files", "Upload Folder", "New File", "New Folder", "Templates")
                val tabIcons = listOf(
                    Icons.Default.UploadFile,
                    Icons.Default.DriveFolderUpload,
                    Icons.Default.NoteAdd,
                    Icons.Default.CreateNewFolder,
                    null
                )

                tabTitles.forEachIndexed { index, title ->
                    val isTabSelected = selectedTab == index
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTab = index },
                        color = if (isTabSelected) GitAccentSoft else GitSurface,
                        border = BorderStroke(1.dp, if (isTabSelected) GitAccent else GitBorderStrong),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabIcons[index]?.let { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (isTabSelected) GitAccent else GitText2
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTabSelected) GitAccent else GitText1
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = GitBorder, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Target Directory Section
                Text(
                    text = "TARGET DIRECTORY PATH IN REPOSITORY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = GitText2,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Path Selector Chips (Always keeping Browse Repo Folders accessible even with long paths)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Browse Folders Primary Quick Action Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showFolderPickerDialog = true },
                        color = GitSurface,
                        border = BorderStroke(1.dp, GitBorderStrong),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = GitAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse Folders...", fontSize = 11.5.sp, color = GitText1, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = GitText2)
                        }
                    }

                    // Repo Root Chip
                    val isRoot = targetDirectory.isBlank()
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { targetDirectory = "" },
                        color = if (isRoot) GitAccentSoft else GitSurface,
                        border = BorderStroke(1.dp, if (isRoot) GitAccent else GitBorderStrong),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = if (isRoot) GitAccent else GitText2,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Repo Root ( / )",
                                fontSize = 11.5.sp,
                                fontWeight = if (isRoot) FontWeight.Bold else FontWeight.Medium,
                                color = if (isRoot) GitAccent else GitText1
                            )
                        }
                    }

                    // Current Folder Chip (Truncated with max width so it NEVER pushes other buttons off-screen)
                    if (initialDirectory.isNotBlank()) {
                        val isInitial = targetDirectory == initialDirectory
                        val displayDirName = initialDirectory.substringAfterLast('/')
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { targetDirectory = initialDirectory },
                            color = if (isInitial) GitAccentSoft else GitSurface,
                            border = BorderStroke(1.dp, if (isInitial) GitAccent else GitBorderStrong),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = if (isInitial) GitAccent else GitText2,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Current ($displayDirName)",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isInitial) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isInitial) GitAccent else GitText1,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 160.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Target Directory Text Input with Trailing Folder Picker Action
                OutlinedTextField(
                    value = targetDirectory,
                    onValueChange = { targetDirectory = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("target_dir_input"),
                    placeholder = { Text("Leave blank for repo root or enter folder path...", color = GitText3, fontSize = 12.5.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = GitText2, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { showFolderPickerDialog = true }) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = "Browse Repository Folders",
                                tint = GitAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GitAccent,
                        unfocusedBorderColor = GitBorderStrong,
                        focusedContainerColor = GitSurface,
                        unfocusedContainerColor = GitSurface
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // TAB 0: UPLOAD FILES
                if (selectedTab == 0) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showDeviceStorageExplorer = true },
                        colors = CardDefaults.cardColors(containerColor = GitSurface),
                        border = BorderStroke(1.dp, GitBorderStrong),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GitAccentSoft,
                                border = BorderStroke(1.dp, GitAccent.copy(alpha = 0.3f)),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Smartphone,
                                        contentDescription = null,
                                        tint = GitAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Device File Explorer",
                                        fontWeight = FontWeight.Bold,
                                        color = GitText1,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = GitAccent
                                    ) {
                                        Text(
                                            text = "MULTI-SELECT",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Browse storage with folder pinning, sorting, and multi-file selection",
                                    fontSize = 11.5.sp,
                                    color = GitText2
                                )
                            }

                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GitText2, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = GitBorder)
                        Text(
                            text = "or",
                            fontSize = 11.sp,
                            color = GitText3,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = GitBorder)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GitBorderStrong),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GitText1)
                    ) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp), tint = GitText2)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose via System File Picker", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    if (isReadingFiles) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = GitAccent, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reading files...", fontSize = 12.sp, color = GitText2)
                        }
                    }

                    if (stagedFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = GitAccentSoft,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, GitAccent.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stagedSummary,
                                    fontWeight = FontWeight.Bold,
                                    color = GitAccentDeep,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                stagedFiles.take(5).forEach { (name, bytes) ->
                                    Text(
                                        text = "• $name (${formatTotalBytes(bytes.size.toLong())})",
                                        fontSize = 11.sp,
                                        color = GitText1,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (stagedFiles.size > 5) {
                                    Text(
                                        text = "+ ${stagedFiles.size - 5} more files...",
                                        fontSize = 10.sp,
                                        color = GitText2
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 1: UPLOAD FOLDERS
                if (selectedTab == 1) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showDeviceStorageExplorer = true },
                        colors = CardDefaults.cardColors(containerColor = GitAccentSoft),
                        border = BorderStroke(1.5.dp, GitAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GitSurface,
                                border = BorderStroke(1.dp, GitAccent.copy(alpha = 0.3f)),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DriveFolderUpload,
                                        contentDescription = null,
                                        tint = GitAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Device Folder Explorer",
                                    fontWeight = FontWeight.Bold,
                                    color = GitText1,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Select single or multiple folders to upload recursively",
                                    fontSize = 11.5.sp,
                                    color = GitText2
                                )
                            }

                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GitAccent, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GitBorderStrong),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GitText1)
                    ) {
                        Icon(Icons.Default.DriveFolderUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = GitText2)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Or choose via System Folder Picker", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    if (isReadingFiles) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = GitAccent, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Indexing local directory...", fontSize = 12.sp, color = GitText2)
                        }
                    }

                    if (stagedFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = GitAccentSoft,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, GitAccent.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stagedSummary,
                                    fontWeight = FontWeight.Bold,
                                    color = GitAccentDeep,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                stagedFiles.take(5).forEach { (relPath, bytes) ->
                                    Text(
                                        text = "• $relPath (${formatTotalBytes(bytes.size.toLong())})",
                                        fontSize = 11.sp,
                                        color = GitText1,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (stagedFiles.size > 5) {
                                    Text(
                                        text = "+ ${stagedFiles.size - 5} more files...",
                                        fontSize = 10.sp,
                                        color = GitText2
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 2: NEW FILE
                if (selectedTab == 2) {
                    Text(
                        text = "FILE NAME & EXTENSION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GitText2,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_file_name_input"),
                        placeholder = { Text("e.g. MyService.kt, config.json, README.md", color = GitText3, fontSize = 12.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GitAccent,
                            unfocusedBorderColor = GitBorderStrong,
                            focusedContainerColor = GitSurface,
                            unfocusedContainerColor = GitSurface
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "INITIAL CONTENT / CODE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GitText2,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("new_file_content_input"),
                        placeholder = { Text("Paste code or text content here...", color = GitText3, fontSize = 12.sp) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = GitText1),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GitSurface,
                            unfocusedContainerColor = GitSurface,
                            focusedBorderColor = GitAccent,
                            unfocusedBorderColor = GitBorderStrong
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // TAB 3: NEW FOLDER
                if (selectedTab == 3) {
                    Text(
                        text = "FOLDER NAME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GitText2,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. components, utils, assets", color = GitText3, fontSize = 12.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GitAccent,
                            unfocusedBorderColor = GitBorderStrong,
                            focusedContainerColor = GitSurface,
                            unfocusedContainerColor = GitSurface
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Git requires a file to create a directory. A .gitkeep placeholder will be created inside.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GitText3,
                        fontSize = 11.sp
                    )
                }

                // TAB 4: TEMPLATES
                if (selectedTab == 4) {
                    Text(
                        text = "SELECT A STARTER TEMPLATE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GitText2,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { (label, data) ->
                            val (tName, tContent) = data
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        fileName = tName
                                        fileContent = tContent
                                        selectedTab = 2
                                    },
                                color = GitSurface,
                                border = BorderStroke(1.dp, GitBorderStrong)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GitText1)
                                        Text(tName, fontSize = 11.sp, color = GitAccent, fontFamily = FontFamily.Monospace)
                                    }
                                    Text("Use", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GitAccent)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Commit Message
                Text(
                    text = "COMMIT MESSAGE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = GitText2,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
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
                            },
                            color = GitText3,
                            fontSize = 12.5.sp
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GitAccent,
                        unfocusedBorderColor = GitBorderStrong,
                        focusedContainerColor = GitSurface,
                        unfocusedContainerColor = GitSurface
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Upload Progress Bar if active
                if (isUploading && uploadProgress != null) {
                    val (completed, total, curFile) = uploadProgress
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GitAccentSoft,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GitAccent.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Uploading files to GitHub...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GitText1)
                                Text("$completed / $total", fontSize = 12.sp, color = GitAccent, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(curFile, fontSize = 11.sp, color = GitText2, maxLines = 1, fontFamily = FontFamily.Monospace)
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
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GitBorderStrong),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GitText1)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
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
                            .weight(1.2f)
                            .testTag("submit_create_file_btn"),
                        enabled = !isCommitting && !isUploading && (
                                ((selectedTab == 0 || selectedTab == 1) && stagedFiles.isNotEmpty()) ||
                                        (selectedTab == 2 && fileName.isNotBlank()) ||
                                        (selectedTab == 3 && fileName.isNotBlank()) ||
                                        (selectedTab == 4 && fileName.isNotBlank())
                                ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GitText1,
                            contentColor = Color.White,
                            disabledContainerColor = GitSurface3,
                            disabledContentColor = GitText3
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isCommitting || isUploading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Committing...", color = Color.White, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (selectedTab == 0 || selectedTab == 1) "Upload & Commit" else "Create & Commit",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
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

    // Repository Folder Picker Dialog (Fast, searchable, beautiful Light Modern aesthetic)
    if (showFolderPickerDialog) {
        FolderPickerModalDialog(
            directories = existingDirectories,
            currentSelection = targetDirectory,
            onSelectFolder = { selected ->
                targetDirectory = selected
                showFolderPickerDialog = false
            },
            onDismiss = { showFolderPickerDialog = false }
        )
    }
}

@Composable
private fun FolderPickerModalDialog(
    directories: List<String>,
    currentSelection: String,
    onSelectFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isFullscreen by remember { mutableStateOf(false) }

    val filteredDirectories = remember(directories, searchQuery) {
        val sorted = directories.sorted()
        if (searchQuery.isBlank()) sorted
        else sorted.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isFullscreen) 0.98f else 0.94f)
                .fillMaxHeight(if (isFullscreen) 0.94f else 0.72f)
                .clip(RoundedCornerShape(16.dp)),
            color = GitSurface,
            border = BorderStroke(1.dp, GitBorderStrong),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GitSurface)
                    .padding(20.dp)
            ) {
                // Header Bar with Fullscreen toggle and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GitSurface,
                            border = BorderStroke(1.dp, GitBorderStrong),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = GitAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Browse Repository Folders",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GitText1,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${directories.size} folders in repository",
                                style = MaterialTheme.typography.labelSmall,
                                color = GitText2
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isFullscreen = !isFullscreen },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.CloseFullscreen else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Expand Fullscreen",
                                tint = GitText2,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = GitText2, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Filter Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Filter repository folders...", color = GitText3, fontSize = 12.5.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = GitText2, modifier = Modifier.size(17.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = GitText2, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GitAccent,
                        unfocusedBorderColor = GitBorderStrong,
                        focusedContainerColor = GitSurface,
                        unfocusedContainerColor = GitSurface
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Folder List (Takes available height dynamically)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Always show Repo Root ( / ) at top matching SS 8
                    item {
                        val isSelected = currentSelection.isBlank()
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectFolder("") },
                            color = if (isSelected) GitAccentSoft else GitSurface,
                            border = BorderStroke(1.dp, if (isSelected) GitAccent else GitBorderStrong),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) GitAccent else GitText2,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Repo Root ( / )",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) GitAccent else GitText1
                                    )
                                }
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = GitAccent
                                    ) {
                                        Text(
                                            text = "SELECTED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = GitBorder,
                            thickness = 0.5.dp
                        )
                    }

                    if (filteredDirectories.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching folders found for \"$searchQuery\"",
                                    color = GitText3,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }

                    items(filteredDirectories) { dirPath ->
                        val isSelected = currentSelection == dirPath
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectFolder(dirPath) },
                            color = if (isSelected) GitAccentSoft else GitSurface,
                            border = BorderStroke(1.dp, if (isSelected) GitAccent else GitBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) GitAccent else GitText2,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = dirPath,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GitAccent else GitText1,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = GitAccent
                                    ) {
                                        Text(
                                            text = "SELECTED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 38.dp),
                            color = GitBorder,
                            thickness = 0.5.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer Close Button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = GitSurface,
                        contentColor = GitText1
                    ),
                    border = BorderStroke(1.dp, GitBorderStrong)
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
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
