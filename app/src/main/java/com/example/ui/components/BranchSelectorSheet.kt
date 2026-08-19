package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GitHubBranch
import com.example.ui.theme.GitHubBlue
import com.example.ui.theme.GitHubDarkBorder
import com.example.ui.theme.GitHubDarkSurface
import com.example.ui.theme.GitHubDarkSurfaceVariant
import com.example.ui.theme.GitHubDarkTextMuted
import com.example.ui.theme.GitHubDarkTextPrimary
import com.example.ui.theme.GitHubDarkTextSecondary
import com.example.ui.theme.GitHubYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchSelectorSheet(
    branches: List<GitHubBranch>,
    selectedBranch: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectBranch: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var branchSearch by remember { mutableStateOf("") }

    val filteredBranches = remember(branches, branchSearch) {
        if (branchSearch.isBlank()) branches
        else branches.filter { it.name.contains(branchSearch.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GitHubDarkSurface,
        modifier = Modifier.testTag("branch_selector_sheet")
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
                        imageVector = Icons.Default.ForkRight,
                        contentDescription = null,
                        tint = GitHubBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Switch Branch / Tag",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GitHubDarkTextPrimary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = GitHubDarkTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search branch
            OutlinedTextField(
                value = branchSearch,
                onValueChange = { branchSearch = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("branch_search_input"),
                placeholder = { Text("Filter branches...", color = GitHubDarkTextMuted, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = GitHubDarkTextSecondary, modifier = Modifier.size(18.dp))
                },
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

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = GitHubBlue, modifier = Modifier.size(28.dp))
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = GitHubDarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GitHubDarkBorder)
                ) {
                    LazyColumn {
                        items(filteredBranches, key = { it.name }) { branch ->
                            val isSelected = branch.name == selectedBranch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectBranch(branch.name) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ForkRight,
                                        contentDescription = null,
                                        tint = if (isSelected) GitHubBlue else GitHubDarkTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = branch.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) GitHubBlue else GitHubDarkTextPrimary
                                    )
                                    if (branch.`protected`) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Protected",
                                            tint = GitHubYellow,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = GitHubBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = GitHubDarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
