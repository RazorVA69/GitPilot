package com.example

import com.example.ui.components.terminal.SedCommandEngine
import com.example.ui.components.terminal.ShellScriptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalScriptAndSedTest {

    @Test
    fun `ShellScriptParser preserves semicolons pipes and escapes inside single quotes`() {
        val line1 = """sed -i 's/selector().withBuildType("release").withFlavor("licensing" to "foss")/selector().withBuildType("release")/; s/gallery-\${'$'}versionCode-foss\${'$'}{abiSuffix}/gallery-${'$'}versionCode${'$'}{abiSuffix}/' app/build.gradle.kts"""
        val topParts = ShellScriptParser.splitTopLevel(line1, ";")
        assertEquals(1, topParts.size)

        val pipeParts = ShellScriptParser.splitPipeline(line1)
        assertEquals(1, pipeParts.size)

        val tokens = ShellScriptParser.tokenize(line1)
        assertEquals("sed", tokens[0])
        assertEquals("-i", tokens[1])
        assertTrue(tokens[2].contains("""gallery-\${'$'}versionCode-foss\${'$'}{abiSuffix}"""))
        assertTrue(tokens[2].contains("""; s/gallery-"""))
        assertEquals("app/build.gradle.kts", tokens[3])
    }

    @Test
    fun `ShellScriptParser does not expand variables inside single quotes`() {
        val cmd = """sed -i 's/foo/${'$'}USER/g' file.txt && echo "${'$'}USER""""
        val substituted = ShellScriptParser.substituteVariables(cmd, mapOf("USER" to "BlazeFTL"))
        assertEquals("""sed -i 's/foo/${'$'}USER/g' file.txt && echo "BlazeFTL"""", substituted)

        val chain = ShellScriptParser.splitConditionalChain(substituted)
        assertEquals(2, chain.size)
        assertEquals("""sed -i 's/foo/${'$'}USER/g' file.txt""", chain[0].first)
        assertEquals("&&", chain[0].second)
        assertEquals("""echo "BlazeFTL"""", chain[1].first)
        assertEquals(null, chain[1].second)
    }

    @Test
    fun `ShellScriptParser handles pipes inside single quotes without splitting`() {
        val line4 = """sed -i 's|<bool name="require_all_files_access">true</bool>|&\n    <bool name="show_donate_in_about">true</bool>\n    <bool name="is_google_play_build">false</bool>|' app/src/main/res/values/bools.xml"""
        assertEquals(1, ShellScriptParser.splitPipeline(line4).size)

        val piped = """cat README.md | sed 's/foo/bar/g' | grep bar"""
        val stages = ShellScriptParser.splitPipeline(piped)
        assertEquals(3, stages.size)
        assertEquals("cat README.md", stages[0])
        assertEquals("sed 's/foo/bar/g'", stages[1])
        assertEquals("grep bar", stages[2])
    }

    @Test
    fun `SedCommandEngine runs pasted gradle bools and readme edits accurately`() {
        // 1. Range delete + substitution + blank line collapse on app/build.gradle.kts
        val originalGradle = """
            android {
                flavorDimensions.add("licensing")
                productFlavors {
                    create("foss") {}
                }

                variantFilter {
                    selector().withBuildType("release").withFlavor("licensing" to "foss")
                }


                applicationVariants.all {
                    val name = "gallery-${'$'}versionCode-foss${'$'}{abiSuffix}.apk"
                }
            }
        """.trimIndent() + "\n"

        val step1Tokens = ShellScriptParser.tokenize("""sed -i '/flavorDimensions.add("licensing")/,/^    }$/d' app/build.gradle.kts""")
        val step1Parsed = SedCommandEngine.parseArgs(step1Tokens.drop(1))
        assertTrue(step1Parsed.inPlace)
        assertEquals(listOf("app/build.gradle.kts"), step1Parsed.files)

        // Note: in our sample the inner block ends with 4 spaces + }
        val afterStep1 = SedCommandEngine.transform(originalGradle, step1Parsed.expressions.joinToString("\n"))
        assertFalse(afterStep1.contains("flavorDimensions"))
        assertFalse(afterStep1.contains("productFlavors"))

        val step2Tokens = ShellScriptParser.tokenize(
            """sed -i 's/selector().withBuildType("release").withFlavor("licensing" to "foss")/selector().withBuildType("release")/; s/gallery-\${'$'}versionCode-foss\${'$'}{abiSuffix}/gallery-${'$'}versionCode${'$'}{abiSuffix}/' app/build.gradle.kts"""
        )
        val step2Parsed = SedCommandEngine.parseArgs(step2Tokens.drop(1))
        val afterStep2 = SedCommandEngine.transform(afterStep1, step2Parsed.expressions.joinToString("\n"))
        assertTrue(afterStep2.contains("""selector().withBuildType("release")"""))
        assertFalse(afterStep2.contains("""withFlavor"""))
        assertTrue(afterStep2.contains("""gallery-${'$'}versionCode${'$'}{abiSuffix}.apk"""))

        val step3Tokens = ShellScriptParser.tokenize("""sed -i '/^$/N;/^\n$/D' app/build.gradle.kts""")
        val step3Parsed = SedCommandEngine.parseArgs(step3Tokens.drop(1))
        val afterStep3 = SedCommandEngine.transform(afterStep2, step3Parsed.expressions.joinToString("\n"))
        assertFalse(afterStep3.contains("\n\n\n"))

        // 2. bools.xml insertion using & and \n
        val originalBools = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <bool name="require_all_files_access">true</bool>
            </resources>
        """.trimIndent() + "\n"

        val boolsTokens = ShellScriptParser.tokenize(
            """sed -i 's|<bool name="require_all_files_access">true</bool>|&\n    <bool name="show_donate_in_about">true</bool>\n    <bool name="is_google_play_build">false</bool>|' app/src/main/res/values/bools.xml"""
        )
        val boolsParsed = SedCommandEngine.parseArgs(boolsTokens.drop(1))
        val afterBools = SedCommandEngine.transform(originalBools, boolsParsed.expressions.joinToString("\n"))
        assertTrue(afterBools.contains("""<bool name="require_all_files_access">true</bool>"""))
        assertTrue(afterBools.contains("""<bool name="show_donate_in_about">true</bool>"""))
        assertTrue(afterBools.contains("""<bool name="is_google_play_build">false</bool>"""))

        // 3. README.md multi-command sed with | inside single quotes
        val originalReadme = """
            | Feature | Details |
            | Flavor | foss / gplay |
            | Architecture | universal |
            | R8 | Enabled |
            | Publish release | Pre-release |
            Run `./gradlew assembleFossRelease` at https://example.com?a=1&include_prereleases
        """.trimIndent() + "\n"

        val readmeTokens = ShellScriptParser.tokenize(
            """sed -i '/^| Flavor |/d;/^| Architecture |/d;/^| Upload artifacts |/d;/^| Shrink resources |/d;/^| Image optimization |/d;s/^| R8 | .*/| Optimize build | R8, resource shrinking and PNG crunching (release only) |/;s/^| Publish release | .*/| Create release | Publishes the APKs as a release |/;s/assembleFossRelease/assembleRelease/;s/&include_prereleases//' README.md"""
        )
        val readmeParsed = SedCommandEngine.parseArgs(readmeTokens.drop(1))
        val afterReadme = SedCommandEngine.transform(originalReadme, readmeParsed.expressions.joinToString("\n"))
        assertFalse(afterReadme.contains("| Flavor |"))
        assertFalse(afterReadme.contains("| Architecture |"))
        assertTrue(afterReadme.contains("| Optimize build | R8, resource shrinking and PNG crunching (release only) |"))
        assertTrue(afterReadme.contains("| Create release | Publishes the APKs as a release |"))
        assertTrue(afterReadme.contains("assembleRelease"))
        assertFalse(afterReadme.contains("&include_prereleases"))
    }

    @Test
    fun `SedCommandEngine handles macOS style empty backup suffix after -i`() {
        val tokens = ShellScriptParser.tokenize("""sed -i '' 's/foo/bar/' sample.txt""")
        val parsed = SedCommandEngine.parseArgs(tokens.drop(1))
        assertTrue(parsed.inPlace)
        assertEquals(listOf("s/foo/bar/"), parsed.expressions)
        assertEquals(listOf("sample.txt"), parsed.files)
        assertEquals("bar baz\n", SedCommandEngine.transform("foo baz\n", parsed.expressions.joinToString("\n")))
    }
}
