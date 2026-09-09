package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitAccentSoft
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitBorderStrong
import com.example.ui.theme.GitButtonPrimary
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    // Sorting State - Remembered in SharedPreferences
    var foldersFirst by rememberSaveable {
        mutableStateOf(prefs.getBoolean("folders_first", true))
    }
    var sortFoldersByName by rememberSaveable {
        mutableStateOf(prefs.getBoolean("sort_folders_by_name", true))
    }
    var sortBy by rememberSaveable {
        val savedName = prefs.getString("sort_by", DeviceFolderSortBy.NAME.name) ?: DeviceFolderSortBy.NAME.name
        mutableStateOf(try { DeviceFolderSortBy.valueOf(savedName) } catch (_: Exception) { DeviceFolderSortBy.NAME })
    }
    var isSortReversed by rememberSaveable {
        mutableStateOf(prefs.getBoolean("sort_reversed", false))
    }
    var showSortDropdown by remember { mutableStateOf(false) }

    fun updateSort(
        newSortBy: DeviceFolderSortBy = sortBy,
        newSortReversed: Boolean = isSortReversed,
        newFoldersFirst: Boolean = foldersFirst,
        newSortFoldersByName: Boolean = sortFoldersByName
    ) {
        sortBy = newSortBy
        isSortReversed = newSortReversed
        foldersFirst = newFoldersFirst
        sortFoldersByName = newSortFoldersByName
        prefs.edit()
            .putString("sort_by", newSortBy.name)
            .putBoolean("sort_reversed", newSortReversed)
            .putBoolean("folders_first", newFoldersFirst)
            .putBoolean("sort_folders_by_name", newSortFoldersByName)
            .apply()
    }

    // Intercept Back Press: Navigate up a directory instead of closing File Explorer, or dismiss if at root
    BackHandler(enabled = true) {
        val parent = currentDir.parentFile
        val canGoUp = parent != null && parent.canRead() && currentDir.absolutePath != defaultRoot.absolutePath
        if (canGoUp) {
            currentDir = parent!!
        } else {
            onDismiss()
        }
    }

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
        scope.launch(Dispatchers.IO) {
            val items = try {
                dir.listFiles()?.map { f ->
                    LocalFileItem(
                        file = f,
                        name = f.name,
                        isDirectory = f.isDirectory,
                        size = if (f.isFile) f.length() else 0L,
                        lastModified = f.lastModified(),
                        extension = if (f.isFile && f.name.contains('.')) f.name.substringAfterLast('.').lowercase(Locale.ROOT) else ""
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

    // Initial directory load
    LaunchedEffect(currentDir) {
        isLoadingFiles = true
        refreshDirectory(currentDir)
    }

    // Automatic file discovery using FileObserver on currentDir
    DisposableEffect(currentDir) {
        val path = currentDir.absolutePath
        val eventsMask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MODIFY or
                FileObserver.MOVED_TO or FileObserver.MOVED_FROM or FileObserver.CLOSE_WRITE

        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(File(path), eventsMask) {
                override fun onEvent(event: Int, eventPath: String?) {
                    refreshDirectory(currentDir)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(path, eventsMask) {
                override fun onEvent(event: Int, eventPath: String?) {
                    refreshDirectory(currentDir)
                }
            }
        }

        try {
            observer.startWatching()
        } catch (_: Exception) {}

        onDispose {
            try {
                observer.stopWatching()
            } catch (_: Exception) {}
        }
    }

    // Automatic refresh when app resumes from background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentDir) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAllFilesPermission = checkAllFilesPermission(context)
                refreshDirectory(currentDir)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Periodic auto-sync to discover background file creations
    LaunchedEffect(currentDir) {
        while (true) {
            delay(3000)
            val currentOnDisk = try { currentDir.listFiles() } catch (_: Exception) { null }
            if (currentOnDisk != null && currentOnDisk.size != fileList.size) {
                refreshDirectory(currentDir)
            }
        }
    }

    // Filter & Sort with "Sort Folders By Name" and "Reverse inside Sort"
    val displayedList = remember(fileList, searchQuery, sortBy, isSortReversed, foldersFirst, sortFoldersByName) {
        val filtered = if (searchQuery.isBlank()) fileList
        else fileList.filter { it.name.contains(searchQuery, ignoreCase = true) }

        if (foldersFirst) {
            val (folders, files) = filtered.partition { it.isDirectory }
            val sortedFolders = if (sortFoldersByName) {
                folders.sortedBy { it.name.lowercase(Locale.ROOT) }
            } else {
                when (sortBy) {
                    DeviceFolderSortBy.NAME -> folders.sortedBy { it.name.lowercase(Locale.ROOT) }
                    DeviceFolderSortBy.DATE_MODIFIED -> folders.sortedByDescending { it.lastModified }
                    DeviceFolderSortBy.SIZE -> folders.sortedByDescending { it.size }
                    DeviceFolderSortBy.TYPE -> folders.sortedBy { it.name.lowercase(Locale.ROOT) }
                }
            }

            val sortedFiles = when (sortBy) {
                DeviceFolderSortBy.NAME -> files.sortedBy { it.name.lowercase(Locale.ROOT) }
                DeviceFolderSortBy.DATE_MODIFIED -> files.sortedByDescending { it.lastModified }
                DeviceFolderSortBy.SIZE -> files.sortedByDescending { it.size }
                DeviceFolderSortBy.TYPE -> files.sortedWith(compareBy({ it.extension }, { it.name.lowercase(Locale.ROOT) }))
            }

            val finalFolders = if (isSortReversed && !sortFoldersByName) sortedFolders.reversed() else sortedFolders
            val finalFiles = if (isSortReversed) sortedFiles.reversed() else sortedFiles
            finalFolders + finalFiles
        } else {
            val sorted = when (sortBy) {
                DeviceFolderSortBy.NAME -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
                DeviceFolderSortBy.DATE_MODIFIED -> filtered.sortedByDescending { it.lastModified }
                DeviceFolderSortBy.SIZE -> filtered.sortedByDescending { it.size }
                DeviceFolderSortBy.TYPE -> filtered.sortedWith(compareBy({ !it.isDirectory }, { it.extension }, { it.name.lowercase(Locale.ROOT) }))
            }
            if (isSortReversed) sorted.reversed() else sorted
        }
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

    // Full-screen Dialog with 10% scaled down UI
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val currentDensity = LocalDensity.current
        val scaledDensity = remember(currentDensity) {
            Density(
                density = currentDensity.density * 0.90f,
                fontScale = currentDensity.fontScale * 0.90f
            )
        }

        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GitSurface)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar with Close button and navigation controls
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GitSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Up button if navigated into subfolder
                                val parentFile = currentDir.parentFile
                                val canGoUp = parentFile != null && parentFile.canRead() && currentDir.absolutePath != defaultRoot.absolutePath
                                if (canGoUp) {
                                    IconButton(
                                        onClick = { currentDir = parentFile!! },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Go up",
                                            tint = GitText1,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = GitAccentSoft,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Smartphone,
                                            contentDescription = null,
                                            tint = GitAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Device File Explorer",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GitText1,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Select files or folders to upload",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GitText2,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Search toggle
                                IconButton(
                                    onClick = { isSearchExpanded = !isSearchExpanded },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = if (isSearchExpanded) GitAccent else GitText2,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(2.dp))

                                // Sort Menu (Reverse is inside Sort, Folders By Name option added)
                                Box {
                                    IconButton(
                                        onClick = { showSortDropdown = true },
                                        modifier = Modifier.size(32.dp).testTag("sort_menu_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sort,
                                            contentDescription = "Sort Options",
                                            tint = GitText2,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showSortDropdown,
                                        onDismissRequest = { showSortDropdown = false },
                                        containerColor = GitSurface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GitBorderStrong),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        // Option 1: Folders First
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Folders First", fontSize = 12.5.sp, color = GitText1)
                                                    if (foldersFirst) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                updateSort(newFoldersFirst = !foldersFirst)
                                            }
                                        )

                                        // Option 2: Sort Folders By Name (Ticked by default)
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Sort Folders By Name", fontSize = 12.5.sp, color = GitText1)
                                                    if (sortFoldersByName) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                updateSort(newSortFoldersByName = !sortFoldersByName)
                                            }
                                        )

                                        HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                                        // Sort Criteria: Name
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Name", fontSize = 12.5.sp, color = GitText1)
                                                    if (sortBy == DeviceFolderSortBy.NAME) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                updateSort(newSortBy = DeviceFolderSortBy.NAME)
                                                showSortDropdown = false
                                            }
                                        )

                                        // Sort Criteria: Date Modified
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Date Modified", fontSize = 12.5.sp, color = GitText1)
                                                    if (sortBy == DeviceFolderSortBy.DATE_MODIFIED) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                updateSort(newSortBy = DeviceFolderSortBy.DATE_MODIFIED)
                                                showSortDropdown = false
                                            }
                                        )

                                        // Sort Criteria: Size
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Size", fontSize = 12.5.sp, color = GitText1)
                                                    if (sortBy == DeviceFolderSortBy.SIZE) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                updateSort(newSortBy = DeviceFolderSortBy.SIZE)
                                                showSortDropdown = false
                                            }
                                        )

                                        // Sort Criteria: File Type
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("File Type", fontSize = 12.5.sp, color = GitText1)
                                                    if (sortBy == DeviceFolderSortBy.TYPE) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                updateSort(newSortBy = DeviceFolderSortBy.TYPE)
                                                showSortDropdown = false
                                            }
                                        )

                                        HorizontalDivider(color = GitBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                                        // Reverse Order Toggle (Inside Sort dropdown!)
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.SwapVert,
                                                            contentDescription = null,
                                                            tint = if (isSortReversed) GitAccent else GitText2,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Reverse Order",
                                                            fontSize = 12.5.sp,
                                                            color = if (isSortReversed) GitAccent else GitText1
                                                        )
                                                    }
                                                    if (isSortReversed) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = GitAccent, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                updateSort(newSortReversed = !isSortReversed)
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Button in Top to Close File Explorer
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("close_device_explorer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close File Explorer",
                                        tint = GitText1,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = GitBorder, thickness = 1.dp)

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
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFFE65100),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "All files access recommended for full storage browsing",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF5D4037)
                                    )
                                }

                                TextButton(
                                    onClick = { requestManageStoragePermission() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE65100)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Grant", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }
                            }
                        }
                        HorizontalDivider(color = GitBorder, thickness = 0.5.dp)
                    }

                    // Pinned Folders Quick Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GitSurface)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = GitText2
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Pinned:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GitText2
                            )
                        }

                        // Render Pinned Folder Pills
                        pinnedFolderPaths.forEach { path ->
                            val file = File(path)
                            val label = if (path == defaultRoot.absolutePath) "Storage Root" else file.name
                            val isCurrent = currentDir.absolutePath == path

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (file.exists() && file.canRead()) {
                                            currentDir = file
                                        }
                                    },
                                color = if (isCurrent) GitAccentSoft else GitSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCurrent) GitAccent else GitBorderStrong
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (isCurrent) GitAccent else GitText2
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) GitAccent else GitText1
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Unpin",
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .clickable { folderToUnpinPrompt = path },
                                        tint = GitText3
                                    )
                                }
                            }
                        }

                        // Button to Pin Current Directory
                        val isAlreadyPinned = pinnedFolderPaths.contains(currentDir.absolutePath)
                        if (!isAlreadyPinned) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { pinCurrentFolder() },
                                color = GitAccentSoft,
                                border = androidx.compose.foundation.BorderStroke(1.dp, GitAccent.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = GitAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Pin Current",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GitAccent
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
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            placeholder = { Text("Filter items in folder...", fontSize = 12.sp, color = GitText3) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = GitText2) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp), tint = GitText2)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = GitSurface,
                                unfocusedContainerColor = GitSurface,
                                focusedBorderColor = GitAccent,
                                unfocusedBorderColor = GitBorderStrong
                            )
                        )
                    }

                    // Path Breadcrumbs & Up Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GitSurface)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val parentFile = currentDir.parentFile
                        IconButton(
                            onClick = { if (parentFile != null && parentFile.canRead()) currentDir = parentFile },
                            enabled = parentFile != null && parentFile.canRead(),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Parent Directory",
                                tint = if (parentFile != null && parentFile.canRead()) GitAccent else GitText3,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = currentDir.absolutePath.replace("/storage/emulated/0", "Internal Storage"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = GitText1
                        )
                    }

                    HorizontalDivider(color = GitBorder, thickness = 0.5.dp)

                    // Selection Summary Bar & Select All
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${selectedPaths.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPaths.isNotEmpty()) GitAccent else GitText2,
                                fontSize = 11.5.sp
                            )
                            if (selectedPaths.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(10.dp))
                                TextButton(
                                    onClick = { selectedPaths.clear() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Clear", fontSize = 11.sp, color = GitText2)
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
                            Text("Select All in Folder", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = GitAccent)
                        }
                    }

                    HorizontalDivider(color = GitBorder, thickness = 0.5.dp)

                    // Main File & Folder List
                    Box(modifier = Modifier.weight(1f)) {
                        if (isLoadingFiles) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = GitAccent, modifier = Modifier.size(30.dp))
                            }
                        } else if (displayedList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = GitText3, modifier = Modifier.size(38.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Folder is empty or unreadable", color = GitText2, fontSize = 12.5.sp)
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
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
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
                                                checkedColor = GitAccent,
                                                uncheckedColor = GitBorderStrong
                                            ),
                                            modifier = Modifier.size(28.dp)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Extension or File Type Icon with custom tint container
                                        if (item.isDirectory) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = GitAccentSoft,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = "Folder",
                                                        tint = GitAccent,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            val meta = FileIcons.getMeta(item.name, false)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = meta.color.copy(alpha = 0.12f),
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = meta.icon,
                                                        contentDescription = meta.label,
                                                        tint = meta.color,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Name & Meta
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (item.isDirectory) FontWeight.Bold else FontWeight.Normal,
                                                color = GitText1,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            if (!item.isDirectory) {
                                                Text(
                                                    text = formatFileSize(item.size),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = GitText2,
                                                    fontSize = 10.5.sp
                                                )
                                            }
                                        }

                                        if (item.isDirectory) {
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Open",
                                                tint = GitText2,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 54.dp),
                                        color = GitBorder,
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Confirmation Action Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GitSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GitBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GitBorderStrong),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GitText1)
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Medium, fontSize = 12.5.sp)
                            }

                            Button(
                                onClick = { stageAndConfirmSelection() },
                                enabled = selectedPaths.isNotEmpty() && !isCollectingFiles,
                                modifier = Modifier.weight(1.5f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GitButtonPrimary,
                                    contentColor = Color.White,
                                    disabledContainerColor = GitBorder,
                                    disabledContentColor = GitText3
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isCollectingFiles) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reading Files...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Select ${selectedPaths.size} Item(s)",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for unpinning
    folderToUnpinPrompt?.let { path ->
        val name = File(path).name
        AlertDialog(
            onDismissRequest = { folderToUnpinPrompt = null },
            shape = RoundedCornerShape(14.dp),
            containerColor = GitSurface,
            title = { Text("Unpin Folder", fontWeight = FontWeight.Bold, color = GitText1) },
            text = { Text("Remove \"$name\" from pinned folders?", color = GitText2) },
            confirmButton = {
                Button(
                    onClick = {
                        unpinFolder(path)
                        folderToUnpinPrompt = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GitButtonPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Unpin", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { folderToUnpinPrompt = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = GitText2)
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
