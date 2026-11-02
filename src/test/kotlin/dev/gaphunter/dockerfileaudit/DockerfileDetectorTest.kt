package dev.gaphunter.dockerfileaudit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockerfileDetectorTest {

    @Test
    fun `recognises the canonical name regardless of case`() {
        assertTrue(DockerfileDetector.isDockerfile("Dockerfile"))
        assertTrue(DockerfileDetector.isDockerfile("dockerfile"))
        assertTrue(DockerfileDetector.isDockerfile("DOCKERFILE"))
    }

    @Test
    fun `recognises the dot-dockerfile extension form`() {
        assertTrue(DockerfileDetector.isDockerfile("app.dockerfile"))
        assertTrue(DockerfileDetector.isDockerfile("node.Dockerfile"))
    }

    @Test
    fun `recognises a real tagged variant`() {
        assertTrue(DockerfileDetector.isDockerfile("Dockerfile.prod"))
        assertTrue(DockerfileDetector.isDockerfile("Dockerfile.arm64"))
    }

    @Test
    fun `rejects a documentation file that merely starts with dockerfile`() {
        assertFalse(DockerfileDetector.isDockerfile("Dockerfile.md"))
        assertFalse(DockerfileDetector.isDockerfile("Dockerfile.markdown"))
        assertFalse(DockerfileDetector.isDockerfile("Dockerfile.rst"))
        assertFalse(DockerfileDetector.isDockerfile("Dockerfile.adoc"))
    }

    @Test
    fun `rejects unrelated files`() {
        assertFalse(DockerfileDetector.isDockerfile("README.md"))
        assertFalse(DockerfileDetector.isDockerfile("docker-compose.yml"))
        assertFalse(DockerfileDetector.isDockerfile("Makefile"))
    }
}
