package com.example.ui.components.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitBorder
import com.example.ui.theme.GitBorderStrong
import com.example.ui.theme.GitButtonPrimary
import com.example.ui.theme.GitSurface
import com.example.ui.theme.GitSurface2
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3
import com.example.ui.theme.Md3LightError
import kotlin.math.roundToInt

@Composable
fun EditorGoToLineDialog(
    lineCount: Int,
    input: String,
    onInputChange: (String) -> Unit,
    onConfirmLine: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GitSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FormatListNumbered,
                    contentDescription = null,
                    tint = GitAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Go to Line",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GitText1
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Enter line number (1 – $lineCount):",
                    style = MaterialTheme.typography.bodySmall,
                    color = GitText2
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { raw ->
                        if (raw.all { it.isDigit() }) {
                            onInputChange(raw)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            input.toIntOrNull()?.let(onConfirmLine)
                        }
                    ),
                    placeholder = { Text("e.g. 42", color = GitText3) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("go_to_line_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GitSurface,
                        unfocusedContainerColor = GitSurface,
                        focusedBorderColor = GitAccent,
                        unfocusedBorderColor = GitBorderStrong
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lineNum = input.toIntOrNull()
                    if (lineNum != null) {
                        onConfirmLine(lineNum)
                    } else {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GitAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Go", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GitText2)
            }
        }
    )
}

@Composable
fun EditorSearchReplaceBar(
    visible: Boolean,
    isReplaceMode: Boolean,
    searchQuery: String,
    replaceQuery: String,
    matchCount: Int,
    currentMatchIndex: Int,
    searchFocusRequester: FocusRequester,
    onSearchQueryEdited: (String) -> Unit,
    onReplaceQueryEdited: (String) -> Unit,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onClearOrClose: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = GitSurface,
            border = BorderStroke(1.dp, GitBorderStrong)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryEdited,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester)
                            .testTag("editor_find_input"),
                        placeholder = { Text("Find...", fontSize = 12.sp, color = GitText3) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = GitText2)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                Text(
                                    text = if (matchCount > 0) "${(currentMatchIndex + 1).coerceAtMost(matchCount)}/$matchCount" else "0/0",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (matchCount > 0) GitAccent else Md3LightError,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onNextMatch() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GitSurface,
                            unfocusedContainerColor = GitSurface,
                            focusedBorderColor = GitAccent,
                            unfocusedBorderColor = GitBorderStrong
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onPreviousMatch,
                        enabled = matchCount > 0,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match", modifier = Modifier.size(20.dp), tint = GitText1)
                    }

                    IconButton(
                        onClick = onNextMatch,
                        enabled = matchCount > 0,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match", modifier = Modifier.size(20.dp), tint = GitText1)
                    }

                    IconButton(
                        onClick = onClearOrClose,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = if (searchQuery.isNotEmpty()) "Clear Search" else "Close Find",
                            modifier = Modifier.size(18.dp),
                            tint = GitText2
                        )
                    }
                }

                if (isReplaceMode) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = onReplaceQueryEdited,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_replace_input"),
                            placeholder = { Text("Replace with...", fontSize = 12.sp, color = GitText3) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp), tint = GitText2)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = GitSurface,
                                unfocusedContainerColor = GitSurface,
                                focusedBorderColor = GitAccent,
                                unfocusedBorderColor = GitBorderStrong
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = onReplaceCurrent,
                            enabled = matchCount > 0,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GitButtonPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Replace", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Button(
                            onClick = onReplaceAll,
                            enabled = matchCount > 0,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GitButtonPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.EditorVirtualVerticalScrollbar(
    controller: EditorController
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableDoubleStateOf(0.0) }
    val currentController by rememberUpdatedState(controller)

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(44.dp)
            .padding(top = 4.dp, bottom = 4.dp, end = 2.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragAccumulator = currentController.scrollY.toDouble()
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val track = size.height.toDouble()
                        val maxVal = currentController.maxScrollY.toDouble()
                        if (track > 0.0 && maxVal > 0.0) {
                            val totalH = maxVal + track
                            val thumbRatio = (track / totalH).coerceIn(0.06, 0.40)
                            val thumbH = (track * thumbRatio).coerceIn(44.0 * density.density, 140.0 * density.density)
                            val availableTrack = (track - thumbH).coerceAtLeast(1.0)
                            val scrollDelta = (dragAmount.toDouble() / availableTrack) * maxVal
                            dragAccumulator = (dragAccumulator + scrollDelta).coerceIn(0.0, maxVal)
                            currentController.scrollToVerticalPx(dragAccumulator.roundToInt(), animate = false)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val track = size.height.toDouble()
                    val maxVal = currentController.maxScrollY.toDouble()
                    if (track > 0.0 && maxVal > 0.0) {
                        val totalH = maxVal + track
                        val thumbRatio = (track / totalH).coerceIn(0.06, 0.40)
                        val thumbH = (track * thumbRatio).coerceIn(44.0 * density.density, 140.0 * density.density)
                        val availableTrack = (track - thumbH).coerceAtLeast(1.0)
                        val targetCenterY = offset.y.toDouble() - (thumbH / 2.0)
                        val fraction = (targetCenterY / availableTrack).coerceIn(0.0, 1.0)
                        val target = (fraction * maxVal).roundToInt()
                        currentController.scrollToVerticalPx(target, animate = true)
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(if (isDragging) 8.dp else 5.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    val maxVal = controller.maxScrollY.toDouble()
                    val track = size.height.toDouble()
                    if (maxVal > 0.0 && track > 0.0) {
                        alpha = 1f
                        val totalH = (maxVal + track).coerceAtLeast(1.0)
                        val thumbRatio = (track / totalH).coerceIn(0.06, 0.40)
                        val thumbH = (track * thumbRatio).coerceIn(44.0 * density.density, 140.0 * density.density)
                        val availableTrack = (track - thumbH).coerceAtLeast(1.0)
                        val fraction = (controller.scrollY.toDouble() / maxVal).coerceIn(0.0, 1.0)

                        translationY = (availableTrack * fraction).toFloat()
                        scaleY = (thumbH / track).toFloat()
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    } else {
                        alpha = 0f
                    }
        }
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isDragging) GitAccent.copy(alpha = 0.9f)
                    else GitText3.copy(alpha = 0.55f)
                )
        )
    }
}

@Composable
fun BoxScope.EditorVirtualHorizontalScrollbar(
    controller: EditorController
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableDoubleStateOf(0.0) }
    val currentController by rememberUpdatedState(controller)

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(36.dp)
            .padding(start = 4.dp, end = 4.dp, bottom = 2.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragAccumulator = currentController.scrollX.toDouble()
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val track = size.width.toDouble()
                        val maxVal = currentController.maxScrollX.toDouble()
                        if (track > 0.0 && maxVal > 0.0) {
                            val totalW = maxVal + track
                            val thumbRatio = (track / totalW).coerceIn(0.08, 0.40)
                            val thumbW = (track * thumbRatio).coerceIn(44.0 * density.density, 140.0 * density.density)
                            val availableTrack = (track - thumbW).coerceAtLeast(1.0)
                            val scrollDelta = (dragAmount.toDouble() / availableTrack) * maxVal
                            dragAccumulator = (dragAccumulator + scrollDelta).coerceIn(0.0, maxVal)
                            currentController.scrollToHorizontalPx(dragAccumulator.roundToInt(), animate = false)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val track = size.width.toDouble()
                    val maxVal = currentController.maxScrollX.toDouble()
                    if (track > 0.0 && maxVal > 0.0) {
                        val totalW = maxVal + track
                        val thumbRatio = (track / totalW).coerceIn(0.08, 0.40)
                        val thumbW = (track * thumbRatio).coerceIn(44.0 * density.density, 140.0 * density.density)
                        val availableTrack = (track - thumbW).coerceAtLeast(1.0)
                        val targetCenterX = offset.x.toDouble() - (thumbW / 2.0)
                        val fraction = (targetCenterX / availableTrack).coerceIn(0.0, 1.0)
                        val target = (fraction * maxVal).roundToInt()
                        currentController.scrollToHorizontalPx(target, animate = true)
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .height(if (isDragging) 8.dp else 5.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val maxVal = controller.maxScrollX.toDouble()
                    val track = size.width.toDouble()
                    if (maxVal > 0.0 && track > 0.0) {
                        alpha = 1f
                        val totalW = (maxVal + track).coerceAtLeast(1.0)
                        val thumbRatio = (track / totalW).coerceIn(0.08, 0.40)
                        val thumbW = (track * thumbRatio).coerceIn(44.0 * density.density, 140.0 * density.density)
                        val availableTrack = (track - thumbW).coerceAtLeast(1.0)
                        val fraction = (controller.scrollX.toDouble() / maxVal).coerceIn(0.0, 1.0)

                        translationX = (availableTrack * fraction).toFloat()
                        scaleX = (thumbW / track).toFloat()
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    } else {
                        alpha = 0f
                    }
                }
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isDragging) GitAccent.copy(alpha = 0.9f)
                    else GitText3.copy(alpha = 0.55f)
                )
        )
    }
}

@Composable
fun EditorFloatingScrollButtons(
    controller: EditorController,
    onScrollToTop: () -> Unit,
    onScrollToBottom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canScrollUp by remember(controller) {
        derivedStateOf { controller.scrollY > 120 }
    }
    val canScrollDown by remember(controller) {
        derivedStateOf {
            controller.maxScrollY > 0 && controller.scrollY < (controller.maxScrollY - 120).coerceAtLeast(0)
        }
    }
    val isVisible by remember(controller) {
        derivedStateOf { canScrollUp || canScrollDown }
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (canScrollUp) {
                    Surface(
                        onClick = onScrollToTop,
                        shape = CircleShape,
                        color = GitSurface,
                        border = BorderStroke(1.dp, GitBorderStrong),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("editor_floating_up_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Go to Top",
                                tint = GitText1,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (canScrollDown) {
                    Surface(
                        onClick = onScrollToBottom,
                        shape = CircleShape,
                        color = GitSurface,
                        border = BorderStroke(1.dp, GitBorderStrong),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("editor_floating_down_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Go to File End Line",
                                tint = GitAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditorQuickSymbolBar(
    onInsertSymbol: (symbol: String, cursorOffset: Int) -> Unit
) {
    val symbols = remember {
        listOf(
            "→" to Pair("    ", 4),
            "/" to Pair("/", 1),
            "+" to Pair("+", 1),
            "-" to Pair("-", 1),
            "*" to Pair("*", 1),
            "=" to Pair("=", 1),
            "<" to Pair("<", 1),
            ">" to Pair(">", 1),
            "\"" to Pair("\"\"", 1),
            "'" to Pair("''", 1),
            "(" to Pair("()", 1),
            ")" to Pair(")", 1),
            "{" to Pair("{}", 1),
            "}" to Pair("}", 1),
            "[" to Pair("[]", 1),
            "]" to Pair("]", 1),
            ":" to Pair(":", 1),
            ";" to Pair(";", 1)
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GitSurface,
        border = BorderStroke(0.5.dp, GitBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            symbols.forEach { (label, insertion) ->
                Surface(
                    onClick = { onInsertSymbol(insertion.first, insertion.second) },
                    shape = RoundedCornerShape(8.dp),
                    color = GitSurface2,
                    border = BorderStroke(0.5.dp, GitBorder),
                    modifier = Modifier
                        .height(36.dp)
                        .defaultMinSize(minWidth = 36.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = label,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = GitText1
                        )
                    }
                }
            }
        }
    }
}
