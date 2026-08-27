package com.example.ui.components.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.FileIconForExtension
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitAccentSoft
import com.example.ui.theme.GitAppBg
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitSurface2
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3
import com.example.ui.theme.GitYellow
import com.example.ui.viewmodel.EditorTabInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorTabsRow(
    tabs: List<EditorTabInfo>,
    activeFilePath: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onTogglePinTab: (String) -> Unit,
    onOpenFolderDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GitSurface,
        border = BorderStroke(0.5.dp, GitBorder)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folder drawer quick launcher button
            item(key = "folder_drawer_launcher") {
                Surface(
                    onClick = onOpenFolderDrawer,
                    color = GitSurface2,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        .height(30.dp)
                        .testTag("tabs_folder_drawer_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Folder Files",
                            tint = GitAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Files",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GitText1
                        )
                    }
                }
            }

            item(key = "tabs_divider") {
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(GitBorder)
                )
            }

            // Render all open editor tabs efficiently with key
            items(
                items = tabs,
                key = { it.path }
            ) { tab ->
                val isActive = tab.path == activeFilePath
                val ext = tab.fileName.substringAfterLast('.', "")

                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    color = if (isActive) GitAppBg else GitSurface,
                    border = BorderStroke(
                        1.dp,
                        if (isActive) GitAccent.copy(alpha = 0.5f) else Color.Transparent
                    ),
                    modifier = Modifier
                        .padding(horizontal = 2.dp, vertical = 3.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .combinedClickable(
                            onClick = { onSelectTab(tab.path) },
                            onLongClick = { onTogglePinTab(tab.path) }
                        )
                        .testTag("tab_${tab.fileName}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    ) {
                        // Pin icon if pinned
                        if (tab.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned Tab",
                                tint = GitAccent,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .combinedClickable(
                                        onClick = { onTogglePinTab(tab.path) }
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // File type icon
                        FileIconForExtension(
                            extension = ext,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // File Name
                        Text(
                            text = tab.fileName,
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) GitText1 else GitText2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Dirty unsaved changes indicator
                        if (tab.isDirty) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(GitYellow, CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Close Tab Button
                        IconButton(
                            onClick = { onCloseTab(tab.path) },
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("close_tab_${tab.fileName}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tab",
                                tint = if (isActive) GitText2 else GitText3,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
