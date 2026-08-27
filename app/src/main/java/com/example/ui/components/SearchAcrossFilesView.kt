package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GitTreeItem
import com.example.data.model.RepoFileGroupedMatches
import com.example.data.model.RepoFileSearchMatch
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitAccentSoft
import com.example.ui.theme.GitAppBg
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitBorderStrong
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitSurface2
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAcrossFilesView(
    repoName: String,
    branch: String,
    searchQuery: String,
    selectedPath: String,
    pinnedFolders: Set<String>,
    allTreeItems: List<GitTreeItem>,
    isSearching: Boolean,
    progress: Pair<Int, Int>?,
    results: List<RepoFileSearchMatch>,
    onSearchQueryChange: (String) -> Unit,
    onPathSelected: (String) -> Unit,
    onSelectMatch: (path: String, line: Int) -> Unit,
    onRefreshSearch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)
    val focusManager = LocalFocusManager.current

    // Group results by file path
    val groupedResults by remember(results) {
        derivedStateOf {
            results.groupBy { it.path }
                .map { (path, matches) ->
                    RepoFileGroupedMatches(
                        path = path,
                        fileName = path.substringAfterLast('/'),
                        matches = matches
                    )
                }
        }
    }

    // List of directories in repo for custom path picker
    val availableDirectories by remember(allTreeItems) {
        derivedStateOf {
            val dirs = allTreeItems.filter { it.isDirectory }.map { it.path }.sorted()
            listOf("") + dirs
        }
    }

    var showPathPickerDropdown by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GitAppBg)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Search across all files",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = GitText1
                    )
                    Text(
                        text = "$repoName ($branch)",
                        fontSize = 12.sp,
                        color = GitText3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("search_across_files_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GitText1
                    )
                }
            },
            actions = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = GitAccent
                    )
                } else if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = onRefreshSearch) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh search",
                            tint = GitText2,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = GitAppBg,
                titleContentColor = GitText1
            )
        )

        HorizontalDivider(color = GitBorder, thickness = 1.dp)

        // Search Input Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_across_files_input"),
                placeholder = {
                    Text("Search in repository (e.g. class, function, TODO)...", fontSize = 13.sp, color = GitText3)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (searchQuery.isNotEmpty()) GitAccent else GitText2,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = GitText2,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = GitSurface,
                    unfocusedContainerColor = GitSurface,
                    focusedBorderColor = GitAccent,
                    unfocusedBorderColor = GitBorderStrong
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Path Scope Row & Pinned Folders
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Path Scope:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GitText2,
                    modifier = Modifier.padding(end = 8.dp)
                )

                // Current Path Selector Pill
                Box {
                    Surface(
                        onClick = { showPathPickerDropdown = true },
                        shape = RoundedCornerShape(8.dp),
                        color = GitSurface,
                        border = BorderStroke(1.dp, GitBorderStrong),
                        modifier = Modifier.testTag("search_path_scope_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedPath.isEmpty()) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint = GitAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedPath.isEmpty()) "/ (Root Repository)" else selectedPath,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = GitText1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Dropdown for selecting folder path
                    DropdownMenu(
                        expanded = showPathPickerDropdown,
                        onDismissRequest = { showPathPickerDropdown = false },
                        modifier = Modifier.background(GitSurface)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "/ (Root - All Files)",
                                    fontWeight = if (selectedPath.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedPath.isEmpty()) GitAccent else GitText1,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = GitAccent, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                onPathSelected("")
                                showPathPickerDropdown = false
                            }
                        )

                        if (availableDirectories.isNotEmpty()) {
                            HorizontalDivider(color = GitBorder, thickness = 0.5.dp)
                            availableDirectories.filter { it.isNotEmpty() }.take(40).forEach { dir ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            dir,
                                            fontWeight = if (selectedPath == dir) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedPath == dir) GitAccent else GitText1,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = GitText2, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        onPathSelected(dir)
                                        showPathPickerDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Pinned Folders at Top for quick selection
            if (pinnedFolders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pinned:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GitText3,
                        modifier = Modifier.padding(end = 2.dp)
                    )

                    // Root option chip
                    Surface(
                        onClick = { onPathSelected("") },
                        shape = RoundedCornerShape(6.dp),
                        color = if (selectedPath.isEmpty()) GitAccentSoft else GitSurface,
                        border = BorderStroke(1.dp, if (selectedPath.isEmpty()) GitAccent else GitBorder),
                        modifier = Modifier.testTag("pinned_path_root")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "/",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPath.isEmpty()) GitAccent else GitText2
                            )
                        }
                    }

                    // Pinned folder chips
                    pinnedFolders.forEach { folderPath ->
                        val isSelected = selectedPath == folderPath
                        Surface(
                            onClick = { onPathSelected(folderPath) },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) GitAccentSoft else GitSurface,
                            border = BorderStroke(1.dp, if (isSelected) GitAccent else GitBorder),
                            modifier = Modifier.testTag("pinned_path_${folderPath.replace('/', '_')}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (isSelected) GitAccent else GitText3,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = folderPath,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GitAccent else GitText1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Search Progress Bar & Stats
        if (isSearching && progress != null) {
            val (scanned, total) = progress
            val progressFraction = if (total > 0) scanned.toFloat() / total.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = GitAccent,
                trackColor = GitBorder
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Searching $scanned / $total files in repository...",
                    fontSize = 11.sp,
                    color = GitText2
                )
                Text(
                    text = "${results.size} matches",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GitAccent
                )
            }
        } else if (searchQuery.length >= 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Found ${results.size} matches across ${groupedResults.size} files",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GitText1
                )
                if (selectedPath.isNotEmpty()) {
                    Text(
                        text = "in $selectedPath",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = GitText3
                    )
                }
            }
        }

        HorizontalDivider(color = GitBorder, thickness = 1.dp)

        // Results Body
        if (searchQuery.length < 2) {
            // Empty state: instructions
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = GitText3,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Search across all files in repository",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GitText1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Type at least 2 characters to search across cached and synced files in this repository.",
                        fontSize = 13.sp,
                        color = GitText2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (!isSearching && results.isEmpty()) {
            // No matches found
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = GitText3,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No matches found for \"$searchQuery\"",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = GitText1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (selectedPath.isNotEmpty()) "Try switching scope to Root / or verifying search terms." else "No files in the repository contain this text.",
                        fontSize = 12.sp,
                        color = GitText2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Match results list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("search_across_files_results_list"),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                groupedResults.forEach { group ->
                    item(key = "header_${group.path}") {
                        FileResultHeader(
                            filePath = group.path,
                            fileName = group.fileName,
                            matchCount = group.matches.size
                        )
                    }

                    items(group.matches, key = { "${it.path}:${it.lineNumber}:${it.matchStartIndex}" }) { match ->
                        MatchResultRow(
                            match = match,
                            searchQuery = searchQuery,
                            onClick = { onSelectMatch(match.path, match.lineNumber) }
                        )
                    }

                    item(key = "divider_${group.path}") {
                        HorizontalDivider(
                            color = GitBorder,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileResultHeader(
    filePath: String,
    fileName: String,
    matchCount: Int
) {
    val meta = FileIcons.getMeta(fileName = fileName, isDirectory = false)

    Surface(
        color = GitSurface2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = meta.icon,
                    contentDescription = null,
                    tint = meta.color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = filePath,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = GitText1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = GitAccentSoft,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "$matchCount ${if (matchCount == 1) "match" else "matches"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GitAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun MatchResultRow(
    match: RepoFileSearchMatch,
    searchQuery: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("match_row_${match.path}_line_${match.lineNumber}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Line Number Badge
            Surface(
                color = GitSurface,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, GitBorder),
                modifier = Modifier.padding(top = 1.dp)
            ) {
                Text(
                    text = "L${match.lineNumber}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = GitText2,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Line Content with Match Highlighted
            val trimmedContent = match.lineContent.trimStart()
            val annotated = buildAnnotatedString {
                var searchStart = 0
                val lowerTrimmed = trimmedContent.lowercase()
                val lowerQuery = searchQuery.lowercase()

                while (searchStart < trimmedContent.length) {
                    val hitIdx = lowerTrimmed.indexOf(lowerQuery, searchStart)
                    if (hitIdx == -1) {
                        append(trimmedContent.substring(searchStart))
                        break
                    }
                    if (hitIdx > searchStart) {
                        append(trimmedContent.substring(searchStart, hitIdx))
                    }
                    val hitEnd = (hitIdx + searchQuery.length).coerceAtMost(trimmedContent.length)
                    withStyle(
                        SpanStyle(
                            background = GitAccentSoft,
                            color = GitAccent,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(trimmedContent.substring(hitIdx, hitEnd))
                    }
                    searchStart = hitEnd
                }
            }

            Text(
                text = annotated,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = GitText1,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
