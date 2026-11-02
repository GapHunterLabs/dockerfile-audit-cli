package dev.gaphunter.dockerfileaudit

import java.io.File

data class ScanHit(
    val file: String,
    val line: Int,
    val column: Int,
    val kind: String,
    val description: String,
)

object Scanner {

    const val MISSING_USER = "DOCKERFILE_MISSING_USER"
    const val UNUSED_STAGE = "DOCKERFILE_UNUSED_STAGE"
    const val EXPENSIVE_RUN = "DOCKERFILE_EXPENSIVE_RUN"
    const val COMBINABLE_RUN = "DOCKERFILE_COMBINABLE_RUN"

    private const val MISSING_USER_MESSAGE =
        "Final build stage has no non-root USER instruction -- the container runs as root by default"

    // Directories never worth descending into: VCS metadata, build output,
    // dependency caches. Keeps a scan of a whole repo fast and avoids
    // reporting findings buried in third-party/generated code nobody owns.
    private val SKIP_DIRS = setOf(
        ".git", ".gradle", ".idea", "node_modules", "build", "dist",
        "target", "out", "vendor", ".venv", "venv", "__pycache__",
    )

    /**
     * Scans [root] for Dockerfiles and reports every final build stage
     * that runs as root. [root] may be a directory (walked recursively,
     * skipping VCS/build/dependency dirs) or a single file. Files are
     * recognised as Dockerfiles by name only (see [DockerfileDetector]):
     * a file passed directly that is not named like a Dockerfile yields
     * no findings, same contract as the editor plugin.
     */
    fun scan(root: File): List<ScanHit> {
        // Report paths relative to the directory being scanned; for a single
        // file, relative to its own parent so the output is just the name.
        val base = if (root.isFile) (root.absoluteFile.parentFile ?: File(".")) else root
        val files = if (root.isFile) {
            sequenceOf(root)
        } else {
            root.walkTopDown().onEnter { dir -> dir.name !in SKIP_DIRS }.filter { it.isFile }
        }
        return files
            .filter { DockerfileDetector.isDockerfile(it.name) }
            .flatMap { scanFile(it, base).asSequence() }
            .toList()
    }

    private fun scanFile(file: File, base: File): List<ScanHit> {
        val relativePath = file.relativeToOrSelf(base).path.replace('\\', '/')
        val text = try {
            file.readText()
        } catch (e: Exception) {
            // Unreadable despite a Dockerfile-looking name (binary content,
            // odd encoding, permissions) -- skip it, never crash the whole
            // scan over one file.
            return emptyList()
        }
        val hits = mutableListOf<ScanHit>()

        // Security: the final build stage runs as root.
        DockerfileUserScanner.scan(text).mapTo(hits) { hit ->
            ScanHit(relativePath, hit.anchorLineNumber, 1, MISSING_USER, MISSING_USER_MESSAGE)
        }

        // Hygiene: a named build stage nothing ever copies from.
        DockerfileStageScanner.scan(text).mapTo(hits) { hit ->
            ScanHit(
                relativePath, hit.lineNumber, 1, UNUSED_STAGE,
                "Build stage '${hit.stageName}' is never used by a later COPY --from and is not the final stage -- wasted build time",
            )
        }

        // Hygiene: known-expensive RUN patterns that bloat a layer.
        val instructions = DockerfileParser.parse(text)
        for (instruction in instructions) {
            if (!DockerfileParser.isRunKeyword(instruction.keyword)) continue
            val reasons = ExpensiveRunPatterns.check(instruction)
            if (reasons.isNotEmpty()) {
                hits += ScanHit(
                    relativePath, instruction.lineNumber + 1, 1, EXPENSIVE_RUN,
                    reasons.joinToString(" | "),
                )
            }
        }
        for (group in ExpensiveRunPatterns.findCombinableRuns(instructions)) {
            val last = group.last()
            hits += ScanHit(
                relativePath, last.lineNumber + 1, 1, COMBINABLE_RUN,
                "${group.size} consecutive RUN instructions could be combined into one to save per-layer overhead",
            )
        }

        return hits
    }
}
