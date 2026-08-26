package com.example.ui.components.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitAccentSoft
import com.example.ui.theme.GitAppBg
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitBorderStrong
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3

enum class EditorFontFamily(
    val id: String,
    val displayName: String,
    val description: String,
    val fontFamily: FontFamily
) {
    JETBRAINS_MONO("mono", "JetBrains Mono", "Standard developer monospace", FontFamily.Monospace),
    SYSTEM_MONO("system_mono", "System Code", "Fixed-width system terminal font", FontFamily.Monospace),
    SANS_SERIF("sans", "Modern Sans (Inter)", "Humanist clean proportional typeface", FontFamily.SansSerif),
    SERIF("serif", "Literary Serif", "Editorial & book formatting style", FontFamily.Serif),
    CURSIVE("cursive", "Faux Fira / Script", "Stylized cursive aesthetic font", FontFamily.Cursive)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorTypographyModal(
    currentFontSize: Float,
    currentFontFamily: EditorFontFamily,
    onFontSizeChange: (Float) -> Unit,
    onFontFamilyChange: (EditorFontFamily) -> Unit,
    onDismiss: () -> Unit
) {
    val quickSizes = listOf(10f, 11f, 12f, 13f, 13.5f, 14f, 15f, 16f, 18f, 20f, 24f)
    val sampleCode = "val greeting = \"Hello, Developer!\"\nfun calculateScore(base: Int): Int {\n    return base * 2\n}"

    val sampleSyntaxHighlighter = remember { SyntaxHighlighter(SupportedLanguage.KOTLIN) }
    val highlightedSample = remember(sampleCode) { sampleSyntaxHighlighter.highlight(sampleCode) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GitSurface,
            border = BorderStroke(1.dp, GitBorderStrong),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(GitAccentSoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = null,
                                tint = GitAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Editor Typography",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GitText1
                            )
                            Text(
                                text = "Font family & font sizing",
                                style = MaterialTheme.typography.bodySmall,
                                color = GitText2,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = GitText2,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // FONT FAMILY SECTION
                Text(
                    text = "FONT FAMILY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = GitText3,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EditorFontFamily.values().forEach { fontOption ->
                        val isSelected = fontOption == currentFontFamily
                        Surface(
                            onClick = { onFontFamilyChange(fontOption) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) GitAccentSoft else GitAppBg,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) GitAccent else GitBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fontOption.displayName,
                                        style = TextStyle(
                                            fontFamily = fontOption.fontFamily,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isSelected) GitAccent else GitText1
                                        )
                                    )
                                    Text(
                                        text = fontOption.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GitText2,
                                        fontSize = 10.5.sp
                                    )
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(GitAccent, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GitBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // FONT SIZE SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FONT SIZE (${String.format("%.1f", currentFontSize)}sp)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GitText3,
                        letterSpacing = 1.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decrease
                        Surface(
                            onClick = { onFontSizeChange((currentFontSize - 1f).coerceAtLeast(8f)) },
                            shape = CircleShape,
                            color = GitAppBg,
                            border = BorderStroke(1.dp, GitBorder),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease",
                                    tint = GitText1,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Reset
                        Surface(
                            onClick = { onFontSizeChange(13.5f) },
                            shape = RoundedCornerShape(6.dp),
                            color = GitAppBg,
                            border = BorderStroke(1.dp, GitBorder),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset",
                                    tint = GitText2,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Reset", fontSize = 11.sp, color = GitText2)
                            }
                        }

                        // Increase
                        Surface(
                            onClick = { onFontSizeChange((currentFontSize + 1f).coerceAtMost(28f)) },
                            shape = CircleShape,
                            color = GitAppBg,
                            border = BorderStroke(1.dp, GitBorder),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase",
                                    tint = GitText1,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Size Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickSizes.forEach { sizeVal ->
                        val isSelected = Math.abs(currentFontSize - sizeVal) < 0.1f
                        Surface(
                            onClick = { onFontSizeChange(sizeVal) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) GitAccent else GitAppBg,
                            border = BorderStroke(1.dp, if (isSelected) GitAccent else GitBorder),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${if (sizeVal % 1 == 0f) sizeVal.toInt() else sizeVal}sp",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else GitText1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // LIVE PREVIEW BOX
                Text(
                    text = "LIVE SYNTAX PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = GitText3,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GitAppBg,
                    border = BorderStroke(1.dp, GitBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = highlightedSample,
                        style = TextStyle(
                            fontFamily = currentFontFamily.fontFamily,
                            fontSize = currentFontSize.sp,
                            color = GitText1,
                            lineHeight = (currentFontSize * 1.45f).sp
                        ),
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GitAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Apply & Close", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
