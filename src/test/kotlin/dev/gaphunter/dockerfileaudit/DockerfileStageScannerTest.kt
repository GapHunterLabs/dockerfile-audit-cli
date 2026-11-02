package dev.gaphunter.dockerfileaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockerfileStageScannerTest {

    @Test
    fun `an intermediate stage never referenced is flagged`() {
        val text = """
            FROM golang:1.21 AS builder
            RUN go build -o app

            FROM alpine:3.19
            COPY app /app
        """.trimIndent()
        val hits = DockerfileStageScanner.scan(text)
        assertEquals(1, hits.size)
        assertEquals("builder", hits[0].stageName)
    }

    @Test
    fun `a stage referenced by COPY --from is not flagged`() {
        val text = """
            FROM golang:1.21 AS builder
            RUN go build -o app

            FROM alpine:3.19
            COPY --from=builder /app /app
        """.trimIndent()
        assertTrue(DockerfileStageScanner.scan(text).isEmpty())
    }

    @Test
    fun `the final stage is never flagged even if unnamed reference target`() {
        val text = """
            FROM golang:1.21 AS builder
            RUN go build -o app

            FROM alpine:3.19 AS final
            COPY --from=builder /app /app
        """.trimIndent()
        assertTrue(DockerfileStageScanner.scan(text).isEmpty())
    }

    @Test
    fun `a single-stage Dockerfile is never flagged`() {
        val text = """
            FROM alpine:3.19
            RUN echo hello
        """.trimIndent()
        assertTrue(DockerfileStageScanner.scan(text).isEmpty())
    }

    @Test
    fun `stage name matching is case-insensitive`() {
        val text = """
            FROM golang:1.21 AS Builder
            FROM alpine:3.19
            COPY --from=builder /app /app
        """.trimIndent()
        assertTrue(DockerfileStageScanner.scan(text).isEmpty())
    }
}
