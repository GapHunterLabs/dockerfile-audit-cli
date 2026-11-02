package dev.gaphunter.dockerfileaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OutputFormatTest {

    private val sampleHit = ScanHit(
        file = "services/api/Dockerfile",
        line = 3,
        column = 1,
        kind = Scanner.MISSING_USER,
        description = "Final build stage has no non-root USER instruction -- the container runs as root by default",
    )

    @Test
    fun `text output reports a clean no-findings line`() {
        assertEquals("dockerfile-audit-cli: no findings in .", renderText(emptyList(), File(".")))
    }

    @Test
    fun `text output lists each finding with path, line, column and rule`() {
        val out = renderText(listOf(sampleHit), File("."))
        assertTrue(out.contains("services/api/Dockerfile:3:1: [DOCKERFILE_MISSING_USER]"))
        assertTrue(out.trim().endsWith("dockerfile-audit-cli: 1 finding(s) in ."))
    }

    @Test
    fun `sarif output carries the tool identity and the finding`() {
        val out = renderSarif(listOf(sampleHit))
        assertTrue(out.contains("\"name\": \"dockerfile-audit-cli\""))
        assertTrue(out.contains("\"version\": \"2.1.0\""))
        assertTrue(out.contains("\"ruleId\": \"DOCKERFILE_MISSING_USER\""))
        assertTrue(out.contains("\"startLine\": 3"))
        assertTrue(out.contains("\"level\": \"warning\""))
    }

    @Test
    fun `sarif output is well-formed with no findings`() {
        val out = renderSarif(emptyList())
        assertTrue(out.contains("\"name\": \"dockerfile-audit-cli\""))
        assertTrue(out.contains("\"results\": ["))
        // Balanced braces -- a crude but real guard against a broken template.
        assertEquals(out.count { it == '{' }, out.count { it == '}' })
    }
}
