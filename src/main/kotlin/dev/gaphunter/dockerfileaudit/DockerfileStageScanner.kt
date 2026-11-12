package dev.gaphunter.dockerfileaudit

/**
 * Plain-text line scanner for a multi-stage Dockerfile's named build
 * stages (`FROM <image> AS <name>`) -- flags a stage that is never
 * referenced by a later instruction and isn't the file's own final
 * stage (the last `FROM` in the file, which is always the real build
 * target for a plain `docker build` with no `--target`). A later
 * instruction references a stage in one of three ways:
 *
 *  - `COPY --from=<name>` (or `--from=<name>` inside a later
 *    `RUN --mount=...,from=<name>`);
 *  - `FROM <name>` / `FROM <name> AS <other>`, the way the common
 *    `base` -> `final` and `build` -> `publish` chains inherit from an
 *    earlier stage. Docker resolves the base of a `FROM` against the
 *    stages defined *above* it before treating it as an image, so only
 *    an earlier stage counts; a later homonym is an image reference.
 *
 * An orphaned intermediate stage is real, wasted build time and a
 * confusing read for the next person touching the file.
 *
 * Manual, deliberate port of the scanner inside Gap Hunter Labs'
 * dockerfile-unused-stage-companion IntelliJ plugin -- identical
 * detection logic, kept as a standalone copy (mirror any rule change in
 * both).
 *
 * **Scope, stated honestly:** a stage referenced only via an explicit
 * `docker build --target=<name>` (not visible in the Dockerfile text
 * itself) will be flagged as a false positive -- this tool can't see
 * build invocations outside the file. Stage names are matched
 * case-insensitively (Docker itself is case-insensitive here). A
 * `FROM` whose base is a build argument (`FROM ${BASE}`) is treated as
 * an image, never as a stage reference.
 */
object DockerfileStageScanner {

    /** `FROM [--platform=<p>] <base> [AS <name>]`: group 1 is the base, group 2 the optional stage name. */
    private val FROM_PARTS = Regex(
        """^FROM\s+(?:--platform=\S+\s+)?(\S+)(?:\s+AS\s+([A-Za-z0-9_.-]+))?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val FROM_LINE = Regex("""^FROM\s+""", RegexOption.IGNORE_CASE)
    private val COPY_FROM_REF = Regex("""--from=([A-Za-z0-9_.-]+)""", RegexOption.IGNORE_CASE)

    fun scan(text: String): List<StageHit> {
        val lines = text.removePrefix("﻿").lines()
        val stages = mutableListOf<Pair<String, Int>>() // name to line number (1-based)
        val referenced = mutableSetOf<String>()
        var lastFromLineIndex = -1

        lines.forEachIndexed { index, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed

            if (FROM_LINE.containsMatchIn(trimmed)) {
                lastFromLineIndex = index
                val parts = FROM_PARTS.find(trimmed)
                if (parts != null) {
                    val base = parts.groupValues[1].lowercase()
                    // Only a stage already defined above can be inherited from; a later
                    // stage with the same name does not make this base a stage reference.
                    if (stages.any { (name, _) -> name == base }) {
                        referenced += base
                    }
                    val stageName = parts.groupValues[2]
                    if (stageName.isNotEmpty()) {
                        stages += stageName.lowercase() to (index + 1)
                    }
                }
            }

            for (refMatch in COPY_FROM_REF.findAll(trimmed)) {
                referenced += refMatch.groupValues[1].lowercase()
            }
        }

        if (stages.isEmpty() || lastFromLineIndex < 0) return emptyList()
        val finalStageLine = lastFromLineIndex + 1

        return stages
            .filter { (_, lineNumber) -> lineNumber != finalStageLine }
            .filter { (name, _) -> name !in referenced }
            .map { (name, lineNumber) -> StageHit(name, lineNumber) }
    }
}
