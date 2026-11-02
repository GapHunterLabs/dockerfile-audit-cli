package dev.gaphunter.dockerfileaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockerfileUserScannerTest {

    @Test
    fun `flags a Dockerfile with no USER instruction at all`() {
        val dockerfile = """
            FROM node:20
            COPY . .
            CMD ["node", "index.js"]
        """.trimIndent()

        val hits = DockerfileUserScanner.scan(dockerfile)

        assertEquals(1, hits.size)
        assertEquals(1, hits[0].anchorLineNumber)
    }

    @Test
    fun `flags a Dockerfile whose only USER is root`() {
        val dockerfile = """
            FROM node:20
            USER root
            CMD ["node", "index.js"]
        """.trimIndent()

        assertEquals(1, DockerfileUserScanner.scan(dockerfile).size)
    }

    @Test
    fun `flags a Dockerfile whose only USER is 0`() {
        val dockerfile = """
            FROM node:20
            USER 0
            CMD ["node", "index.js"]
        """.trimIndent()

        assertEquals(1, DockerfileUserScanner.scan(dockerfile).size)
    }

    @Test
    fun `does not flag a Dockerfile with a real non-root USER`() {
        val dockerfile = """
            FROM node:20
            RUN useradd appuser
            USER appuser
            CMD ["node", "index.js"]
        """.trimIndent()

        assertTrue(DockerfileUserScanner.scan(dockerfile).isEmpty())
    }

    @Test
    fun `does not flag USER with a group suffix that is non-root`() {
        val dockerfile = """
            FROM node:20
            USER appuser:appgroup
            CMD ["node", "index.js"]
        """.trimIndent()

        assertTrue(DockerfileUserScanner.scan(dockerfile).isEmpty())
    }

    @Test
    fun `flags when the final stage switches back to root after a non-root USER`() {
        val dockerfile = """
            FROM node:20
            USER appuser
            USER root
            CMD ["node", "index.js"]
        """.trimIndent()

        assertEquals(1, DockerfileUserScanner.scan(dockerfile).size)
    }

    @Test
    fun `only checks the FINAL stage in a multi-stage build -- USER in an earlier stage never counts`() {
        val dockerfile = """
            FROM node:20 AS build
            USER appuser
            RUN npm install

            FROM node:20 AS final
            COPY --from=build /app /app
            CMD ["node", "index.js"]
        """.trimIndent()

        val hits = DockerfileUserScanner.scan(dockerfile)

        assertEquals(1, hits.size)
        // The final stage's own FROM line, not the first stage's.
        assertEquals(dockerfile.lines().indexOfFirst { it.contains("AS final") } + 1, hits[0].anchorLineNumber)
    }

    @Test
    fun `does not flag when the final stage of a multi-stage build has its own non-root USER`() {
        val dockerfile = """
            FROM node:20 AS build
            RUN npm install

            FROM node:20 AS final
            COPY --from=build /app /app
            USER appuser
            CMD ["node", "index.js"]
        """.trimIndent()

        assertTrue(DockerfileUserScanner.scan(dockerfile).isEmpty())
    }

    @Test
    fun `does not flag when the final stage inherits a non-root USER from a named base stage it builds on`() {
        val dockerfile = """
            FROM node:20 AS base
            RUN useradd -m appuser
            USER appuser

            FROM base AS final
            COPY --chown=appuser:appuser . .
            CMD ["node", "index.js"]
        """.trimIndent()

        assertTrue(DockerfileUserScanner.scan(dockerfile).isEmpty())
    }

    @Test
    fun `flags when the final stage builds on a named stage that never set a non-root USER`() {
        val dockerfile = """
            FROM node:20 AS base
            RUN echo "no user switch here"

            FROM base AS final
            CMD ["node", "index.js"]
        """.trimIndent()

        val hits = DockerfileUserScanner.scan(dockerfile)
        assertEquals(1, hits.size)
        assertEquals(dockerfile.lines().indexOfFirst { it.contains("AS final") } + 1, hits[0].anchorLineNumber)
    }

    @Test
    fun `does not flag when the final stage builds on a chain of two named stages ending in non-root`() {
        val dockerfile = """
            FROM node:20 AS base
            RUN useradd -m appuser
            USER appuser

            FROM base AS deps
            RUN npm install

            FROM deps AS final
            CMD ["node", "index.js"]
        """.trimIndent()

        assertTrue(DockerfileUserScanner.scan(dockerfile).isEmpty())
    }

    @Test
    fun `returns empty for a file with no FROM at all`() {
        assertTrue(DockerfileUserScanner.scan("# just a comment\nUSER appuser\n").isEmpty())
    }

    @Test
    fun `flags a root Dockerfile even when the file begins with a UTF-8 BOM`() {
        val dockerfile = "﻿FROM node:20\nCMD [\"node\", \"index.js\"]"

        val hits = DockerfileUserScanner.scan(dockerfile)

        assertEquals(1, hits.size)
        assertEquals(1, hits[0].anchorLineNumber)
    }

    @Test
    fun `skips a leading --platform flag on the FROM line`() {
        val dockerfile = """
            FROM --platform=linux/amd64 node:20 AS final
            CMD ["node", "index.js"]
        """.trimIndent()

        assertEquals(1, DockerfileUserScanner.scan(dockerfile).size)
    }
}
