package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3

data class FileTypeMeta(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

object FileIcons {
    fun getMeta(fileName: String, isDirectory: Boolean): FileTypeMeta {
        if (isDirectory) {
            return FileTypeMeta(
                icon = Icons.Outlined.Folder,
                color = GitAccent,
                label = "DIR"
            )
        }

        val ext = if (fileName.contains('.')) fileName.substringAfterLast('.').lowercase() else fileName.lowercase()
        val lowerName = fileName.lowercase()

        if (lowerName.startsWith(".env") || lowerName == ".gitignore" || lowerName == ".gitattributes" || lowerName == ".editorconfig") {
            return FileTypeMeta(Icons.Outlined.Description, GitText2, "Config")
        }

        if (lowerName.endsWith(".properties") || lowerName.endsWith(".pro")) {
            return FileTypeMeta(Icons.Outlined.Settings, GitText2, "Config")
        }

        return when (ext) {
            "kt", "kts", "java", "js", "jsx", "mjs", "ts", "tsx", "py", "go", "rs", "cpp", "c", "h", "hpp", "html", "htm", "css", "scss", "sass", "less", "json", "gradle" ->
                FileTypeMeta(Icons.Outlined.Code, GitText2, "Code")
            "xml", "yaml", "yml", "toml", "properties", "pro" ->
                FileTypeMeta(Icons.Outlined.Settings, GitText2, "Config")
            "md", "markdown", "txt", "rst", "log" ->
                FileTypeMeta(Icons.Outlined.Description, GitText2, "Doc")
            "png", "jpg", "jpeg", "gif", "svg", "webp", "ico" ->
                FileTypeMeta(Icons.Outlined.Image, GitText2, "Image")
            "sh", "bash", "zsh", "bat", "cmd" ->
                FileTypeMeta(Icons.Outlined.Terminal, GitText2, "Script")
            else ->
                FileTypeMeta(Icons.Outlined.Description, GitText2, "File")
        }
    }

    fun getLanguageColor(language: String?): Color {
        return when (language?.lowercase()) {
            "kotlin" -> Color(0xFFA97BFF)
            "java" -> Color(0xFFB07219)
            "javascript" -> Color(0xFFF1E05A)
            "typescript" -> Color(0xFF3178C6)
            "python" -> Color(0xFF3572A5)
            "go" -> Color(0xFF00ADD8)
            "rust" -> Color(0xFFDEA584)
            "c++", "cpp" -> Color(0xFFF34B7D)
            "c" -> Color(0xFF555555)
            "c#" -> Color(0xFF178600)
            "ruby" -> Color(0xFF701516)
            "php" -> Color(0xFF4F5D95)
            "html" -> Color(0xFFE34C26)
            "css" -> Color(0xFF563D7C)
            "swift" -> Color(0xFFF05138)
            "dart" -> Color(0xFF00B4AB)
            "shell" -> Color(0xFF89E051)
            else -> GitText3
        }
    }

    fun formatFileSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1) return "$bytes B"
        val mb = kb / 1024.0
        if (mb < 1) return String.format("%.1f KB", kb)
        val gb = mb / 1024.0
        if (gb < 1) return String.format("%.1f MB", mb)
        return String.format("%.1f GB", gb)
    }
}

@Composable
fun FileIconForExtension(
    extension: String = "",
    fileName: String = extension,
    modifier: Modifier = Modifier,
    isDirectory: Boolean = false
) {
    val name = if (fileName.isNotBlank()) fileName else extension
    val meta = FileIcons.getMeta(name, isDirectory)
    Icon(
        imageVector = meta.icon,
        contentDescription = meta.label,
        tint = meta.color,
        modifier = modifier
    )
}

@Composable
fun GitHubMarkIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            // Draw GitHub Mark Octocat silhouette
            moveTo(w * 0.5f, 0f)
            cubicTo(w * 0.224f, 0f, 0f, h * 0.224f, 0f, h * 0.5f)
            cubicTo(0f, h * 0.721f, w * 0.143f, h * 0.908f, w * 0.342f, h * 0.974f)
            cubicTo(w * 0.367f, h * 0.979f, w * 0.376f, h * 0.963f, w * 0.376f, h * 0.95f)
            cubicTo(w * 0.376f, h * 0.938f, w * 0.375f, h * 0.898f, w * 0.375f, h * 0.849f)
            cubicTo(w * 0.236f, h * 0.879f, w * 0.207f, h * 0.782f, w * 0.207f, h * 0.782f)
            cubicTo(w * 0.184f, h * 0.724f, w * 0.152f, h * 0.709f, w * 0.152f, h * 0.709f)
            cubicTo(w * 0.107f, h * 0.678f, w * 0.155f, h * 0.679f, w * 0.155f, h * 0.679f)
            cubicTo(w * 0.205f, h * 0.682f, w * 0.231f, h * 0.73f, w * 0.231f, h * 0.73f)
            cubicTo(w * 0.275f, h * 0.805f, w * 0.346f, h * 0.783f, w * 0.374f, h * 0.77f)
            cubicTo(w * 0.378f, h * 0.738f, w * 0.391f, h * 0.716f, w * 0.405f, h * 0.704f)
            cubicTo(w * 0.294f, h * 0.691f, w * 0.178f, h * 0.648f, w * 0.178f, h * 0.457f)
            cubicTo(w * 0.178f, h * 0.403f, w * 0.197f, h * 0.358f, w * 0.229f, h * 0.323f)
            cubicTo(w * 0.224f, h * 0.31f, w * 0.207f, h * 0.26f, w * 0.234f, h * 0.192f)
            cubicTo(w * 0.234f, h * 0.192f, w * 0.276f, h * 0.178f, w * 0.371f, h * 0.242f)
            cubicTo(w * 0.411f, h * 0.231f, w * 0.454f, h * 0.225f, w * 0.497f, h * 0.225f)
            cubicTo(w * 0.54f, h * 0.225f, w * 0.583f, h * 0.231f, w * 0.623f, h * 0.242f)
            cubicTo(w * 0.718f, h * 0.178f, w * 0.76f, h * 0.192f, w * 0.76f, h * 0.192f)
            cubicTo(w * 0.787f, h * 0.26f, w * 0.77f, h * 0.31f, w * 0.765f, h * 0.323f)
            cubicTo(w * 0.797f, h * 0.358f, w * 0.816f, h * 0.403f, w * 0.816f, h * 0.457f)
            cubicTo(w * 0.816f, h * 0.649f, w * 0.7f, h * 0.691f, w * 0.589f, h * 0.703f)
            cubicTo(w * 0.607f, h * 0.719f, w * 0.623f, h * 0.749f, w * 0.623f, h * 0.796f)
            cubicTo(w * 0.623f, h * 0.864f, w * 0.622f, h * 0.919f, w * 0.622f, h * 0.936f)
            cubicTo(w * 0.622f, h * 0.95f, w * 0.631f, h * 0.966f, w * 0.657f, h * 0.961f)
            cubicTo(w * 0.856f, h * 0.895f, w * 0.999f, h * 0.708f, w * 0.999f, h * 0.487f)
            cubicTo(w * 0.999f, h * 0.218f, w * 0.775f, 0f, w * 0.5f, 0f)
            close()
        }
        drawPath(path, color = tint)
    }
}

