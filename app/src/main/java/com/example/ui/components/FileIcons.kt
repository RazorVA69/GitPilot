package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.GitHubBlue
import com.example.ui.theme.GitHubGreen
import com.example.ui.theme.GitHubOrange
import com.example.ui.theme.GitHubPurple
import com.example.ui.theme.GitHubTeal
import com.example.ui.theme.GitHubYellow

data class FileTypeMeta(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

object FileIcons {
    fun getMeta(fileName: String, isDirectory: Boolean): FileTypeMeta {
        if (isDirectory) {
            return FileTypeMeta(
                icon = Icons.Default.Folder,
                color = GitHubYellow,
                label = "DIR"
            )
        }

        val ext = if (fileName.contains('.')) fileName.substringAfterLast('.').lowercase() else ""
        return when (ext) {
            "kt", "kts" -> FileTypeMeta(Icons.Default.Code, GitHubPurple, "Kotlin")
            "java" -> FileTypeMeta(Icons.Default.Code, GitHubOrange, "Java")
            "js", "jsx", "mjs" -> FileTypeMeta(Icons.Default.Code, GitHubYellow, "JavaScript")
            "ts", "tsx" -> FileTypeMeta(Icons.Default.Code, GitHubBlue, "TypeScript")
            "py" -> FileTypeMeta(Icons.Default.Code, GitHubBlue, "Python")
            "go" -> FileTypeMeta(Icons.Default.Code, GitHubBlue, "Go")
            "rs" -> FileTypeMeta(Icons.Default.Code, GitHubOrange, "Rust")
            "cpp", "c", "h", "hpp" -> FileTypeMeta(Icons.Default.Code, GitHubBlue, "C/C++")
            "html", "htm" -> FileTypeMeta(Icons.Default.Code, GitHubOrange, "HTML")
            "css", "scss", "sass", "less" -> FileTypeMeta(Icons.Default.Code, GitHubBlue, "CSS")
            "json" -> FileTypeMeta(Icons.Default.Code, GitHubTeal, "JSON")
            "xml", "yaml", "yml", "toml" -> FileTypeMeta(Icons.Default.Settings, GitHubOrange, "Config")
            "md", "markdown", "txt", "rst" -> FileTypeMeta(Icons.Default.Description, GitHubBlue, "Doc")
            "png", "jpg", "jpeg", "gif", "svg", "webp", "ico" -> FileTypeMeta(Icons.Default.Image, GitHubTeal, "Image")
            "sh", "bash", "zsh", "bat", "cmd" -> FileTypeMeta(Icons.Default.Terminal, GitHubGreen, "Script")
            "gradle", "properties", "pro" -> FileTypeMeta(Icons.Default.Settings, GitHubBlue, "Build")
            else -> FileTypeMeta(Icons.Default.Description, Color.Gray, "File")
        }
    }

    fun getLanguageColor(language: String?): Color {
        return when (language?.lowercase()) {
            "kotlin" -> GitHubPurple
            "java" -> GitHubOrange
            "javascript" -> GitHubYellow
            "typescript" -> GitHubBlue
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
            else -> Color(0xFF8B949E)
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
