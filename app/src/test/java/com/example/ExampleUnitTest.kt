package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPythonScriptExecution_unifyPatch() = kotlinx.coroutines.runBlocking {
    val samplePatchContent = """
package app.ftl.patches

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

private val COMPATIBILITY_FIREFOX_DEV = Compatibility(
    target = AppTarget.FIREFOX
)

fun apply() {
    compatibleWith(COMPATIBILITY_FIREFOX_DEV)
}
""".trimIndent()

    val fileStorage = mutableMapOf<String, String>()
    fileStorage["SomePatch.kt"] = samplePatchContent

    val printed = mutableListOf<String>()

    val context = com.example.ui.components.terminal.PythonScriptEngine.Context(
        workingDir = "patches/src/main/kotlin/app/ftl/patches/FireFox",
        getFile = { path -> fileStorage[path] },
        writeFile = { path, content -> fileStorage[path] = content },
        listMatchingFiles = { pat -> if (pat == "*Patch.kt") listOf("SomePatch.kt") else emptyList() },
        printOutput = { msg -> printed.add(msg) },
        printError = { err -> println("Error: $err") },
        tempFiles = emptyMap()
    )

    val script = """
import re, glob
for f in glob.glob("*Patch.kt"):
    s = open(f, encoding="utf-8").read()
    if f == "OldMenuPatch.kt" or "private val COMPATIBILITY_FIREFOX_" not in s:
        continue
    s = re.sub(r"private val COMPATIBILITY_FIREFOX_\w+ = Compatibility\(.*?\n\)\n\n?", "", s, count=1, flags=re.S)
    s = re.sub(r"compatibleWith\(COMPATIBILITY_FIREFOX_(?!NIGHTLY)\w+\)", "compatibleWith(COMPATIBILITY_FIREFOX_NIGHTLY)", s)
    if "AppTarget" not in s.replace("import app.morphe.patcher.patch.AppTarget\n", ""):
        s = s.replace("import app.morphe.patcher.patch.AppTarget\n", "")
    if "Compatibility" not in s.replace("import app.morphe.patcher.patch.Compatibility\n", "").replace("COMPATIBILITY_", ""):
        s = s.replace("import app.morphe.patcher.patch.Compatibility\n", "")
    open(f, "w", encoding="utf-8").write(s)
    print("fixed", f)
""".trimIndent()

    val success = com.example.ui.components.terminal.PythonScriptEngine.execute(script, context)
    assertTrue(success)
    assertEquals(listOf("fixed SomePatch.kt"), printed)

    val updatedContent = fileStorage["SomePatch.kt"] ?: ""
    assertFalse(updatedContent.contains("private val COMPATIBILITY_FIREFOX_DEV"))
    assertTrue(updatedContent.contains("compatibleWith(COMPATIBILITY_FIREFOX_NIGHTLY)"))
    assertFalse(updatedContent.contains("import app.morphe.patcher.patch.Compatibility"))
  }
}
