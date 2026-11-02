package dev.gaphunter.dockerfileaudit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ScannerTest {

    private fun tempTree(): File {
        val root = Files.createTempDirectory("dockerfile-audit-test").toFile()
        root.deleteOnExit()
        return root
    }

    private fun write(root: File, relativePath: String, content: String) {
        val f = File(root, relativePath)
        f.parentFile.mkdirs()
        f.writeText(content.trimIndent())
    }

    @Test
    fun `flags a root-running Dockerfile and reports its relative path`() {
        val root = tempTree()
        write(root, "Dockerfile", "FROM node:20\nCMD [\"node\", \"index.js\"]")

        val hits = Scanner.scan(root)

        assertEquals(1, hits.size)
        assertEquals("Dockerfile", hits[0].file)
        assertEquals(Scanner.MISSING_USER, hits[0].kind)
        assertEquals(1, hits[0].line)
    }

    @Test
    fun `does not flag a Dockerfile that drops to a non-root USER`() {
        val root = tempTree()
        write(root, "Dockerfile", "FROM node:20\nUSER appuser\nCMD [\"node\", \"index.js\"]")

        assertTrue(Scanner.scan(root).isEmpty())
    }

    @Test
    fun `finds nested Dockerfiles and ignores non-Dockerfile files`() {
        val root = tempTree()
        write(root, "services/api/Dockerfile", "FROM python:3.12\nCMD [\"python\", \"app.py\"]")
        write(root, "README.md", "FROM node:20\n# this is prose, not a Dockerfile")
        write(root, "docker-compose.yml", "services:\n  api:\n    build: .")

        val hits = Scanner.scan(root)

        assertEquals(1, hits.size)
        assertEquals("services/api/Dockerfile", hits[0].file)
    }

    @Test
    fun `skips dependency and build directories`() {
        val root = tempTree()
        write(root, "node_modules/some-dep/Dockerfile", "FROM node:20\nCMD [\"node\"]")
        write(root, "build/Dockerfile", "FROM node:20\nCMD [\"node\"]")

        assertTrue(Scanner.scan(root).isEmpty())
    }

    @Test
    fun `scans a single file passed directly, reporting just its name`() {
        val root = tempTree()
        write(root, "Dockerfile.prod", "FROM node:20\nCMD [\"node\", \"index.js\"]")

        val hits = Scanner.scan(File(root, "Dockerfile.prod"))

        assertEquals(1, hits.size)
        assertEquals("Dockerfile.prod", hits[0].file)
    }
}
