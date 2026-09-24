package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ConflictResolverUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GitPilot", appName)
  }

  @Test
  fun `conflict markers detected and parsed correctly`() {
    val conflictedCode = """
<<<<<<< HEAD
val version = "1.0.0-dev"
=======
val version = "1.1.0-upstream"
>>>>>>> upstream/dev

class MorpheManager
""".trimIndent()

    assertTrue(ConflictResolverUtil.hasConflictMarkers(conflictedCode))
    val conflict = ConflictResolverUtil.parseConflict("build.gradle.kts", conflictedCode)
    assertNotNull(conflict)
    assertEquals("val version = \"1.0.0-dev\"", conflict?.oursSnippet)
    assertEquals("val version = \"1.1.0-upstream\"", conflict?.theirsSnippet)

    val resolvedOurs = ConflictResolverUtil.resolveText(conflictedCode, "ours")
    assertTrue(resolvedOurs.contains("val version = \"1.0.0-dev\""))
    assertFalse(ConflictResolverUtil.hasConflictMarkers(resolvedOurs))

    val resolvedTheirs = ConflictResolverUtil.resolveText(conflictedCode, "theirs")
    assertTrue(resolvedTheirs.contains("val version = \"1.1.0-upstream\""))
    assertFalse(ConflictResolverUtil.hasConflictMarkers(resolvedTheirs))
  }
}
