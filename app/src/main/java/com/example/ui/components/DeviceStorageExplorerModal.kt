package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GitHubBlue
import com.example.ui.theme.GitHubGreen
import com.example.ui.theme.GitHubYellow
import com.example.ui.theme.Md3LightBackground
import com.example.ui.theme.Md3LightOutline
import com.example.ui.theme.Md3LightOutlineVariant
import com.example.ui.theme.Md3LightPrimary
import com.example.ui.theme.Md3LightPrimaryContainer
import com.example.ui.theme.Md3LightSurface
import com.example.ui.theme.Md3LightSurfaceVariant
import com.example.ui.theme.Md3LightTextPrimary
import com.example.ui.theme.Md3LightTextSecondary
import com.example.ui.theme.Md3LightTextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DeviceFolderSortBy {
    NAME,
    DATE_MODIFIED,
    SIZE,
    TYPE
}

data class LocalFileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeviceStorageExplorerModal(
    onDismiss: () -> Unit,
    onFilesSelected: (files: List<Pair<String, ByteArray>>, summary: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("device_explorer_prefs", Context.MODE_PRIVATE) }

    var hasAllFilesPermission by remember {
        mutableStateOf(checkAllFilesPermission(context))
    }

    val defaultRoot = remember {
        Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
    }

    var currentDir by remember { mutableStateOf(defaultRoot) }
    var fileList by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Sorting State
    var sortBy by remember { mutableStateOf(DeviceFolderSortBy.TYPE) }
    var isSortReversed by remember { mutableStateOf(false) }
    var showSortDropdown by remember { mutableStateOf(false) }

    // Pinned Device Folders State
    var pinnedFolderPaths by remember {
        val saved = prefs.getStringSet("pinned_device_dirs", null)
        val initial = saved?.toList() ?: listOf(
            defaultRoot.absolutePath,
            File(defaultRoot, "Download").absolutePath,
            File(defaultRoot, "Documents").absolutePath
        )
        mutableStateOf(initial.filter { File(it).exists() || it == defaultRoot.absolutePath })
    }

    var folderToUnpinPrompt by remember { mutableStateOf<String?>(null) }

    // Selected items (Absolute paths of selected files and folders)
    val selectedPaths = remember { mutableStateListOf<String>() }

    var isCollectingFiles by remember { mutableStateOf(false) }

    fun savePinnedFolders(paths: List<String>) {
        pinnedFolderPaths = paths
        prefs.edit().putStringSet("pinned_device_dirs", paths.toSet()).apply()
    }

    fun pinCurrentFolder() {
        val current = currentDir.absolutePath
        if (!pinnedFolderPaths.contains(current)) {
            val updated = pinnedFolderPaths + current
            savePinnedFolders(updated)
        }
    }

    fun unpinFolder(path: String) {
        val updated = pinnedFolderPaths.filter { it != path }
        savePinnedFolders(updated)
    }

    // Refresh file list in directory
    fun refreshDirectory(dir: File) {
        isLoadingFiles = true
        scope.launch(Dispatchers.IO) {
            val items = try {
                dir.listFiles()?.map { f ->
                    LocalFileItem(
                        file = f,
                        name = f.name,
                        isDirectory = f.isDirectory,
                        size = if (f.isFile) f.length() else 0L,
                        lastModified = f.lastModified(),
                        extension = if (f.isFile && f.name.contains('.')) f.name.substringAfterLast('.').lowercase() else ""
                    )
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                fileList = items
                isLoadingFiles = false
            }
        }
    }

    LaunchedEffect(currentDir) {
        refreshDirectory(currentDir)
    }

    // Filter & Sort
    val displayedList = remember(fileList, searchQuery, sortBy, isSortReversed) {
        val filtered = if (searchQuery.isBlank()) fileList
        else fileList.filter { it.name.contains(searchQuery, ignoreCase = true) }

        val sorted = when (sortBy) {
            DeviceFolderSortBy.TYPE -> filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            DeviceFolderSortBy.NAME -> filtered.sortedBy { it.name.lowercase() }
            DeviceFolderSortBy.DATE_MODIFIED -> filtered.sortedByDescending { it.lastModified }
            DeviceFolderSortBy.SIZE -> filtered.sortedByDescending { it.size }
        }

        if (isSortReversed) sorted.reversed() else sorted
    }

    fun requestManageStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        }
    }

    fun stageAndConfirmSelection() {
        if (selectedPaths.isEmpty()) return

        isCollectingFiles = true

        scope.launch(Dispatchers.IO) {
            val finalFiles = mutableListOf<Pair<String, ByteArray>>()
            var folderCount = 0
            var fileCount = 0

            for (path in selectedPaths) {
                val targetFile = File(path)
                if (targetFile.exists()) {
                    if (targetFile.isDirectory) {
                        folderCount++
                        val baseParentName = targetFile.name
                        collectFilesInDirectory(targetFile, baseParentName, finalFiles)
                    } else if (targetFile.isFile) {
                        fileCount++
                        val bytes = try { targetFile.readBytes() } catch (e: Exception) { null }
                        if (bytes != null) {
                            finalFiles.add(targetFile.name to bytes)
                        }
                    }
                }
            }

            val totalSize = finalFiles.sumOf { it.second.size.toLong() }
            val summaryText = if (folderCount > 0) {
                "$folderCount folder(s), $fileCount file(s) (${finalFiles.size} total items, ${formatFileSize(totalSize)})"
            } else {
                "${finalFiles.size} file(s) selected (${formatFileSize(totalSize)})"
            }

            withContext(Dispatchers.Main) {
                isCollectingFiles = false
                onFilesSelected(finalFiles, summaryText)
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Md3LightSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
        ) {
            // Header Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Md3LightSurface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = GitHubBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Device File Explorer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Md3LightTextPrimary
                            )
                            Text(
                                text = "Select any files or folders to upload",
                                style = MaterialTheme.typography.labelSmall,
                                color = Md3LightTextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isSearchExpanded) Md3LightPrimary else Md3LightTextSecondary
                            )
                        }

                        // Sort Menu Button
                        Box {
                            IconButton(onClick = { showSortDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort",
                                    tint = Md3LightTextSecondary
                                )
                            }

                            DropdownMenu(
                                expanded = showSortDropdown,
                                onDismissRequest = { showSortDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Folders First")
                                            if (sortBy == DeviceFolderSortBy.TYPE) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(Icons.Default.Check, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        sortBy = DeviceFolderSortBy.TYPE
                                        showSortDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Name")
                                            if (sortBy == DeviceFolderSortBy.NAME) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(Icons.Default.Check, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        sortBy = DeviceFolderSortBy.NAME
                                        showSortDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Date Modified")
                                            if (sortBy == DeviceFolderSortBy.DATE_MODIFIED) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(Icons.Default.Check, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        sortBy = DeviceFolderSortBy.DATE_MODIFIED
                                        showSortDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Size")
                                            if (sortBy == DeviceFolderSortBy.SIZE) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(Icons.Default.Check, contentDescription = null, tint = GitHubBlue, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        sortBy = DeviceFolderSortBy.SIZE
                                        showSortDropdown = false
                                    }
                                )
                            }
                        }

                        // Reverse Order Toggle Button
                        IconButton(onClick = { isSortReversed = !isSortReversed }) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Reverse Sort",
                                tint = if (isSortReversed) Md3LightPrimary else Md3LightTextSecondary
                            )
                        }

                        IconButton(onClick = { refreshDirectory(currentDir) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Md3LightTextSecondary)
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Md3LightTextSecondary)
                        }
                    }
                }
            }

            HorizontalDivider(color = Md3LightOutlineVariant, thickness = 0.5.dp)

            // Permission Banner (if not yet full access)
            if (!hasAllFilesPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFF3E0),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Grant All Files Access for full device storage browsing",
                                fontSize = 11.sp,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Button(
                            onClick = {
                                requestManageStoragePermission()
                                hasAllFilesPermission = checkAllFilesPermission(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Grant Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Pinned Device Folders Quick-Access Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pin Label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = GitHubYellow,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pinned:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Md3LightTextSecondary
                    )
                }

                // Render Pinned Folder Chips
                pinnedFolderPaths.forEach { path ->
                    val file = File(path)
                    val label = if (path == defaultRoot.absolutePath) "Storage Root" else file.name
                    val isCurrent = currentDir.absolutePath == path

                    FilterChip(
                        selected = isCurrent,
                        onClick = {
                            if (file.exists() && file.canRead()) {
                                currentDir = file
                            }
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isCurrent) Md3LightPrimary else GitHubYellow
                            )
                        },
                        trailingIcon = {
                            if (path != defaultRoot.absolutePath) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Unpin",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { folderToUnpinPrompt = path },
                                    tint = Md3LightTextTertiary
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Md3LightPrimaryContainer,
                            selectedLabelColor = Md3LightPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Button to Pin Current Directory
                val isAlreadyPinned = pinnedFolderPaths.contains(currentDir.absolutePath)
                if (!isAlreadyPinned) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { pinCurrentFolder() },
                        color = GitHubGreen.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GitHubGreen.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = GitHubGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Pin Current",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GitHubGreen
                            )
                        }
                    }
                }
            }

            // Search Bar (if visible)
            AnimatedVisibility(visible = isSearchExpanded) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Filter items in folder...", fontSize = 12.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Md3LightPrimary,
                        unfocusedBorderColor = Md3LightOutline
                    )
                )
            }

            // Path Breadcrumbs & Up Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Md3LightSurfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back / Up button
                val parentFile = currentDir.parentFile
                IconButton(
                    onClick = { if (parentFile != null && parentFile.canRead()) currentDir = parentFile },
                    enabled = parentFile != null && parentFile.canRead(),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Parent Directory",
                        tint = if (parentFile != null && parentFile.canRead()) Md3LightPrimary else Md3LightTextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = currentDir.absolutePath.replace("/storage/emulated/0", "Internal Storage"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Md3LightTextPrimary
                )
            }

            HorizontalDivider(color = Md3LightOutlineVariant, thickness = 0.5.dp)

            // Selection Summary Bar & Select All
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${selectedPaths.size} selected",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedPaths.isNotEmpty()) GitHubGreen else Md3LightTextSecondary
                    )
                    if (selectedPaths.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            onClick = { selectedPaths.clear() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Clear", fontSize = 11.sp, color = Md3LightTextSecondary)
                        }
                    }
                }

                TextButton(
                    onClick = {
                        val allInDir = displayedList.map { it.file.absolutePath }
                        if (selectedPaths.containsAll(allInDir)) {
                            selectedPaths.removeAll(allInDir.toSet())
                        } else {
                            allInDir.forEach { if (!selectedPaths.contains(it)) selectedPaths.add(it) }
                        }
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Select All in Folder", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GitHubBlue)
                }
            }

            HorizontalDivider(color = Md3LightOutlineVariant, thickness = 0.5.dp)

            // Main File & Folder List
            Box(modifier = Modifier.weight(1f)) {
                if (isLoadingFiles) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GitHubBlue, modifier = Modifier.size(32.dp))
                    }
                } else if (displayedList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Md3LightTextTertiary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Folder is empty or unreadable", color = Md3LightTextSecondary, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(displayedList, key = { it.file.absolutePath }) { item ->
                            val isSelected = selectedPaths.contains(item.file.absolutePath)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (item.isDirectory) {
                                            currentDir = item.file
                                        } else {
                                            if (isSelected) selectedPaths.remove(item.file.absolutePath)
                                            else selectedPaths.add(item.file.absolutePath)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Multi-select Checkbox on BOTH files and folders
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked == true) {
                                            if (!selectedPaths.contains(item.file.absolutePath)) {
                                                selectedPaths.add(item.file.absolutePath)
                                            }
                                        } else {
                                            selectedPaths.remove(item.file.absolutePath)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = GitHubGreen,
                                        uncheckedColor = Md3LightOutline
                                    ),
                                    modifier = Modifier.size(32.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                // Icon
                                if (item.isDirectory) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Folder",
                                        tint = GitHubYellow,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    FileIconForExtension(
                                        extension = item.extension,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Name & Meta (NO repetitive "Folder (tap to open)" text)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (item.isDirectory) FontWeight.Bold else FontWeight.Medium,
                                        color = Md3LightTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!item.isDirectory) {
                                        Text(
                                            text = formatFileSize(item.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Md3LightTextSecondary
                                        )
                                    }
                                }

                                if (item.isDirectory) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Open",
                                        tint = Md3LightTextTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 58.dp),
                                color = Md3LightOutlineVariant.copy(alpha = 0.4f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }

            // Bottom Confirmation Action Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Md3LightSurface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { stageAndConfirmSelection() },
                        enabled = selectedPaths.isNotEmpty() && !isCollectingFiles,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isCollectingFiles) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reading Files...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Select ${selectedPaths.size} Item(s)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Unpin Confirmation Dialog
    folderToUnpinPrompt?.let { path ->
        val name = File(path).name
        AlertDialog(
            onDismissRequest = { folderToUnpinPrompt = null },
            title = { Text("Unpin Folder") },
            text = { Text("Remove \"$name\" from pinned folders?") },
            confirmButton = {
                Button(
                    onClick = {
                        unpinFolder(path)
                        folderToUnpinPrompt = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GitHubGreen)
                ) {
                    Text("Unpin")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToUnpinPrompt = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun collectFilesInDirectory(
    dir: File,
    relativeParentPath: String,
    resultList: MutableList<Pair<String, ByteArray>>
) {
    val files = dir.listFiles() ?: return
    for (f in files) {
        if (f.isDirectory) {
            val childPath = "$relativeParentPath/${f.name}"
            collectFilesInDirectory(f, childPath, resultList)
        } else if (f.isFile) {
            val bytes = try { f.readBytes() } catch (e: Exception) { null }
            if (bytes != null) {
                val fullRelativePath = "$relativeParentPath/${f.name}"
                resultList.add(fullRelativePath to bytes)
            }
        }
    }
}

private fun checkAllFilesPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024f * 1024f * 1024f))
    }
}
