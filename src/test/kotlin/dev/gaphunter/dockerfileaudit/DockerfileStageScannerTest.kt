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

    // --- stage inheritance through FROM <stage> -------------------------------

    @Test
    fun `the four-stage dotnet pattern inherits via FROM and is clean`() {
        // base -> final and build -> publish are FROM references; publish -> final is a COPY --from.
        val text = """
            FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS base
            WORKDIR /app

            FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
            COPY . .
            RUN dotnet build -c Release

            FROM build AS publish
            RUN dotnet publish -c Release -o /app/publish

            FROM base AS final
            COPY --from=publish /app/publish .
        """.trimIndent()
        assertTrue(DockerfileStageScanner.scan(text).isEmpty())
    }

    @Test
    fun `a stage used only as the base of a later FROM is not flagged`() {
        val text = """
            FROM golang:1.21 AS toolchain
            RUN go install something

            FROM toolchain
            RUN go build -o app
        """.trimIndent()
        assertTrue(DockerfileStageScanner.scan(text).isEmpty())
    }

    @Test
    fun `a FROM reference clears only the inherited stage, not the inheriting one`() {
        // 'builder' is inherited by 'tester'; 'tester' itself is neither inherited, copied from nor final.
        val text = """
            FROM golang:1.21 AS builder
            RUN go build -o app

            FROM builder AS tester
            RUN go test ./...

            FROM alpine:3.19
            COPY --from=builder /app /app
        """.trimIndent()
        val hits = DockerfileStageScanner.scan(text)
        assertEquals(listOf("tester"), hits.map { it.stageName })
    }

    @Test
    fun `FROM stage reference is case-insensitive`() {
        val text = """
            FROM alpine:3.19 AS Base
            FROM base AS final
            RUN echo hello
        """.trimIndent()
        assertTrue(DockerfileStageScanner.scan(text).isEmpty())
    }

    @Test
    fun `a FROM whose base only resembles a stage name is an image, so the stage stays flagged`() {
        // 'alpine:3.19' is not the stage 'alpine': the tag makes it an image reference.
        val text = """
            FROM golang:1.21 AS alpine
            RUN go build -o app

            FROM alpine:3.19
            COPY app /app
        """.trimIndent()
        assertEquals(listOf("alpine"), DockerfileStageScanner.scan(text).map { it.stageName })
    }

    @Test
    fun `a FROM that names a stage defined later refers to an image, not the stage`() {
        // Docker only resolves stage names defined above the FROM; 'cache' here is an image named cache.
        val text = """
            FROM cache AS warm
            RUN echo warm

            FROM alpine:3.19 AS cache
            RUN echo cache

            FROM alpine:3.19
            RUN echo final
        """.trimIndent()
        val hits = DockerfileStageScanner.scan(text).map { it.stageName }
        assertEquals(listOf("warm", "cache"), hits)
    }

    @Test
    fun `a build-argument base is never a stage reference`() {
        val text = """
            ARG BASE=alpine:3.19
            FROM golang:1.21 AS builder
            RUN go build -o app

            FROM ${'$'}{BASE}
            COPY app /app
        """.trimIndent()
        assertEquals(listOf("builder"), DockerfileStageScanner.scan(text).map { it.stageName })
    }

    // --- --platform flag ------------------------------------------------------

    @Test
    fun `a stage declared with --platform is detected and flagged when unused`() {
        val text = """
            FROM --platform=${'$'}BUILDPLATFORM golang:1.21 AS builder
            RUN go build -o app

            FROM alpine:3.19
            COPY app /app
        """.trimIndent()
        assertEquals(listOf("builder"), DockerfileStageScanner.scan(text).map { it.stageName })
    }

    @Test
    fun `a stage declared with --platform and inherited via FROM is clean`() {
        val text = """
            FROM --platform=linux/amd64 golang:1.21 AS builder
            RUN go build -o app

            FROM --platform=linux/amd64 builder AS final
            RUN echo done
        """.trimIndent()
        assertTrue(DockerfileStageScanner.scan(text).isEmpty())
    }
}
