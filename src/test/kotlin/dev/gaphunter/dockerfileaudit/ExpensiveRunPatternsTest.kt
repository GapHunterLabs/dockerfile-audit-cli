package dev.gaphunter.dockerfileaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpensiveRunPatternsTest {

    private fun runInstruction(argsText: String) = DockerfileParser.parse("RUN $argsText").first()

    @Test
    fun `flags apt-get install without --no-install-recommends`() {
        val reasons = ExpensiveRunPatterns.check(runInstruction("apt-get install -y curl"))
        assertTrue(reasons.any { it.contains("--no-install-recommends") })
    }

    @Test
    fun `does not flag apt-get install with --no-install-recommends`() {
        val reasons = ExpensiveRunPatterns.check(runInstruction("apt-get install -y --no-install-recommends curl"))
        assertTrue(reasons.none { it.contains("--no-install-recommends") })
    }

    @Test
    fun `flags apt-get update without cleaning apt lists in same RUN`() {
        val reasons = ExpensiveRunPatterns.check(runInstruction("apt-get update && apt-get install -y curl"))
        assertTrue(reasons.any { it.contains("var/lib/apt/lists") })
    }

    @Test
    fun `does not flag apt-get update when cleaned in same RUN`() {
        val reasons = ExpensiveRunPatterns.check(
            runInstruction("apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*"),
        )
        assertTrue(reasons.none { it.contains("var/lib/apt/lists") })
    }

    @Test
    fun `flags pip install without --no-cache-dir`() {
        val reasons = ExpensiveRunPatterns.check(runInstruction("pip install -r requirements.txt"))
        assertTrue(reasons.any { it.contains("--no-cache-dir") })
    }

    @Test
    fun `does not flag pip install with --no-cache-dir`() {
        val reasons = ExpensiveRunPatterns.check(runInstruction("pip install --no-cache-dir -r requirements.txt"))
        assertTrue(reasons.none { it.contains("--no-cache-dir") })
    }

    @Test
    fun `flags npm install without production flag`() {
        val reasons = ExpensiveRunPatterns.check(runInstruction("npm install"))
        assertTrue(reasons.any { it.contains("npm ci") || it.contains("devDependencies") })
    }

    @Test
    fun `does not flag npm ci`() {
        val reasons = ExpensiveRunPatterns.check(runInstruction("npm ci --production"))
        assertTrue(reasons.none { it.contains("devDependencies") })
    }

    @Test
    fun `flags curl download without visible cleanup`() {
        val reasons = ExpensiveRunPatterns.check(runInstruction("curl -O https://example.com/installer.sh && sh installer.sh"))
        assertTrue(reasons.any { it.contains("cleanup") })
    }

    @Test
    fun `does not flag curl download with cleanup in same RUN`() {
        val reasons = ExpensiveRunPatterns.check(
            runInstruction("curl -O https://example.com/installer.sh && sh installer.sh && rm installer.sh"),
        )
        assertTrue(reasons.none { it.contains("cleanup") })
    }

    @Test
    fun `does not flag installing the curl or wget package itself as an undownloaded-cleanup download`() {
        val aptReasons = ExpensiveRunPatterns.check(
            runInstruction("apt-get update && apt-get install -y --no-install-recommends curl && update-ca-certificates"),
        )
        assertTrue(aptReasons.none { it.contains("cleanup") })

        val apkReasons = ExpensiveRunPatterns.check(runInstruction("apk add --no-cache wget ca-certificates"))
        assertTrue(apkReasons.none { it.contains("cleanup") })
    }

    @Test
    fun `still flags a real curl invocation even in a RUN that also installs curl as a package`() {
        val reasons = ExpensiveRunPatterns.check(
            runInstruction("apt-get install -y curl && curl -O https://example.com/x.tar.gz"),
        )
        assertTrue(reasons.any { it.contains("cleanup") })
    }

    @Test
    fun `clean multi-step RUN produces no reasons`() {
        val reasons = ExpensiveRunPatterns.check(
            runInstruction(
                "apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*",
            ),
        )
        assertEquals(emptyList<String>(), reasons)
    }

    @Test
    fun `finds groups of 2 or more consecutive RUN instructions`() {
        val instructions = DockerfileParser.parse(
            """
            FROM node:20
            RUN echo one
            RUN echo two
            RUN echo three
            WORKDIR /app
            RUN echo four
            """.trimIndent(),
        )

        val groups = ExpensiveRunPatterns.findCombinableRuns(instructions)

        assertEquals(1, groups.size)
        assertEquals(3, groups[0].size)
    }

    @Test
    fun `does not flag a single isolated RUN as combinable`() {
        val instructions = DockerfileParser.parse(
            """
            FROM node:20
            RUN echo one
            WORKDIR /app
            RUN echo two
            """.trimIndent(),
        )

        val groups = ExpensiveRunPatterns.findCombinableRuns(instructions)

        assertEquals(0, groups.size)
    }

    @Test
    fun `joins backslash-continued RUN lines into one instruction`() {
        val instructions = DockerfileParser.parse(
            "FROM node:20\nRUN apt-get update \\\n && apt-get install -y curl",
        )
        val run = instructions.first { it.keyword == "RUN" }
        assertTrue(run.argsText.contains("apt-get update"))
        assertTrue(run.argsText.contains("apt-get install -y curl"))
        // One logical RUN, anchored at its first line (0-based index 1).
        assertEquals(1, run.lineNumber)
    }
}
